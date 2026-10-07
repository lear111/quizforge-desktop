package io.quizforge.core.question.service;

import io.quizforge.core.ErrorCode;
import io.quizforge.core.QuizForgeException;
import io.quizforge.core.question.content.BlockImageNode;
import io.quizforge.core.question.content.BlockMathNode;
import io.quizforge.core.question.content.BlockNode;
import io.quizforge.core.question.content.BlockQuoteNode;
import io.quizforge.core.question.content.BulletListNode;
import io.quizforge.core.question.content.DocumentContent;
import io.quizforge.core.question.content.HeadingNode;
import io.quizforge.core.question.content.InlineImageNode;
import io.quizforge.core.question.content.InlineMathNode;
import io.quizforge.core.question.content.InlineNode;
import io.quizforge.core.question.content.InlineTextNode;
import io.quizforge.core.question.content.LineBreakNode;
import io.quizforge.core.question.content.LinkNode;
import io.quizforge.core.question.content.ListItemNode;
import io.quizforge.core.question.content.OrderedListNode;
import io.quizforge.core.question.content.ParagraphNode;
import io.quizforge.core.question.content.QuestionContent;
import io.quizforge.core.question.content.RichContent;
import io.quizforge.core.question.content.TextContent;
import io.quizforge.core.question.model.EvaluationSpec;
import io.quizforge.core.question.model.Question;
import io.quizforge.core.question.model.QuestionBank;
import io.quizforge.core.question.resource.QBankResource;
import io.quizforge.core.question.resource.ResourceKind;
import io.quizforge.core.question.source.QuestionSourceAddress;
import java.math.BigDecimal;
import java.util.*;

/** Logical-bank validation includes references; resource bytes remain outside this contract. */
public final class QuestionBankValidator {
    private final java.util.function.Function<Question,io.quizforge.core.question.type.QuestionTypeDefinition> resolveType;
    public QuestionBankValidator(){this(io.quizforge.core.question.type.QuestionTypes::forData);}
    public QuestionBankValidator(java.util.function.Function<Question,io.quizforge.core.question.type.QuestionTypeDefinition> resolveType){
        this.resolveType=Objects.requireNonNull(resolveType);
    }
    public void validate(QuestionBank bank) { validate(bank, false); }
    public void validateEmptyDraft(QuestionBank bank) { validate(bank, true); }
    private void validate(QuestionBank bank, boolean draft) {
        if (bank == null || !"2.0".equals(bank.schemaVersion()) || !id(bank.assetId(), "qb_")
                || blank(bank.title()) || (draft ? !bank.questions().isEmpty() : bank.questions().isEmpty()))
            fail("Invalid QBank v2 metadata");
        Map<String,QBankResource> resources = new HashMap<>();
        for (var resource : bank.resources()) {
            if (!id(resource.id(), "res_") || resource.kind() == null || blank(resource.mediaType())
                    || !resource.mediaType().matches("[A-Za-z0-9.+-]+/[A-Za-z0-9.+-]+")
                    || !resource.mediaType().startsWith(switch(resource.kind()) {case IMAGE -> "image/";case AUDIO -> "audio/";case DOCUMENT -> "application/";})
                    || !locator(resource.locator()) || resource.sha256() == null
                    || !resource.sha256().matches("[0-9a-f]{64}")
                    || resources.putIfAbsent(resource.id(), resource) != null) fail("Invalid or duplicate resource");
        }
        Set<String> stimuli = new HashSet<>();
        for (var stimulus : bank.stimuli()) {
            if (!identifier(stimulus.id()) || !stimuli.add(stimulus.id())) fail("Invalid or duplicate stimulus");
            content(stimulus.content(), true, resources);
        }
        Set<String> questionIds = new HashSet<>(), allOptions = new HashSet<>();
        Map<String,String> revisions = new HashMap<>();
        for (var question : bank.questions()) {
            if (!id(question.id(), "q_") || !questionIds.add(question.id())) fail("Invalid question id");
            var type=resolveType.apply(question);
            Set<String> usedStimuli = new HashSet<>();
            for (String ref : question.stimulusRefs())
                if (!stimuli.contains(ref) || !usedStimuli.add(ref)) fail("Invalid stimulusRef");
            content(question.prompt(), true, resources);
            if (question.analysis() != null) content(question.analysis(), false, resources);
            if (question.scoreSpec() == null || question.scoreSpec().defaultMaxScore() == null
                    || question.scoreSpec().defaultMaxScore().signum() <= 0) fail("defaultMaxScore must be positive");
            type.validate(question,new io.quizforge.core.question.type.QuestionValidationContext(
                    (value,required)->content(value,required,resources),allOptions));
            Set<String> refs = new HashSet<>();
            for (var ref : question.sourceRefs()) {
                if (!id(ref.documentAssetId(), "doc_") || ref.documentContentId() == null
                        || !ref.documentContentId().matches("qfd:v[12]:[0-9a-f]{64}")
                        || ref.address() == null || ref.address().kind() != QuestionSourceAddress.Kind.ANCHOR
                        || ref.occurrence() == null || ref.occurrence() < 1
                        || !refs.add(ref.documentAssetId() + "\0" + ref.address())) fail("Invalid sourceRefs");
                String previous = revisions.putIfAbsent(ref.documentAssetId(), ref.documentContentId());
                if (previous != null && !previous.equals(ref.documentContentId())) fail("Conflicting source revisions");
            }
            evaluation(question.evaluationSpec());
        }
    }
    private void evaluation(EvaluationSpec spec) {
        if (spec == null) return;
        Set<String> ids = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (var criterion : spec.criteria()) {
            if (!identifier(criterion.id()) || !ids.add(criterion.id()) || blank(criterion.description())
                    || criterion.weight() == null || criterion.weight().signum() <= 0) fail("Invalid evaluation criterion");
            total = total.add(criterion.weight());
        }
        if (!spec.criteria().isEmpty() && total.compareTo(BigDecimal.ONE) != 0)
            fail("Evaluation criterion weights must sum exactly to 1");
    }
    private void content(QuestionContent content, boolean nonblank, Map<String,QBankResource> resources) {
        if (content == null) fail("Question content is required");
        if (content instanceof TextContent text) {
            if (nonblank && blank(text.text())) fail("Content cannot be blank");
        } else if (content instanceof DocumentContent doc) {
            var resource = resources.get(doc.resourceId());
            if (resource == null || resource.kind() != ResourceKind.DOCUMENT
                    || !"application/vnd.quizforge.canvas+json".equals(resource.mediaType())) fail("Invalid document resourceId");
        } else if (content instanceof RichContent rich) {
            if (nonblank && rich.document().blocks().stream().noneMatch(this::meaningful))
                fail("Rich content cannot be blank");
            for (var block : rich.document().blocks()) block(block,resources);
        } else fail("Unsupported content kind");
    }
    private void block(BlockNode node,Map<String,QBankResource> resources) {
        if(node instanceof ParagraphNode p) inline(p.children(),resources);
        else if(node instanceof HeadingNode h) inline(h.children(),resources);
        else if(node instanceof BulletListNode l) l.items().forEach(i->item(i,resources));
        else if(node instanceof OrderedListNode l) l.items().forEach(i->item(i,resources));
        else if(node instanceof BlockQuoteNode q) q.blocks().forEach(b->block(b,resources));
        else if(node instanceof BlockImageNode i) image(i.resourceId(),resources);
        else if(node instanceof BlockMathNode m && blank(m.tex())) fail("Math TeX is required");
    }
    private void item(ListItemNode item,Map<String,QBankResource> resources) {
        if(item.blocks().isEmpty())fail("List item cannot be empty");
        item.blocks().forEach(b->block(b,resources));
    }
    private void inline(List<InlineNode> nodes, Map<String,QBankResource> resources) {
        for (var node : nodes) {
            if (node instanceof InlineTextNode t && (t.text() == null
                    || new java.util.HashSet<>(t.marks()).size()!=t.marks().size())) fail("Invalid inline text marks");
            else if (node instanceof InlineImageNode i) image(i.resourceId(), resources);
            else if (node instanceof InlineMathNode m && blank(m.tex())) fail("Math TeX is required");
            else if (node instanceof LinkNode link) {
                if (blank(link.href())) fail("Link href is required");
                inline(link.children(), resources);
            }
        }
    }
    private void image(String id, Map<String,QBankResource> resources) {
        if (!resources.containsKey(id) || resources.get(id).kind() != ResourceKind.IMAGE) fail("Invalid image resourceId");
    }
    private boolean meaningful(BlockNode node) {
        return switch (node) {
            case ParagraphNode paragraph -> paragraph.children().stream().anyMatch(this::meaningful);
            case HeadingNode heading -> heading.children().stream().anyMatch(this::meaningful);
            case BulletListNode list -> list.items().stream().anyMatch(i->i.blocks().stream().anyMatch(this::meaningful));
            case OrderedListNode list -> list.items().stream().anyMatch(i->i.blocks().stream().anyMatch(this::meaningful));
            case BlockQuoteNode quote -> quote.blocks().stream().anyMatch(this::meaningful);
            case BlockImageNode image -> true;
            case BlockMathNode math -> !blank(math.tex());
        };
    }
    private boolean meaningful(InlineNode node) {
        return switch (node) {
            case InlineTextNode text -> !blank(text.text());
            case InlineImageNode image -> true;
            case InlineMathNode math -> !blank(math.tex());
            case LineBreakNode lineBreak -> false;
            case LinkNode link -> link.children().stream().anyMatch(this::meaningful);
        };
    }
    private boolean locator(String value) {
        if (blank(value) || value.startsWith("/") || value.contains("\\") || value.contains(":")
                || value.chars().anyMatch(Character::isISOControl)) return false;
        for (String segment : value.split("/", -1))
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) return false;
        return true;
    }
    private boolean id(String value,String prefix) { return value != null && value.matches(prefix + "[A-Za-z0-9_-]+"); }
    private boolean identifier(String value) { return value != null && value.matches("[A-Za-z][A-Za-z0-9_-]*"); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private void fail(String message) { throw new QuizForgeException(ErrorCode.QUESTION_BANK_FILE_INVALID,message); }
}
