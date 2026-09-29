package io.quizforge.desktop.ui;

import io.quizforge.core.question.*;

import io.quizforge.core.practice.PracticePayload;
import io.quizforge.core.workspace.WorkspaceId;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Decodes only archived source snapshots; current QBank content never participates. */
final class HistorySourceNavigationAdapter {
    private final QuestionSourceNavigationAdapter sources;

    HistorySourceNavigationAdapter(QuestionSourceNavigationAdapter sources) { this.sources = sources; }

    List<QuestionSourceNavigationAdapter.Source> inspect(WorkspaceId workspace, PracticePayload snapshot) {
        return sources.inspect(workspace, references(snapshot));
    }

    void open(WorkspaceId workspace, SourceRef ref) { sources.open(workspace, ref); }

    private static List<SourceRef> references(PracticePayload snapshot) {
        if (!(snapshot.value() instanceof List<?> values))
            throw new IllegalArgumentException("Invalid archived source snapshot");
        return values.stream().map(value -> {
            if (!(value instanceof Map<?, ?> fields))
                throw new IllegalArgumentException("Invalid archived source reference");
            int addresses = (fields.containsKey("anchorName") ? 1 : 0)
                    + (fields.containsKey("nodeId") ? 1 : 0) + (fields.containsKey("sectionId") ? 1 : 0);
            if (addresses != 1) throw new IllegalArgumentException("Invalid archived source address");
            QuestionSourceAddress address;
            if (fields.containsKey("anchorName")) {
                if (!(fields.get("occurrence") instanceof BigDecimal occurrence))
                    throw new IllegalArgumentException("Invalid archived anchor occurrence");
                address = QuestionSourceAddress.anchor(text(fields, "anchorName"), occurrence.intValueExact());
            } else if (fields.containsKey("nodeId")) address = QuestionSourceAddress.node(text(fields, "nodeId"));
            else address = QuestionSourceAddress.section(text(fields, "sectionId"));
            return new SourceRef(text(fields, "documentAssetId"),
                    text(fields, "documentContentId"), address,
                    displayText(fields, "documentTitle"), displayText(fields, "sectionTitle"));
        }).toList();
    }

    private static String text(Map<?, ?> fields, String key) {
        if (!(fields.get(key) instanceof String text) || text.isBlank())
            throw new IllegalArgumentException("Invalid archived source field: " + key);
        return text;
    }

    private static String displayText(Map<?, ?> fields, String key) {
        return fields.get(key) instanceof String text ? text : "";
    }
}
