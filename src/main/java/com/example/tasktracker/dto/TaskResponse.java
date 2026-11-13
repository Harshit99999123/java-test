package com.example.tasktracker.dto;

import com.example.tasktracker.model.Task;
import com.example.tasktracker.model.TaskStatus;

import java.time.LocalDate;

public record TaskResponse(Long id, String title, String description, TaskStatus status, LocalDate dueDate) {

    public static TaskResponse from(Task task) {
        return new TaskResponse(task.getId(), task.getTitle(), task.getDescription(),
                task.getStatus(), task.getDueDate());
    }
}
