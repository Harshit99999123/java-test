package com.example.tasktracker.web;

import com.example.tasktracker.dto.TaskRequest;
import com.example.tasktracker.dto.TaskResponse;
import com.example.tasktracker.dto.TaskStatsResponse;
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
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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

    @Test
    void completeReturns200WithDoneTask() throws Exception {
        LocalDate due = LocalDate.of(2030, 1, 1);
        when(service.complete(7L)).thenReturn(new TaskResponse(7L, "Ship it", "v1", TaskStatus.DONE, due));

        mockMvc.perform(patch("/api/tasks/7/complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.title").value("Ship it"))
                .andExpect(jsonPath("$.description").value("v1"))
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.dueDate").value(due.toString()));

        verify(service).complete(7L);
    }

    @Test
    void completeMissingTaskReturns404() throws Exception {
        when(service.complete(42L)).thenThrow(new TaskNotFoundException(42L));

        mockMvc.perform(patch("/api/tasks/42/complete"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Task 42 not found"));
    }

    @Test
    void deleteReturns204WithEmptyBody() throws Exception {
        mockMvc.perform(delete("/api/tasks/7"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(service).delete(7L);
    }

    @Test
    void deleteMissingTaskReturns404() throws Exception {
        doThrow(new TaskNotFoundException(42L)).when(service).delete(42L);

        mockMvc.perform(delete("/api/tasks/42"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Task 42 not found"));
    }

    @Test
    void deleteRejectsNonNumericId() throws Exception {
        mockMvc.perform(delete("/api/tasks/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors.id").value("id has an invalid value"));

        verify(service, never()).delete(any());
    }

    @Test
    void completeRejectsNonNumericId() throws Exception {
        mockMvc.perform(patch("/api/tasks/abc/complete"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors.id").value("id has an invalid value"));

        verify(service, never()).complete(any());
    }

    @Test
    void searchReturnsMatchingTasks() throws Exception {
        when(service.search("report")).thenReturn(List.of(
                new TaskResponse(3L, "Weekly report", null, TaskStatus.TODO, null)));

        mockMvc.perform(get("/api/tasks/search").param("q", "report"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(3))
                .andExpect(jsonPath("$[0].title").value("Weekly report"));

        verify(service).search("report");
    }

    @Test
    void searchRejectsMissingQuery() throws Exception {
        mockMvc.perform(get("/api/tasks/search"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors.q").value("q is required"));

        verify(service, never()).search(any());
    }

    @Test
    void searchRejectsBlankQuery() throws Exception {
        mockMvc.perform(get("/api/tasks/search").param("q", "   "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors.q").value("q is required"));

        verify(service, never()).search(any());
    }

    @Test
    void searchRejectsTooLongQuery() throws Exception {
        mockMvc.perform(get("/api/tasks/search").param("q", "x".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors.q").value("q must be at most 100 characters"));

        verify(service, never()).search(any());
    }

    @Test
    void searchAppliesLengthLimitAfterTrimming() throws Exception {
        when(service.search("report")).thenReturn(List.of());

        mockMvc.perform(get("/api/tasks/search").param("q", " ".repeat(95) + "report" + " ".repeat(10)))
                .andExpect(status().isOk());

        verify(service).search("report");
    }

    @Test
    void searchReportsOnlyRequiredForLongWhitespaceQuery() throws Exception {
        mockMvc.perform(get("/api/tasks/search").param("q", " ".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors.length()").value(1))
                .andExpect(jsonPath("$.errors.q").value("q is required"));

        verify(service, never()).search(any());
    }

    @Test
    void statsReturnsCountsByStatus() throws Exception {
        when(service.stats()).thenReturn(new TaskStatsResponse(5,
                Map.of(TaskStatus.TODO, 2L, TaskStatus.IN_PROGRESS, 0L, TaskStatus.DONE, 3L)));

        mockMvc.perform(get("/api/tasks/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.byStatus.TODO").value(2))
                .andExpect(jsonPath("$.byStatus.IN_PROGRESS").value(0))
                .andExpect(jsonPath("$.byStatus.DONE").value(3));

        verify(service).stats();
    }

    @Test
    void unknownRouteReturnsProblemDetail404() throws Exception {
        mockMvc.perform(get("/api/nope"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Resource not found"))
                .andExpect(jsonPath("$.instance").value("/api/nope"));
    }

    @Test
    void unsupportedMethodReturnsProblemDetail405WithAllow() throws Exception {
        mockMvc.perform(delete("/api/tasks"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("Allow", containsString("GET")))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.detail").value("Method DELETE is not supported for this resource"));
    }

    @Test
    void unsupportedContentTypeReturnsProblemDetail415() throws Exception {
        mockMvc.perform(post("/api/tasks").contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.detail").value("Content-Type must be application/json"));

        verify(service, never()).create(any());
    }

    @Test
    void unacceptableAcceptHeaderReturnsProblemDetail406() throws Exception {
        when(service.stats()).thenReturn(new TaskStatsResponse(0,
                Map.of(TaskStatus.TODO, 0L, TaskStatus.IN_PROGRESS, 0L, TaskStatus.DONE, 0L)));

        mockMvc.perform(get("/api/tasks/stats").accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(406))
                .andExpect(jsonPath("$.detail").value("Responses can only be produced as application/json"));
    }

    @Test
    void unexpectedExceptionReturnsProblemDetail500WithoutLeakingDetails() throws Exception {
        when(service.stats()).thenThrow(new IllegalStateException("SELECT secret FROM internal_table"));

        mockMvc.perform(get("/api/tasks/stats"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
                .andExpect(content().string(not(containsString("secret"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))));
    }
}
