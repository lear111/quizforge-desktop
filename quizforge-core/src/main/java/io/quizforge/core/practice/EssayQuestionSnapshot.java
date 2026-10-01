package io.quizforge.core.practice;

import io.quizforge.core.port.QuestionResourceInput;
import io.quizforge.core.question.content.QuestionContent;
import io.quizforge.core.question.content.QuestionContentData;
import io.quizforge.core.question.model.Question;
import io.quizforge.core.question.resource.QBankResource;
import io.quizforge.core.question.resource.ResourceKind;
import java.io.*;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.util.*;

/** Archived essay presentation and owned resource bytes, independent of the current QBank. */
public record EssayQuestionSnapshot(QuestionContent prompt, QuestionContent reference, QuestionContent analysis,
        BigDecimal maxScore, String guidance, List<QBankResource> resources, Map<String,String> resourceData) {
    public EssayQuestionSnapshot { resources=List.copyOf(resources);resourceData=Map.copyOf(resourceData); }

    public static EssayQuestionSnapshot capture(Question question,List<QBankResource> resources,QuestionResourceInput input) {
        var used=new HashSet<>(QuestionContentData.resourceIds(question.prompt()));
        var reference=question.essayAnswerSpec().referenceAnswer();
        used.addAll(QuestionContentData.resourceIds(reference));used.addAll(QuestionContentData.resourceIds(question.analysis()));
        var catalogue=resources.stream().filter(resource->used.contains(resource.id())).toList();
        Map<String,String> data=new LinkedHashMap<>();
        if(input!=QuestionResourceInput.NONE)for(var resource:catalogue)try(var stream=input.open(resource)){
            if(stream==null)throw new IOException("Missing resource: "+resource.id());
            byte[] bytes=stream.readNBytes(EssayPracticeAnswer.MAX_DOCUMENT_CHARACTERS+1);
            if(bytes.length>EssayPracticeAnswer.MAX_DOCUMENT_CHARACTERS)throw new IOException("Essay resource is too large");
            String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            if(!hash.equals(resource.sha256()))throw new IOException("Essay resource hash mismatch");
            data.put(resource.id(),Base64.getEncoder().encodeToString(bytes));
        }catch(IOException | java.security.NoSuchAlgorithmException error){throw new IllegalStateException("Cannot snapshot essay resource",error);}
        return new EssayQuestionSnapshot(question.prompt(),reference,question.analysis(),question.scoreSpec().defaultMaxScore(),
                question.evaluationSpec()==null?null:question.evaluationSpec().evaluatorGuidance(),catalogue,data);
    }

    public PracticePayload payload(){
        Map<String,Object> fields=new LinkedHashMap<>();fields.put("prompt",QuestionContentData.encode(prompt));
        if(reference!=null)fields.put("reference",QuestionContentData.encode(reference));
        if(analysis!=null)fields.put("analysis",QuestionContentData.encode(analysis));
        if(maxScore!=null)fields.put("maxScore",maxScore);if(guidance!=null)fields.put("guidance",guidance);
        fields.put("resources",resources.stream().map(r->Map.of("id",r.id(),"kind",r.kind().name(),
                "mediaType",r.mediaType(),"path",r.locator(),"sha256",r.sha256())).toList());
        fields.put("resourceData",resourceData);return new PracticePayload(fields);
    }

    public static EssayQuestionSnapshot from(PracticePayload payload){
        var fields=QuestionContentData.map(payload.value());
        var resources=((List<?>)fields.get("resources")).stream().map(value->{
            var r=QuestionContentData.map(value);return new QBankResource((String)r.get("id"),ResourceKind.valueOf((String)r.get("kind")),
                    (String)r.get("mediaType"),(String)r.get("path"),(String)r.get("sha256"));
        }).toList();
        Map<String,String> data=new LinkedHashMap<>();QuestionContentData.map(fields.get("resourceData"))
                .forEach((id,bytes)->data.put((String)id,(String)bytes));
        return new EssayQuestionSnapshot(QuestionContentData.decode(fields.get("prompt")),
                fields.containsKey("reference")?QuestionContentData.decode(fields.get("reference")):null,
                fields.containsKey("analysis")?QuestionContentData.decode(fields.get("analysis")):null,
                fields.containsKey("maxScore")?new BigDecimal(fields.get("maxScore").toString()):null,
                (String)fields.get("guidance"),resources,data);
    }

    public InputStream open(QBankResource resource){
        String encoded=resourceData.get(resource.id());
        return encoded==null?null:new ByteArrayInputStream(Base64.getDecoder().decode(encoded));
    }
}
