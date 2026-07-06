package com.documind.chat.web;

import java.util.UUID;

public record ChatSessionResponse(UUID sessionId, UUID documentId, String title) {
}
