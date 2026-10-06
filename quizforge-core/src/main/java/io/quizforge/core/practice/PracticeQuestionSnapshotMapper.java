package io.quizforge.core.practice;

import io.quizforge.core.question.content.QuestionContentData;
import io.quizforge.core.question.model.Question;
import io.quizforge.core.question.resource.QBankResource;
import io.quizforge.core.question.source.SourceRef;
import io.quizforge.core.question.type.QuestionTypes;
import io.quizforge.core.question.type.extension.ExternalQuestionTypeDefinition;
import io.quizforge.core.question.codec.QuestionDataCodec;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Package-defined frozen snapshots; missing packages preserve data without executing type rules. */
public final class PracticeQuestionSnapshotMapper {
    public PracticeSessionQuestion.Snapshot map(Question question,List<QBankResource> resources,
            io.quizforge.core.port.QuestionResourceInput input) { return map(question,resources,input,null); }
    public PracticeSessionQuestion.Snapshot map(Question question,List<QBankResource> resources,
            io.quizforge.core.port.QuestionResourceInput input,String extensionVersion) {
        var snapshot = extensionVersion == null ? map(question) : map(question,extensionVersion);
        var fields = new LinkedHashMap<>(ExternalQuestionTypeDefinition.object(snapshot.correctAnswer().value()));
        var presentation = new LinkedHashMap<>(ExternalQuestionTypeDefinition.object(fields.get("extensionPresentation")));
        presentation.put("resources",resources.stream().map(r -> Map.of("id",r.id(),"kind",r.kind().name(),"mediaType",r.mediaType(),"path",r.locator(),"sha256",r.sha256())).toList());
        var data = new LinkedHashMap<String,String>();
        if(input != io.quizforge.core.port.QuestionResourceInput.NONE) for(var resource: resources) try(var stream=input.open(resource)) {
            if(stream == null)throw new java.io.IOException("Missing question resource");
            var bytes=stream.readNBytes(EssayPracticeAnswer.MAX_DOCUMENT_CHARACTERS+1);
            if(bytes.length>EssayPracticeAnswer.MAX_DOCUMENT_CHARACTERS || !java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)).equals(resource.sha256()))throw new java.io.IOException("Invalid question resource");
            data.put(resource.id(),java.util.Base64.getEncoder().encodeToString(bytes));
        } catch(java.io.IOException|java.security.NoSuchAlgorithmException failure) {throw new IllegalStateException("Cannot snapshot question resource",failure);}
        presentation.put("resourceData",data); fields.put("extensionPresentation",presentation);
        return copy(snapshot,new PracticePayload(fields));
    }
    public PracticeSessionQuestion.Snapshot map(Question question) {
        var type=QuestionTypes.find(question.type()).orElse(null);
        if(type instanceof ExternalQuestionTypeDefinition external)return map(question,external.version());
        var stored=QuestionDataCodec.encodePersisted(question);
        var publicQuestion=new LinkedHashMap<>(stored);publicQuestion.remove("answerSpec");publicQuestion.remove("analysis");publicQuestion.remove("evaluationSpec");
        var presentation=new LinkedHashMap<String,Object>();
        presentation.put("question",publicQuestion);presentation.put("missingExtension",true);
        presentation.put("targets",List.of(Map.of("id",question.id(),"number",1,"gradable",false,"locked",false,"label","缺少对应题型扩展")));
        presentation.put("resources",List.of());presentation.put("resourceData",Map.of());
        var fields=new LinkedHashMap<String,Object>();
        fields.put("correctOptionIds",List.of());fields.put("maxScore",question.scoreSpec().defaultMaxScore());
        fields.put("missingExtension",true);fields.put("storedQuestion",stored);fields.put("extensionPresentation",presentation);
        return new PracticeSessionQuestion.Snapshot(question.type(),QuestionContentData.plainText(question.prompt()),new PracticePayload(List.of()),new PracticePayload(fields),
                QuestionContentData.plainText(question.analysis()),new PracticePayload(question.sourceRefs().stream().map(this::sourceRef).toList()));
    }
    public PracticeSessionQuestion.Snapshot map(Question question,String extensionVersion) {
        if(!QuestionTypes.isExtension(question.type()))return map(question);
        var value=QuestionTypes.requireVersion(question.type(),extensionVersion).snapshot(question);
        return new PracticeSessionQuestion.Snapshot(value.questionType(),value.stem(),value.options(),value.correctAnswer(),value.analysis(),new PracticePayload(question.sourceRefs().stream().map(this::sourceRef).toList()));
    }
    public static PracticeSessionQuestion.Snapshot logical(PracticeSessionQuestion.Snapshot snapshot) {
        var fields=new LinkedHashMap<>(ExternalQuestionTypeDefinition.object(snapshot.correctAnswer().value()));
        fields.remove("extensionPresentation");fields.remove("maxScore");
        if(fields.get("extension") instanceof Map<?,?> metadata) {
            var extension=new LinkedHashMap<>(ExternalQuestionTypeDefinition.object(metadata));
            var question=new LinkedHashMap<>(ExternalQuestionTypeDefinition.object(extension.get("question")));
            question.remove("analysis");question.remove("scoreSpec");question.remove("maxScore");extension.put("question",question);fields.put("extension",extension);
        }
        return copy(snapshot,new PracticePayload(fields));
    }
    private static PracticeSessionQuestion.Snapshot copy(PracticeSessionQuestion.Snapshot value,PracticePayload correct) {
        return new PracticeSessionQuestion.Snapshot(value.questionType(),value.stem(),value.options(),correct,value.analysis(),value.sourceRefs());
    }
    private Map<String,Object> sourceRef(SourceRef ref) {
        var fields=new LinkedHashMap<String,Object>(); fields.put("documentAssetId",ref.documentAssetId());fields.put("documentContentId",ref.documentContentId());
        if(ref.address().kind()!=io.quizforge.core.question.source.QuestionSourceAddress.Kind.ANCHOR)throw new IllegalArgumentException("Current QBank snapshots require named source anchors");
        fields.put("anchorName",ref.anchorName());fields.put("occurrence",ref.occurrence());fields.put("documentTitle",ref.documentTitle());fields.put("sectionTitle",ref.sectionTitle());return fields;
    }
}
