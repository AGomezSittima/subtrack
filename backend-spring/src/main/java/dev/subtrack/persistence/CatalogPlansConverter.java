package dev.subtrack.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.List;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Converter
public class CatalogPlansConverter implements AttributeConverter<List<StoredPlan>, String> {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final TypeReference<List<StoredPlan>> PLANS = new TypeReference<>() {};

  @Override
  public @Nullable String convertToDatabaseColumn(@Nullable List<StoredPlan> value) {
    return value == null ? null : JSON.writeValueAsString(value);
  }

  @Override
  public @Nullable List<StoredPlan> convertToEntityAttribute(@Nullable String value) {
    return value == null ? null : List.copyOf(JSON.readValue(value, PLANS));
  }
}
