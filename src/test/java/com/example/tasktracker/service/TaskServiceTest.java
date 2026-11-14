package com.example.tasktracker.service;

import com.example.tasktracker.dto.TaskRequest;
import com.example.tasktracker.dto.TaskResponse;
import com.example.tasktracker.model.Task;
import com.example.tasktracker.model.TaskStatus;
import com.example.tasktracker.repository.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private TaskRepository repository;

    @InjectMocks
    private TaskService service;

    private static Task task(Long id, String title, TaskStatus status) {
        Task t = new Task(title, "desc", status, LocalDate.of(2030, 1, 1));
        t.setId(id);
        return t;
    }

    @Test
    void findAllReturnsMappedTasks() {
        when(repository.findAll()).thenReturn(List.of(task(1L, "A", TaskStatus.TODO), task(2L, "B", TaskStatus.DONE)));

        List<TaskResponse> result = service.findAll();

        assertThat(result).extracting(TaskResponse::title).containsExactly("A", "B");
    }

    @Test
    void findByStatusReturnsMappedTasks() {
        when(repository.findByStatus(TaskStatus.DONE)).thenReturn(List.of(task(2L, "B", TaskStatus.DONE)));

        List<TaskResponse> result = service.findByStatus(TaskStatus.DONE);

        assertThat(result).extracting(TaskResponse::id).containsExactly(2L);
    }

    @Test
    void findByIdReturnsTask() {
        when(repository.findById(1L)).thenReturn(Optional.of(task(1L, "A", TaskStatus.TODO)));

        TaskResponse result = service.findById(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.title()).isEqualTo("A");
    }

    @Test
    void findByIdThrowsWhenMissing() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(TaskNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void createSavesTaskFromRequest() {
        TaskRequest request = new TaskRequest("New", "details", TaskStatus.TODO, LocalDate.of(2030, 5, 1));
        when(repository.save(any(Task.class))).thenAnswer(inv -> {
            Task saved = inv.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        TaskResponse result = service.create(request);

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("New");
        assertThat(captor.getValue().getStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(result.id()).isEqualTo(10L);
        assertThat(result.dueDate()).isEqualTo(LocalDate.of(2030, 5, 1));
    }

    @Test
    void updateModifiesExistingTask() {
        Task existing = task(1L, "Old", TaskStatus.TODO);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        TaskResponse result = service.update(1L,
                new TaskRequest("Updated", "new desc", TaskStatus.IN_PROGRESS, null));

        assertThat(result.title()).isEqualTo("Updated");
        assertThat(result.description()).isEqualTo("new desc");
        assertThat(result.status()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(result.dueDate()).isNull();
    }

    @Test
    void updateThrowsWhenMissing() {
        when(repository.findById(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(5L, new TaskRequest("x", null, TaskStatus.TODO, null)))
                .isInstanceOf(TaskNotFoundException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void deleteRemovesExistingTask() {
        when(repository.existsById(1L)).thenReturn(true);

        service.delete(1L);

        verify(repository).deleteById(1L);
    }

    @Test
    void deleteThrowsWhenMissing() {
        when(repository.existsById(1L)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(TaskNotFoundException.class);
        verify(repository, never()).deleteById(any());
    }

    @Test
    void updateKeepsUnchangedPastDueDate() {
        LocalDate pastDue = LocalDate.now().minusDays(3);
        Task existing = new Task("Overdue", null, TaskStatus.IN_PROGRESS, pastDue);
        existing.setId(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        TaskResponse result = service.update(1L, new TaskRequest("Overdue", null, TaskStatus.DONE, pastDue));

        assertThat(result.status()).isEqualTo(TaskStatus.DONE);
        assertThat(result.dueDate()).isEqualTo(pastDue);
    }

    @Test
    void updateRejectsChangingDueDateToThePast() {
        Task existing = task(1L, "Task", TaskStatus.TODO);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(1L,
                new TaskRequest("Task", null, TaskStatus.TODO, LocalDate.now().minusDays(1))))
                .isInstanceOf(InvalidTaskException.class)
                .hasMessage("dueDate must be today or in the future")
                .extracting("field").isEqualTo("dueDate");
        verify(repository, never()).save(any());
    }
}
