# Subtrack: single entry point for build, test and run commands.
# Run `just` to list recipes.

set shell := ["bash", "-euo", "pipefail", "-c"]

# Test stacks use their own Compose project, so they never touch the real `subs-data` volume.
test_compose := "docker compose -p subtrack-test -f docker-compose.yml -f docker-compose.test.yml"

[private]
default:
    @just --list

# Start the stack with one backend (fastapi or spring) on http://127.0.0.1:8080
up profile:
    @just _check-profile {{ profile }}
    docker compose --profile {{ profile }} up -d --build --wait

# Stop the stack; data stays on the volume
down:
    docker compose --profile fastapi --profile spring down

# Regenerate types from contract/openapi.yaml
gen:
    pnpm --filter @subtrack/contract run gen

# Lint, format-check and type-check every part
lint: lint-js lint-fastapi lint-spring

[private]
lint-js:
    pnpm exec prettier --check .
    pnpm --recursive --if-present run lint

[private]
lint-fastapi:
    cd backend-fastapi && uv run --locked ruff check .
    cd backend-fastapi && uv run --locked ruff format --check .
    cd backend-fastapi && uv run --locked mypy

[private]
lint-spring:
    cd backend-spring && ./gradlew --quiet spotlessCheck compileJava compileTestJava

# Format and auto-fix every part
fmt:
    pnpm exec prettier --write .
    pnpm --recursive --if-present run lint:fix
    cd backend-fastapi && uv run ruff check --fix .
    cd backend-fastapi && uv run ruff format .
    cd backend-spring && ./gradlew --quiet spotlessApply

# All fast checks: static analysis, unit tests, API suite against both backends
test: lint test-unit (test-api "fastapi") (test-api "spring")

# Unit tests of every part
test-unit: test-unit-js test-unit-fastapi test-unit-spring

[private]
test-unit-js:
    pnpm --recursive --if-present run test

[private]
test-unit-fastapi:
    cd backend-fastapi && uv run --locked pytest

[private]
test-unit-spring:
    cd backend-spring && ./gradlew --quiet test

# Fail if generated files differ from what the generators produce (used by CI)
[private]
check-generated:
    just gen
    pnpm --filter @subtrack/frontend run build
    git diff --exit-code -- contract/generated frontend/src/routeTree.gen.ts

# Shared API suite against one backend (fastapi or spring), in test mode on a fresh database
test-api profile:
    @just _with-test-stack {{ profile }} api

# Full browser suite against both backends
test-e2e: (test-e2e-profile "fastapi") (test-e2e-profile "spring")

# Browser suite against one backend (fastapi or spring)
test-e2e-profile profile:
    @just _with-test-stack {{ profile }} ui

# Install the Playwright browser used by the UI suite
e2e-setup:
    pnpm --filter @subtrack/e2e exec playwright install chromium

[private]
_with-test-stack profile project:
    #!/usr/bin/env bash
    set -euo pipefail
    just _check-profile {{ profile }}
    compose="{{ test_compose }} --profile {{ profile }}"
    # On failure (including a stack that never gets healthy), show why before tearing it down.
    cleanup() {
        local status=$?
        if (( status != 0 )); then
            $compose ps --all
            $compose logs --no-color --timestamps
        fi
        $compose down --volumes --remove-orphans
        exit "$status"
    }
    trap cleanup EXIT
    $compose up -d --build --wait
    EXPECTED_BACKEND={{ profile }} pnpm --filter @subtrack/e2e exec playwright test --project {{ project }}

[private]
_check-profile profile:
    @[[ "{{ profile }}" == "fastapi" || "{{ profile }}" == "spring" ]] || { echo "profile must be 'fastapi' or 'spring', got '{{ profile }}'" >&2; exit 1; }
