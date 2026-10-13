package com.sapio.study.read;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface StudyNoteRepository extends JpaRepository<StudyNote, UUID> {

    Optional<StudyNote> findByNodeIdAndStyle(UUID nodeId, NoteStyle style);

    List<StudyNote> findByNodeIdInAndStyle(Collection<UUID> nodeIds, NoteStyle style);
}
