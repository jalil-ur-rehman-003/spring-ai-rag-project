package com.documind.chat.infrastructure;

import com.documind.chat.domain.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {
}
