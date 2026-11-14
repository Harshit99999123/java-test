package com.example.tasktracker.web;

import com.example.tasktracker.dto.TaskRequest;
import com.example.tasktracker.dto.TaskResponse;
import com.example.tasktracker.model.TaskStatus;
import com.example.tasktracker.service.InvalidTaskException;
import com.example.tasktracker.service.TaskNotFoundException;
import com.example.tasktracker.service.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskController.class)
class TaskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TaskService service;

    @Test
    void createReturns201WithLocation() throws Exception {
        LocalDate due = LocalDate.now().plusDays(7);
        when(service.create(any(TaskRequest.class)))
                .thenReturn(new TaskResponse(1L, "Write docs", "README", TaskStatus.TODO, due));

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Write docs","description":"README","status":"TODO","dueDate":"%s"}
                                """.formatted(due)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/tasks/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Write docs"))
                .andExpect(jsonPath("$.status").value("TODO"));
    }

    @Test
    void createRejectsInvalidBody() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"  ","dueDate":"2000-01-01"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").value("title is required"))
                .andExpect(jsonPath("$.errors.status").value("status is required"))
                .andExpect(jsonPath("$.errors.dueDate").value("dueDate must be today or in the future"));

        verify(service, never()).create(any());
    }

    @Test
    void createRejectsEmptyBody() throws Exception {
        postJson("")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Request body is required"))
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.timestamp").doesNotExist());

        verify(service, never()).create(any());
    }

    @Test
    void createRejectsUnknownStatus() throws Exception {
        postJson("""
                {"title":"x","status":"BLOCKED"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors.status").value("status must be one of [TODO, IN_PROGRESS, DONE]"))
                .andExpect(jsonPath("$.timestamp").doesNotExist());

        verify(service, never()).create(any());
    }

    @Test
    void createRejectsUnparseableDate() throws Exception {
        postJson("""
                {"title":"x","status":"TODO","dueDate":"tomorrow"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors.dueDate").value("dueDate has an invalid value"))
                .andExpect(jsonPath("$.timestamp").doesNotExist());

        verify(service, never()).create(any());
    }

    @Test
    void createRejectsMalformedJson() throws Exception {
        postJson("{\"title\":")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Malformed JSON request body"))
                .andExpect(jsonPath("$.errors").isEmpty())
                .andExpect(jsonPath("$.timestamp").doesNotExist());

        verify(service, never()).create(any());
    }

    private ResultActions postJson(String body) throws Exception {
        return mockMvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void listWithoutStatusReturnsAllTasks() throws Exception {
        when(service.findAll()).thenReturn(List.of(
                new TaskResponse(1L, "A", null, TaskStatus.TODO, null),
                new TaskResponse(2L, "B", null, TaskStatus.DONE, null)));

        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        verify(service, never()).findByStatus(any());
    }

    @Test
    void listFiltersByStatus() throws Exception {
        when(service.findByStatus(TaskStatus.DONE))
                .thenReturn(List.of(new TaskResponse(2L, "B", null, TaskStatus.DONE, null)));

        mockMvc.perform(get("/api/tasks").param("status", "DONE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(2))
                .andExpect(jsonPath("$[0].status").value("DONE"));

        verify(service, never()).findAll();
    }

    @Test
    void listRejectsUnknownStatus() throws Exception {
        mockMvc.perform(get("/api/tasks").param("status", "BLOCKED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors.status").value("status must be one of [TODO, IN_PROGRESS, DONE]"));

        verify(service, never()).findByStatus(any());
        verify(service, never()).findAll();
    }

    @Test
    void getMissingTaskReturns404() throws Exception {
        when(service.findById(42L)).thenThrow(new TaskNotFoundException(42L));

        mockMvc.perform(get("/api/tasks/42"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Task 42 not found"));
    }

    @Test
    void updateAcceptsPastDueDate() throws Exception {
        LocalDate pastDue = LocalDate.now().minusDays(3);
        when(service.update(eq(7L), any(TaskRequest.class)))
                .thenReturn(new TaskResponse(7L, "Overdue", null, TaskStatus.DONE, pastDue));

        mockMvc.perform(put("/api/tasks/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Overdue","status":"DONE","dueDate":"%s"}
                                """.formatted(pastDue)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.dueDate").value(pastDue.toString()));

        verify(service).update(7L, new TaskRequest("Overdue", null, TaskStatus.DONE, pastDue));
    }

    @Test
    void updateStillValidatesOtherFields() throws Exception {
        mockMvc.perform(put("/api/tasks/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"  ","dueDate":"2000-01-01"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").value("title is required"))
                .andExpect(jsonPath("$.errors.status").value("status is required"))
                .andExpect(jsonPath("$.errors.dueDate").doesNotExist());

        verify(service, never()).update(any(), any());
    }

    @Test
    void updateMapsServiceRuleViolationTo400() throws Exception {
        when(service.update(eq(7L), any(TaskRequest.class)))
                .thenThrow(new InvalidTaskException("dueDate", "dueDate must be today or in the future"));

        mockMvc.perform(put("/api/tasks/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"x","status":"TODO","dueDate":"2000-01-01"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors.dueDate").value("dueDate must be today or in the future"));
    }
}
