package com.example.tasktracker.dto;

import com.example.tasktracker.model.TaskStatus;

import java.util.Map;

/**
 * Task counts for {@code GET /api/tasks/stats}. {@code byStatus} holds every {@link TaskStatus}
 * in enum order (0 when no task has it), and {@code total} is the sum of those counts.
 */
public record TaskStatsResponse(long total, Map<TaskStatus, Long> byStatus) {
}
