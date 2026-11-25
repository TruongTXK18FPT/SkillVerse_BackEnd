---
name: spring-debug
description: Systematically diagnoses and fixes a Java Spring Boot bug or runtime error
---

Follow this workflow to diagnose, reproduce, and resolve a bug or test failure in the Spring Boot application.

Inputs:
- errorMessage: Stack trace, error logs, or problem description
- componentName: Name of the suspected class, package, or API endpoint

Steps:
1. **Analyze Exception Stack Traces:**
   - Read the logs to identify the root cause exception type (e.g., `NullPointerException`, `DataIntegrityViolationException`, `LazyInitializationException`).
   - Trace the error to the specific line number in the codebase.

2. **Isolate and Create a Reproducer Test Case:**
   - Before editing the code, write a minimal unit test (in `src/test/java/`) that reproduces the error.
   - For Controller errors: Add a test inside the corresponding `@WebMvcTest`.
   - For DB/Repository query errors: Add a test inside the corresponding `@DataJpaTest`.
   - Run the test and confirm it fails with the reported exception.

3. **Check Database Queries & Configurations:**
   - If the error involves data persistence, check the console output or enable Hibernate SQL logs:
     `logging.level.org.hibernate.SQL: DEBUG`
   - Inspect database constraints (e.g., non-null columns, unique constraints, foreign keys) and compare them with JPA Entity annotations.

4. **Verify Transactions & Proxies:**
   - Verify if `@Transactional` is declared correctly. Check if the error is caused by self-invocation traps (calling transactional methods from within the same class).

5. **Apply the Fix & Re-Verify:**
   - Fix the bug in the code.
   - Run the reproducer test case to verify it now passes.
   - Run the full project verification command:
     - `./mvnw clean test`
