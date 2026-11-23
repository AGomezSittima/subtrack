package dev.subtrack.service;

import dev.subtrack.persistence.StoredPlan;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

public record CatalogChanges(
    @Nullable String name,
    @Nullable Optional<String> domain,
    @Nullable String category,
    @Nullable List<StoredPlan> plans,
    @Nullable Boolean hidden) {}
