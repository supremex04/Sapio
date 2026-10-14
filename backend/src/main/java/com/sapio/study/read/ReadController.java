package com.sapio.study.read;

import java.util.UUID;

import jakarta.validation.Valid;

import com.sapio.security.CurrentUserId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/documents/{documentId}")
class ReadController {

    private final ReadService read;

    ReadController(ReadService read) {
        this.read = read;
    }

    @GetMapping("/nodes/{nodeId}/notes")
    ReadResponses.NotesResponse notes(@CurrentUserId UUID userId, @PathVariable UUID documentId,
                                      @PathVariable UUID nodeId,
                                      @RequestParam(defaultValue = "EXPAND") NoteStyle style) {
        return read.notes(userId, documentId, nodeId, style);
    }

    /** Rewrites the notes from the current text (the learner asked: it costs an AI call). */
    @PostMapping("/nodes/{nodeId}/notes/rewrite")
    ReadResponses.NotesResponse rewriteNotes(@CurrentUserId UUID userId, @PathVariable UUID documentId,
                                             @PathVariable UUID nodeId,
                                             @RequestParam(defaultValue = "EXPAND") NoteStyle style) {
        return read.rewriteNotes(userId, documentId, nodeId, style);
    }

    @PostMapping("/ask")
    ReadResponses.AskResponse ask(@CurrentUserId UUID userId, @PathVariable UUID documentId,
                                  @Valid @RequestBody ReadResponses.AskRequest body) {
        return read.ask(userId, documentId, body);
    }
}
