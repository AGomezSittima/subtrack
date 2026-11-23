package dev.subtrack.api;

import java.time.Instant;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record HealthResponse(
    String status,
    String backend,
    String version,
    LocalDate today,
    @Nullable Instant lastSchedulerRun) {}
