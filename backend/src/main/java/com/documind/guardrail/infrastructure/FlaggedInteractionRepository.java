package com.documind.guardrail.infrastructure;

import com.documind.guardrail.domain.FlaggedInteraction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FlaggedInteractionRepository extends JpaRepository<FlaggedInteraction, UUID> {
}
