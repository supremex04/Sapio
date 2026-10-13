package com.ved.user.personalization;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ved.user.personalization.LearnerProfile.AcademicLevel;
import com.ved.user.personalization.LearnerProfile.Depth;
import com.ved.user.personalization.LearnerProfile.Goal;
import com.ved.user.personalization.LearnerProfile.Occupation;
import com.ved.user.personalization.LearnerProfile.Preference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads and edits a learner's profile. Other modules use {@link #profile(UUID)} to tailor what they generate. */
@Service
public class PersonalizationService {

    private final LearnerProfileRepository profiles;
    private final Clock clock;

    PersonalizationService(LearnerProfileRepository profiles, Clock clock) {
        this.profiles = profiles;
        this.clock = clock;
    }

    /** The learner's profile, if they have personalised anything. */
    @Transactional(readOnly = true)
    public Optional<LearnerProfile> profile(UUID userId) {
        return profiles.findById(userId).filter(LearnerProfile::isConfigured);
    }

    /** Replaces the whole profile (fields left out become unset). */
    @Transactional
    public LearnerProfile update(UUID userId, AcademicLevel academicLevel, Occupation occupation, String fieldOfStudy,
                                 Depth depth, List<Preference> preferences, Goal goal) {
        LearnerProfile profile = profiles.findById(userId).orElseGet(() -> new LearnerProfile(userId));
        profile.update(academicLevel, occupation, fieldOfStudy, depth, preferences, goal, clock.instant());
        return profiles.save(profile);
    }
}
