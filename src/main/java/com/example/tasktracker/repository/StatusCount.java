package com.example.tasktracker.repository;

import com.example.tasktracker.model.TaskStatus;

/** One row of {@link TaskRepository#countByStatus()}: a status and how many tasks have it. */
public record StatusCount(TaskStatus status, Long count) {
}
