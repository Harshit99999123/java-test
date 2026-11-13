package com.example.tasktracker.repository;

import com.example.tasktracker.model.Task;
import com.example.tasktracker.model.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByStatus(TaskStatus status);
}
