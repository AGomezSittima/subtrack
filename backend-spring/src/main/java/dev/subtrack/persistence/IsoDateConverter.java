package dev.subtrack.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

@Converter(autoApply = true)
public class IsoDateConverter implements AttributeConverter<LocalDate, String> {

  @Override
  public @Nullable String convertToDatabaseColumn(@Nullable LocalDate value) {
    return value == null ? null : IsoText.formatDate(value);
  }

  @Override
  public @Nullable LocalDate convertToEntityAttribute(@Nullable String value) {
    return value == null ? null : IsoText.parseDate(value);
  }
}
