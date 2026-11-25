# Java Spring Boot Code Review & Security Rules

## 1. `@Transactional`
- Use `readOnly = true` for read-only service methods.
- Avoid self-invocation (calling `@Transactional` from same bean).
- Use `rollbackFor = Exception.class` for checked exceptions.

## 2. Exception Handling
- Never catch & swallow exceptions. Log with stack trace: `logger.error("Msg: {}", err.getMessage(), err)`.
- Use `@RestControllerAdvice` for global error mapping.
- Map custom exceptions (e.g. `ResourceNotFoundException`) to HTTP statuses.

## 3. Security Checkpoints
- Avoid SQL injection: Use parameterized queries/bind variables, never string concatenation.
- Enforce authorization via Spring Security annotations (e.g., `@PreAuthorize`) on Controller/Service.
- Annotate request bodies with validation constraints (`@NotBlank`, `@Size`).

## 4. Quality & Resource Management
- Prevent resource leaks: Use `try-with-resources`.
- Keep beans stateless/thread-safe.
- Remove unused imports, comments, and debug logs.

