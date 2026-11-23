package dev.subtrack.service;

import dev.subtrack.persistence.StoredPlan;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record CatalogEntry(
    long id,
    String name,
    @Nullable String domain,
    String category,
    List<StoredPlan> plans,
    boolean builtin,
    boolean hidden,
    Instant createdAt,
    Instant updatedAt) {}
