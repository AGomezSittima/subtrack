package dev.subtrack.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import dev.subtrack.service.CatalogChanges;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

// Not a record: Jackson passes absent record components through the null handler, so they look like
// null.
final class CatalogServiceUpdateRequest {

  @JsonProperty
  @JsonSetter(nulls = Nulls.FAIL)
  @MinCodePoints(1)
  @MaxCodePoints(200)
  @TrimmedText
  private @Nullable String name;

  @JsonProperty
  private @Nullable Optional<@Pattern(regexp = CatalogServiceCreateRequest.DOMAIN) @MaxCodePoints(253) String>
      domain;

  @JsonProperty
  @JsonSetter(nulls = Nulls.FAIL)
  @OneOf(CodeSet.CATEGORY)
  private @Nullable String category;

  @JsonProperty
  @JsonSetter(nulls = Nulls.FAIL, contentNulls = Nulls.FAIL)
  @Size(max = 50) private @Nullable List<@Valid CatalogPlanRequest> plans;

  @JsonProperty
  @JsonSetter(nulls = Nulls.FAIL)
  private @Nullable Boolean hidden;

  CatalogChanges changes() {
    return new CatalogChanges(
        name,
        domain,
        category,
        plans == null ? null : plans.stream().map(CatalogPlanRequest::stored).toList(),
        hidden);
  }
}
