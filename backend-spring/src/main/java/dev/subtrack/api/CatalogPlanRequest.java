package dev.subtrack.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import dev.subtrack.persistence.StoredPlan;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigInteger;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

// Not a record: Jackson passes absent record components through the null handler, so they look like
// null.
final class CatalogPlanRequest {

  @JsonProperty
  @JsonSetter(nulls = Nulls.FAIL)
  @NotNull @MinCodePoints(1)
  @MaxCodePoints(200)
  private @Nullable String plan;

  @JsonProperty
  @Min(0) @Max(Long.MAX_VALUE) private @Nullable BigInteger priceCents;

  @JsonProperty
  @JsonSetter(nulls = Nulls.FAIL)
  @NotNull @Min(1) @Max(366) private @Nullable BigInteger intervalCount;

  @JsonProperty
  @JsonSetter(nulls = Nulls.FAIL)
  @NotNull @OneOf(CodeSet.INTERVAL_UNIT)
  private @Nullable String intervalUnit;

  StoredPlan stored() {
    return new StoredPlan(
        Objects.requireNonNull(plan),
        priceCents == null ? null : priceCents.longValueExact(),
        Objects.requireNonNull(intervalCount).intValueExact(),
        Objects.requireNonNull(intervalUnit));
  }
}
