package com.example.tasktracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Query parameters for {@code GET /api/tasks/search}, bound from {@code ?q=}.
 * {@code q} is trimmed on construction, so the constraints apply to the value that is
 * actually searched for. After trimming, a blank value is empty and can only fail
 * {@code @NotBlank}, so at most one message is ever reported for {@code q}.
 */
public record TaskSearchRequest(
        @NotBlank(message = "q is required")
        @Size(max = 100, message = "q must be at most 100 characters")
        String q
) {
    public TaskSearchRequest {
        q = q == null ? null : q.strip();
    }
}
