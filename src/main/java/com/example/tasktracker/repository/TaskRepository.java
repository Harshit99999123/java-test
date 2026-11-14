package com.example.tasktracker.repository;

import com.example.tasktracker.model.Task;
import com.example.tasktracker.model.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findByStatus(TaskStatus status);

    List<Task> findByTitleContainingIgnoreCaseOrderByIdAsc(String keyword);

    /** One row per status that has at least one task; statuses with no tasks are absent. */
    @Query("select new com.example.tasktracker.repository.StatusCount(t.status, count(t)) "
            + "from Task t group by t.status")
    List<StatusCount> countByStatus();
}
