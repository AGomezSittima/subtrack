package dev.subtrack.persistence;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.jspecify.annotations.Nullable;

@JsonPropertyOrder({"plan", "price_cents", "interval_count", "interval_unit"})
public record StoredPlan(
    @JsonProperty("plan") String plan,
    @JsonProperty("price_cents") @Nullable Long priceCents,
    @JsonProperty("interval_count") int intervalCount,
    @JsonProperty("interval_unit") String intervalUnit) {}
