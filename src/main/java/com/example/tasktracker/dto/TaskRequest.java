package com.example.tasktracker.dto;

import com.example.tasktracker.model.TaskStatus;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record TaskRequest(
        @NotBlank(message = "title is required")
        @Size(max = 100, message = "title must be at most 100 characters")
        String title,

        @Size(max = 1000, message = "description must be at most 1000 characters")
        String description,

        @NotNull(message = "status is required")
        TaskStatus status,

        @FutureOrPresent(message = "dueDate must be today or in the future")
        LocalDate dueDate
) {
}
