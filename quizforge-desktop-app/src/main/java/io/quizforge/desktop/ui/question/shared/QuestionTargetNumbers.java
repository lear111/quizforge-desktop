package io.quizforge.desktop.ui.question.shared;

import io.quizforge.core.question.type.QuestionTarget;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** The same bank-order numbering used by the outline, independent of question type. */
public final class QuestionTargetNumbers {
    private QuestionTargetNumbers() { }
    public static List<QuestionTarget> archived(io.quizforge.core.practice.PracticeHistoryDetail.Question row){
        var metadata=row.contentSnapshot()==null?Map.<String,Object>of():io.quizforge.core.question.content.QuestionContentData.map(row.contentSnapshot().value());
        if(!(metadata.get("extensionPresentation") instanceof Map<?,?> presentation))return List.of(new QuestionTarget(row.questionId(),1,true,false,""));
        if(!(presentation.get("targets") instanceof List<?> targets))return List.of(new QuestionTarget(row.questionId(),1,true,false,""));
        return targets.stream().map(io.quizforge.core.question.content.QuestionContentData::map).map(t->new QuestionTarget((String)t.get("id"),((Number)t.get("number")).intValue(),!Boolean.FALSE.equals(t.get("gradable")),Boolean.TRUE.equals(t.get("locked")),t.get("label") instanceof String label?label:"")).toList();
    }
    public static List<Map<String,Object>> current(List<? extends List<QuestionTarget>> questions,int index){
        var result=new ArrayList<Map<String,Object>>();int number=0;
        for(int i=0;i<questions.size();i++)for(var target:questions.get(i)){
            if(!target.gradable()||target.locked())continue;
            number++;if(i==index)result.add(Map.of("id",target.id(),"number",number,"localNumber",target.number(),"label",target.label()));
        }
        return List.copyOf(result);
    }
}
