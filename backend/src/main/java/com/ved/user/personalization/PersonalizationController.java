package com.ved.user.personalization;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import com.ved.security.CurrentUserId;
import com.ved.user.personalization.LearnerProfile.AcademicLevel;
import com.ved.user.personalization.LearnerProfile.Depth;
import com.ved.user.personalization.LearnerProfile.Goal;
import com.ved.user.personalization.LearnerProfile.Occupation;
import com.ved.user.personalization.LearnerProfile.Preference;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** "Personalize": the learner's profile. Spec: docs/specs/personalization.md. */
@RestController
class PersonalizationController {

    private final PersonalizationService service;

    PersonalizationController(PersonalizationService service) {
        this.service = service;
    }

    record PersonalizationRequest(AcademicLevel academicLevel, Occupation occupation,
                                  @Size(max = 100) String fieldOfStudy, Depth depth,
                                  @Size(max = 6) List<Preference> preferences, Goal goal) {
    }

    /** @param configured false until the learner has set anything (the UI then invites them to) */
    record PersonalizationResponse(boolean configured, AcademicLevel academicLevel, Occupation occupation,
                                   String fieldOfStudy, Depth depth, List<Preference> preferences, Goal goal,
                                   Instant updatedAt) {
        static PersonalizationResponse of(LearnerProfile p) {
            return new PersonalizationResponse(p.isConfigured(), p.getAcademicLevel(), p.getOccupation(),
                    p.getFieldOfStudy(), p.getDepth(), p.getPreferences(), p.getGoal(), p.getUpdatedAt());
        }

        static PersonalizationResponse empty() {
            return new PersonalizationResponse(false, null, null, null, null, List.of(), null, null);
        }
    }

    @GetMapping("/api/me/personalization")
    PersonalizationResponse get(@CurrentUserId UUID userId) {
        return service.profile(userId).map(PersonalizationResponse::of).orElseGet(PersonalizationResponse::empty);
    }

    @PutMapping("/api/me/personalization")
    PersonalizationResponse update(@CurrentUserId UUID userId, @Valid @RequestBody PersonalizationRequest request) {
        return PersonalizationResponse.of(service.update(userId, request.academicLevel(), request.occupation(),
                request.fieldOfStudy(), request.depth(), request.preferences(), request.goal()));
    }
}
