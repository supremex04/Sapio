package com.sapio.user.personalization;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface LearnerProfileRepository extends JpaRepository<LearnerProfile, UUID> {
}
