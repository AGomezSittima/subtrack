package dev.subtrack.api;

import dev.subtrack.persistence.StoredPlan;
import org.jspecify.annotations.Nullable;

record CatalogPlanResponse(
    String plan, @Nullable Long priceCents, int intervalCount, String intervalUnit) {

  static CatalogPlanResponse of(StoredPlan plan) {
    return new CatalogPlanResponse(
        plan.plan(), plan.priceCents(), plan.intervalCount(), plan.intervalUnit());
  }
}
