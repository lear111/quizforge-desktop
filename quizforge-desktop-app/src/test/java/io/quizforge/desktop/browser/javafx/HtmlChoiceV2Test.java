package io.quizforge.desktop.browser.javafx;

import io.quizforge.desktop.learning.SharedPracticeAdapter;
import io.quizforge.desktop.learning.SharedPracticeViewModel;
import io.quizforge.core.practice.*;
import io.quizforge.core.question.model.*;
import io.quizforge.core.question.content.TextContent;
import io.quizforge.core.question.type.QuestionTypes;
import io.quizforge.core.question.service.*;
import io.quizforge.core.port.QuestionResourceInput;
import io.quizforge.desktop.extension.ExtensionManager;
import io.quizforge.desktop.testing.FxTestRuntime;
import io.quizforge.desktop.ui.question.extension.ExtensionEditorFields;
import io.quizforge.desktop.ui.question.shared.QuestionEditorContext;
import io.quizforge.desktop.ui.question.history.*;
import io.quizforge.infrastructure.filesystem.qbank.QuestionBankV2Codec;
import io.quizforge.infrastructure.persistence.practice.*;
import io.quizforge.infrastructure.persistence.SqliteDatabase;
import java.util.*;
import java.util.concurrent.*;
import java.nio.file.*;
import java.time.*;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.*;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
class HtmlChoiceV2Test {
 static Path packages;
 @TempDir Path directory;
 @BeforeAll static void start() throws Exception {
  FxTestRuntime.start();packages=Files.createTempDirectory("qf-html-v2-test");
  // Tests explicitly inspect, import, approve and restart external packages.
  fx(()->io.quizforge.desktop.browser.webview2.ExternalExtensionAcceptance.initialize(ExtensionManager.getDefault(), packages.resolve("extensions"), Path.of("../extensions/dist"))).toCompletableFuture().get(90,TimeUnit.SECONDS);
  assertTrue(fx(()->ExtensionManager.getDefault().failures().isEmpty()),()->"Package load failed");
 }
 @AfterAll static void stop() throws Exception {fx(()->{ExtensionManager.getDefault().close();return null;});}
 static QuestionBank bank(String type) throws Exception {
  var definition=QuestionTypes.require(type);
  var q=fx(()->definition.createDraft(prefix->prefix+UUID.randomUUID(),List.of()));
  return new QuestionBank("qb_html_"+UUID.randomUUID().toString().replace("-",""),"HTML Choice",List.of(),List.of(q),List.of());
 }
 @ParameterizedTest @ValueSource(strings={"SINGLE_CHOICE","MULTIPLE_CHOICE"})
 void templateCloneEditorRoundTrip(String type) throws Exception {
  var bank=bank(type);var original=bank.questions().getFirst();
  var copy=fx(()->QuestionTypes.require(type).duplicate(original,prefix->prefix+UUID.randomUUID()));
  assertNotEquals(original.id(),copy.id());
  assertTrue(copy.choicePayload().options().stream().noneMatch(o->original.choicePayload().options().stream().anyMatch(old->old.id().equals(o.id()))));
  assertTrue(copy.choiceAnswerSpec().correctOptionIds().stream().allMatch(id->copy.choicePayload().options().stream().anyMatch(o->o.id().equals(id))));
  var model=new QuestionBankEditorModel(bank);var body=new VBox();var errors=new VBox();var validators=new ArrayList<Runnable>();
  var stage=fx(()->{var w=new Stage();w.setScene(new Scene(body,900,650));w.setOpacity(0);
   ExtensionEditorFields.render(new QuestionEditorContext(model,0,body,new HBox(),new Region(),new HashMap<>(),validators,errors,QuestionResourceInput.NONE,()->w,()->{}));w.show();return w;});
  try{
   var web=fx(()->(WebView)body.lookup("#extension-question-editor"));
   waitFor(()->Boolean.TRUE.equals(fx(()->web.getEngine().executeScript("Boolean(document.querySelector('[data-qf-ready=true]'))"))));
   fx(()->web.getEngine().executeScript("(()=>{const n=document.querySelector('[data-prompt]');n.value='Updated HTML question';n.dispatchEvent(new Event('input',{bubbles:true}));})()"));
   fx(()->{validators.forEach(Runnable::run);return null;});
   assertEquals("Updated HTML question",((TextContent)model.bank().questions().getFirst().prompt()).text());
   assertTrue(fx(()->errors.getChildren().isEmpty()));
   var codec=new QuestionBankV2Codec();assertEquals(model.bank(),fx(()->codec.parse(codec.write(model.bank()))));
  }finally{fx(()->{stage.close();return null;});}
 }
 @ParameterizedTest @ValueSource(strings={"SINGLE_CHOICE","MULTIPLE_CHOICE"})
 void sharedEditorShellFlushesNavigatesAndKeepsSources(String type) throws Exception {
  var first=bank(type).questions().getFirst();
  var second=bank(type.equals("SINGLE_CHOICE")?"MULTIPLE_CHOICE":"SINGLE_CHOICE").questions().getFirst();
  var missing=new Question("q_shell_missing","ESSAY",List.of(),new TextContent("Preserved article"),
    new io.quizforge.core.question.model.extension.ExtensionPayload(Map.of()),new io.quizforge.core.question.model.extension.ExtensionAnswerSpec(Map.of()),
    new ScoreSpec(java.math.BigDecimal.TEN),null,new TextContent("Preserved analysis"),List.of());
  var bank=new QuestionBank("qb_shell","Shell",List.of(),List.of(first,second,missing),List.of());
  var ref=io.quizforge.core.question.source.SourceRef.anchor("doc_shell","qfd:v2:"+"a".repeat(64),"<img>",1,"Source","<img>");
  var workspace=new io.quizforge.core.workspace.model.WorkspaceId(UUID.randomUUID());
  var opened=new java.util.concurrent.atomic.AtomicReference<io.quizforge.core.document.navigation.QuizForgeNavigationLink>();
  var saved=new java.util.concurrent.atomic.AtomicReference<QuestionBank>();
  var failSave=new java.util.concurrent.atomic.AtomicBoolean();
  var asset=new io.quizforge.core.asset.Asset("doc_shell",io.quizforge.core.asset.AssetType.REGISTERED_MARKDOWN,"Source.md","Source",ref.documentContentId(),"2.0");
  var index=new io.quizforge.core.port.AssetIndexRepository(){
   public Optional<io.quizforge.core.asset.Asset> findById(io.quizforge.core.workspace.model.WorkspaceId owner,String id){assertEquals(workspace,owner);return "doc_shell".equals(id)?Optional.of(asset):Optional.empty();}
   public List<io.quizforge.core.asset.Asset> list(io.quizforge.core.workspace.model.WorkspaceId owner){return List.of(asset);}
   public void synchronize(io.quizforge.core.workspace.model.WorkspaceId owner,List<io.quizforge.core.asset.Asset> assets,Instant time){throw new UnsupportedOperationException();}
  };
  var nodes=new io.quizforge.core.port.DocumentNodeLookup(){
   public Result lookup(io.quizforge.core.workspace.model.WorkspaceId owner,io.quizforge.core.asset.Asset document,String node){throw new UnsupportedOperationException();}
   public AnchorResult lookupAnchor(io.quizforge.core.workspace.model.WorkspaceId owner,io.quizforge.core.asset.Asset document,String name,int occurrence){
    assertEquals(workspace,owner);return new AnchorResult(ref.documentContentId(),"<img>".equals(name)&&occurrence==1,false);
   }
  };
  var links=new io.quizforge.core.question.source.QuestionSourceLinkService(index,nodes);
  var sources=new io.quizforge.desktop.ui.question.source.QuestionSourceNavigationAdapter(
    new io.quizforge.core.question.source.QuestionBankReferenceResolver(owner->{assertEquals(workspace,owner);return new io.quizforge.core.asset.WorkspaceScanResult(List.of(asset),List.of());},nodes),links,opened::set,message->fail(message));
  var sourceLink=new io.quizforge.core.document.navigation.MarkdownNavigationLinkCodec().format("Source.md",io.quizforge.core.document.navigation.QuizForgeNavigationLink.anchor("doc_shell","<img>",1));
  var editor=fx(()->new io.quizforge.desktop.ui.question.editor.QuestionBankEditorView(bank,workspace,links,sources,value->{
   if(failSave.get())throw new IllegalStateException("磁盘保存失败");saved.set(value);
  }));
  var scroll=fx(()->{var s=new javafx.scene.control.ScrollPane(editor);s.setFitToWidth(true);return s;});
  var stage=fx(()->{var s=new Stage();s.setOpacity(0);s.setScene(new Scene(scroll,900,450));s.show();return s;});
  try {
   var web=fx(()->(WebView)editor.lookup("#extension-question-editor"));
   waitFor(()->Boolean.TRUE.equals(fx(()->web.getEngine().executeScript("Boolean(document.querySelector('[data-qf-ready=true]'))"))));
   String url=fx(()->web.getEngine().getLocation());
   assertFalse(fx(editor::dirty),"Opening an editor must not mark the bank dirty");
   fx(()->web.getEngine().executeScript("window.shellReuseToken='persistent';(()=>{const p=document.querySelector('[data-prompt]');p.value='Pending input';p.dispatchEvent(new Event('input',{bubbles:true}));})()"));
   fx(()->web.getEngine().executeScript("document.querySelector('[data-source-link]').value="+io.quizforge.infrastructure.json.DocumentJson.mapper().writeValueAsString(sourceLink)+";document.querySelector('[data-source-add]').click()"));
   waitFor(()->Boolean.TRUE.equals(fx(()->web.getEngine().executeScript("document.querySelectorAll('#extension-editor [data-source-action=open]').length===1 && !document.querySelector('[data-source-add]').disabled"))));
   assertTrue(Boolean.TRUE.equals(fx(()->web.getEngine().executeScript("document.querySelector('.qf-source-open').textContent==='Source · <img>' && !document.querySelector('#qbank-source-list img')"))));
   shellCommand(web,"source.add","[Heading](quizforge://asset/doc_shell/heading/Test?occurrence=1)");
   assertTrue(Boolean.TRUE.equals(fx(()->web.getEngine().executeScript("document.querySelector('#qbank-editor-error').textContent.includes('Source Anchor') && document.querySelectorAll('.qf-source-row').length===1"))));
   fx(()->web.getEngine().executeScript("document.querySelector('#extension-editor [data-source-action=open]').click()"));
   waitFor(()->opened.get()!=null);assertEquals(io.quizforge.core.document.navigation.QuizForgeNavigationLink.anchor("doc_shell","<img>",1),opened.get());
   waitFor(()->Boolean.TRUE.equals(fx(()->web.getEngine().executeScript("!document.querySelector('[data-bank-save]').disabled"))));
   failSave.set(true);shellCommand(web,"save",null);
   assertNull(saved.get());
   assertTrue(Boolean.TRUE.equals(fx(()->web.getEngine().executeScript("document.querySelector('#qbank-editor-error').textContent.includes('磁盘保存失败') && document.querySelector('[data-prompt]').value==='Pending input'"))));
   failSave.set(false);
   // Saving through HTML flushes input and references into the same persisted Question shape.
   waitFor(()->Boolean.TRUE.equals(fx(()->web.getEngine().executeScript("!document.querySelector('[data-bank-save]').hidden && !document.querySelector('[data-bank-save]').disabled"))));
   fx(()->web.getEngine().executeScript("document.querySelector('[data-bank-save]').click()"));
   waitFor(()->saved.get()!=null);
   assertEquals("Pending input",((TextContent)saved.get().questions().getFirst().prompt()).text());
   assertEquals(List.of(ref),saved.get().questions().getFirst().sourceRefs());
   waitFor(()->Boolean.TRUE.equals(fx(()->web.getEngine().executeScript("document.querySelector('#qf-editor-shell').getAttribute('aria-busy')==='false'"))));
   shellCommand(web,"navigate",1);
   waitFor(()->Boolean.TRUE.equals(fx(()->web.getEngine().executeScript("Boolean(document.querySelector('[data-qf-ready=true]'))"))));
   shellCommand(web,"navigate",2);
   assertTrue(Boolean.TRUE.equals(fx(()->web.getEngine().executeScript("!document.querySelector('#qbank-editor-missing').hidden && !window.extensionEditorInstance"))));
   shellCommand(web,"navigate",0);
   waitFor(()->Boolean.TRUE.equals(fx(()->web.getEngine().executeScript("Boolean(document.querySelector('[data-qf-ready=true]'))"))));
   assertEquals("Pending input",fx(()->web.getEngine().executeScript("document.querySelector('[data-prompt]').value")));
   shellCommand(web,"navigate",99);
   assertEquals("1 / 3",fx(()->web.getEngine().executeScript("document.querySelector('#qbank-editor-position').textContent")));
   fx(()->web.getEngine().executeScript("document.querySelector('#extension-editor [data-source-action=remove]').click()"));
   waitFor(()->Boolean.TRUE.equals(fx(()->web.getEngine().executeScript("document.querySelectorAll('#extension-editor [data-source-action=remove]').length===0 && !document.querySelector('[data-bank-duplicate]').disabled"))));
   assertEquals(0,((Number)fx(()->web.getEngine().executeScript("document.querySelectorAll('.qf-source-row').length"))).intValue());
   fx(()->web.getEngine().executeScript("document.querySelector('[data-bank-duplicate]').click()"));
   waitFor(()->Boolean.TRUE.equals(fx(()->web.getEngine().executeScript("document.querySelector('#qbank-editor-position').textContent==='2 / 4'"))));
   waitFor(()->Boolean.TRUE.equals(fx(()->web.getEngine().executeScript("Boolean(document.querySelector('[data-qf-ready=true]'))"))));
   shellCommand(web,"save",null);
   var duplicate=saved.get().questions().get(1);assertNotEquals(first.id(),duplicate.id());
   assertTrue(duplicate.choicePayload().options().stream().noneMatch(o->first.choicePayload().options().stream().anyMatch(old->old.id().equals(o.id()))));
   fx(()->web.getEngine().executeScript("document.querySelector('[data-bank-delete]').click();document.querySelector('[data-delete-cancel]').click()"));
   assertTrue(Boolean.TRUE.equals(fx(()->web.getEngine().executeScript("document.querySelector('[data-delete-confirm]').hidden"))));
   fx(()->web.getEngine().executeScript("document.querySelector('[data-bank-delete]').click();document.querySelector('[data-delete-accept]').click()"));
   waitFor(()->Boolean.TRUE.equals(fx(()->web.getEngine().executeScript("document.querySelector('#qbank-editor-position').textContent==='2 / 3'"))));
   waitFor(()->Boolean.TRUE.equals(fx(()->web.getEngine().executeScript("Boolean(document.querySelector('[data-qf-ready=true]'))"))));
   fx(()->web.getEngine().executeScript("(()=>{const s=document.querySelector('[data-bank-add]');s.value='"+type+"';s.dispatchEvent(new Event('change',{bubbles:true}));})()"));
   waitFor(()->Boolean.TRUE.equals(fx(()->web.getEngine().executeScript("document.querySelector('#qbank-editor-position').textContent==='4 / 4'"))));
   waitFor(()->Boolean.TRUE.equals(fx(()->web.getEngine().executeScript("Boolean(document.querySelector('[data-qf-ready=true]'))"))));
   shellCommand(web,"save",null);
   assertEquals(4,saved.get().questions().size());assertEquals(missing,saved.get().questions().get(2));
   assertSame(web,fx(()->editor.lookup("#extension-question-editor")));
   assertEquals(url,fx(()->web.getEngine().getLocation()));
   assertEquals("persistent",fx(()->web.getEngine().executeScript("window.shellReuseToken")));
   assertTrue(fx(()->editor.getHeight()>scroll.getViewportBounds().getHeight()));
   assertEquals("hidden",fx(()->web.getEngine().executeScript("getComputedStyle(document.documentElement).overflowY")));
   // Native outline navigation uses the same persistent shell and flushes before changing cards.
   fx(()->{editor.jumpTo(0);return null;});
   waitFor(()->Boolean.TRUE.equals(fx(()->web.getEngine().executeScript("Boolean(document.querySelector('[data-qf-ready=true]'))"))));
   assertEquals("Pending input",fx(()->web.getEngine().executeScript("document.querySelector('[data-prompt]').value")));
   assertEquals("persistent",fx(()->web.getEngine().executeScript("window.shellReuseToken")));
   var codec=new QuestionBankV2Codec();assertEquals(saved.get(),fx(()->codec.parse(codec.write(saved.get()))));
  }finally{fx(()->{stage.close();return null;});}
 }
 @Test void emptyEditorShellCanCreateAndDeleteLastQuestion() throws Exception {
  var saved=new java.util.concurrent.atomic.AtomicReference<QuestionBank>();
  var editor=fx(()->new io.quizforge.desktop.ui.question.editor.QuestionBankEditorView(
    new QuestionBank("qb_empty_shell","Empty",List.of(),List.of(),List.of()),null,null,null,saved::set));
  var stage=fx(()->{var s=new Stage();s.setOpacity(0);s.setScene(new Scene(editor,900,650));s.show();return s;});
  try {
   var web=fx(()->(WebView)editor.lookup("#extension-question-editor"));
   waitFor(()->Boolean.TRUE.equals(fx(()->web.getEngine().executeScript("Boolean(window.qfEditorShell?.getState())"))));
   shellCommand(web,"add","SINGLE_CHOICE");
   waitFor(()->Boolean.TRUE.equals(fx(()->web.getEngine().executeScript("Boolean(document.querySelector('[data-qf-ready=true]'))"))));
   shellCommand(web,"delete",null);shellCommand(web,"save",null);
   assertNotNull(saved.get());assertTrue(saved.get().questions().isEmpty());
   assertTrue(Boolean.TRUE.equals(fx(()->web.getEngine().executeScript("!document.querySelector('#qbank-editor-empty').hidden && document.querySelector('#qbank-editor-position').textContent==='0 / 0'"))));
  }finally{fx(()->{stage.close();return null;});}
 }
 static void shellCommand(WebView web,String action,Object argument) throws Exception {
  var json=io.quizforge.infrastructure.json.DocumentJson.mapper();
  fx(()->{
   var window=(netscape.javascript.JSObject)web.getEngine().executeScript("window");
   window.setMember("shellTestAction",action);window.setMember("shellTestArgument",json.writeValueAsString(argument));
   web.getEngine().executeScript("window.shellTestReply=null;window.qfEditorShell.command(window.shellTestAction,JSON.parse(window.shellTestArgument)).then(reply=>{window.shellTestReply=JSON.stringify(reply);})");return null;
  });
  waitFor(()->Boolean.TRUE.equals(fx(()->web.getEngine().executeScript("typeof window.shellTestReply==='string'"))));
 }
 @ParameterizedTest @ValueSource(strings={"SINGLE_CHOICE","MULTIPLE_CHOICE"})
 void editorUsesOuterPageScrolling(String type) throws Exception {
  var model=new QuestionBankEditorModel(bank(type));var body=new VBox();var errors=new VBox();var validators=new ArrayList<Runnable>();
  var content=new VBox(new javafx.scene.control.Label("题库编辑"),body,new javafx.scene.control.Label("引用来源"));
  var scroll=new javafx.scene.control.ScrollPane(content);scroll.setFitToWidth(true);
  var stage=fx(()->{var w=new Stage();ExtensionEditorFields.render(new QuestionEditorContext(model,0,body,new HBox(),new Region(),new HashMap<>(),validators,errors,QuestionResourceInput.NONE,()->w,()->{}));w.setScene(new Scene(scroll,900,450));w.setOpacity(0);w.show();return w;});
  try {
   var web=fx(()->(WebView)body.lookup("#extension-question-editor"));
   waitFor(()->Boolean.TRUE.equals(fx(()->web.getEngine().executeScript("Boolean(document.querySelector('[data-qf-ready=true]'))"))));
   waitFor(()->fx(()->web.getHeight()>460 && Boolean.TRUE.equals(web.getEngine().executeScript("window.innerHeight>=Math.ceil(document.querySelector('#extension-editor').getBoundingClientRect().height)"))));
   assertTrue(fx(()->content.getHeight()>scroll.getViewportBounds().getHeight()));
   assertEquals("hidden",fx(()->web.getEngine().executeScript("getComputedStyle(document.documentElement).overflowY")));
   fx(()->{javafx.event.Event.fireEvent(web,new javafx.scene.input.ScrollEvent(javafx.scene.input.ScrollEvent.SCROLL,10,10,10,10,false,false,false,false,false,false,0,-120,0,-120,javafx.scene.input.ScrollEvent.HorizontalTextScrollUnits.NONE,0,javafx.scene.input.ScrollEvent.VerticalTextScrollUnits.NONE,0,0,null));return null;});
   waitFor(()->fx(()->scroll.getVvalue()>0));
   assertEquals(0,((Number)fx(()->web.getEngine().executeScript("window.scrollY"))).intValue());
   double height=fx(web::getHeight);
   fx(()->web.getEngine().executeScript("document.querySelector('[data-add-option]').click()"));
   waitFor(()->fx(()->web.getHeight()>height+20));
   fx(()->web.getEngine().executeScript("document.querySelector('[data-options]').lastElementChild.querySelector('button').click()"));
   waitFor(()->fx(()->Math.abs(web.getHeight()-height)<=1));
   assertEquals(4,model.bank().questions().getFirst().choicePayload().options().size());
  } finally {fx(()->{stage.close();return null;});}
 }
 @ParameterizedTest @ValueSource(strings={"SINGLE_CHOICE","MULTIPLE_CHOICE"})
 void realPracticeSubmitRetryAndReadOnlyHistory(String type) throws Exception {
  var bank=bank(type);var q=bank.questions().getFirst();var codec=new QuestionBankV2Codec();
  var db=new SqliteDatabase(directory.resolve(type+".db"));var transaction=new SqlitePracticeTransaction(db);var service=new PracticeSessionService(transaction,Clock.systemUTC());
  var adapter=new SharedPracticeAdapter(service,fx(()->service.openOrCreateActiveSession(bank,codec.contentId(bank))));
  assertNull(((SharedPracticeViewModel.ExtensionPresentation)adapter.viewModel().question().presentation()).reference());
  assertFalse(((SharedPracticeViewModel.ExtensionPresentation)adapter.viewModel().question().presentation()).question().containsKey("answerSpec"));
  var view=fx(()->new SharedPracticeCanvasWebView(adapter));
  var stage=fx(()->{var w=new Stage();w.setScene(new Scene(new StackPane(view.view()),1000,700));w.setOpacity(0);w.show();return w;});
  try{
   view.ready().toCompletableFuture().get(25,TimeUnit.SECONDS);
   waitFor(()->Boolean.TRUE.equals(fx(()->view.view().getEngine().executeScript("Boolean(document.querySelector('[data-qf-ready=true]'))"))));
   for(String id:q.choiceAnswerSpec().correctOptionIds()){
    fx(()->view.view().getEngine().executeScript("document.querySelector('input[value=\""+id+"\"]').click()"));
    waitFor(()->SharedPracticeViewModel.answerIds(adapter.snapshot().questions().getFirst().sessionQuestion().draftAnswer()).contains(id));
   }
   fx(()->view.view().getEngine().executeScript("document.querySelector('[data-submit-answer]').click()"));
   waitFor(()->Boolean.TRUE.equals(fx(()->view.view().getEngine().executeScript("!document.querySelector('[data-answer-confirm]').hidden"))));
   assertNotEquals(PracticeSessionQuestion.State.SUBMITTED,adapter.snapshot().questions().getFirst().sessionQuestion().practiceState());
   fx(()->view.view().getEngine().executeScript("document.querySelector('[data-cancel-answer]').click()"));
   waitFor(()->Boolean.TRUE.equals(fx(()->view.view().getEngine().executeScript("document.querySelector('[data-answer-confirm]').hidden && !document.querySelector('[data-submit-answer]').disabled"))));
   fx(()->view.view().getEngine().executeScript("document.querySelector('[data-submit-answer]').click();document.querySelector('[data-confirm-answer]').click()"));
   waitFor(()->adapter.snapshot().questions().getFirst().sessionQuestion().practiceState()==PracticeSessionQuestion.State.SUBMITTED);
   assertEquals("CORRECT",adapter.viewModel().question().result().status());
   waitFor(()->Boolean.TRUE.equals(fx(()->view.view().getEngine().executeScript("Array.from(document.querySelectorAll('.qf-choice-option input:checked')).length>0 && !document.querySelector('[data-result]').hidden"))));
   assertEquals(q.choiceAnswerSpec().correctOptionIds().size(),((Number)fx(()->view.view().getEngine().executeScript("document.querySelectorAll('.qf-choice-option input:checked').length"))).intValue());
   fx(()->view.view().getEngine().executeScript("document.querySelector('[data-retry-answer]').click()"));
   waitFor(()->adapter.snapshot().questions().getFirst().sessionQuestion().practiceState()==PracticeSessionQuestion.State.RETRYING);
   waitFor(()->Boolean.TRUE.equals(fx(()->view.view().getEngine().executeScript("document.querySelectorAll('.qf-choice-option input:checked').length===0"))));
   assertEquals(1,adapter.snapshot().questions().getFirst().attempts().size());
   fx(view::flushPendingDraft).toCompletableFuture().get(25,TimeUnit.SECONDS);
   fx(()->{view.destroy();return null;});
   var sessionId=adapter.snapshot().session().id();new SqlitePracticeSessionRepository(db).archive(sessionId,Instant.now());
   var history=new PracticeHistoryService(transaction);var detail=history.loadArchivedSessionDetail(bank.assetId(),sessionId);var row=detail.questions().getFirst();
   var replay=new HistoryDraftAdapter(history,bank.assetId(),detail).load(row,row.attempts().getFirst());assertNotNull(replay.card());
   var historyView=fx(HistoryDraftWebView::new);
   try{historyView.ready().toCompletableFuture().get(25,TimeUnit.SECONDS);fx(()->{historyView.load(replay.card(),replay.draft().document());return null;});
    waitFor(()->Boolean.TRUE.equals(fx(()->historyView.view().getEngine().executeScript("Boolean(document.querySelector('[data-qf-ready=true]'))"))));
    assertEquals(0,((Number)fx(()->historyView.view().getEngine().executeScript("document.querySelectorAll('.qf-choice-option input:not(:disabled)').length"))).intValue());
   }finally{fx(()->{historyView.destroy();return null;});}

  }finally{fx(()->{view.destroy();stage.close();return null;});}
 }
 @Test void extensionsChoosePublicUiWithoutChangingPermissions() throws Exception {
  var manager=ExtensionManager.getDefault();
  var installed=fx(()->manager.loaded().stream().filter(e->e.manifest().types().stream().anyMatch(t->t.id().equals("SINGLE_CHOICE"))).findFirst().orElseThrow());
  var definition=installed.manifest().types().getFirst();
  Path source=directory.resolve("custom-ui");Files.createDirectories(source);
  try(var files=Files.list(installed.directory())){for(Path file:files.filter(Files::isRegularFile).toList())Files.copy(file,source.resolve(file.getFileName()));}
  Files.writeString(source.resolve(definition.editor()),Files.readString(source.resolve(definition.editor()))+"<button type=\"button\" data-custom-save>保存</button>");
  Files.writeString(source.resolve(definition.editorScript()),Files.readString(source.resolve(definition.editorScript()))+"\nQF.ui.configure({title:false,save:false,position:false,typeLabel:false,add:false,duplicate:false,delete:false,sources:false,outline:false});window.editorUiTest=QF;QF.dom.on(QF.dom.root.querySelector('[data-custom-save]'),'click',()=>QF.bank.save());");
  Files.writeString(source.resolve(definition.editorScript()),Files.readString(source.resolve(definition.editorScript()))+"\nQF.layout.configure({cardWidth:560,maxCardWidth:'95%',horizontalAlign:'left',verticalAlign:'top',padding:18});");
  Files.writeString(source.resolve(definition.renderer()),Files.readString(source.resolve(definition.renderer()))+"<button type=\"button\" data-custom-submit>提交</button><button type=\"button\" data-custom-retry>重试</button>");
  Files.writeString(source.resolve(definition.rendererScript()),Files.readString(source.resolve(definition.rendererScript()))+"\nQF.ui.configure({card:false,typeLabel:false,position:false,score:false,state:false,submit:false,retry:false,confirmation:false,note:false,sources:false,outline:false,draftToggle:false,draftToolbar:false,draftZoom:false});window.practiceUiTest=QF;QF.dom.on(QF.dom.root.querySelector('[data-custom-submit]'),'click',()=>QF.practice.submit());QF.dom.on(QF.dom.root.querySelector('[data-custom-retry]'),'click',()=>QF.practice.retry());");
  Files.writeString(source.resolve(definition.rendererScript()),Files.readString(source.resolve(definition.rendererScript()))+"\nQF.layout.configure({cardWidth:600,maxCardWidth:'80%',horizontalAlign:'left',verticalAlign:'top',padding:30});");
  Files.writeString(source.resolve(definition.renderer()),Files.readString(source.resolve(definition.renderer()))+"<nav data-custom-tools><button type=\"button\" data-custom-interact>选择</button></nav>");
  Files.writeString(source.resolve(definition.rendererScript()),Files.readString(source.resolve(definition.rendererScript()))+"\nQF.ui.mountControls(QF.dom.$('[data-custom-tools]'));QF.dom.on(QF.dom.$('[data-custom-interact]'),'click',()=>QF.whiteboard.setTool('INTERACT'));window.boardNotifications=0;QF.host.subscribe(()=>window.boardNotifications++);");
  fx(()->manager.loadDevelopmentDirectory(source)).toCompletableFuture().get(25,TimeUnit.SECONDS);
  var first=bank("SINGLE_CHOICE").questions().getFirst();var second=bank("MULTIPLE_CHOICE").questions().getFirst();
  var bank=new QuestionBank("qb_custom_ui","Custom UI",List.of(),List.of(first,second),List.of());var codec=new QuestionBankV2Codec();
  var saved=new java.util.concurrent.atomic.AtomicReference<QuestionBank>();
  var editorPrefs=new java.util.concurrent.atomic.AtomicReference<Map<String,Boolean>>();
  var editor=fx(()->{var e=new io.quizforge.desktop.ui.question.editor.QuestionBankEditorView(bank,null,null,null,saved::set);e.onUiChange(editorPrefs::set);return e;});
  var db=new SqliteDatabase(directory.resolve("custom-ui.db"));var transaction=new SqlitePracticeTransaction(db);var service=new PracticeSessionService(transaction,Clock.systemUTC());
  var runtime=fx(()->new PersistentPracticeRuntime(service,bank,codec.contentId(bank)));
  var mixed=fx(()->new io.quizforge.desktop.ui.question.practice.MixedQuestionPracticeView(bank,QuestionResourceInput.NONE,()->runtime,refs->null));
  var stage=fx(()->{var w=new Stage();w.setOpacity(0);w.setScene(new Scene(new HBox(new javafx.scene.control.ScrollPane(editor),mixed),1500,750));w.show();return w;});
  try {
   var editorWeb=fx(()->(WebView)editor.lookup("#extension-question-editor"));
   waitFor(()->editorPrefs.get()!=null && Boolean.FALSE.equals(editorPrefs.get().get("save")));
   assertTrue(Boolean.TRUE.equals(fx(()->editorWeb.getEngine().executeScript("document.querySelector('#qbank-save').hidden && document.querySelector('#qbank-editor-sources').hidden && !document.querySelector('#qbank-editor-next').hidden && window.editorUiTest.ui.configure({navigation:false}).error.code==='INVALID_UI'"))));
   waitFor(()->Boolean.TRUE.equals(fx(()->editorWeb.getEngine().executeScript("window.editorUiTest.layout.getConfiguration().data.cardWidth===560 && document.querySelector('#qf-editor-shell').getBoundingClientRect().width<=560 && Math.abs(document.querySelector('#qf-editor-shell').getBoundingClientRect().left-18)<1"))));
   fx(()->editorWeb.getEngine().executeScript("document.querySelector('[data-custom-save]').click()"));waitFor(()->saved.get()!=null);assertEquals(bank,saved.get());
   assertEquals(true,sdkCall(editorWeb,"window.editorUiTest.bank.addQuestion('SINGLE_CHOICE')").get("ok"));
   waitFor(()->Boolean.TRUE.equals(fx(()->editorWeb.getEngine().executeScript("Boolean(document.querySelector('[data-qf-ready=true]'))"))));
   assertEquals(true,sdkCall(editorWeb,"window.editorUiTest.bank.duplicateQuestion()").get("ok"));
   waitFor(()->Boolean.TRUE.equals(fx(()->editorWeb.getEngine().executeScript("Boolean(document.querySelector('[data-qf-ready=true]'))"))));
   assertEquals(4,((Map<?,?>)sdkCall(editorWeb,"window.editorUiTest.bank.getState()").get("data")).get("count"));
   assertEquals(true,sdkCall(editorWeb,"window.editorUiTest.bank.deleteQuestion()").get("ok"));
   shellCommand(editorWeb,"navigate",2);
   waitFor(()->Boolean.TRUE.equals(fx(()->editorWeb.getEngine().executeScript("Boolean(document.querySelector('[data-qf-ready=true]'))"))));
   assertEquals(true,sdkCall(editorWeb,"window.editorUiTest.bank.deleteQuestion()").get("ok"));
   shellCommand(editorWeb,"navigate",0);
   waitFor(()->Boolean.TRUE.equals(fx(()->editorWeb.getEngine().executeScript("Boolean(document.querySelector('[data-qf-ready=true]'))"))));
   assertEquals(2,((Map<?,?>)sdkCall(editorWeb,"window.editorUiTest.navigation.getState()").get("data")).get("count"));
   shellCommand(editorWeb,"navigate",1);
   waitFor(()->Boolean.TRUE.equals(fx(()->editorWeb.getEngine().executeScript("Boolean(document.querySelector('[data-qf-ready=true]')) && !document.querySelector('[data-bank-save]').hidden"))));
   assertTrue(Boolean.TRUE.equals(fx(()->editorWeb.getEngine().executeScript("!document.querySelector('[data-bank-save]').hidden && document.querySelector('#qbank-save').hidden && !document.querySelector('#qbank-editor-previous').hidden"))));
   var surface=mixed.surface();surface.ready().toCompletableFuture().get(25,TimeUnit.SECONDS);var web=surface.draftView().view();
   waitFor(()->Boolean.TRUE.equals(fx(()->web.getEngine().executeScript("Boolean(document.querySelector('[data-qf-ready=true]')) && document.querySelector('#practice-submit').hidden"))));
   waitFor(()->fx(()->mixed.getItems().size()==1 && !surface.toggleButton().isVisible()));
   var sourceLabel=fx(()->{var label=new javafx.scene.control.Label("Source");surface.setSourceContent(label);return label;});
   assertFalse(fx(sourceLabel::isVisible));assertFalse(fx(sourceLabel::isManaged));
   assertTrue(Boolean.TRUE.equals(fx(()->web.getEngine().executeScript("document.querySelector('#question-card').classList.contains('qf-bare-card') && document.querySelector('.practice-meta').hidden && getComputedStyle(document.querySelector('.practice-meta')).display==='none' && document.querySelector('#draft-canvas-root').classList.contains('qf-hide-draft-toolbar') && document.querySelector('#draft-canvas-root').classList.contains('qf-hide-draft-zoom')"))));
   assertTrue(fx(()->mixed.lookup("#draft-next-question").isVisible()));
   waitFor(()->Boolean.TRUE.equals(fx(()->web.getEngine().executeScript("window.practiceUiTest.layout.getState().data.cardWidth===600 && Math.abs(document.querySelector('#question-card').getBoundingClientRect().left-document.querySelector('#viewport').getBoundingClientRect().left-30)<1 && Math.abs(document.querySelector('#question-card').getBoundingClientRect().top-document.querySelector('#viewport').getBoundingClientRect().top-30)<1"))));
   assertEquals("INVALID_LAYOUT",((Map<?,?>)sdkCall(web,"window.practiceUiTest.layout.configure({cardWidth:0,padding:5})").get("error")).get("code"));
   assertEquals(30,((Map<?,?>)sdkCall(web,"window.practiceUiTest.layout.getConfiguration()").get("data")).get("padding"));
   waitFor(()->Boolean.TRUE.equals(fx(()->web.getEngine().executeScript("!window.sharedPractice.autosaveState().dirty && !window.sharedPractice.autosaveState().saving && window.practiceUiTest.layout.getState().data.hasSavedGeometry"))));
   // Reopening before the first Draft visit must retain both declared width and initial camera.
   String initialGeometry=(String)fx(()->web.getEngine().executeScript("window.draftCanvas.getDraft()"));
   fx(surface.draftView()::refreshCurrent).toCompletableFuture().get(25,TimeUnit.SECONDS);
   waitFor(()->Boolean.TRUE.equals(fx(()->web.getEngine().executeScript("Boolean(document.querySelector('[data-qf-ready=true]'))"))));
   assertEquals(initialGeometry,fx(()->web.getEngine().executeScript("window.draftCanvas.getDraft()")));
   // The initial-layout exception must not permit changing a saved width or adding ink in Practice.
   for(String patch:List.of("d.questionCard.width=601","d.strokes.push({id:'forbidden',tool:'PEN',color:'#000000',width:2,points:[{x:1,y:1,pressure:.5}]})")){
    fx(()->web.getEngine().executeScript("window.layoutGuardReply=null;(()=>{const d=JSON.parse(window.draftCanvas.getDraft());"+patch+";window.sharedPractice.saveDraft(d,{initialLayout:true}).then(()=>window.layoutGuardReply='accepted',error=>window.layoutGuardReply=error.message);})()"));
    waitFor(()->Boolean.TRUE.equals(fx(()->web.getEngine().executeScript("typeof window.layoutGuardReply==='string'"))));
    assertTrue(((String)fx(()->web.getEngine().executeScript("window.layoutGuardReply"))).contains("annotations are locked"));
   }
   var pageState=(Map<?,?>)sdkCall(web,"window.practiceUiTest.navigation.getState()").get("data");
   assertEquals(2,((List<?>)pageState.get("questions")).size());assertFalse(pageState.toString().contains("answerSpec"));
   assertEquals(List.of(),sdkCall(web,"window.practiceUiTest.sources.list()").get("data"));
   assertEquals(false,sdkCall(web,"window.practiceUiTest.bank.addQuestion('SINGLE_CHOICE')").get("ok"));
   var switchReply=sdkCall(web,"window.practiceUiTest.learning.setMode('DRAFT')");
   assertEquals(true,switchReply.get("ok"),switchReply.toString());
   assertEquals("DRAFT",sdkCall(web,"window.practiceUiTest.learning.getMode()").get("data"));
   assertTrue(Boolean.TRUE.equals(fx(()->web.getEngine().executeScript("Math.abs(document.querySelector('#question-card').getBoundingClientRect().left-document.querySelector('#viewport').getBoundingClientRect().left-30)<1 && Math.abs(document.querySelector('#question-card').getBoundingClientRect().top-document.querySelector('#viewport').getBoundingClientRect().top-30)<1"))));
   assertEquals(true,sdkCall(web,"window.practiceUiTest.whiteboard.setAppearance({color:'#fff7dc',pattern:'LINES'})").get("ok"));
   assertEquals(true,sdkCall(web,"window.practiceUiTest.whiteboard.setZoom(1.4)").get("ok"));
   var board=(Map<?,?>)sdkCall(web,"window.practiceUiTest.whiteboard.getState()").get("data");
   assertEquals(1.4,board.get("zoom"));assertEquals("LINES",((Map<?,?>)board.get("paper")).get("pattern"));
   assertEquals(false,sdkCall(web,"window.practiceUiTest.whiteboard.setAppearance({color:'url(remote)'})").get("ok"));
   assertEquals("#fff7dc",((Map<?,?>)((Map<?,?>)sdkCall(web,"window.practiceUiTest.whiteboard.getState()").get("data")).get("paper")).get("color"));
   assertEquals(true,sdkCall(web,"window.practiceUiTest.whiteboard.undo()").get("ok"));
   assertEquals(true,sdkCall(web,"window.practiceUiTest.whiteboard.redo()").get("ok"));
   assertEquals(true,sdkCall(web,"window.practiceUiTest.whiteboard.setTool('PEN')").get("ok"));
   assertTrue(Boolean.TRUE.equals(fx(()->web.getEngine().executeScript("getComputedStyle(document.querySelector('[data-custom-tools]')).pointerEvents==='auto'"))));
   fx(()->web.getEngine().executeScript("(()=>{const b=document.querySelector('[data-custom-interact]');b.dispatchEvent(new PointerEvent('pointerdown',{bubbles:true,isPrimary:true,pointerId:7,button:0}));b.dispatchEvent(new PointerEvent('pointerup',{bubbles:true,isPrimary:true,pointerId:7,button:0}));b.click();})()"));
   waitFor(()->Boolean.TRUE.equals(fx(()->web.getEngine().executeScript("window.draftCanvas.uiState().tool==='INTERACT' && window.boardNotifications>0"))));
   assertEquals(0,((Number)fx(()->web.getEngine().executeScript("JSON.parse(window.draftCanvas.getDraft()).strokes.length"))).intValue());
   assertEquals(true,sdkCall(web,"window.practiceUiTest.learning.setMode('PRACTICE')").get("ok"));
   assertEquals(false,sdkCall(web,"window.practiceUiTest.whiteboard.setZoom(2)").get("ok"));
   for(String id:first.choiceAnswerSpec().correctOptionIds()){
    fx(()->web.getEngine().executeScript("document.querySelector('input[value=\""+id+"\"]').click()"));
    waitFor(()->SharedPracticeViewModel.answerIds(runtime.snapshot().questions().getFirst().sessionQuestion().draftAnswer()).contains(id));
   }
   fx(()->web.getEngine().executeScript("document.querySelector('[data-custom-submit]').click()"));
   waitFor(()->runtime.snapshot().questions().getFirst().sessionQuestion().practiceState()==PracticeSessionQuestion.State.SUBMITTED);
   assertEquals("CORRECT",SharedPracticeViewModel.from(runtime.snapshot()).question().result().status());
   waitFor(()->Boolean.TRUE.equals(fx(()->web.getEngine().executeScript("Boolean(document.querySelector('[data-qf-ready=true]')) && document.querySelector('#practice-retry').hidden"))));
   fx(()->web.getEngine().executeScript("document.querySelector('[data-custom-retry]').click()"));
   waitFor(()->runtime.snapshot().questions().getFirst().sessionQuestion().practiceState()==PracticeSessionQuestion.State.RETRYING);
   fx(surface::flushBeforeLeave).toCompletableFuture().get(25,TimeUnit.SECONDS);
   assertEquals(true,sdkCall(web,"window.practiceUiTest.navigation.next()").get("ok"));
   waitFor(()->fx(()->runtime.session().index()==1 && !surface.busy() && mixed.getItems().size()==2 && surface.toggleButton().isVisible()));
   assertTrue(fx(()->mixed.lookup("#draft-previous-question").isVisible()));
   waitFor(()->Boolean.TRUE.equals(fx(()->web.getEngine().executeScript("Boolean(document.querySelector('[data-qf-ready=true]')) && !document.querySelector('[data-submit-answer]').hidden && document.querySelector('#practice-submit').hidden && document.querySelector('.practice-meta').hidden && !document.querySelector('#draft-canvas-root').classList.contains('qf-hide-draft-toolbar')"))));
   // Builtins keep the public draft entry and floating tools; custom pages may opt out.
   assertTrue(Boolean.TRUE.equals(fx(()->web.getEngine().executeScript("!document.querySelector('[data-learning-controls]') && !document.querySelector('#draft-canvas-root').classList.contains('qf-hide-draft-zoom')"))));
   fx(()->{surface.toggleButton().fire();return null;});
   waitFor(()->fx(()->!surface.busy() && surface.learningMode()==io.quizforge.desktop.ui.question.practice.SharedLearningSurfaceMode.DRAFT));
   assertTrue(Boolean.TRUE.equals(fx(()->web.getEngine().executeScript("getComputedStyle(document.querySelector('.floating-toolbar')).display!=='none'"))));
   fx(()->{surface.toggleButton().fire();return null;});
   waitFor(()->fx(()->!surface.busy() && surface.learningMode()==io.quizforge.desktop.ui.question.practice.SharedLearningSurfaceMode.PRACTICE));
   var sessionId=runtime.snapshot().session().id();fx(()->{surface.destroy();return null;});new SqlitePracticeSessionRepository(db).archive(sessionId,Instant.now());
   var history=new PracticeHistoryService(transaction);var detail=history.loadArchivedSessionDetail(bank.assetId(),sessionId);var row=detail.questions().getFirst();
   var replay=new HistoryDraftAdapter(history,bank.assetId(),detail).load(row,row.attempts().getFirst());
   var historyPrefs=new java.util.concurrent.atomic.AtomicReference<Map<String,Boolean>>();
   var historyDetail=fx(()->new PracticeHistoryDetailView(detail,()->{},null,null,bank,codec.contentId(bank),QuestionResourceInput.NONE,new HistoryDraftAdapter(history,bank.assetId(),detail)));
   var historyView=fx(()->{var v=historyDetail.surface().draftView();v.onUiChange(historyPrefs::set);return v;});
   try {
    historyView.ready().toCompletableFuture().get(25,TimeUnit.SECONDS);fx(()->{historyView.load(replay.card(),replay.draft().document());return null;});
    waitFor(()->Boolean.FALSE.equals(historyPrefs.get().get("outline")));
    assertTrue(Boolean.TRUE.equals(fx(()->historyView.view().getEngine().executeScript("document.querySelector('#question-card').classList.contains('qf-bare-card')"))));
    fx(()->historyView.view().getEngine().executeScript("window.readonlyUiReply=null;Promise.all([window.practiceUiTest.practice.submit(),window.practiceUiTest.practice.retry()]).then(replies=>window.readonlyUiReply=JSON.stringify(replies))"));
    waitFor(()->Boolean.TRUE.equals(fx(()->historyView.view().getEngine().executeScript("typeof window.readonlyUiReply==='string'"))));
    assertTrue(((String)fx(()->historyView.view().getEngine().executeScript("window.readonlyUiReply"))).contains("READ_ONLY"));
    var historyWeb=historyView.view();
    assertEquals(true,sdkCall(historyWeb,"window.practiceUiTest.learning.setMode('DRAFT')").get("ok"));
    assertEquals(true,sdkCall(historyWeb,"window.practiceUiTest.whiteboard.setZoom(1.5)").get("ok"));
    String frozenGeometry=(String)fx(()->historyWeb.getEngine().executeScript("window.draftCanvas.getDraft()"));
    assertEquals(true,sdkCall(historyWeb,"window.practiceUiTest.layout.configure({cardWidth:900,horizontalAlign:'right',padding:12})").get("ok"));
    assertEquals(frozenGeometry,fx(()->historyWeb.getEngine().executeScript("window.draftCanvas.getDraft()")));
    assertEquals(600,((Map<?,?>)sdkCall(historyWeb,"window.practiceUiTest.layout.getState()").get("data")).get("cardWidth"));
    for(String expression:List.of("window.practiceUiTest.whiteboard.clear()","window.practiceUiTest.whiteboard.undo()","window.practiceUiTest.whiteboard.setAppearance({pattern:'GRID'})","window.practiceUiTest.whiteboard.setTool('PEN')","window.practiceUiTest.sources.add('anything')"))
     assertEquals(false,sdkCall(historyWeb,expression).get("ok"));
    assertEquals(true,sdkCall(historyWeb,"window.practiceUiTest.navigation.next()").get("ok"));
    waitFor(()->Boolean.TRUE.equals(fx(()->historyWeb.getEngine().executeScript("Boolean(document.querySelector('[data-qf-ready=true]')) && window.historyDraftReplay.getViewState().question.index===1"))));
    assertEquals(1,history.loadArchivedSessionDetail(bank.assetId(),sessionId).questions().getFirst().attempts().size());
   } finally {fx(()->{historyDetail.destroy();return null;});}
  } finally {fx(()->{mixed.surface().destroy();stage.close();manager.stopDevelopment();return null;});}
 }
 static Map<String,Object> sdkCall(WebView web,String expression)throws Exception{
  fx(()->web.getEngine().executeScript("window.sdkTestReply=null;Promise.resolve("+expression+").then(reply=>window.sdkTestReply=JSON.stringify(reply),failure=>window.sdkTestReply=JSON.stringify({ok:false,error:{message:failure.message}}))"));
  waitFor(()->Boolean.TRUE.equals(fx(()->web.getEngine().executeScript("typeof window.sdkTestReply==='string'"))));
  return io.quizforge.infrastructure.json.DocumentJson.mapper().readValue((String)fx(()->web.getEngine().executeScript("window.sdkTestReply")),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>() { });
 }
 @ParameterizedTest @ValueSource(strings={"SINGLE_CHOICE","MULTIPLE_CHOICE"})
 void choicesRemainInteractiveAfterWhiteboardGestures(String type) throws Exception {
  var bank=bank(type);var codec=new QuestionBankV2Codec();
  var service=new PracticeSessionService(new SqlitePracticeTransaction(new SqliteDatabase(directory.resolve("gestures-"+type+".db"))),Clock.systemUTC());
  var adapter=new SharedPracticeAdapter(service,fx(()->service.openOrCreateActiveSession(bank,codec.contentId(bank))));
  var view=fx(()->new SharedPracticeCanvasWebView(adapter));
  var stage=fx(()->{var w=new Stage();w.setScene(new Scene(new StackPane(view.view()),1000,700));w.setOpacity(0);w.show();return w;});
  try {
   view.ready().toCompletableFuture().get(25,TimeUnit.SECONDS);
   waitFor(()->Boolean.TRUE.equals(fx(()->view.view().getEngine().executeScript("Boolean(document.querySelector('[data-qf-ready=true]'))"))));
   int option=0;
   for(String destination:List.of("DRAFT","PRACTICE")) for(String tool:List.of("PEN","PAN")) {
    fx(()->view.view().getEngine().executeScript("window.sharedPractice.setLearningMode('DRAFT');document.querySelector('[data-mode="+tool+"]').click();window.gestureBefore=JSON.parse(window.draftCanvas.getDraft());(()=>{const v=document.querySelector('#viewport'),b=v.getBoundingClientRect();for(const [name,offset] of [['pointerdown',0],['pointermove',30],['pointerup',40]])v.dispatchEvent(new PointerEvent(name,{bubbles:true,cancelable:true,isPrimary:true,pointerId:1,button:0,buttons:name==='pointerup'?0:1,clientX:b.left+20+offset,clientY:b.top+100+offset}));window.sharedPractice.flushPendingDraft();})()"));
    waitFor(()->Boolean.TRUE.equals(fx(()->view.view().getEngine().executeScript("!window.sharedPractice.autosaveState().dirty && !window.sharedPractice.autosaveState().saving && document.querySelector('#question-card').getAttribute('aria-busy')==='false'"))));
    assertTrue(Boolean.TRUE.equals(fx(()->view.view().getEngine().executeScript(tool.equals("PEN")?"JSON.parse(window.draftCanvas.getDraft()).strokes.length>window.gestureBefore.strokes.length":"JSON.parse(window.draftCanvas.getDraft()).viewport.x!==window.gestureBefore.viewport.x"))),"Whiteboard gesture must change the document");
    fx(()->view.view().getEngine().executeScript(destination.equals("DRAFT")?"document.querySelector('[data-mode=INTERACT]').click()":"window.sharedPractice.setLearningMode('PRACTICE')"));
    // HTML subscriptions apply capabilities asynchronously after the tool change.
    waitFor(()->Boolean.TRUE.equals(fx(()->view.view().getEngine().executeScript("Array.from(document.querySelectorAll('.qf-choice-option input')).every(n=>!n.disabled)"))));
    assertTrue(Boolean.TRUE.equals(fx(()->view.view().getEngine().executeScript("Array.from(document.querySelectorAll('.qf-choice-option input')).every(n=>!n.disabled)"))),tool+" must release answer controls when returning to "+destination);
    String id=bank.questions().getFirst().choicePayload().options().get(option++).id();
    fx(()->view.view().getEngine().executeScript("document.querySelector('input[value=\""+id+"\"]').click()"));
    waitFor(()->SharedPracticeViewModel.answerIds(adapter.snapshot().questions().getFirst().sessionQuestion().draftAnswer()).contains(id));
   }
  } finally {fx(()->{view.destroy();stage.close();return null;});}
 }
 @ParameterizedTest @ValueSource(strings={"SINGLE_CHOICE","MULTIPLE_CHOICE"})
 void mainBrowserLiveUpdatesPreserveEditorAnswersAndCanvas(String type) throws Exception {
  var bank=bank(type);var q=bank.questions().getFirst();var codec=new QuestionBankV2Codec();var manager=ExtensionManager.getDefault();
  var installed=fx(()->manager.loaded().stream().filter(e->e.manifest().types().stream().anyMatch(t->t.id().equals(type))).findFirst().orElseThrow());
  Path source=directory.resolve("source-"+type);Files.createDirectories(source);
  try(var files=Files.list(installed.directory())){for(Path file:files.filter(Files::isRegularFile).toList())Files.copy(file,source.resolve(file.getFileName()));}
  var definition=installed.manifest().types().getFirst();
  var service=new PracticeSessionService(new SqlitePracticeTransaction(new SqliteDatabase(directory.resolve("live-"+type+".db"))),Clock.systemUTC());
  var adapter=new SharedPracticeAdapter(service,fx(()->service.openOrCreateActiveSession(bank,codec.contentId(bank))));
  var view=fx(()->new SharedPracticeCanvasWebView(adapter));
  var model=new QuestionBankEditorModel(bank);var body=new VBox();var errors=new VBox();var validators=new ArrayList<Runnable>();
  var stage=fx(()->{var w=new Stage();ExtensionEditorFields.render(new QuestionEditorContext(model,0,body,new HBox(),new Region(),new HashMap<>(),validators,errors,QuestionResourceInput.NONE,()->w,()->{}));w.setScene(new Scene(new HBox(new StackPane(view.view()),body),1500,750));w.setOpacity(0);w.show();return w;});
  try {
   view.ready().toCompletableFuture().get(25,TimeUnit.SECONDS);var editor=fx(()->(WebView)body.lookup("#extension-question-editor"));
   waitFor(()->Boolean.TRUE.equals(fx(()->editor.getEngine().executeScript("Boolean(document.querySelector('[data-qf-ready=true]'))"))));
   fx(()->manager.loadDevelopmentDirectory(source)).toCompletableFuture().get(25,TimeUnit.SECONDS);
   waitFor(()->Boolean.TRUE.equals(fx(()->view.view().getEngine().executeScript("Boolean(document.querySelector('[data-qf-ready=true]'))"))));
   fx(()->editor.getEngine().executeScript("(()=>{const n=document.querySelector('[data-prompt]');n.value='Unsaved editor content';n.dispatchEvent(new Event('input',{bubbles:true}));})()"));
   waitFor(()->"Unsaved editor content".equals(((TextContent)model.bank().questions().getFirst().prompt()).text()));
   fx(()->view.view().getEngine().executeScript("window.draftCanvas.setMode('PEN');(()=>{const v=document.querySelector('#viewport'),b=v.getBoundingClientRect();for(const [name,offset] of [['pointerdown',0],['pointermove',30],['pointerup',40]])v.dispatchEvent(new PointerEvent(name,{bubbles:true,cancelable:true,isPrimary:true,pointerId:1,button:0,clientX:b.left+20+offset,clientY:b.top+100+offset}));window.sharedPractice.flushPendingDraft();})()"));
   waitFor(()->Boolean.TRUE.equals(fx(()->view.view().getEngine().executeScript("!window.sharedPractice.autosaveState().dirty && !window.sharedPractice.autosaveState().saving"))));
   fx(()->{view.setMode("INTERACT");return null;});
   waitFor(()->Boolean.TRUE.equals(fx(()->view.view().getEngine().executeScript("Array.from(document.querySelectorAll('.qf-choice-option input')).every(n=>!n.disabled)"))));
   for(String id:q.choiceAnswerSpec().correctOptionIds()) {
    fx(()->view.view().getEngine().executeScript("document.querySelector('input[value=\""+id+"\"]').click()"));
    waitFor(()->SharedPracticeViewModel.answerIds(adapter.snapshot().questions().getFirst().sessionQuestion().draftAnswer()).contains(id));
   }
   String draft=fx(view::getDraft);
   Files.writeString(source.resolve(definition.renderer()),Files.readString(source.resolve(definition.renderer()))+"<p data-live-preview>Updated practice HTML</p>");
   Files.writeString(source.resolve(definition.editor()),Files.readString(source.resolve(definition.editor()))+"<p data-live-editor>Updated editor HTML</p>");
   String script=Files.readString(source.resolve(definition.rendererScript()))+"\nQF.layout.configure({cardWidth:900,horizontalAlign:'right',padding:36});QF.dom.root.dataset.liveScript='updated';";
   Files.writeString(source.resolve(definition.rendererScript()),script);
   Files.writeString(source.resolve(definition.styles().getFirst()),Files.readString(source.resolve(definition.styles().getFirst()))+"\n.qf-extension-page { background-color: rgb(240,248,230); }");
   // View changes may accompany a rule edit, but active grading must use the installed version.
   Files.writeString(source.resolve(definition.rules()),"throw new Error('Development rules must not grade active practice');");
   waitFor(()->Boolean.TRUE.equals(fx(()->view.view().getEngine().executeScript("Boolean(document.querySelector('[data-live-preview]')) && document.querySelector('[data-live-script=updated]')!==null && getComputedStyle(document.querySelector('.qf-extension-page')).backgroundColor==='rgb(240, 248, 230)'"))));
   waitFor(()->Boolean.TRUE.equals(fx(()->editor.getEngine().executeScript("Boolean(document.querySelector('[data-live-editor]')) && document.querySelector('[data-prompt]').value==='Unsaved editor content'"))));
   assertEquals(draft,fx(view::getDraft));
   assertEquals(q.choiceAnswerSpec().correctOptionIds().size(),((Number)fx(()->view.view().getEngine().executeScript("document.querySelectorAll('.qf-choice-option input:checked').length"))).intValue());
   Files.writeString(source.resolve(definition.rendererScript()),"const = broken;");
   waitFor(()->fx(()->manager.developmentStatusProperty().get()).contains("更新失败"));
   assertTrue(Boolean.TRUE.equals(fx(()->view.view().getEngine().executeScript("Boolean(document.querySelector('[data-live-preview]'))"))));
   Files.writeString(source.resolve(definition.rendererScript()),script+"\nthrow new Error('View initialization failure');");
   waitFor(()->Boolean.TRUE.equals(fx(()->view.view().getEngine().executeScript("Boolean(document.querySelector('[data-qf-error]')) && document.querySelector('[data-qf-error]').textContent.includes('View initialization failure')"))));
   Files.writeString(source.resolve(definition.rendererScript()),script+"\nQF.dom.root.dataset.recovered='true';");
   waitFor(()->Boolean.TRUE.equals(fx(()->view.view().getEngine().executeScript("Boolean(document.querySelector('[data-recovered=true]'))"))));
   fx(()->view.view().getEngine().executeScript("document.querySelector('[data-submit-answer]').click();document.querySelector('[data-confirm-answer]').click()"));
   waitFor(()->adapter.snapshot().questions().getFirst().sessionQuestion().practiceState()==PracticeSessionQuestion.State.SUBMITTED);
   assertEquals("CORRECT",adapter.viewModel().question().result().status());
   fx(()->{manager.stopDevelopment();return null;});
   waitFor(()->Boolean.TRUE.equals(fx(()->view.view().getEngine().executeScript("!document.querySelector('[data-live-preview]') && Boolean(document.querySelector('[data-qf-ready=true]'))"))));
   assertEquals(q.choiceAnswerSpec().correctOptionIds().size(),((Number)fx(()->view.view().getEngine().executeScript("document.querySelectorAll('.qf-choice-option input:checked').length"))).intValue());
  } finally {fx(()->{manager.stopDevelopment();view.destroy();stage.close();return null;});}
 }
 @Test void missingTypePreservesDataAndNavigatesInMixedBank() throws Exception {
  var available=bank("SINGLE_CHOICE");
  var missing=new Question("q_missing","ESSAY",List.of(),new TextContent("Preserved question"),new io.quizforge.core.question.model.extension.ExtensionPayload(Map.of()),new io.quizforge.core.question.model.extension.ExtensionAnswerSpec(Map.of()),new ScoreSpec(java.math.BigDecimal.TEN),null,new TextContent("Preserved analysis"),List.of());
  var bank=new QuestionBank("qb_missing","Missing",List.of(),List.of(missing,available.questions().getFirst()),List.of());
  var codec=new QuestionBankV2Codec();assertEquals(bank,fx(()->codec.parse(codec.write(bank))));assertFalse(SharedPracticeViewModel.supportsType("ESSAY"));
  var db=new SqliteDatabase(directory.resolve("missing.db"));var service=new PracticeSessionService(new SqlitePracticeTransaction(db),Clock.systemUTC());
  var session=fx(()->service.openOrCreateActiveSession(bank,codec.contentId(bank)));
  assertEquals(2,session.questions().size());assertThrows(IllegalArgumentException.class,()->SharedPracticeViewModel.from(session));
  assertTrue(((Map<?,?>)session.questions().getFirst().sessionQuestion().snapshot().correctAnswer().value()).containsKey("storedQuestion"));
  var runtime=fx(()->new PersistentPracticeRuntime(service,bank,codec.contentId(bank)));
  var mixed=fx(()->new io.quizforge.desktop.ui.question.practice.MixedQuestionPracticeView(bank,QuestionResourceInput.NONE,()->runtime,refs->null));
  var stage=fx(()->{var w=new Stage();w.setOpacity(0);w.setScene(new Scene(mixed,1000,700));w.show();return w;});
  try{
   assertFalse(fx(()->mixed.surface().supportsCurrent()));
   fx(()->{((javafx.scene.control.Button)mixed.lookup("#authoring-next-question")).fire();return null;});
   mixed.surface().ready().toCompletableFuture().get(25,TimeUnit.SECONDS);
   assertEquals(1,runtime.session().index());assertTrue(fx(()->mixed.surface().supportsCurrent()));
  }finally{fx(()->{mixed.surface().destroy();stage.close();return null;});}
 }
 @Test void requestedAcceptanceBanksRemainReadable() throws Exception {
  String paths=System.getProperty("quizforge.acceptanceBanks");org.junit.jupiter.api.Assumptions.assumeTrue(paths!=null);
  int index=0;for(String source:paths.split(";")){
   try(var loaded=fx(()->new io.quizforge.infrastructure.filesystem.qbank.QBankPackageReader().open(Path.of(source)))){
    var bank=loaded.bank();assertFalse(bank.questions().isEmpty());var codec=new QuestionBankV2Codec();
    var db=new SqliteDatabase(directory.resolve("acceptance-"+(index++)+".db"));var service=new PracticeSessionService(new SqlitePracticeTransaction(db),Clock.systemUTC());
    var runtime=fx(()->new PersistentPracticeRuntime(service,bank,codec.contentId(bank),loaded::open));
    for(int i=0;i<bank.questions().size();i++){int target=i;fx(()->{runtime.goTo(target);return null;});assertEquals(bank.questions().get(i).id(),runtime.session().current().id());}
   }
  }
 }
 static <T>T fx(Callable<T> task)throws Exception{if(Platform.isFxApplicationThread())return task.call();var future=new FutureTask<T>(task);Platform.runLater(future);return future.get(30,TimeUnit.SECONDS);}
 static void waitFor(Callable<Boolean> check)throws Exception{long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(60);while(!check.call()){if(System.nanoTime()>until)fail("Timed out waiting for HTML host");Thread.sleep(30);}}
}
