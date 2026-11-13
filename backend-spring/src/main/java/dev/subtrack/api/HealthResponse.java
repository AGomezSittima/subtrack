package dev.subtrack.api;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/** Contract schema {@code Health}. */
public record HealthResponse(
    String status, String backend, String version, @Nullable Instant lastSchedulerRun) {}
