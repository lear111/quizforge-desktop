package io.quizforge.desktop.browser.javafx;
import io.quizforge.core.practice.*;
import io.quizforge.core.port.QuestionResourceInput;
import io.quizforge.desktop.extension.ExtensionManager;
import io.quizforge.desktop.ui.question.editor.QuestionBankEditorView;
import io.quizforge.desktop.ui.question.practice.MixedQuestionPracticeView;
import io.quizforge.desktop.ui.question.history.HistoryDraftAdapter;
import io.quizforge.desktop.ui.question.history.HistoryDraftWebView;
import io.quizforge.infrastructure.filesystem.qbank.QuestionBankV2Codec;
import io.quizforge.infrastructure.persistence.SqliteDatabase;
import io.quizforge.infrastructure.persistence.practice.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static io.quizforge.desktop.browser.javafx.HtmlChoiceV2Test.*;

class IsolatedChoiceWebViewTest {
 @TempDir Path directory;
 @BeforeAll static void start()throws Exception{HtmlChoiceV2Test.start();}
 @AfterAll static void stop()throws Exception{HtmlChoiceV2Test.stop();}
 // Test instrumentation belongs only to temporary development packages, never the production SDK.
 static final String PROBE="""
 window.addEventListener('message',event=>{
  const m=event.data;if(event.source!==parent||m?.channel!=='qf-type-frame'||m.kind!=='test-probe')return;
  const script=document.createElement('script');script.setAttribute('nonce','qf-isolated-page-v1');
  script.textContent='(async()=>{'+m.code+'})().then(value=>parent.postMessage({channel:"qf-type-frame",session:'+JSON.stringify(m.session)+',kind:"test-result",id:'+JSON.stringify(m.id)+',value:JSON.stringify(value??null)},"*"),error=>parent.postMessage({channel:"qf-type-frame",session:'+JSON.stringify(m.session)+',kind:"test-result",id:'+JSON.stringify(m.id)+',value:JSON.stringify({testError:error.message})},"*"));';
  document.body.append(script);script.remove();
 });
 """;
 static Object frame(WebView web,String code)throws Exception{
  var json=io.quizforge.infrastructure.json.DocumentJson.mapper();String id=UUID.randomUUID().toString();
  String payload=json.writeValueAsString(Map.of("channel","qf-type-frame","kind","test-probe","id",id,"code",code));
  fx(()->web.getEngine().executeScript("window.qfTestValue=null;(()=>{const frame=document.querySelector('.qf-extension-page:not(.qf-frame-retiring)>.qf-type-frame'),m="+payload+";m.session=frame.dataset.qfRemotePage;const listener=event=>{if(event.data?.channel==='qf-type-frame'&&event.data.kind==='test-result'&&event.data.id==="+json.writeValueAsString(id)+"){window.removeEventListener('message',listener);window.qfTestValue=event.data.value;}};window.addEventListener('message',listener);frame.contentWindow.postMessage(m,'*');})()"));
  waitFor(()->Boolean.TRUE.equals(fx(()->web.getEngine().executeScript("typeof window.qfTestValue==='string'"))));
  Object value=json.readValue((String)fx(()->web.getEngine().executeScript("window.qfTestValue")),Object.class);
  if(value instanceof Map<?,?> map&&map.containsKey("testError"))fail(map.toString());return value;
 }
 static void ready(WebView web)throws Exception{
  long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(60);
  while(!Boolean.TRUE.equals(fx(()->web.getEngine().executeScript("Boolean(document.querySelector('[data-qf-ready=true]:not(.qf-frame-retiring)'))")))){
   if(System.nanoTime()>deadline)fail(String.valueOf(fx(()->web.getEngine().executeScript("document.body.innerText.slice(0,1500)"))));
   Thread.sleep(50);
  }
 }
 static void workbench(io.quizforge.desktop.extension.ExtensionDevelopmentWindow window,String previous)throws Exception{
  long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(110);
  while(fx(()->window.loading()||previous!=null&&Objects.equals(previous,window.revision()))){
   if(fx(()->!window.loading()&&window.status().startsWith("刷新失败")))fail(fx(window::status));
   if(System.nanoTime()>deadline)fail(fx(window::status));Thread.sleep(50);
  }
  assertNotNull(fx(window::view),()->{try{return fx(window::status);}catch(Exception e){return e.toString();}});
  if(previous!=null)assertNotEquals(previous,fx(window::revision),fx(window::status));
 }
 @Test void developmentWorkbenchWaitsForIsolatedPagesAndKeepsState()throws Exception{
  var manager=ExtensionManager.getDefault();var installed=fx(()->manager.loaded().stream().filter(e->e.manifest().id().equals("quizforge.types.single-choice")).findFirst().orElseThrow());
  var source=directory.resolve("workbench");Files.createDirectories(source);
  try(var files=Files.list(installed.directory())){for(var p:files.filter(Files::isRegularFile).toList())Files.copy(p,source.resolve(p.getFileName()));}
  var window=fx(()->{var w=new io.quizforge.desktop.extension.ExtensionDevelopmentWindow(null,source);w.stage().setOpacity(0);w.stage().show();return w;});
  try{
   workbench(window,null);ready(window.view());
   String state=(String)fx(()->window.view().getEngine().executeScript("window.extensionWorkbench.getState()"));
   assertTrue(state.contains("SINGLE_CHOICE"));
   assertEquals(1,fx(()->((Number)window.view().getEngine().executeScript("document.querySelectorAll('[data-qf-remote-page]').length")).intValue()));
   assertTrue(fx(()->Boolean.TRUE.equals(window.view().getEngine().executeScript("Boolean(window.nativeRulesHost)"))));
   String revision=fx(window::revision);Files.writeString(source.resolve("style.css"),Files.readString(source.resolve("style.css"))+"\n.qf-choice-editor{color:#334455}");
   workbench(window,revision);ready(window.view());
   var json=io.quizforge.infrastructure.json.DocumentJson.mapper();var before=json.readTree(state);var after=json.readTree((String)fx(()->window.view().getEngine().executeScript("window.extensionWorkbench.getState()")));
   assertEquals(before.get("question"),after.get("question"));
  }finally{fx(()->{window.close();return null;});}
 }
 @Test void undeclaredOperationsAreDeniedAtTheIsolatedPageBoundary()throws Exception{
  var manager=ExtensionManager.getDefault();var installed=fx(()->manager.loaded().stream().filter(e->e.manifest().id().equals("quizforge.types.single-choice")).findFirst().orElseThrow());
  var source=directory.resolve("restricted");Files.createDirectories(source);
  try(var files=Files.list(installed.directory())){for(var p:files.filter(Files::isRegularFile).toList())Files.copy(p,source.resolve(p.getFileName()));}
  var json=io.quizforge.infrastructure.json.DocumentJson.mapper();var manifest=json.readTree(Files.readString(source.resolve("manifest.json")));
  ((com.fasterxml.jackson.databind.node.ObjectNode)manifest.path("types").get(0)).putArray("permissions");
  Files.writeString(source.resolve("manifest.json"),json.writeValueAsString(manifest));
  Files.writeString(source.resolve("editor.js"),Files.readString(source.resolve("editor.js"))+"\n"+PROBE);
  var window=fx(()->{var w=new io.quizforge.desktop.extension.ExtensionDevelopmentWindow(null,source);w.stage().setOpacity(0);w.stage().show();return w;});
  try {
   workbench(window,null);ready(window.view());
   var context=(Map<?,?>)((Map<?,?>)frame(window.view(),"return await QF.host.getContext();")).get("data");
   assertEquals(false,((Map<?,?>)context.get("capabilities")).get("editQuestion"));
   assertEquals(List.of(),((Map<?,?>)context.get("permissions")).get("granted"));
   var before=frame(window.view(),"return await QF.editor.getData();");
   for(String call:List.of("QF.editor.update({prompt:{kind:'TEXT',text:'must not change'}})","QF.bank.deleteQuestion()","QF.editor.save()")) {
    var reply=(Map<?,?>)frame(window.view(),"return await "+call+";");
    assertEquals(false,reply.get("ok"));assertEquals("PERMISSION_DENIED",((Map<?,?>)reply.get("error")).get("code"));
   }
   assertEquals(before,frame(window.view(),"return await QF.editor.getData();"));
   assertThrows(Exception.class,()->fx(()->manager.loadDevelopmentDirectory(source)).toCompletableFuture().get(45,TimeUnit.SECONDS));
  } finally {fx(()->{window.close();return null;});}
 }
 @ParameterizedTest @ValueSource(strings={"SINGLE_CHOICE","MULTIPLE_CHOICE"})
 void isolatedEditorPracticeDraftAndHotReload(String type)throws Exception{
  var manager=ExtensionManager.getDefault();var installed=fx(()->manager.loaded().stream().filter(e->e.manifest().types().stream().anyMatch(t->t.id().equals(type))).findFirst().orElseThrow());var definition=installed.manifest().types().getFirst();
  var source=directory.resolve("source");Files.createDirectories(source);
  try(var files=Files.list(installed.directory())){for(var p:files.filter(Files::isRegularFile).toList())Files.copy(p,source.resolve(p.getFileName()));}
  for(String name:List.of(definition.editorScript(),definition.rendererScript()))Files.writeString(source.resolve(name),Files.readString(source.resolve(name))+"\n"+PROBE);
  Files.writeString(source.resolve(definition.renderer()),Files.readString(source.resolve(definition.renderer()))+"<nav data-test-tools><button data-test-interact>选择</button></nav>");
  Files.writeString(source.resolve(definition.rendererScript()),Files.readString(source.resolve(definition.rendererScript()))+"\nQF.ui.mountControls(QF.dom.$('[data-test-tools]'));QF.dom.on(QF.dom.$('[data-test-interact]'),'click',()=>QF.whiteboard.setTool('INTERACT'));");
  Files.writeString(source.resolve(definition.styles().getFirst()),Files.readString(source.resolve(definition.styles().getFirst()))+"\nbody{background:rgb(255,0,0)}");
  fx(()->manager.loadDevelopmentDirectory(source)).toCompletableFuture().get(45,TimeUnit.SECONDS);
  var originalBank=bank(type);var q=originalBank.questions().getFirst();
  var second=fx(()->io.quizforge.core.question.type.QuestionTypes.require(type).duplicate(q,prefix->prefix+UUID.randomUUID()));
  var bank=new io.quizforge.core.question.model.QuestionBank(originalBank.assetId(),originalBank.title(),originalBank.stimuli(),List.of(q,second),originalBank.resources());
  var codec=new QuestionBankV2Codec();var saved=new AtomicReference<io.quizforge.core.question.model.QuestionBank>();
  var editor=fx(()->new QuestionBankEditorView(bank,null,null,null,saved::set));
  var db=new SqliteDatabase(directory.resolve("practice.db"));var transaction=new SqlitePracticeTransaction(db);var service=new PracticeSessionService(transaction,Clock.systemUTC());
  var runtime=fx(()->new PersistentPracticeRuntime(service,bank,codec.contentId(bank)));
  var mixed=fx(()->new MixedQuestionPracticeView(bank,QuestionResourceInput.NONE,()->runtime,refs->null));
  var stage=fx(()->{var w=new Stage();w.setOpacity(0);w.setScene(new Scene(new HBox(new ScrollPane(editor),mixed),1500,800));w.show();return w;});
  try{
   var editorWeb=fx(()->(WebView)editor.lookup("#extension-question-editor"));ready(editorWeb);
   assertEquals(true,frame(editorWeb,"try{parent.document.body;return false;}catch{return true;}"));
   assertEquals(true,frame(editorWeb,"return typeof window.bankEditorHost==='undefined'&&typeof window.editorHost==='undefined';"));
   assertNotEquals("rgb(255, 0, 0)",fx(()->editorWeb.getEngine().executeScript("getComputedStyle(document.body).backgroundColor")));
   frame(editorWeb,"const n=QF.dom.$('[data-prompt]');n.value='Isolated editor content';n.dispatchEvent(new Event('input',{bubbles:true}));return true;");
   assertEquals(true,((Map<?,?>)frame(editorWeb,"return await QF.bank.save();")).get("ok"));
   waitFor(()->saved.get()!=null);assertEquals("Isolated editor content",((io.quizforge.core.question.content.TextContent)saved.get().questions().getFirst().prompt()).text());
   assertEquals(true,((Map<?,?>)frame(editorWeb,"return await QF.bank.duplicateQuestion();")).get("ok"));ready(editorWeb);
   assertEquals(true,((Map<?,?>)frame(editorWeb,"return await QF.navigation.previous();")).get("ok"));ready(editorWeb);
   assertEquals("INVALID_ARGUMENT",((Map<?,?>)((Map<?,?>)frame(editorWeb,"return await QF.editor.update({scoreSpec:{defaultMaxScore:Infinity}});")).get("error")).get("code"));
   var beforeInvalidEdit=frame(editorWeb,"return await QF.editor.getData();");
   var invalidEdit=(Map<?,?>)frame(editorWeb,"return await QF.editor.update({answerSpec:'malformed'});");
   assertEquals("DATA_VALIDATION_FAILED",((Map<?,?>)invalidEdit.get("error")).get("code"));
   assertTrue(((Map<?,?>)invalidEdit.get("error")).get("message").toString().contains("/question/answerSpec"));
   assertEquals(beforeInvalidEdit,frame(editorWeb,"return await QF.editor.getData();"));
   var approval=fx(()->manager.grantedPermissions(installed));
   var restricted=new LinkedHashMap<String,Set<String>>();
   approval.forEach((id,values)->restricted.put(id,values.stream().filter(permission->!permission.equals("question.edit")&&!permission.equals("bank.delete")).collect(java.util.stream.Collectors.toUnmodifiableSet())));
   var beforePermissionChange=frame(editorWeb,"return await QF.editor.getData();");
   fx(()->editorWeb.getEngine().executeScript("window.qfPermissionFrame=document.querySelector('.qf-type-frame');"));
   try {
    fx(()->{manager.setPermissions(installed,restricted);return null;});
    assertEquals(true,fx(()->editorWeb.getEngine().executeScript("window.qfPermissionFrame===document.querySelector('.qf-type-frame')")));
    var denied=(Map<?,?>)frame(editorWeb,"return await QF.editor.update({prompt:{kind:'TEXT',text:'blocked permission update'}});");
    assertEquals("PERMISSION_DENIED",((Map<?,?>)denied.get("error")).get("code"));
    assertEquals(beforePermissionChange,frame(editorWeb,"return await QF.editor.getData();"));
    var deniedContext=(Map<?,?>)((Map<?,?>)frame(editorWeb,"return await QF.host.getContext();")).get("data");
    assertEquals(false,((Map<?,?>)deniedContext.get("capabilities")).get("editQuestion"));
    assertEquals(1,((Number)((Map<?,?>)deniedContext.get("sdk")).get("apiMinor")).intValue());
    Path permissionStyle=source.resolve(definition.styles().getFirst());
    Files.writeString(permissionStyle,Files.readString(permissionStyle)+"\n.qf-choice-editor{--permission-test:1}");
    fx(()->manager.loadDevelopmentDirectory(source)).toCompletableFuture().get(45,TimeUnit.SECONDS);
    waitFor(()->Boolean.TRUE.equals(fx(()->editorWeb.getEngine().executeScript("window.qfPermissionFrame!==document.querySelector('.qf-type-frame')"))));ready(editorWeb);
    assertEquals("PERMISSION_DENIED",((Map<?,?>)((Map<?,?>)frame(editorWeb,"return await QF.bank.deleteQuestion();")).get("error")).get("code"));
   } finally {fx(()->{manager.setPermissions(installed,approval);return null;});}
   assertEquals(true,((Map<?,?>)frame(editorWeb,"return await QF.editor.save();")).get("ok"));
   var editorBeforeFailure=frame(editorWeb,"return await QF.editor.getData();");
   frame(editorWeb,type.equals("SINGLE_CHOICE")?"setTimeout(()=>{while(true){}},200);return true;":"setTimeout(()=>{throw new Error('test editor event failure')},200);return true;");
   waitFor(()->Boolean.TRUE.equals(fx(()->editorWeb.getEngine().executeScript("Boolean(document.querySelector('[data-qf-reload]'))"))));
   fx(()->editorWeb.getEngine().executeScript("document.querySelector('[data-qf-reload]').click()"));ready(editorWeb);
   assertEquals(editorBeforeFailure,frame(editorWeb,"return await QF.editor.getData();"));
   var surface=mixed.surface();surface.ready().toCompletableFuture().get(45,TimeUnit.SECONDS);var web=surface.draftView().view();ready(web);
   assertEquals(true,frame(web,"try{parent.document.querySelector('#viewport');return false;}catch{return true;}"));
   assertEquals(true,((Map<?,?>)frame(web,"return await QF.learning.setMode('DRAFT');")).get("ok"));
   assertEquals(true,((Map<?,?>)frame(web,"return await QF.whiteboard.setTool('PEN');")).get("ok"));
   assertEquals(false,((Map<?,?>)frame(web,"return await QF.answer.update({selectedOptionIds:[]});")).get("ok"));
   waitFor(()->Boolean.TRUE.equals(fx(()->web.getEngine().executeScript("Boolean(document.querySelector('[data-qf-host-controls]'))"))));
   fx(()->web.getEngine().executeScript("(()=>{const n=document.querySelector('[data-qf-host-controls]'),r=n.getBoundingClientRect();n.dispatchEvent(new MouseEvent('click',{bubbles:true,clientX:r.left+12,clientY:r.top+12}));})()"));
   waitFor(()->Boolean.TRUE.equals(fx(()->web.getEngine().executeScript("window.draftCanvas.uiState().tool==='INTERACT'"))));
   frame(web,"return await QF.whiteboard.setTool('PEN');");
   fx(()->web.getEngine().executeScript("(()=>{const v=document.querySelector('#viewport'),r=v.getBoundingClientRect();for(const [type,x,y]of [['pointerdown',25,25],['pointermove',70,65],['pointerup',90,80]])v.dispatchEvent(new PointerEvent(type,{bubbles:true,isPrimary:true,pointerId:9,button:0,clientX:r.left+x,clientY:r.top+y,pressure:.5}));})()"));
   assertEquals(1,((Number)fx(()->web.getEngine().executeScript("JSON.parse(window.draftCanvas.getDraft()).strokes.length"))).intValue());
   assertEquals(true,((Map<?,?>)frame(web,"return await QF.whiteboard.setTool('INTERACT');")).get("ok"));
   var answerBeforeRevocation=frame(web,"return await QF.answer.get();");
   var answerRestricted=new LinkedHashMap<String,Set<String>>();
   approval.forEach((id,values)->answerRestricted.put(id,values.stream().filter(permission->!permission.equals("answer.write")).collect(java.util.stream.Collectors.toUnmodifiableSet())));
   fx(()->web.getEngine().executeScript("window.qfPermissionFrame=document.querySelector('.qf-type-frame');"));
   try {
    fx(()->{manager.setPermissions(installed,answerRestricted);return null;});
    assertEquals(true,fx(()->web.getEngine().executeScript("window.qfPermissionFrame===document.querySelector('.qf-type-frame')")));
    assertEquals("PERMISSION_DENIED",((Map<?,?>)((Map<?,?>)frame(web,"return await QF.answer.update({selectedOptionIds:[]});")).get("error")).get("code"));
    assertEquals(answerBeforeRevocation,frame(web,"return await QF.answer.get();"));
   } finally {fx(()->{manager.setPermissions(installed,approval);return null;});}
   assertEquals(true,((Map<?,?>)frame(web,"return await QF.learning.setMode('PRACTICE');")).get("ok"));
   var beforeInvalidAnswer=frame(web,"return await QF.answer.get();");
   var invalidAnswer=(Map<?,?>)frame(web,"return await QF.answer.update({selectedOptionIds:[42]});");
   assertEquals("DATA_VALIDATION_FAILED",((Map<?,?>)invalidAnswer.get("error")).get("code"));
   assertTrue(((Map<?,?>)invalidAnswer.get("error")).get("message").toString().contains("/answer/selectedOptionIds/0"));
   assertEquals(beforeInvalidAnswer,frame(web,"return await QF.answer.get();"));
   var ids=io.quizforge.infrastructure.json.DocumentJson.mapper().writeValueAsString(q.choiceAnswerSpec().correctOptionIds());
   assertEquals(true,((Map<?,?>)frame(web,"return await QF.answer.update({selectedOptionIds:"+ids+"});")).get("ok"));
   String geometry=(String)fx(()->web.getEngine().executeScript("window.draftCanvas.getDraft()"));
   var answerBeforeFailure=frame(web,"return await QF.answer.get();");
   frame(web,type.equals("SINGLE_CHOICE")?"setTimeout(()=>{while(true){}},200);return true;":"setTimeout(()=>{throw new Error('test practice event failure')},200);return true;");
   waitFor(()->Boolean.TRUE.equals(fx(()->web.getEngine().executeScript("Boolean(document.querySelector('[data-qf-reload]'))"))));
   assertEquals(true,fx(()->web.getEngine().executeScript("document.querySelectorAll('.qf-type-frame').length===0")));
   fx(()->surface.draftView().flushPendingDraft()).toCompletableFuture().get(5,TimeUnit.SECONDS);
   fx(()->{surface.draftView().setLearningMode(io.quizforge.desktop.ui.question.practice.SharedLearningSurfaceMode.PRACTICE);return null;});
   fx(()->web.getEngine().executeScript("document.querySelector('[data-qf-reload]').click()"));ready(web);
   assertEquals(answerBeforeFailure,frame(web,"return await QF.answer.get();"));
   assertEquals(geometry,fx(()->web.getEngine().executeScript("window.draftCanvas.getDraft()")));
   Files.writeString(source.resolve(definition.renderer()),Files.readString(source.resolve(definition.renderer()))+"<span data-hot-reloaded>更新</span>");
   waitFor(()->Boolean.TRUE.equals(fx(()->web.getEngine().executeScript("Boolean(document.querySelector('[data-qf-ready=true]')) && document.querySelector('.qf-type-frame').srcdoc.includes('data-hot-reloaded')"))));
   assertEquals(true,frame(web,"return Boolean(QF.dom.$('[data-hot-reloaded]'));"));
   assertEquals(geometry,fx(()->web.getEngine().executeScript("window.draftCanvas.getDraft()")));
   var submissions=(List<?>)frame(web,"return await Promise.all([QF.practice.submit(),QF.practice.submit()]);");
   assertEquals(2,submissions.size());for(var value:submissions){var reply=(Map<?,?>)value;assertEquals(true,reply.get("ok"),reply.toString());assertNotNull(((Map<?,?>)reply.get("data")).get("result"));}
   waitFor(()->runtime.snapshot().questions().getFirst().sessionQuestion().practiceState()==PracticeSessionQuestion.State.SUBMITTED);ready(web);
   assertEquals(q.choiceAnswerSpec().correctOptionIds().size(),((Number)frame(web,"return QF.dom.root.querySelectorAll('input:checked').length;")).intValue());
   assertEquals(true,((Map<?,?>)frame(web,"return await QF.practice.retry();")).get("ok"));
   waitFor(()->runtime.snapshot().questions().getFirst().sessionQuestion().practiceState()==PracticeSessionQuestion.State.RETRYING);ready(web);
   assertEquals(0,((Number)frame(web,"return QF.dom.root.querySelectorAll('input:checked').length;")).intValue());
   assertEquals(0,((Number)fx(()->web.getEngine().executeScript("JSON.parse(window.draftCanvas.getDraft()).strokes.length"))).intValue());
   assertEquals(true,((Map<?,?>)frame(web,"return await QF.navigation.next();")).get("ok"));ready(web);assertEquals(1,runtime.session().index());
   assertEquals(true,((Map<?,?>)frame(web,"return await QF.navigation.previous();")).get("ok"));ready(web);assertEquals(0,runtime.session().index());
   waitFor(()->Boolean.TRUE.equals(fx(()->web.getEngine().executeScript("document.querySelectorAll('.qf-frame-retiring').length===0"))));
   var sessionId=runtime.snapshot().session().id();fx(()->{surface.destroy();return null;});new SqlitePracticeSessionRepository(db).archive(sessionId,Instant.now());
   var history=new PracticeHistoryService(transaction);var detail=history.loadArchivedSessionDetail(bank.assetId(),sessionId);var row=detail.questions().getFirst();var replay=new HistoryDraftAdapter(history,bank.assetId(),detail).load(row,row.attempts().getFirst());
   var historyView=fx(()->new HistoryDraftWebView());
   try{
    historyView.ready().toCompletableFuture().get(45,TimeUnit.SECONDS);fx(()->{historyView.load(replay.card(),replay.draft().document());stage.setScene(new Scene(new javafx.scene.layout.StackPane(historyView.view()),1000,750));return null;});ready(historyView.view());
    assertEquals(false,((Map<?,?>)frame(historyView.view(),"return await QF.answer.update({selectedOptionIds:[]});")).get("ok"));
    assertEquals(false,((Map<?,?>)frame(historyView.view(),"return await QF.practice.submit();")).get("ok"));
    assertEquals(true,frame(historyView.view(),"return Array.from(QF.dom.root.querySelectorAll('.qf-choice-option input')).every(n=>n.disabled);"));
    assertEquals(1,((Number)fx(()->historyView.view().getEngine().executeScript("JSON.parse(window.draftCanvas.getDraft()).strokes.length"))).intValue());
   }finally{fx(()->{historyView.destroy();return null;});}
  }finally{fx(()->{mixed.surface().destroy();stage.close();manager.stopDevelopment();return null;});}
 }

 @ParameterizedTest @ValueSource(strings={"SINGLE_CHOICE","MULTIPLE_CHOICE"})
 void isolatedSubmitAndRetry(String type)throws Exception{
  var manager=ExtensionManager.getDefault();var installed=fx(()->manager.loaded().stream().filter(e->e.manifest().types().stream().anyMatch(t->t.id().equals(type))).findFirst().orElseThrow());
  var source=directory.resolve("submit-probe");Files.createDirectories(source);
  try(var files=Files.list(installed.directory())){for(var p:files.filter(Files::isRegularFile).toList())Files.copy(p,source.resolve(p.getFileName()));}
  Files.writeString(source.resolve("practice.js"),Files.readString(source.resolve("practice.js"))+"\n"+PROBE);fx(()->manager.loadDevelopmentDirectory(source)).toCompletableFuture().get(45,TimeUnit.SECONDS);
  var bank=bank(type);var codec=new QuestionBankV2Codec();var db=new SqliteDatabase(directory.resolve("submit.db"));
  var runtime=fx(()->new PersistentPracticeRuntime(new PracticeSessionService(new SqlitePracticeTransaction(db),Clock.systemUTC()),bank,codec.contentId(bank)));
  var mixed=fx(()->new MixedQuestionPracticeView(bank,QuestionResourceInput.NONE,()->runtime,refs->null));
  var editor=fx(()->new QuestionBankEditorView(bank,null,null,null,ignored->{}));
  var stage=fx(()->{var w=new Stage();w.setOpacity(0);w.setScene(new Scene(new HBox(new ScrollPane(editor),mixed),1500,800));w.show();return w;});
  try{
   ready(fx(()->(WebView)editor.lookup("#extension-question-editor")));
   var surface=mixed.surface();surface.ready().toCompletableFuture().get(45,TimeUnit.SECONDS);var web=surface.draftView().view();ready(web);
   assertEquals(true,((Map<?,?>)frame(web,"return await QF.learning.setMode('PRACTICE');")).get("ok"));
   var ids=io.quizforge.infrastructure.json.DocumentJson.mapper().writeValueAsString(bank.questions().getFirst().choiceAnswerSpec().correctOptionIds());
   assertEquals(true,((Map<?,?>)frame(web,"return await QF.answer.update({selectedOptionIds:"+ids+"});")).get("ok"));
   frame(web,"setTimeout(()=>{while(true){}},200);return true;");
   waitFor(()->Boolean.TRUE.equals(fx(()->web.getEngine().executeScript("Boolean(document.querySelector('[data-qf-reload]'))"))));
   fx(()->surface.draftView().flushPendingDraft()).toCompletableFuture().get(5,TimeUnit.SECONDS);
   fx(()->{surface.draftView().setLearningMode(io.quizforge.desktop.ui.question.practice.SharedLearningSurfaceMode.PRACTICE);return null;});
   fx(()->web.getEngine().executeScript("document.querySelector('[data-qf-reload]').click()"));ready(web);
   frame(web,"return await QF.answer.get();");
   Files.writeString(source.resolve("practice.html"),Files.readString(source.resolve("practice.html"))+"<span data-hot-reloaded>更新</span>");
   waitFor(()->Boolean.TRUE.equals(fx(()->web.getEngine().executeScript("Boolean(document.querySelector('[data-qf-ready=true]'))&&document.querySelector('.qf-type-frame').srcdoc.includes('data-hot-reloaded')"))));
   frame(web,"return Boolean(QF.dom.$('[data-hot-reloaded]'));");
   var context=frame(web,"return await QF.host.getContext();");
   var reply=(Map<?,?>)frame(web,"return await QF.practice.submit();");assertEquals(true,reply.get("ok"),reply+" / "+context);
   assertEquals(PracticeSessionQuestion.State.SUBMITTED,runtime.snapshot().questions().getFirst().sessionQuestion().practiceState());
   var retried=(Map<?,?>)frame(web,"return await QF.practice.retry();");assertEquals(true,retried.get("ok"),retried.toString());
  }finally{fx(()->{mixed.surface().destroy();stage.close();manager.stopDevelopment();return null;});}
 }
}
