---
name: new-endpoint
description: Add a new REST endpoint to the task-tracker API end to end (DTO, service, controller, error handling, controller test, integration test), then run the full suite. Use when asked to add or implement a new endpoint under /api/tasks.
argument-hint: <description of the endpoint, e.g. "PATCH /api/tasks/{id}/status to change only the status">
---

# New endpoint

Endpoint to add: **$ARGUMENTS**

If `$ARGUMENTS` is empty or doesn't say the HTTP method, path, request shape and response, ask before going further.

Follow the conventions in `CLAUDE.md`. Work through these steps in order.

## 1. Plan before editing

Do not edit anything yet. Read the relevant code first (`TaskController`, `TaskService`, `GlobalExceptionHandler`, the `dto` package and both test classes), then use plan mode to present:

- the HTTP method, path, status codes and request/response bodies
- every file you will create or change, with a one-line reason for each
- the error cases and the status code each one returns

Wait for approval before starting step 2.

## 2. Implement: DTO, service, controller

- **DTO (only if needed):** add a Java `record` in `com.example.tasktracker.dto`. Put Bean Validation constraints and their user-facing messages on the record. Reuse `TaskRequest` / `TaskResponse` when they fit. Never return the `Task` entity from a controller; map it with `TaskResponse.from(task)` or a similar static factory.
- **Service:** add the method to `TaskService`. The class is already `@Transactional`; mark read-only queries `@Transactional(readOnly = true)`. Throw `TaskNotFoundException` for missing IDs and `InvalidTaskException` for business-rule violations.
- **Controller:** add a thin method to `TaskController` that only delegates to the service. Use `@Valid` (or `@Validated` with a group) on request bodies. Use constructor injection only; no field `@Autowired` in `src/main`. For creation, return 201 with a `Location` header, matching the existing POST.

## 3. Route errors through GlobalExceptionHandler

- Every error must come back as an RFC 7807 `ProblemDetail` from `GlobalExceptionHandler`. Don't build error bodies or use `ResponseStatusException` in the controller.
- Every 400 response must include an `errors` map (field → message; it may be empty).
- If you add a new exception type, add a handler for it in `GlobalExceptionHandler` with the right status. Never echo internal or Jackson exception messages to the client.

## 4. Tests

Add both of these; every new endpoint needs them:

- **Controller test** in `web/TaskControllerTest` (a `@WebMvcTest` slice with `TaskService` as `@MockitoBean`). Cover the success status and body, the `Location` header if one is returned, and each error case, asserting the exact `detail` and `errors.*` strings.
- **Integration test** in `TaskApiIntegrationTest` (`@SpringBootTest` + MockMvc against real H2). Cover the happy path end to end, plus the main error cases (such as 404). Keep the class **not** `@Transactional` and rely on the existing `deleteAll()` in `@AfterEach`.
- If the service method has real logic, also add cases to `service/TaskServiceTest` (Mockito unit test).

## 5. Run the full suite and report

Run the full suite with the wrapper (never the system `mvn`):

```bash
./mvnw test
```

If anything fails, fix it and run the full suite again until it passes. Then report:

- the final Maven summary line: `Tests run: N, Failures: F, Errors: E, Skipped: S`
- how many tests you added, by class
- the files you changed
