package com.ved.study.learn;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TopicProgressRepository extends JpaRepository<TopicProgress, UUID> {

    Optional<TopicProgress> findByUserIdAndTopicId(UUID userId, UUID topicId);

    List<TopicProgress> findByUserIdAndTopicIdIn(UUID userId, Collection<UUID> topicIds);

    List<TopicProgress> findByUserIdOrderByUpdatedAtDesc(UUID userId);
}
