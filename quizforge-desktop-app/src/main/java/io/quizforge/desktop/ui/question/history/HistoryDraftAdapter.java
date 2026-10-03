package io.quizforge.desktop.ui.question.history;

import io.quizforge.core.practice.HistoryDraftReplay;
import io.quizforge.core.practice.PracticeHistoryDetail;
import io.quizforge.core.practice.PracticeHistoryService;
import io.quizforge.core.question.type.QuestionTypes;
import io.quizforge.desktop.poc.sharedpractice.SharedPracticeViewModel;
import io.quizforge.desktop.ui.question.objective.choice.ChoicePresentationMapper;
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
        if (attempt == null || attempt.attemptId() == null || !SharedPracticeViewModel.supportsType(question.questionType()))
            return new Replay(HistoryDraftReplay.missing(), null);
        var draft=history.loadDraftReplay(bankAssetId,detail.sessionId(),question.sessionQuestionId(),attempt.attemptId());
        if (draft.status()!=HistoryDraftReplay.Status.READY) return new Replay(draft,null);
        try { return new Replay(draft,card(question,attempt)); }
        catch (IllegalArgumentException | IllegalStateException invalid) {
            return new Replay(HistoryDraftReplay.unavailable("此历史题卡暂不支持草稿回放："+invalid.getMessage()),null);
        }
    }
    private SharedPracticeViewModel card(PracticeHistoryDetail.Question row, PracticeHistoryDetail.Attempt attempt) {
        var selected=("MATCHING".equals(row.questionType()) || "TRANSLATION".equals(row.questionType()) || "ESSAY".equals(row.questionType()))?List.<String>of():List.copyOf(ChoicePresentationMapper.answerIds(attempt.answer()));
        var metadata = row.contentSnapshot()==null ? java.util.Map.of() : (java.util.Map<?,?>)row.contentSnapshot().value();
        var flatOptions = new io.quizforge.core.practice.PracticePayload(row.options().stream().map(o->java.util.Map.of("id",o.id(),"content",o.content())).toList());
        var projected = SharedPracticeViewModel.project(row.questionType(),new SharedPracticeViewModel.Text("TEXT",row.stem()), flatOptions, metadata, selected, row.correctOptionIds(), true, attempt.answer());
        var options=projected.options();
        if (detail.bankContentId()==null
                || !List.of("CORRECT","INCORRECT","UNSCORED").contains(attempt.result().name()))
            throw new IllegalArgumentException("Shared history requires authoritative Attempt metadata");
        var result=new SharedPracticeViewModel.Result(attempt.result().name(),attempt.score(),attempt.maxScore(),
                attempt.attemptId(),attempt.attemptNo(),attempt.mode().name(),row.correctOptionIds(),
                new SharedPracticeViewModel.Text("TEXT",row.analysis()==null?"":row.analysis()));
        return new SharedPracticeViewModel("1.0",new SharedPracticeViewModel.Session(detail.sessionId(),bankAssetId,detail.bankContentId()),
                new SharedPracticeViewModel.Question(row.sessionQuestionId(),row.questionId(),row.questionType(),
                        detail.questions().indexOf(row),detail.questions().size(),projected.prompt(),
                        options,selected,SharedPracticeViewModel.State.SUBMITTED,attempt.maxScore(),result,
                        SharedPracticeViewModel.selectionModeFor(row.questionType()), projected.presentation()));
    }
}
