package com.ved.study.question;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StudyQuestionRepository extends JpaRepository<StudyQuestion, UUID> {

    List<StudyQuestion> findByLessonIdOrderByPosition(UUID lessonId);

    /** Prompts this learner already saw in assessments on a material (to avoid repeats). */
    @Query("""
            select q.prompt from StudyQuestion q
            where q.id in (select i.questionId from AssessmentItem i join Assessment a on a.id = i.assessmentId
                           where a.userId = :userId and a.materialId = :materialId)""")
    List<String> findAssessedPrompts(UUID userId, UUID materialId);
}
