package dev.subtrack.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import dev.subtrack.service.NewCatalogEntry;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

// Not a record: Jackson passes absent record components through the null handler, so they look like
// null.
final class CatalogServiceCreateRequest {

  static final String DOMAIN = "^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$";

  @JsonProperty
  @JsonSetter(nulls = Nulls.FAIL)
  @NotNull @MinCodePoints(1)
  @MaxCodePoints(200)
  @TrimmedText
  private @Nullable String name;

  @JsonProperty
  @Pattern(regexp = DOMAIN) @MaxCodePoints(253)
  private @Nullable String domain;

  @JsonProperty
  @JsonSetter(nulls = Nulls.FAIL)
  @NotNull @OneOf(CodeSet.CATEGORY)
  private @Nullable String category;

  @JsonProperty
  @JsonSetter(nulls = Nulls.FAIL, contentNulls = Nulls.FAIL)
  @NotNull @Size(max = 50) private @Nullable List<@Valid CatalogPlanRequest> plans;

  NewCatalogEntry entry() {
    return new NewCatalogEntry(
        Objects.requireNonNull(name),
        domain,
        Objects.requireNonNull(category),
        Objects.requireNonNull(plans).stream().map(CatalogPlanRequest::stored).toList());
  }
}
