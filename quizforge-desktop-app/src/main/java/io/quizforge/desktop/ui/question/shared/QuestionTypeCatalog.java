package io.quizforge.desktop.ui.question.shared;
import io.quizforge.core.question.type.QuestionTypes;
import io.quizforge.desktop.ui.question.extension.ExtensionEditorFields;
import io.quizforge.desktop.ui.shared.UiTheme;
import java.util.List;
/** Names and editing are provided exclusively by installed question packages. */
public final class QuestionTypeCatalog {
 private QuestionTypeCatalog(){}
 public static void renderEditor(String type,QuestionEditorContext context){
  if(QuestionTypes.find(type).isEmpty()){context.body().getChildren().add(UiTheme.quietState("缺少题型扩展","安装 "+type+" 对应扩展后可编辑此题。原始数据和资源已保留。"));return;}
  ExtensionEditorFields.render(context);
 }
 public static String label(String type){return QuestionTypes.find(type).map(d->d.label()).orElse(type+"（缺少扩展）");}
 public static List<String> editableTypes(){return QuestionTypes.definitions().stream().map(d->d.id()).toList();}
}
