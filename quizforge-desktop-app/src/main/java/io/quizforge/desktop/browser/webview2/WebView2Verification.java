package io.quizforge.desktop.browser.webview2;

import com.fasterxml.jackson.databind.JsonNode;
import io.quizforge.infrastructure.json.DocumentJson;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

/** Opt-in integration acceptance driver: actual Chromium input, existing extension and Core persistence. */
final class WebView2Verification {
    static volatile boolean passed;
    private final WebView2Browser browser;
    private final WebView2PracticeSession session;
    private final List<String> checks=new ArrayList<>();
    private final Map<String,Double> timings=new LinkedHashMap<>();
    private WebView2Verification(WebView2Browser browser,WebView2PracticeSession session){this.browser=browser;this.session=session;}
    private interface ModeCommand{void set(String mode)throws Exception;default boolean navigationReady()throws Exception{return true;}}
    private interface ExtraChecks{void run(WebView2Verification driver)throws Exception;}
    /** Focused current-SDK check, including recovery after a host navigation failure. */
    static void runNavigation(io.quizforge.desktop.ui.question.practice.PracticeSurfaceHost host,
            io.quizforge.core.practice.PersistentPracticeRuntime runtime,Stage stage,Path directory){
        CompletableFuture.runAsync(()->{
            var surface=(WebView2LearningSurface)host.learningSurface();
            var driver=new WebView2Verification(surface.browser(),surface.session());
            try {
                driver.waitFor(()->!fx(host::busy));
                driver.child("document.querySelector('input[type=radio]').click();true");
                driver.waitFor(()->driver.selected()==1);
                for(int target:List.of(1,2,0,2,1,0)){
                    long start=System.nanoTime();
                    fx(()->host.navigate(()->{runtime.goTo(target);host.showQuestion();})).toCompletableFuture().get(25,TimeUnit.SECONDS);
                    driver.waitFor(()->driver.questionLoaded(target)&&!fx(host::busy));
                    driver.timings.put("switch"+driver.timings.size(),(System.nanoTime()-start)/1_000_000d);
                }
                driver.check(driver.selected()==1,"question switch retains persisted selection");
                driver.child("QF.requestAction({action:'goToQuestion',params:{direction:'next'}}).then(r=>window.navReply=r);true");
                driver.waitFor(()->driver.questionLoaded(1)&&!fx(host::busy));
                driver.check(true,"extension-origin navigation completes without waiting for itself");
                var failed=fx(()->host.navigate(()->{throw new IllegalStateException("Injected navigation failure");}));
                try{failed.toCompletableFuture().get(25,TimeUnit.SECONDS);throw new AssertionError("Failure not injected");}
                catch(ExecutionException expected){ }
                fx(()->host.navigate(()->{runtime.goTo(0);host.showQuestion();})).toCompletableFuture().get(25,TimeUnit.SECONDS);
                driver.waitFor(()->driver.questionLoaded(0)&&!fx(host::busy));
                driver.check(driver.selected()==1,"failed navigation recovers without losing answer or blocking next switch");
                Files.writeString(directory.resolve("navigation-verification.json"),DocumentJson.mapper().writerWithDefaultPrettyPrinter()
                        .writeValueAsString(Map.of("checks",driver.checks,"timingsMs",driver.timings)));
                passed=true;System.out.println("WEBVIEW2_NAVIGATION_VERIFY_PASS "+driver.timings);
            }catch(Exception|AssertionError failure){
                failure.printStackTrace();System.err.println("WEBVIEW2_NAVIGATION_VERIFY_FAILED "+failure);
                try{Files.writeString(directory.resolve("navigation-failure.json"),DocumentJson.mapper().writerWithDefaultPrettyPrinter().writeValueAsString(Map.of(
                        "checks",driver.checks,"timings",driver.timings,"error",failure.toString(),"page",driver.eval("({view:window.sharedPractice.getViewState(),error:document.querySelector('.practice-error:not([hidden])')?.textContent})"))));}catch(Exception ignored){ }
            }finally{Platform.runLater(()->{surface.abortVerification();stage.hide();});}
        });
    }
    static void runHistory(io.quizforge.desktop.ui.question.history.PracticeHistoryDetailView view,io.quizforge.core.practice.PracticeHistoryDetail detail,Stage stage,Path directory,javafx.scene.layout.BorderPane root){
        CompletableFuture.runAsync(()->{
            var nativeSurface=(WebView2HistorySurface)view.surface().learningSurface();
            var driver=new WebView2Verification(nativeSurface.browser(),null);
            try{
                driver.verifyHistory(view,detail,directory,root);
                Files.writeString(directory.resolve("verification.json"),DocumentJson.mapper().writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("checks",driver.checks)));
                passed=true;System.out.println("WEBVIEW2_HISTORY_VERIFY_PASS "+driver.checks);
            }catch(Exception|AssertionError failure){
                failure.printStackTrace();System.err.println("WEBVIEW2_HISTORY_VERIFY_FAILED "+failure.getMessage());
                try{
                    Files.writeString(directory.resolve("failure.json"),DocumentJson.mapper().writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("checks",driver.checks,"error",failure.toString(),"page",driver.eval("({view:window.historyDraftReplay.getViewState(),diagnostics:window.historyDraftReplay.diagnostics(),frames:document.querySelectorAll('iframe').length,error:document.querySelector('.practice-error:not([hidden])')?.textContent})"),"child",driver.child("({ready:document.querySelector('.qf-extension-page')?.dataset.qfReady,error:document.querySelector('[data-qf-error]')?.textContent})"))));
                    var shot=driver.cdp("Page.captureScreenshot",Map.of("format","png"));Files.write(directory.resolve("failure.png"),Base64.getDecoder().decode(shot.path("data").asText()));
                }catch(Exception ignored){}
            }finally{Platform.runLater(()->{view.destroy();stage.hide();});}
        });
    }
    private void historyLoaded(io.quizforge.desktop.ui.question.history.PracticeHistoryDetailView view,int index,String attempt)throws Exception{
        waitFor(()->!fx(()->view.surface().busy())&&eval("window.historyDraftReplay.getViewState()?.question.index").asInt(-1)==index&&Objects.equals(eval("window.historyDraftReplay.getViewState()?.question.result?.attemptId??null").isNull()?null:eval("window.historyDraftReplay.getViewState()?.question.result?.attemptId").asText(),attempt));
    }
    private void historyButton(io.quizforge.desktop.ui.question.history.PracticeHistoryDetailView view,String id)throws Exception{fx(()->{((javafx.scene.control.Button)view.lookup("#"+id)).fire();return null;});}
    private void verifyHistory(io.quizforge.desktop.ui.question.history.PracticeHistoryDetailView view,io.quizforge.core.practice.PracticeHistoryDetail detail,Path directory,javafx.scene.layout.BorderPane root)throws Exception{
        var first=detail.questions().getFirst().attempts().getFirst();var last=detail.questions().getFirst().attempts().getLast();
        historyLoaded(view,0,last.attemptId());String before=historyRecords(directory);Files.writeString(directory.resolve("records-before.json"),before);
        verifyNavigationStyle();
        Files.write(directory.resolve("navigation.png"),Base64.getDecoder().decode(cdp("Page.captureScreenshot",Map.of("format","png")).path("data").asText()));
        check(!eval("Boolean(window.nativePagesHost)").asBoolean(),"formal history uses direct browser frames");
        check(child("(()=>{try{void parent.document;return false;}catch(e){return e.name==='SecurityError';}})()").asBoolean(),"history frame cannot access trusted parent");
        child("window.chrome?.webview?.postMessage({kind:'chrome-action',action:'next',questionId:"+DocumentJson.mapper().writeValueAsString(detail.questions().getFirst().sessionQuestionId())+"});true");
        Thread.sleep(300);
        check(eval("window.historyDraftReplay.getViewState().question.index").asInt()==0,"native messages sent directly by a history frame are not accepted");
        check(eval("window.historyDraftReplay.diagnostics().accessMode==='READ_ONLY'&&typeof window.practiceHost==='undefined'&&typeof window.historyHost.submit==='undefined'").asBoolean(),"history exposes no active Practice or save host");
        check(eval("JSON.parse(window.draftCanvas.getDraft()).strokes[0].id==='ink-second'").asBoolean(),"latest attempt restores its own ink");
        check(eval("JSON.parse(window.draftCanvas.getDraft()).texts[0].id==='text-second'&&JSON.parse(window.draftCanvas.getDraft()).paper.color==='#fff7dc'").asBoolean(),"frozen text and paper are restored");
        check(child("Array.from(document.querySelectorAll('input[type=radio]')).every(n=>n.disabled)&&document.querySelector('input[type=radio]:checked')!==null").asBoolean(),"submitted selection stays visible and disabled");
        child("Promise.all([QF.answer.update({selectedOptionIds:[]}),QF.practice.submit(),QF.practice.retry(),QF.editor.update({}),QF.bank.save()]).then(r=>window.qfHistoryDenied=r);true");
        waitFor(()->child("window.qfHistoryDenied?.length===5").asBoolean());
        check(child("window.qfHistoryDenied.every(r=>!r.ok)").asBoolean(),"answer edits, submit, retry, editor and save are rejected");
        clickNavigation("previousAttempt");historyLoaded(view,0,first.attemptId());
        check(eval("JSON.parse(window.draftCanvas.getDraft()).strokes[0].id==='ink-first'").asBoolean(),"previous attempt restores different frozen ink");
        check(eval("window.historyDraftReplay.getViewState().question.result.status==='INCORRECT'").asBoolean(),"previous attempt retains original grading");
        clickNavigation("nextAttempt");historyLoaded(view,0,last.attemptId());
        fx(()->{view.surface().toggle();return null;});waitFor(()->eval("document.querySelector('#draft-canvas-root').dataset.learningMode==='DRAFT'").asBoolean());
        eval("window.historyChanges=0;window.draftCanvas.onChange(()=>window.historyChanges++);window.draftCanvas.setZoom(1.2)");
        check(eval("['PEN','ERASER','TEXT'].every(m=>{try{window.draftCanvas.setMode(m);return false;}catch(e){return true;}})").asBoolean(),"history whiteboard rejects ink and text tools");
        fx(()->{view.surface().toggle();return null;});waitFor(()->eval("document.querySelector('#draft-canvas-root').dataset.learningMode==='PRACTICE'").asBoolean());
        check(eval("JSON.parse(window.draftCanvas.getDraft()).strokes[0].id==='ink-second'&&window.historyChanges===0").asBoolean(),"practice/draft switching preserves annotations without writes");
        eval("document.querySelector('[data-native-action=next]').click()");historyLoaded(view,1,null);
        check(child("Boolean(document.querySelector('input[type=checkbox]:checked'))&&Array.from(document.querySelectorAll('input')).every(n=>n.disabled)").asBoolean(),"unsubmitted multiple choice answer replays read-only");
        check(eval("JSON.parse(window.draftCanvas.getDraft()).strokes[0].id==='ink-pending'").asBoolean(),"final unsubmitted draft restores its annotations");
        eval("document.querySelector('[data-native-action=next]').click()");historyLoaded(view,2,null);
        check(eval("JSON.parse(window.draftCanvas.getDraft()).strokes.length===0&&window.historyDraftReplay.getViewState().question.result===null").asBoolean(),"unanswered question does not inherit another attempt's ink or result");
        fx(()->{root.setCenter(new Label("另一个标签页"));return null;});waitFor(()->!browser.nativeVisible());checks.add("inactive history tab hides native window");
        fx(()->{root.setCenter(view);return null;});waitFor(browser::nativeVisible);checks.add("return to history tab reuses browser");
        eval("document.querySelector('[data-native-action=next]').click()");waitFor(()->fx(()->view.lookup("#history-summary")!=null)&&!browser.nativeVisible());
        check(fx(()->((javafx.scene.control.Label)view.lookup("#history-summary-score")).getText()).equals("1"),"final score card uses archived Core summary");
        check(fx(()->view.lookup("#history-draft-previous-question").isVisible()),"summary retains previous-question arrow");
        historyButton(view,"history-draft-previous-question");historyLoaded(view,2,null);waitFor(browser::nativeVisible);checks.add("summary returns to last question in same browser");
        var installed=fx(()->io.quizforge.desktop.extension.ExtensionManager.getDefault().loaded().stream().filter(e->e.manifest().id().equals("quizforge.types.single-choice")).findFirst().orElseThrow());
        var grants=fx(()->io.quizforge.desktop.extension.ExtensionManager.getDefault().grantedPermissions(installed));
        var denied=new LinkedHashMap<String,Set<String>>(grants);var revoked=new HashSet<>(grants.get("SINGLE_CHOICE"));revoked.remove("learning.mode");denied.put("SINGLE_CHOICE",revoked);
        fx(()->{io.quizforge.desktop.extension.ExtensionManager.getDefault().setPermissions(installed,denied);return null;});
        waitFor(()->{child("QF.host.getContext().then(r=>window.qfHistoryContext=r);true");return !child("window.qfHistoryContext?.data?.permissions.granted.includes('learning.mode')??true").asBoolean();});
        child("QF.learning.setMode('DRAFT').then(r=>window.qfHistoryMode=r);true");waitFor(()->child("window.qfHistoryMode?.error?.code==='PERMISSION_DENIED'").asBoolean());checks.add("permission revocation reaches existing history frame");
        fx(()->{io.quizforge.desktop.extension.ExtensionManager.getDefault().setPermissions(installed,grants);return null;});
        Path source=Files.createDirectory(directory.resolve("live-source"));
        try(var files=Files.list(installed.directory())){for(Path file:files.filter(Files::isRegularFile).toList())if(!file.getFileName().toString().startsWith("."))Files.copy(file,source.resolve(file.getFileName()));}
        Files.writeString(source.resolve("style.css"),"\n.qf-choice-practice{--qf-history-verification:1}\n",StandardOpenOption.APPEND);
        fx(()->io.quizforge.desktop.extension.ExtensionManager.getDefault().loadDevelopmentDirectory(source)).toCompletableFuture().get(20,TimeUnit.SECONDS);
        waitFor(()->child("getComputedStyle(document.querySelector('.qf-choice-practice')).getPropertyValue('--qf-history-verification').trim()==='1'").asBoolean());checks.add("history receives development page updates");
        check(before.equals(historyRecords(directory)),"all practice, answer, attempt and draft rows remain unchanged");
        Files.writeString(directory.resolve("records-after.json"),historyRecords(directory));
    }
    private static String historyRecords(Path directory)throws Exception{
        var records=new LinkedHashMap<String,Object>();
        try(var connection=new io.quizforge.infrastructure.persistence.SqliteDatabase(directory.resolve("practice.db")).openConnection()){
            for(String table:List.of("practice_session","practice_session_question","question_attempt","practice_draft_canvas","attempt_draft_snapshot")){
                var rows=new ArrayList<List<String>>();
                try(var statement=connection.createStatement();var result=statement.executeQuery("SELECT * FROM "+table+" ORDER BY 1")){
                    while(result.next()){var row=new ArrayList<String>();for(int i=1;i<=result.getMetaData().getColumnCount();i++)row.add(result.getString(i));rows.add(row);}
                }records.put(table,rows);
            }
        }return DocumentJson.mapper().writeValueAsString(records);
    }
    static void runEditor(io.quizforge.desktop.ui.question.editor.QuestionBankEditorView editor,Stage stage,Path directory,javafx.scene.layout.BorderPane root){
        CompletableFuture.runAsync(()->{
            var driver=new WebView2Verification(editor.nativeSurface().browser(),null);
            try {
                if(driver.bank(editor).path("questions").path(0).path("type").asText().equals("TRUE_FALSE"))driver.verifyTrueFalseEditor(editor,directory);
                else driver.verifyEditor(editor,directory,root);
                Files.writeString(directory.resolve("verification.json"),DocumentJson.mapper().writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("checks",driver.checks)));
                passed=true;System.out.println("WEBVIEW2_EDITOR_VERIFY_PASS "+driver.checks);
            } catch(Exception|AssertionError failure){
                failure.printStackTrace();System.err.println("WEBVIEW2_EDITOR_VERIFY_FAILED "+failure.getMessage());
                try{
                    Files.writeString(directory.resolve("failure.json"),DocumentJson.mapper().writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("checks",driver.checks,"error",failure.toString(),"bank",driver.bank(editor),"child",driver.child("({focus:document.activeElement?.tagName,value:document.querySelector('[data-prompt]')?.value,error:document.querySelector('[data-qf-error]')?.textContent})"),"page",driver.eval("({state:window.qfEditorShell.getState(),error:document.querySelector('#qbank-editor-error')?.textContent,frames:Array.from(document.querySelectorAll('iframe')).map(f=>f.getBoundingClientRect().toJSON()),viewport:[innerWidth,innerHeight]})"))));
                    var shot=driver.cdp("Page.captureScreenshot",Map.of("format","png"));Files.write(directory.resolve("failure.png"),Base64.getDecoder().decode(shot.path("data").asText()));
                }catch(Exception ignored){}
            } finally {Platform.runLater(()->editor.prepareCloseAsync().whenComplete((v,e)->Platform.runLater(()->{editor.destroy();stage.hide();})));}
        });
    }
    private static <T>T fx(java.util.concurrent.Callable<T> action)throws Exception{var task=new FutureTask<T>(action);Platform.runLater(task);return task.get(20,TimeUnit.SECONDS);}
    private JsonNode bank(io.quizforge.desktop.ui.question.editor.QuestionBankEditorView editor)throws Exception{
        return fx(()->DocumentJson.mapper().readTree(new io.quizforge.infrastructure.filesystem.qbank.QuestionBankV2Codec().write(editor.bank())));
    }
    private void loadedEditor(int index)throws Exception {
        waitFor(()->{
            var state=eval("window.qfEditorShell.getState()");
            return state.path("index").asInt()==index&&child("document.querySelector('input[type=radio],input[type=checkbox]')?.name==="+DocumentJson.mapper().writeValueAsString("qf-correct-"+state.path("question").path("id").asText())).asBoolean();
        });
    }
    private void verifyEditor(io.quizforge.desktop.ui.question.editor.QuestionBankEditorView editor,Path directory,javafx.scene.layout.BorderPane root)throws Exception {
        loadedEditor(0);
        check(!eval("Boolean(window.nativePagesHost)").asBoolean(),"formal editor uses direct browser frames");
        check(child("(()=>{try{void parent.document;return false;}catch(e){return e.name==='SecurityError';}})()").asBoolean(),"editor frame cannot access trusted parent");
        clickEditor("[data-prompt]");String target=frameSession();
        try{cdp(target,"Input.insertText",Map.of("text","正式编辑中文验证"));}finally{cdp("Target.detachFromTarget",Map.of("sessionId",target));}
        waitFor(()->bank(editor).path("questions").path(0).path("prompt").path("text").asText().contains("正式编辑中文验证"));checks.add("Chinese input reaches formal editor model");
        eval("window.qfEditorShell.command('navigate',1)");loadedEditor(1);
        check(child("Boolean(document.querySelector('input[type=checkbox]'))").asBoolean(),"same browser loads multiple choice editor");
        clickEditor(".qf-choice-edit-row:nth-child(2) input");
        waitFor(()->bank(editor).path("questions").path(1).path("answerSpec").path("correctOptionIds").size()==3);checks.add("multiple correct answers reach Core model");
        eval("window.qfEditorShell.command('navigate',0)");loadedEditor(0);
        check(child("document.querySelector('[data-prompt]').value.includes('正式编辑中文验证')").asBoolean(),"navigation retains unsaved edits");
        clickEditor("[data-bank-duplicate]");loadedEditor(1);
        waitFor(()->bank(editor).path("questions").size()==4);checks.add("SDK duplicate changes real bank order");
        eval("window.qfEditorShell.command('delete')");loadedEditor(1);
        waitFor(()->bank(editor).path("questions").size()==3);checks.add("SDK delete removes real question");
        eval("window.qfEditorShell.command('navigate',0)");loadedEditor(0);
        var installed=fx(()->io.quizforge.desktop.extension.ExtensionManager.getDefault().loaded().stream().filter(e->e.manifest().id().equals("quizforge.types.single-choice")).findFirst().orElseThrow());
        var grants=fx(()->io.quizforge.desktop.extension.ExtensionManager.getDefault().grantedPermissions(installed));
        var denied=new LinkedHashMap<String,Set<String>>(grants);var revoked=new HashSet<>(grants.get("SINGLE_CHOICE"));revoked.remove("question.edit");denied.put("SINGLE_CHOICE",revoked);
        fx(()->{io.quizforge.desktop.extension.ExtensionManager.getDefault().setPermissions(installed,denied);return null;});
        waitFor(()->{child("QF.host.getContext().then(r=>window.qfPermissionCheck=r);true");return !child("window.qfPermissionCheck?.data?.permissions.granted.includes('question.edit')??true").asBoolean();});
        child("QF.editor.update({prompt:{kind:'TEXT',text:'不允许的编辑'}}).then(r=>window.qfDeniedUpdate=r);true");
        waitFor(()->child("window.qfDeniedUpdate?.error?.code==='PERMISSION_DENIED'").asBoolean());
        check(bank(editor).path("questions").path(0).path("prompt").path("text").asText().contains("正式编辑中文验证"),"revoked editor permission rejects edits without changing Core data");
        fx(()->{io.quizforge.desktop.extension.ExtensionManager.getDefault().setPermissions(installed,grants);return null;});
        waitFor(()->{child("QF.host.getContext().then(r=>window.qfPermissionCheck=r);true");return child("window.qfPermissionCheck?.data?.permissions.granted.includes('question.edit')??false").asBoolean();});
        Path source=Files.createDirectory(directory.resolve("live-source"));
        try(var files=Files.list(installed.directory())){for(Path file:files.filter(Files::isRegularFile).toList())if(!file.getFileName().toString().startsWith("."))Files.copy(file,source.resolve(file.getFileName()));}
        Files.writeString(source.resolve("style.css"),"\n.qf-choice-editor{--qf-editor-verification:1}\n",StandardOpenOption.APPEND);
        fx(()->io.quizforge.desktop.extension.ExtensionManager.getDefault().loadDevelopmentDirectory(source)).toCompletableFuture().get(20,TimeUnit.SECONDS);
        waitFor(()->child("getComputedStyle(document.querySelector('.qf-choice-editor')).getPropertyValue('--qf-editor-verification').trim()==='1'").asBoolean());
        check(child("document.querySelector('[data-prompt]').value.includes('正式编辑中文验证')").asBoolean(),"HTML development update retains editor draft");
        check(!eval("document.querySelector('[data-shell-editor]')").asBoolean(),"shared editor shell mounted once");
        eval("window.scrollTo(0,document.body.scrollHeight)");waitFor(()->eval("window.scrollY").asDouble()>0);checks.add("whole editor page scrolls with its controls");
        eval("window.scrollTo(0,0)");clickEditor("[data-prompt]");target=frameSession();
        try{cdp(target,"Input.dispatchKeyEvent",Map.of("type","keyDown","key","s","code","KeyS","modifiers",2));cdp(target,"Input.dispatchKeyEvent",Map.of("type","keyUp","key","s","code","KeyS","modifiers",2));}finally{cdp("Target.detachFromTarget",Map.of("sessionId",target));}
        waitFor(()->Files.isRegularFile(directory.resolve("saved-bank.json")));checks.add("Ctrl+S inside extension frame invokes bank save");
        check(DocumentJson.mapper().readTree(Files.readString(directory.resolve("saved-bank.json"))).path("questions").path(0).path("prompt").path("text").asText().contains("正式编辑中文验证"),"saved bank contains flushed editor content");
        fx(()->{editor.saveChanges();return null;});checks.add("native save entry flushes without blocking browser replies");
        fx(()->{root.setCenter(new Label("另一个标签页"));return null;});waitFor(()->!browser.nativeVisible());
        fx(editor::prepareCloseAsync).toCompletableFuture().get(20,TimeUnit.SECONDS);checks.add("hidden editor flushes before closing");
        fx(()->{editor.cancelClose();root.setCenter(editor);return null;});waitFor(browser::nativeVisible);checks.add("return to editor tab reuses browser");
    }
    private void verifyTrueFalseEditor(io.quizforge.desktop.ui.question.editor.QuestionBankEditorView editor,Path directory)throws Exception {
        waitFor(()->child("document.querySelector('[data-correct-true]')?.checked===true").asBoolean());
        check(child("document.querySelectorAll('input[type=radio]').length===2&&!document.querySelector('[data-add-option]')").asBoolean(),"judgment editor has exactly two fixed choices");
        child("document.querySelector('[data-correct-false]').scrollIntoView({block:'center'});true");clickChild("[data-correct-false]");
        waitFor(()->{
            var question=bank(editor).path("questions").path(0);
            return question.path("answerSpec").path("correctOptionIds").path(0).asText().equals(question.path("payload").path("options").path(1).path("id").asText());
        });checks.add("judgment correct-answer edit reaches Core model");
        check(child("Boolean(document.querySelector('[data-analysis] textarea')&&document.querySelector('[data-analysis] button'))").asBoolean(),"judgment analysis mounts format-toggle editor");
        eval("window.scrollTo(0,0)");Thread.sleep(100);
        screenshot(directory.resolve("editor.png"));
        clickChild("[data-bank-save]");waitFor(()->Files.isRegularFile(directory.resolve("saved-bank.json")));
        var saved=DocumentJson.mapper().readTree(Files.readString(directory.resolve("saved-bank.json"))).path("questions").path(0);
        check(saved.path("type").asText().equals("TRUE_FALSE")&&saved.path("answerSpec").path("correctOptionIds").path(0).asText().equals(saved.path("payload").path("options").path(1).path("id").asText()),"saved qbank retains judgment type and changed correct answer");
    }
    private void screenshot(Path file)throws Exception {
        Files.write(file,Base64.getDecoder().decode(cdp("Page.captureScreenshot",Map.of("format","png")).path("data").asText()));
    }
    static void runTrueFalse(io.quizforge.desktop.ui.question.practice.PracticeSurfaceHost host,Stage stage,Path directory){
        var surface=(WebView2LearningSurface)host.learningSurface();
        CompletableFuture.runAsync(()->{
            var driver=new WebView2Verification(surface.browser(),surface.session());
            try {
                driver.verifyTrueFalse(host,directory);
                Files.writeString(directory.resolve("verification.json"),DocumentJson.mapper().writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("checks",driver.checks)));
                passed=true;System.out.println("WEBVIEW2_TRUE_FALSE_VERIFY_PASS "+driver.checks);
            }catch(Exception|AssertionError failure){failure.printStackTrace();System.err.println("WEBVIEW2_TRUE_FALSE_VERIFY_FAILED "+failure.getMessage());try{driver.screenshot(directory.resolve("failure.png"));}catch(Exception ignored){}}
            finally{Platform.runLater(()->host.prepareCloseAsync().whenComplete((v,e)->Platform.runLater(()->{host.destroy();stage.hide();})));}
        });
    }
    private void verifyTrueFalse(io.quizforge.desktop.ui.question.practice.PracticeSurfaceHost host,Path directory)throws Exception {
        waitFor(()->child("document.querySelectorAll('input[type=radio]').length===2&&!document.querySelector('[data-answer-true]').disabled").asBoolean());
        check(state().path("question").path("type").asText().equals("TRUE_FALSE"),"external judgment extension loads through product registry");
        waitFor(()->!eval("Boolean(document.querySelector('#question-card').dataset.qfSwitching)").asBoolean());
        check(!eval("Boolean(document.querySelector('#question-card > .practice-loading'))").asBoolean(),"ready judgment card removes initial loading placeholder");
        screenshot(directory.resolve("practice.png"));
        clickChild("[data-answer-false]");waitFor(()->selected()==1);
        clickChild("[data-submit-answer]");clickChild("[data-confirm-answer]");
        waitFor(()->state().path("question").path("state").asText().equals("SUBMITTED")&&child("!document.querySelector('[data-result]').hidden").asBoolean());
        check(child("document.querySelector('[data-answer-false]').checked&&document.querySelector('[data-answer-false]').closest('label').classList.contains('incorrect')&&document.querySelector('[data-answer-true]').closest('label').classList.contains('correct')").asBoolean(),"wrong judgment keeps selection and marks both feedback states");
        check(state().path("question").path("result").path("score").asDouble(-1)==0,"wrong judgment is graded and persisted by Core");
        screenshot(directory.resolve("incorrect.png"));
        clickChild("[data-retry-answer]");waitFor(()->selected()==0&&child("!document.querySelector('[data-answer-true]').disabled").asBoolean());
        clickChild("[data-answer-true]");waitFor(()->selected()==1);
        clickChild("[data-submit-answer]");clickChild("[data-confirm-answer]");
        waitFor(()->state().path("question").path("result").path("status").asText().equals("CORRECT"));checks.add("judgment retry clears answer and correct submission earns full score");
        fx(()->host.selectAttempt(0)).toCompletableFuture().get(20,TimeUnit.SECONDS);
        var replay=new WebView2Verification(((WebView2HistorySurface)fx(host::attemptSurface)).browser(),null);
        replay.waitFor(()->replay.child("Boolean(document.querySelector('[data-answer-false]')?.checked)&&Array.from(document.querySelectorAll('input[type=radio]')).every(n=>n.disabled)").asBoolean());
        check(replay.child("document.querySelector('[data-submit-answer]').hidden&&document.querySelector('[data-retry-answer]').hidden").asBoolean(),"judgment frozen attempt replays in same template with writes disabled");
    }
    private void clickEditor(String selector)throws Exception {
        String encoded=DocumentJson.mapper().writeValueAsString(selector);
        child("document.querySelector("+encoded+").scrollIntoView({block:'center'});true");
        var point=child("(()=>{const r=document.querySelector("+encoded+").getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2};})()");
        var frame=eval("Array.from(document.querySelectorAll('iframe')).filter(f=>f.isConnected).at(-1).getBoundingClientRect().toJSON()");
        double x=frame.path("x").asDouble()+point.path("x").asDouble(),y=frame.path("y").asDouble()+point.path("y").asDouble();
        browser.mouse(0,x,y,false);Thread.sleep(100);
        browser.mouse(1,x,y,true);Thread.sleep(60);browser.mouse(2,x,y,false);Thread.sleep(100);
    }
    static void run(WebView2Browser browser,WebView2PracticeSession session,Stage stage,Region area,Path directory,Runnable next,Runnable previous) {
        run(browser,session,directory,next,previous,mode->browser.post(Map.of("kind","mode","mode",mode)),driver->{},()->{browser.close();session.close();stage.close();});
    }
    static void runProduct(io.quizforge.desktop.ui.question.practice.PracticeSurfaceHost host,Stage stage,Path directory,javafx.scene.layout.BorderPane root,Runnable next,Runnable previous){
        var nativeSurface=(WebView2LearningSurface)host.learningSurface();
        run(nativeSurface.browser(),nativeSurface.session(),directory,next,previous,new ModeCommand(){
            public void set(String mode)throws Exception{var changed=new CompletableFuture<Void>();Platform.runLater(()->(mode.equals("DRAFT")?host.enterDraft():host.leaveDraft()).whenComplete((v,e)->{if(e==null)changed.complete(null);else changed.completeExceptionally(e);}));changed.get(20,TimeUnit.SECONDS);}
            public boolean navigationReady()throws Exception{var idle=new CompletableFuture<Boolean>();Platform.runLater(()->idle.complete(!host.busy()));return idle.get(2,TimeUnit.SECONDS);}
        },driver->{
            driver.verifySwitchPresentation(host);
            driver.verifyAttempts(host,directory,next,previous);
            var detached=new CompletableFuture<Void>();Platform.runLater(()->{root.setCenter(new Label("另一个标签页"));detached.complete(null);});detached.get(2,TimeUnit.SECONDS);
            driver.waitFor(()->!nativeSurface.browser().nativeVisible());driver.check(true,"inactive tab hides native child window");
            var hiddenSave=new CompletableFuture<Boolean>();Platform.runLater(()->host.prepareCloseAsync().whenComplete((ok,e)->{if(e==null)hiddenSave.complete(ok);else hiddenSave.completeExceptionally(e);}));
            driver.check(hiddenSave.get(20,TimeUnit.SECONDS),"hidden practice saves before editor replacement or tab close");
            var resumed=new CompletableFuture<Void>();Platform.runLater(()->{host.cancelClose();resumed.complete(null);});resumed.get(2,TimeUnit.SECONDS);
            var attached=new CompletableFuture<Void>();Platform.runLater(()->{root.setCenter(host);attached.complete(null);});attached.get(2,TimeUnit.SECONDS);
            driver.waitFor(()->nativeSurface.browser().nativeVisible());driver.check(true,"return to tab reuses native browser");
            var saved=new CompletableFuture<Boolean>();Platform.runLater(()->host.prepareCloseAsync().whenComplete((ok,e)->{if(e==null)saved.complete(ok);else saved.completeExceptionally(e);}));
            driver.check(saved.get(20,TimeUnit.SECONDS),"product close waits for asynchronous save barrier");
        },()->host.prepareCloseAsync().whenComplete((ok,error)->Platform.runLater(()->{if(error!=null||!Boolean.TRUE.equals(ok))nativeSurface.abortVerification();host.destroy();stage.hide();})));
    }
    private void verifySwitchPresentation(io.quizforge.desktop.ui.question.practice.PracticeSurfaceHost host)throws Exception{
        for(int target:List.of(2,1,0)){
            waitFor(()->!fx(host::busy));
            eval("(()=>{const card=document.querySelector('#question-card'),old=card.querySelector('.qf-card-body'),height=card.offsetHeight;window.qfSwapSamples=[];window.qfObserveSwap=true;function sample(){if(!window.qfObserveSwap)return;if(card.dataset.qfSwitching==='true')window.qfSwapSamples.push({retained:old.isConnected&&getComputedStyle(old).visibility==='visible',inert:old.inert,height:card.offsetHeight===height});requestAnimationFrame(sample);}requestAnimationFrame(sample);})()");
            child("QF.navigation.goTo("+target+");true");
            waitFor(()->questionLoaded(target)&&!fx(host::busy));
            eval("window.qfObserveSwap=false");
            if(!eval("window.qfSwapSamples.length>0&&window.qfSwapSamples.every(s=>s.retained&&s.inert&&s.height)").asBoolean())
                throw new AssertionError("Question switch presentation failed: "+eval("window.qfSwapSamples"));
            checks.add("question switch to "+target+" retains old disabled card and height until replacement is ready");
            waitFor(()->eval("document.querySelectorAll('#question-card .qf-card-body').length===1").asBoolean());
            check(eval("!document.querySelector('#question-card').dataset.qfSwitching&&document.querySelector('.qf-card-body iframe').clientHeight>1").asBoolean(),
                "question switch to "+target+" releases previous frame and reveals measured new card");
        }
    }
    private void verifyAttempts(io.quizforge.desktop.ui.question.practice.PracticeSurfaceHost host,Path directory,Runnable next,Runnable previous)throws Exception{
        waitFor(()->questionLoaded(0)&&!fx(host::busy));
        clickChild("input[type=radio]");waitFor(()->selected()==1);
        var pendingAnswer=state().path("question").path("presentation").path("answer");
        fx(host::enterDraft).toCompletableFuture().get(20,TimeUnit.SECONDS);
        waitFor(()->eval("window.sharedPractice.learningMode()==='DRAFT'").asBoolean());
        eval("document.querySelector('[data-mode=PEN]').click()");drag(50,220,90,55);
        waitFor(()->eval("JSON.parse(window.draftCanvas.getDraft()).strokes.length").asInt()>0);
        fx(host::leaveDraft).toCompletableFuture().get(20,TimeUnit.SECONDS);
        var pendingDraft=eval("JSON.parse(window.draftCanvas.getDraft())");
        fx(()->host.selectAttempt(0)).toCompletableFuture().get(25,TimeUnit.SECONDS);
        var replay=new WebView2Verification(((WebView2HistorySurface)fx(host::attemptSurface)).browser(),null);
        waitFor(()->replay.browser.nativeVisible()&&!browser.nativeVisible());
        check(replay.eval("window.historyDraftReplay.getViewState().question.result.attemptNo").asInt()==1,"practice can browse its first frozen attempt");
        check(!pendingAnswer.equals(replay.eval("window.historyDraftReplay.getViewState().question.presentation.answer")),"attempt replay shows saved answer instead of pending retry");
        check(!pendingDraft.path("strokes").equals(replay.eval("JSON.parse(window.draftCanvas.getDraft()).strokes")),"attempt replay restores its own frozen ink");
        String frozen=historyRecords(directory);
        replay.child("Promise.all([QF.answer.update({selectedOptionIds:[]}),QF.practice.submit(),QF.practice.retry()]).then(r=>window.attemptDenied=r);true");
        replay.waitFor(()->replay.child("window.attemptDenied?.length===3").asBoolean());
        check(replay.child("window.attemptDenied.every(r=>!r.ok)").asBoolean(),"past attempt denies editing submit and retry");
        var active=state();var forged=DocumentJson.mapper().createObjectNode().put("kind","practice-event");
        forged.putObject("event").put("operationSeq",999999).put("type","SUBMIT")
            .put("sessionId",active.path("session").path("sessionId").asText()).put("sessionQuestionId",active.path("question").path("sessionQuestionId").asText());
        session.receive(forged);state();
        check(frozen.equals(historyRecords(directory)),"replay and suspended live browser cannot write practice records");
        fx(host::enterDraft).toCompletableFuture().get(20,TimeUnit.SECONDS);
        replay.waitFor(()->replay.eval("document.querySelector('#draft-canvas-root').dataset.learningMode==='DRAFT'").asBoolean());
        check(replay.eval("(()=>{try{window.draftCanvas.setMode('PEN');return false;}catch(e){return true;}})()").asBoolean(),"past attempt draft mode remains read-only");
        fx(host::leaveDraft).toCompletableFuture().get(20,TimeUnit.SECONDS);
        replay.clickNavigation("nextAttempt");
        waitFor(()->!fx(host::busy)&&!fx(host::reviewingAttempt)&&browser.nativeVisible());
        check(pendingAnswer.equals(state().path("question").path("presentation").path("answer")),"return to current attempt retains pending answer");
        check(pendingDraft.path("strokes").equals(eval("JSON.parse(window.draftCanvas.getDraft()).strokes")),"return to current attempt retains pending ink");
        clickChild("label:nth-child(3) input");
        var thirdId=child("document.querySelector('label:nth-child(3) input').value").asText();
        waitFor(()->state().path("question").path("presentation").path("answer").path("selectedOptionIds").path(0).asText().equals(thirdId));
        checks.add("returned current attempt is editable");
        clickChild("[data-submit-answer]");clickChild("[data-confirm-answer]");
        waitFor(()->state().path("question").path("result").path("attemptNo").asInt()==2&&!fx(host::busy));
        clickNavigation("previousAttempt");
        replay.waitFor(()->!fx(host::busy)&&fx(host::reviewingAttempt)&&replay.eval("window.historyDraftReplay.getViewState().question.result.attemptNo").asInt()==1);
        fx(()->host.selectAttempt(1)).toCompletableFuture().get(25,TimeUnit.SECONDS);
        check(state().path("question").path("result").path("attemptNo").asInt()==2,"next attempt restores latest submitted result");
        clickChild("[data-retry-answer]");waitFor(()->state().path("question").path("state").asText().equals("RETRYING"));
        fx(()->host.selectAttempt(1)).toCompletableFuture().get(25,TimeUnit.SECONDS);
        check(replay.eval("window.historyDraftReplay.getViewState().question.result.attemptNo").asInt()==2,"pending third attempt can inspect second attempt");
        replay.eval("document.querySelector('[data-native-action=next]').click()");
        waitFor(()->questionLoaded(1)&&!fx(host::busy));
        Platform.runLater(previous);waitFor(()->questionLoaded(0)&&!fx(host::busy));
        check(state().path("question").path("state").asText().equals("RETRYING")&&selected()==0,"question navigation exits replay and restores current retry");
    }
    private static void run(WebView2Browser browser,WebView2PracticeSession session,Path directory,Runnable next,Runnable previous,ModeCommand mode,ExtraChecks extra,Runnable close){
        CompletableFuture.runAsync(()->{
            var driver=new WebView2Verification(browser,session);
            try {driver.verify(next,previous,mode);extra.run(driver);
                Files.writeString(directory.resolve("verification.json"),DocumentJson.mapper().writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("checks",driver.checks,"timingsMs",driver.timings)));
                System.out.println("WEBVIEW2_VERIFY_PASS "+driver.checks+" "+driver.timings);
                passed=true;
            } catch(Exception | AssertionError failure){
                try {
                    var diagnostics=new LinkedHashMap<String,Object>();diagnostics.put("checks",driver.checks);diagnostics.put("state",driver.state());
                    diagnostics.put("page",driver.eval("({input:window.qfVerificationPointer,dpr:devicePixelRatio,viewport:[innerWidth,innerHeight],frame:Array.from(document.querySelectorAll('iframe')).map(f=>({rect:f.getBoundingClientRect().toJSON(),size:[f.clientWidth,f.clientHeight],pointer:getComputedStyle(f).pointerEvents})),canvas:window.draftCanvas.diagnostics(),error:document.querySelector('.practice-error:not([hidden])')?.textContent})"));
                    diagnostics.put("child",driver.child("({selected:Array.from(document.querySelectorAll('input')).map(n=>({checked:n.checked,disabled:n.disabled,rect:n.getBoundingClientRect().toJSON()})),focus:document.activeElement?.tagName,error:document.querySelector('[data-qf-error]')?.textContent})"));
                    Files.writeString(directory.resolve("failure.json"),DocumentJson.mapper().writerWithDefaultPrettyPrinter().writeValueAsString(diagnostics));
                    var screenshot=driver.cdp("Page.captureScreenshot",Map.of("format","png"));Files.write(directory.resolve("failure.png"),Base64.getDecoder().decode(screenshot.path("data").asText()));
                }catch(Exception ignored){System.err.println("WEBVIEW2_DIAGNOSTICS_FAILED "+ignored.getMessage());}
                failure.printStackTrace();System.err.println("WEBVIEW2_VERIFY_FAILED "+failure.getMessage());
            }
            finally{Platform.runLater(close);}
        });
    }
    private void verifyNavigationStyle() throws Exception {
        check(eval("(()=>{const nodes=[...document.querySelectorAll('.learning-navigation-button')];return nodes.length===4&&nodes.every(n=>{const s=getComputedStyle(n);return s.position==='fixed'&&s.borderRadius==='10px'&&s.backgroundColor==='rgb(255, 255, 255)'&&s.boxShadow!=='none'&&n.querySelector('svg');})&&getComputedStyle(nodes[0]).left==='12px'&&getComputedStyle(nodes[1]).right==='12px'&&getComputedStyle(nodes[2]).top==='12px'&&getComputedStyle(nodes[3]).bottom==='12px';})()").asBoolean(),"four fixed arrow buttons share white rounded rectangle and shadow, with question navigation left/right and attempts top/bottom");
    }
    private void clickNavigation(String action) throws Exception {
        String selector="document.querySelector('[data-native-action="+action+"]')";
        waitFor(()->eval("(()=>{const b="+selector+";return b&&!b.hidden&&!b.disabled;})()").asBoolean());
        eval(selector+".click()");
    }
    private JsonNode eval(String source) throws Exception {return browser.evaluate(source).toCompletableFuture().get(6,TimeUnit.SECONDS);}
    private JsonNode cdp(String method,Object input) throws Exception {return browser.devTools(method,input).toCompletableFuture().get(6,TimeUnit.SECONDS);}
    private JsonNode cdp(String target,String method,Object input) throws Exception {return browser.devTools(target,method,input).toCompletableFuture().get(6,TimeUnit.SECONDS);}
    private String frameSession() throws Exception {
        String marker=UUID.randomUUID().toString();child("window.qfNativeVerificationId='"+marker+"'");
        for(var target:cdp("Target.getTargets",Map.of()).path("targetInfos")){
            if(!target.path("type").asText().equals("iframe"))continue;
            String attached=cdp("Target.attachToTarget",Map.of("targetId",target.path("targetId").asText(),"flatten",true)).path("sessionId").asText();
            if(cdp(attached,"Runtime.evaluate",Map.of("expression","window.qfNativeVerificationId==='"+marker+"'","returnByValue",true)).path("result").path("value").asBoolean())return attached;
            cdp("Target.detachFromTarget",Map.of("sessionId",attached));
        }
        throw new IllegalStateException("No matching iframe DevTools target");
    }
    private JsonNode child(String expression) throws Exception {
        return browser.evaluateFrame(expression).toCompletableFuture().get(6,TimeUnit.SECONDS);
    }
    private void clickChild(String selector) throws Exception {
        var encoded=DocumentJson.mapper().writeValueAsString(selector);
        waitFor(()->child("(()=>{const n=document.querySelector("+encoded+");return !!n&&!n.disabled&&n.getBoundingClientRect().width>0&&n.getBoundingClientRect().height>0;})()").asBoolean());
        Thread.sleep(60); // Core acknowledgement can precede the frame's state refresh and geometry report.
        var rect=child("(()=>{const n=document.querySelector("+DocumentJson.mapper().writeValueAsString(selector)+");if(!n)throw Error('Missing input');const r=n.getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2};})()");
        var target=frameSession();
        try {
            cdp(target,"Input.dispatchMouseEvent",Map.of("type","mousePressed","x",rect.path("x").asDouble(),"y",rect.path("y").asDouble(),"button","left","clickCount",1));
            cdp(target,"Input.dispatchMouseEvent",Map.of("type","mouseReleased","x",rect.path("x").asDouble(),"y",rect.path("y").asDouble(),"button","left","clickCount",1));
        }finally{cdp("Target.detachFromTarget",Map.of("sessionId",target));}
    }
    private void drag(double x,double y,double dx,double dy) throws Exception {
        browser.mouse(0,x,y,false);Thread.sleep(120);browser.mouse(1,x,y,true);Thread.sleep(80);
        for(int i=1;i<=6;i++){browser.mouse(0,x+dx*i/6,y+dy*i/6,true);Thread.sleep(20);}
        browser.mouse(2,x+dx,y+dy,false);Thread.sleep(80);
    }
    private interface Condition {boolean get()throws Exception;}
    private void waitFor(Condition condition) throws Exception {long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);while(!condition.get()){if(System.nanoTime()>until)throw new TimeoutException("Acceptance condition timed out");Thread.sleep(20);}}
    private JsonNode state() throws Exception{return session.state().toCompletableFuture().get(6,TimeUnit.SECONDS);}
    private int selected()throws Exception{return state().path("question").path("presentation").path("answer").path("selectedOptionIds").size();}
    private boolean questionLoaded(int index)throws Exception{
        var current=state().path("question");if(current.path("index").asInt()!=index)return false;
        return child("document.querySelector('input')?.name === "+DocumentJson.mapper().writeValueAsString("qf-choice-"+current.path("questionId").asText())).asBoolean()
            &&eval("Boolean(document.querySelector('[data-qf-ready=true]'))").asBoolean();
    }
    private void verify(Runnable next,Runnable previous,ModeCommand mode)throws Exception {
        eval("document.addEventListener('pointerdown',e=>window.qfVerificationPointer={x:e.clientX,y:e.clientY,target:e.target.tagName},true)");
        check(!eval("Boolean(window.nativePagesHost)").asBoolean(),"no PNG/native-page bridge");
        verifyNavigationStyle();
        check(child("(()=>{try{void parent.document;return false;}catch(error){return error.name==='SecurityError';}})()").asBoolean(),"opaque iframe blocks parent access");
        long start=System.nanoTime();clickChild("input[type=radio]");
        waitFor(()->selected()==1);timings.put("selectAndPersist",(System.nanoTime()-start)/1_000_000d);checks.add("native mouse selection + Core persistence");
        mode.set("DRAFT");waitFor(()->eval("window.sharedPractice.learningMode()").asText().equals("DRAFT"));
        eval("document.querySelector('[data-mode=PEN]').click()");drag(55,180,80,60);
        waitFor(()->eval("JSON.parse(window.draftCanvas.getDraft()).strokes.length").asInt()>0);checks.add("native pen stroke");
        eval("document.querySelector('[data-mode=PAN]').click()");var before=eval("JSON.parse(window.draftCanvas.getDraft()).viewport");drag(70,320,70,35);
        waitFor(()->!before.equals(eval("JSON.parse(window.draftCanvas.getDraft()).viewport")));checks.add("native hand drag");
        var secondId=child("document.querySelector('label:nth-child(2) input').value").asText();
        eval("document.querySelector('[data-mode=INTERACT]').click()");clickChild("label:nth-child(2) input");waitFor(()->state().path("question").path("presentation").path("answer").path("selectedOptionIds").path(0).asText().equals(secondId));checks.add("selection works after pen and hand drag");
        mode.set("PRACTICE");waitFor(()->eval("window.sharedPractice.learningMode()").asText().equals("PRACTICE"));
        check(eval("JSON.parse(window.draftCanvas.getDraft()).strokes.length").asInt()>0,"practice retains draft stroke");
        waitFor(mode::navigationReady);start=System.nanoTime();Platform.runLater(next);waitFor(()->questionLoaded(1)&&mode.navigationReady());
        timings.put("nextQuestion",(System.nanoTime()-start)/1_000_000d);checks.add("switch question without new JVM/WebView2 instance");
        if(state().path("question").path("type").asText().equals("MULTIPLE_CHOICE"))check(child("Boolean(document.querySelector('input[type=checkbox]'))").asBoolean(),"product loads multiple choice extension");
        Platform.runLater(previous);waitFor(()->questionLoaded(0)&&mode.navigationReady());
        check(selected()==1,"return restores saved choice");check(eval("JSON.parse(window.draftCanvas.getDraft()).strokes.length").asInt()>0,"return restores draft stroke");
        clickChild("[data-submit-answer]");clickChild("[data-confirm-answer]");waitFor(()->state().path("question").path("state").asText().equals("SUBMITTED"));checks.add("submit + isolated grading");
        clickChild("[data-retry-answer]");waitFor(()->state().path("question").path("state").asText().equals("RETRYING"));checks.add("retry clears answer");
        eval("window.webview2Verification.showEditor()");waitFor(()->child("Boolean(document.querySelector('[data-prompt]'))").asBoolean());
        clickChild("[data-prompt]");var editorTarget=frameSession();
        try{cdp(editorTarget,"Input.insertText",Map.of("text","中文输入验证"));}finally{cdp("Target.detachFromTarget",Map.of("sessionId",editorTarget));}
        waitFor(()->eval("window.webview2Verification.editorQuestion().prompt.text.includes('中文输入验证')").asBoolean());checks.add("Chinese text commit in actual extension editor");
        cdp("Input.dispatchKeyEvent",Map.of("type","keyDown","key","End","code","End"));cdp("Input.dispatchKeyEvent",Map.of("type","keyUp","key","End","code","End"));
        var panelBefore=eval("document.querySelector('section[style*=fixed]').scrollTop");
        cdp("Input.dispatchMouseEvent",Map.of("type","mouseWheel","x",100,"y",500,"deltaX",0,"deltaY",500));
        waitFor(()->eval("document.querySelector('section[style*=fixed]').scrollTop").asDouble()>panelBefore.asDouble());checks.add("native editor scroll");
        eval("window.webview2Verification.hideEditor()");
        var beats=new CountDownLatch(5);for(int i=0;i<5;i++)Platform.runLater(beats::countDown);check(beats.await(1,TimeUnit.SECONDS),"JavaFX navigation thread remains responsive");
    }
    private void check(boolean ok,String text){if(!ok)throw new AssertionError(text);checks.add(text);}
}
