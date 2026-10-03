package io.quizforge.core.port;

import io.quizforge.core.practice.draft.ActiveDraftCanvas;
import java.util.Optional;

public interface ActiveDraftCanvasRepository {
    Optional<ActiveDraftCanvas> find(String sessionQuestionId);
    void save(ActiveDraftCanvas draft);
    void delete(String sessionQuestionId);
}
