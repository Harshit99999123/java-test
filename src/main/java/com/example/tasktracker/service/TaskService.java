package com.example.tasktracker.service;

import com.example.tasktracker.dto.TaskRequest;
import com.example.tasktracker.dto.TaskResponse;
import com.example.tasktracker.model.Task;
import com.example.tasktracker.model.TaskStatus;
import com.example.tasktracker.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
public class TaskService {

    private final TaskRepository repository;

    public TaskService(TaskRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> findAll() {
        return repository.findAll().stream().map(TaskResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> findByStatus(TaskStatus status) {
        return repository.findByStatus(status).stream().map(TaskResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public TaskResponse findById(Long id) {
        return TaskResponse.from(getTask(id));
    }

    public TaskResponse create(TaskRequest request) {
        Task task = new Task(request.title(), request.description(), request.status(), request.dueDate());
        return TaskResponse.from(repository.save(task));
    }

    public TaskResponse update(Long id, TaskRequest request) {
        Task task = getTask(id);
        LocalDate dueDate = request.dueDate();
        // An already-overdue date may be resent unchanged; moving to a new past date may not.
        if (dueDate != null && !dueDate.equals(task.getDueDate()) && dueDate.isBefore(LocalDate.now())) {
            throw new InvalidTaskException("dueDate", TaskRequest.DUE_DATE_IN_PAST);
        }
        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setStatus(request.status());
        task.setDueDate(request.dueDate());
        return TaskResponse.from(repository.save(task));
    }

    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new TaskNotFoundException(id);
        }
        repository.deleteById(id);
    }

    private Task getTask(Long id) {
        return repository.findById(id).orElseThrow(() -> new TaskNotFoundException(id));
    }
}
