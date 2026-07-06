package com.documind.chat.web;

import java.util.UUID;

/** documentId is optional -- omit it (or send null) to create a collection-wide session spanning every READY document in the org. */
public record CreateChatSessionRequest(UUID documentId, String title) {
}
