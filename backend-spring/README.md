# Subtrack backend (Spring Boot)

Java 25, Spring Boot 4.1.1, Spring Data JPA, Gradle (Kotlin DSL). It implements `contract/openapi.yaml` under `/api/v1`, with the same behavior as `backend-fastapi`.

## Layout

`src/main/java/dev/subtrack/` uses the module names shared with the FastAPI backend: `api`, `service`, `billing`, `repository`, `persistence`, `scheduler`, `security`, `clock`, `config`, `cli`. See `backend-fastapi/README.md` for what each one holds. Every package is `@NullMarked` (JSpecify).

- Dependency versions live in `gradle/libs.versions.toml`. Most come from the Spring Boot BOM; only Spotless, google-java-format and sqlite-jdbc are pinned there.
- The database is SQLite via `sqlite-jdbc` and Hibernate's community `SQLiteDialect`. `ddl-auto=validate` and Flyway disabled: only the `migrate` Compose job changes the schema.
- Compiler warnings are errors (`-Xlint:all -Werror`).

## Commands

Always use the wrapper; no global Gradle is needed. You need JDK 25.

```sh
./gradlew build            # compile, Spotless check, tests, boot jar
./gradlew spotlessApply    # format
./gradlew test             # unit tests (JUnit Jupiter)
```

## Configuration

| Variable             | Default                                  | Meaning                         |
| -------------------- | ---------------------------------------- | ------------------------------- |
| `DB_PATH`            | `subs.db` (`/data/subs.db` in the image) | SQLite file                     |
| `SUBTRACK_TEST_MODE` | `false`                                  | Enables the test-only endpoints |
