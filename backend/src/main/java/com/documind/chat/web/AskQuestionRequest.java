package com.documind.chat.web;

import jakarta.validation.constraints.NotBlank;

public record AskQuestionRequest(@NotBlank(message = "Question is required") String question) {
}
