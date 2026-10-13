package com.sapio.study.read;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.sapio.knowledge.figure.FigureView;

/** Contracts for notes and "ask your document" (docs/specs/knowledge.md). */
public final class ReadResponses {

    private ReadResponses() {
    }

    /** A cited section: {@code citation} is the id used inline in markdown, e.g. "§2.3". */
    public record SourceRef(String citation, UUID nodeId, String label, String title, String pages) {
    }

    /** @param scope "section" (full notes) or "overview" (chapter notes built from its subsections) */
    public record NotesResponse(UUID nodeId, NoteStyle style, String scope, String markdown, Map<String, FigureView> figures,
                                List<SourceRef> sources, Instant generatedAt) {
    }

    public record AskRequest(@NotBlank @Size(max = 2000) String question, UUID nodeId,
                             @Valid @Size(max = 20) List<Turn> history) {
        public record Turn(@Pattern(regexp = "user|tutor") String role, @NotBlank @Size(max = 4000) String text) {
        }
    }

    public record AskResponse(String answer, boolean grounded, List<SourceRef> sources,
                              Map<String, FigureView> figures) {
    }
}
