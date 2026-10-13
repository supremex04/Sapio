package com.sapio.study.ai;

import java.util.UUID;

import com.sapio.user.personalization.LearnerProfile;
import com.sapio.user.personalization.PersonalizationService;
import org.springframework.stereotype.Component;

/**
 * Turns a learner's "Personalize" profile into what the AI receives ({@link StudyAi.Learner}) and the signature that
 * generated content records. Content is written for the material's owner (study materials are private per learner).
 */
@Component
public class Personalization {

    /** Signature of content written without a profile (for a general audience). */
    public static final String GENERAL = "general";

    private final PersonalizationService profiles;

    Personalization(PersonalizationService profiles) {
        this.profiles = profiles;
    }

    /** @param learner null when the learner has not personalised anything */
    public record ForLearner(StudyAi.Learner learner, String signature) {
    }

    public ForLearner forLearner(UUID userId) {
        return profiles.profile(userId)
                .map(p -> new ForLearner(toWire(p), p.signature()))
                .orElse(new ForLearner(null, GENERAL));
    }

    private static StudyAi.Learner toWire(LearnerProfile p) {
        return new StudyAi.Learner(p.getAcademicLevel(), p.getOccupation(), p.getFieldOfStudy(), p.getDepth(),
                p.getPreferences(), p.getGoal());
    }
}
