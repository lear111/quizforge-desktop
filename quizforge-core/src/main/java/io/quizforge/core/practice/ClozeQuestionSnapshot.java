package io.quizforge.core.practice;

import io.quizforge.core.port.QuestionResourceInput;
import io.quizforge.core.question.content.QuestionContentData;
import io.quizforge.core.question.model.*;
import io.quizforge.core.question.resource.*;
import io.quizforge.core.question.type.objective.choice.ChoiceOption;
import io.quizforge.core.question.type.objective.cloze.*;
import java.io.*;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.util.*;

/** Frozen cloze content and resource bytes used by history without the current bank. */
public record ClozeQuestionSnapshot(Question question,List<QBankResource> resources,Map<String,String> resourceData) {
    public ClozeQuestionSnapshot{resources=List.copyOf(resources);resourceData=Map.copyOf(resourceData);}
    public static Map<String,Object> logical(Question q){
        var fields=new LinkedHashMap<String,Object>();
        fields.put("id",q.id());fields.put("prompt",QuestionContentData.encode(q.prompt()));
        fields.put("unitScore",q.scoreSpec().defaultMaxScore());
        fields.put("maxScore",q.scoreSpec().defaultMaxScore().multiply(BigDecimal.valueOf(((ClozePayload)q.payload()).blanks().size())));
        if(q.analysis()!=null)fields.put("analysis",QuestionContentData.encode(q.analysis()));
        fields.put("blanks",((ClozePayload)q.payload()).blanks().stream().map(b->Map.of("id",b.id(),"number",b.number(),
                "options",b.options().stream().map(o->Map.of("id",o.id(),"content",QuestionContentData.encode(o.content()))).toList())).toList());
        fields.put("answers",((ClozeAnswerSpec)q.answerSpec()).answers().stream().map(a->Map.of("blankId",a.blankId(),"correctOptionId",a.correctOptionId())).toList());
        return fields;
    }
    public static ClozeQuestionSnapshot capture(Question q,List<QBankResource> resources,QuestionResourceInput input){
        var used=new HashSet<>(QuestionContentData.resourceIds(q.prompt()));used.addAll(QuestionContentData.resourceIds(q.analysis()));
        var catalogue=resources.stream().filter(r->used.contains(r.id())).toList();var data=new LinkedHashMap<String,String>();
        if(input!=QuestionResourceInput.NONE)for(var r:catalogue)try(var stream=input.open(r)){
            if(stream==null)throw new IOException("Missing resource");
            var bytes=stream.readNBytes(EssayPracticeAnswer.MAX_DOCUMENT_CHARACTERS+1);
            if(bytes.length>EssayPracticeAnswer.MAX_DOCUMENT_CHARACTERS || !HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(r.sha256()))
                throw new IOException("Invalid cloze resource");
            data.put(r.id(),Base64.getEncoder().encodeToString(bytes));
        }catch(IOException | java.security.NoSuchAlgorithmException error){throw new IllegalStateException("Cannot snapshot cloze resource",error);}
        return new ClozeQuestionSnapshot(q,catalogue,data);
    }
    public PracticePayload payload(){
        var fields=new LinkedHashMap<>(logical(question));
        fields.put("resources",resources.stream().map(r->Map.of("id",r.id(),"kind",r.kind().name(),"mediaType",r.mediaType(),"path",r.locator(),"sha256",r.sha256())).toList());
        fields.put("resourceData",resourceData);return new PracticePayload(fields);
    }
    public static ClozeQuestionSnapshot from(PracticePayload payload){
        var f=QuestionContentData.map(payload.value());
        var blanks=((List<?>)f.get("blanks")).stream().map(v->{var b=QuestionContentData.map(v);
            return new ClozeBlank((String)b.get("id"),((Number)b.get("number")).intValue(),((List<?>)b.get("options")).stream().map(o->{
                var option=QuestionContentData.map(o);return new ChoiceOption((String)option.get("id"),QuestionContentData.decode(option.get("content")));}).toList());}).toList();
        var answers=((List<?>)f.get("answers")).stream().map(v->{var a=QuestionContentData.map(v);return new ClozeAnswerSpec.Answer((String)a.get("blankId"),(String)a.get("correctOptionId"));}).toList();
        var q=new Question((String)f.get("id"),"CLOZE",List.of(),QuestionContentData.decode(f.get("prompt")),new ClozePayload(blanks),new ClozeAnswerSpec(answers),
                new ScoreSpec(f.containsKey("unitScore")?new BigDecimal(f.get("unitScore").toString()):
                    new BigDecimal(f.get("maxScore").toString()).divide(BigDecimal.valueOf(blanks.size()),java.math.MathContext.DECIMAL128)),
                null,f.containsKey("analysis")?QuestionContentData.decode(f.get("analysis")):null,List.of());
        var resources=((List<?>)f.get("resources")).stream().map(v->{var r=QuestionContentData.map(v);return new QBankResource((String)r.get("id"),ResourceKind.valueOf((String)r.get("kind")),
                (String)r.get("mediaType"),(String)r.get("path"),(String)r.get("sha256"));}).toList();
        var data=new LinkedHashMap<String,String>();QuestionContentData.map(f.get("resourceData")).forEach((id,bytes)->data.put((String)id,(String)bytes));
        return new ClozeQuestionSnapshot(q,resources,data);
    }
    public InputStream open(QBankResource resource){var bytes=resourceData.get(resource.id());return bytes==null?null:new ByteArrayInputStream(Base64.getDecoder().decode(bytes));}
}
