package io.quizforge.desktop.ui.question.extension;

import io.quizforge.core.port.QuestionResourceInput;
import io.quizforge.core.question.content.QuestionContentData;
import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.core.question.codec.QuestionDataCodec;
import io.quizforge.desktop.extension.ExtensionManager;
import io.quizforge.desktop.learning.SharedContent;
import io.quizforge.desktop.ui.shared.UiTheme;
import javafx.concurrent.Worker;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;
import java.util.*;

/** Read-only fallback uses the same extension preview as the Hub, without practice side effects. */
public final class ExtensionQuestionPreview extends StackPane {
    private final WebView view = new WebView();
    private final QuestionBank bank;
    private final QuestionResourceInput input;
    private final Host host = new Host();
    private final com.fasterxml.jackson.databind.ObjectMapper json = io.quizforge.infrastructure.json.DocumentJson.mapper();
    public ExtensionQuestionPreview(QuestionBank bank, int index, QuestionResourceInput input) {
        this.bank=bank;this.input=input;setMinSize(0,0);view.setId("extension-question-preview");view.setContextMenuEnabled(false);
        getChildren().add(view);
        view.getEngine().getLoadWorker().stateProperty().addListener((o,before,after)->{
            if(after!=Worker.State.SUCCEEDED)return;
            try {
                ExtensionManager.getDefault().installScripts(view);
                var window=(JSObject)view.getEngine().executeScript("window");window.setMember("previewHost",host);
                window.setMember("__previewQuestion",json.writeValueAsString(QuestionDataCodec.encodePersisted(bank.questions().get(index))));
                try {view.getEngine().executeScript("window.questionPreview.showQuestion(JSON.parse(window.__previewQuestion),{showAnswers:false,resolveContent:c=>JSON.parse(window.previewHost.resolveContent(JSON.stringify(c)))})");}
                finally {window.removeMember("__previewQuestion");}
            } catch(Exception failure){getChildren().setAll(UiTheme.quietState("无法预览题目",failure.getMessage()));}
        });
        view.sceneProperty().addListener((o,before,after)->{if(before!=null&&after==null)view.getEngine().load(null);});
        view.getEngine().load(Objects.requireNonNull(getClass().getResource("/editor/draft-canvas/extension-preview.html")).toExternalForm());
    }
    public final class Host {
        public String resolveContent(String encoded) {
            try {
                var raw=json.readValue(encoded,Object.class);var ids=QuestionContentData.resourceIds(QuestionContentData.decode(raw));
                var bytes=new LinkedHashMap<String,String>();var metadata=new ArrayList<Map<String,String>>();
                for(var resource:bank.resources())if(ids.contains(resource.id())) {
                    try(var stream=input.open(resource)){if(stream==null)throw new IllegalArgumentException("资源缺失："+resource.id());bytes.put(resource.id(),Base64.getEncoder().encodeToString(stream.readAllBytes()));}
                    metadata.add(Map.of("id",resource.id(),"mediaType",resource.mediaType()));
                }
                return json.writeValueAsString(SharedContent.read(raw,Map.of("resourceData",bytes,"resources",metadata)));
            }catch(Exception failure){throw new IllegalArgumentException("无法显示题目内容",failure);}
        }
    }
}
