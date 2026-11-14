package com.example.tasktracker;

import com.example.tasktracker.model.Task;
import com.example.tasktracker.model.TaskStatus;
import com.example.tasktracker.repository.TaskRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full stack, no mocks: controller -> service -> JPA -> H2. Deliberately not
 * {@code @Transactional}, so the service's own transaction really commits.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TaskApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void cleanUp() {
        taskRepository.deleteAll();
    }

    @Test
    void postPersistsTaskToDatabase() throws Exception {
        LocalDate due = LocalDate.now().plusDays(7);

        MvcResult result = mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Ship v1","description":"cut release","status":"IN_PROGRESS","dueDate":"%s"}
                                """.formatted(due)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Ship v1"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.dueDate").value(due.toString()))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        long id = body.get("id").asLong();
        String location = result.getResponse().getHeader("Location");
        assertThat(location).endsWith("/api/tasks/" + id);

        Task saved = taskRepository.findById(id).orElseThrow();
        assertThat(saved.getTitle()).isEqualTo("Ship v1");
        assertThat(saved.getDescription()).isEqualTo("cut release");
        assertThat(saved.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(saved.getDueDate()).isEqualTo(due);

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.title").value("Ship v1"));
    }

    @Test
    void listFiltersByStatusAgainstDatabase() throws Exception {
        taskRepository.save(new Task("Todo one", null, TaskStatus.TODO, null));
        taskRepository.save(new Task("Done one", null, TaskStatus.DONE, null));
        taskRepository.save(new Task("Done two", null, TaskStatus.DONE, null));

        mockMvc.perform(get("/api/tasks").param("status", "DONE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].status", everyItem(is("DONE"))));

        mockMvc.perform(get("/api/tasks").param("status", "IN_PROGRESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    void putCanMarkOverdueTaskDone() throws Exception {
        LocalDate pastDue = LocalDate.now().minusDays(3);
        Task overdue = taskRepository.save(new Task("File taxes", null, TaskStatus.IN_PROGRESS, pastDue));

        mockMvc.perform(put("/api/tasks/{id}", overdue.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"File taxes","status":"DONE","dueDate":"%s"}
                                """.formatted(pastDue)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.dueDate").value(pastDue.toString()));

        Task saved = taskRepository.findById(overdue.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(TaskStatus.DONE);
        assertThat(saved.getDueDate()).isEqualTo(pastDue);
    }

    @Test
    void putRejectsMovingDueDateIntoThePast() throws Exception {
        LocalDate future = LocalDate.now().plusDays(5);
        Task task = taskRepository.save(new Task("Plan trip", null, TaskStatus.TODO, future));

        mockMvc.perform(put("/api/tasks/{id}", task.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Plan trip","status":"TODO","dueDate":"%s"}
                                """.formatted(LocalDate.now().minusDays(1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.errors.dueDate").value("dueDate must be today or in the future"));

        assertThat(taskRepository.findById(task.getId()).orElseThrow().getDueDate()).isEqualTo(future);
    }
}
