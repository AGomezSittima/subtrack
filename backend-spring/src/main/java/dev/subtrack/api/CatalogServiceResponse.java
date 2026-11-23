package dev.subtrack.api;

import dev.subtrack.service.CatalogEntry;
import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

record CatalogServiceResponse(
    long id,
    String name,
    @Nullable String domain,
    String category,
    List<CatalogPlanResponse> plans,
    boolean builtin,
    boolean hidden,
    Instant createdAt,
    Instant updatedAt) {

  static CatalogServiceResponse of(CatalogEntry entry) {
    return new CatalogServiceResponse(
        entry.id(),
        entry.name(),
        entry.domain(),
        entry.category(),
        entry.plans().stream().map(CatalogPlanResponse::of).toList(),
        entry.builtin(),
        entry.hidden(),
        entry.createdAt(),
        entry.updatedAt());
  }
}
