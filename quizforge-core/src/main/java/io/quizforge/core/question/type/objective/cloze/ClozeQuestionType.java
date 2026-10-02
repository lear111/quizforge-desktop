package io.quizforge.core.question.type.objective.cloze;

import io.quizforge.core.question.content.QuestionContentData;
import io.quizforge.core.question.content.TextContent;
import io.quizforge.core.question.model.*;
import io.quizforge.core.question.source.SourceRef;
import io.quizforge.core.question.type.*;
import io.quizforge.core.question.type.objective.choice.ChoiceOption;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import static io.quizforge.core.question.type.QuestionValidationContext.reject;

/** Repeated {{n}} markers refer to one blank; escaped markers are ordinary text. */
public final class ClozeQuestionType implements QuestionTypeDefinition {
    private static final Pattern MARKER=Pattern.compile("(\\\\)?\\{\\{([1-9][0-9]*)}}");
    public String id(){return "CLOZE";}
    public Family family(){return Family.OBJECTIVE;}
    public String payloadKind(){return "CLOZE";}
    public Class<ClozePayload> payloadClass(){return ClozePayload.class;}
    public String answerKind(){return "CLOZE";}
    public Class<ClozeAnswerSpec> answerClass(){return ClozeAnswerSpec.class;}
    public static List<Integer> numbers(String text){
        var numbers=new LinkedHashSet<Integer>();var matcher=MARKER.matcher(text);
        while(matcher.find())if(matcher.group(1)==null){
            try{numbers.add(Integer.parseInt(matcher.group(2)));}
            catch(NumberFormatException error){throw new IllegalArgumentException("空号过大："+matcher.group(2));}
        }
        return List.copyOf(numbers);
    }
    public static void validateNumbers(List<Integer> numbers){
        if(numbers.isEmpty())throw new IllegalArgumentException("正文至少需要一个空位，例如 {{1}}");
        for(int i=0;i<numbers.size();i++)if(numbers.get(i)!=i+1)
            throw new IllegalArgumentException("空位首次出现的顺序应为 {{1}}、{{2}}……，此处应为 {{"+(i+1)+"}}");
    }
    public static void validateNewNumbers(List<Integer> numbers,int existingCount){
        int next=existingCount+1;
        for(int number:numbers.stream().sorted().toList())if(number>existingCount){
            if(number!=next)throw new IllegalArgumentException("请先新增小题或补充空位 {{"+next+"}}");
            next++;
        }
    }
    public static ClozeBlank newBlank(int number,Function<String,String> ids){
        var options=new ArrayList<ChoiceOption>();
        for(int i=0;i<4;i++)options.add(new ChoiceOption(ids.apply("opt_"),new TextContent("test")));
        return new ClozeBlank(ids.apply("blank_"),number,options);
    }
    public Question createDraft(Function<String,String> ids,List<SourceRef> sources){
        var blank=newBlank(1,ids);
        return new Question(ids.apply("q_"),id(),List.of(),new TextContent("请阅读短文并选择答案。\n\n在这里输入正文 {{1}}。"),
                new ClozePayload(List.of(blank)),new ClozeAnswerSpec(List.of(new ClozeAnswerSpec.Answer(blank.id(),blank.options().getFirst().id()))),
                ScoreSpec.defaultScore(),null,null,sources);
    }
    public Question duplicate(Question q,Function<String,String> ids){
        var correct=new HashSet<>(((ClozeAnswerSpec)q.answerSpec()).correctOptionIds());
        var answers=new ArrayList<ClozeAnswerSpec.Answer>();
        var blanks=((ClozePayload)q.payload()).blanks().stream().map(b->{
            var fresh=ids.apply("blank_");
            var options=b.options().stream().map(o->{
                var id=ids.apply("opt_");if(correct.contains(o.id()))answers.add(new ClozeAnswerSpec.Answer(fresh,id));
                return new ChoiceOption(id,o.content());
            }).toList();return new ClozeBlank(fresh,b.number(),options);
        }).toList();
        return new Question(ids.apply("q_"),id(),q.stimulusRefs(),q.prompt(),new ClozePayload(blanks),new ClozeAnswerSpec(answers),
                q.scoreSpec(),q.evaluationSpec(),q.analysis(),q.sourceRefs());
    }
    public void validate(Question q,QuestionValidationContext context){
        if(!(q.payload() instanceof ClozePayload) || !(q.answerSpec() instanceof ClozeAnswerSpec))reject("CLOZE requires cloze payload and answers");
        var payload=(ClozePayload)q.payload();var answers=(ClozeAnswerSpec)q.answerSpec();
        var numbers=numbers(QuestionContentData.plainText(q.prompt()));
        if(payload.blanks().isEmpty())reject("至少需要一道小题");
        var blankNumbers=payload.blanks().stream().map(ClozeBlank::number).toList();
        try{validateNumbers(blankNumbers);}catch(IllegalArgumentException error){reject(error.getMessage());}
        if(!blankNumbers.containsAll(numbers))reject("正文空位与选项组不一致");
        var blankIds=new HashSet<String>();var answerIds=new HashSet<String>();
        for(var b:payload.blanks()){
            if(b.id()==null || !b.id().matches("blank_[A-Za-z0-9_-]+") || !blankIds.add(b.id()) || b.options().size()!=4)reject("每道小题必须有四个选项");
            var optionIds=new HashSet<String>();
            for(var o:b.options()){
                if(o.id()==null || !o.id().matches("opt_[A-Za-z0-9_-]+") || !optionIds.add(o.id()) || !context.optionIds().add(o.id()))reject("Invalid cloze option ID");
                if(!(o.content() instanceof TextContent))reject("第一版完形填空选项仅支持文字");
                context.content().accept(o.content(),true);
            }
            var matches=answers.answers().stream().filter(a->b.id().equals(a.blankId())).toList();
            if(matches.size()!=1 || !optionIds.contains(matches.getFirst().correctOptionId()))reject("每个空需要一个正确答案");
        }
        for(var a:answers.answers())if(!blankIds.contains(a.blankId()) || !answerIds.add(a.blankId()))reject("Invalid cloze answer");
    }
    public boolean evaluate(Question q,Set<String> selected){return selected.equals(Set.copyOf(((ClozeAnswerSpec)q.answerSpec()).correctOptionIds()));}
    public static void validateSelection(ClozePayload payload,Set<String> selected){
        if(!payload.options().stream().map(ChoiceOption::id).collect(java.util.stream.Collectors.toSet()).containsAll(selected))throw new IllegalArgumentException("Unknown cloze option");
        for(var b:payload.blanks())if(b.options().stream().filter(o->selected.contains(o.id())).count()>1)throw new IllegalArgumentException("每个空只能选择一个选项");
    }
}
