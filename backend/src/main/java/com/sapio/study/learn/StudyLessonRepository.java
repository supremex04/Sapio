package com.sapio.study.learn;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface StudyLessonRepository extends JpaRepository<StudyLesson, UUID> {

    Optional<StudyLesson> findByTopicId(UUID topicId);
}
