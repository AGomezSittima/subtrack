package dev.subtrack.service;

import dev.subtrack.persistence.StoredPlan;
import java.util.List;
import org.jspecify.annotations.Nullable;

public record NewCatalogEntry(
    String name, @Nullable String domain, String category, List<StoredPlan> plans) {}
