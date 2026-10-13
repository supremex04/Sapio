package com.sapio.study.overview;

import java.util.UUID;

import com.sapio.security.CurrentUserId;
import com.sapio.study.material.MaterialResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
class StudyOverviewController {

    private final StudyOverviewService overview;

    StudyOverviewController(StudyOverviewService overview) {
        this.overview = overview;
    }

    @GetMapping("/api/documents/{documentId}/material")
    MaterialResponse material(@CurrentUserId UUID userId, @PathVariable UUID documentId) {
        return overview.material(userId, documentId);
    }

    @GetMapping("/api/study/next-step")
    NextStep nextStep(@CurrentUserId UUID userId) {
        return overview.nextStep(userId);
    }
}
