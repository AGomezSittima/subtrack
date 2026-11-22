package dev.subtrack.persistence;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

@Converter(autoApply = true)
public class UtcTimestampConverter implements AttributeConverter<Instant, String> {

  @Override
  public @Nullable String convertToDatabaseColumn(@Nullable Instant value) {
    return value == null ? null : IsoText.formatTimestamp(value);
  }

  @Override
  public @Nullable Instant convertToEntityAttribute(@Nullable String value) {
    return value == null ? null : IsoText.parseTimestamp(value);
  }
}
