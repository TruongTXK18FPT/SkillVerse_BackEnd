# Java Spring Boot Style Constraints

- Style: Google Java style.
- Imports: standard java first, third-party second, internal last.
- Generics: Do not use raw types. Specify generic parameters.
- Architecture: Follow layers in @AGENTS.md.
- Testing: Cover custom database queries with `@DataJpaTest`.
- Verification: Run `./mvnw verify` before completion.

