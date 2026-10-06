package io.quizforge.desktop.ui.question.history;

import io.quizforge.core.practice.HistoryDraftReplay;
import io.quizforge.core.practice.PracticeHistoryDetail;
import io.quizforge.core.practice.PracticeHistoryService;
import io.quizforge.core.question.type.QuestionTypes;
import io.quizforge.desktop.learning.SharedPracticeViewModel;
import java.util.List;

/** Frozen History -> existing shared renderer contract. No current bank or Practice mutation API. */
public final class HistoryDraftAdapter {
    private final PracticeHistoryService history;
    private final String bankAssetId;
    private final PracticeHistoryDetail detail;
    public record Replay(HistoryDraftReplay draft, SharedPracticeViewModel card) { }

    public HistoryDraftAdapter(PracticeHistoryService history, String bankAssetId, PracticeHistoryDetail detail) {
        this.history=history; this.bankAssetId=bankAssetId; this.detail=detail;
    }
    public Replay load(PracticeHistoryDetail.Question question, PracticeHistoryDetail.Attempt attempt) {
        if (!SharedPracticeViewModel.supportsType(question.questionType()))
            return new Replay(HistoryDraftReplay.unavailable("缺少对应题型扩展："+question.questionType()),null);
        if (question.contentSnapshot()==null || detail.bankContentId()==null
                || attempt!=null && attempt.attemptId()==null)
            return new Replay(HistoryDraftReplay.unavailable("此记录缺少新版题卡快照，请重新练习生成历史记录。"), null);
        var draft=attempt==null ? history.loadFinalDraftReplay(bankAssetId,detail.sessionId(),question.sessionQuestionId())
                : history.loadDraftReplay(bankAssetId,detail.sessionId(),question.sessionQuestionId(),attempt.attemptId());
        if (draft.status()==HistoryDraftReplay.Status.UNAVAILABLE) return new Replay(draft,null);
        if (draft.status()==HistoryDraftReplay.Status.MISSING) draft=HistoryDraftReplay.ready(io.quizforge.core.practice.draft.DraftCanvasDocument.createEmpty());
        try { return new Replay(draft,card(question,attempt)); }
        catch (IllegalArgumentException | IllegalStateException invalid) {
            return new Replay(HistoryDraftReplay.unavailable("此历史题卡暂不支持草稿回放："+invalid.getMessage()),null);
        }
    }
    private SharedPracticeViewModel card(PracticeHistoryDetail.Question row, PracticeHistoryDetail.Attempt attempt) {
        boolean submitted=attempt!=null;
        var answer=submitted?attempt.answer():row.draftAnswer();
        var selected=SharedPracticeViewModel.answerIds(answer);
        var metadata = row.contentSnapshot()==null ? java.util.Map.of() : (java.util.Map<?,?>)row.contentSnapshot().value();
        var flatOptions = new io.quizforge.core.practice.PracticePayload(row.options().stream().map(o->java.util.Map.of("id",o.id(),"content",o.content())).toList());
        var correct=submitted?row.correctOptionIds():List.<String>of();
        var projected = SharedPracticeViewModel.project(row.questionType(),new SharedPracticeViewModel.Text("TEXT",row.stem()), flatOptions, metadata, selected, correct, submitted, answer);
        var options=projected.options();
        if (detail.bankContentId()==null
                || submitted && !List.of("CORRECT","INCORRECT","UNSCORED").contains(attempt.result().name()))
            throw new IllegalArgumentException("Shared history requires authoritative Attempt metadata");
        var result=submitted?new SharedPracticeViewModel.Result(attempt.result().name(),attempt.score(),attempt.maxScore(),
                attempt.attemptId(),attempt.attemptNo(),attempt.mode().name(),row.correctOptionIds(),
                SharedPracticeViewModel.analysis(row.questionType(),metadata,row.analysis())):null;
        Double maximum=submitted?attempt.maxScore():metadata.get("maxScore") instanceof Number value?value.doubleValue():null;
        var state=submitted?SharedPracticeViewModel.State.SUBMITTED:row.finalState()==io.quizforge.core.practice.PracticeSessionQuestion.State.RETRYING
                ?SharedPracticeViewModel.State.RETRYING:answer==null?SharedPracticeViewModel.State.UNANSWERED:SharedPracticeViewModel.State.DRAFT;
        return new SharedPracticeViewModel("1.0",new SharedPracticeViewModel.Session(detail.sessionId(),bankAssetId,detail.bankContentId()),
                new SharedPracticeViewModel.Question(row.sessionQuestionId(),row.questionId(),row.questionType(),
                        detail.questions().indexOf(row),detail.questions().size(),projected.prompt(),
                        options,selected,state,maximum,result,
                        SharedPracticeViewModel.selectionModeFor(row.questionType()), projected.presentation()));
    }
}
