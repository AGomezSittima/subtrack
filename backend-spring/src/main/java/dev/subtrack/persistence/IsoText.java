package dev.subtrack.persistence;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

public final class IsoText {

  private static final Pattern DATE = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");
  private static final Pattern TIMESTAMP =
      Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{3}Z");
  private static final DateTimeFormatter DATE_FORMAT =
      DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);
  private static final DateTimeFormatter TIMESTAMP_FORMAT =
      DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'")
          .withResolverStyle(ResolverStyle.STRICT);

  private IsoText() {}

  public static String formatDate(LocalDate value) {
    return DATE_FORMAT.format(value);
  }

  public static LocalDate parseDate(String text) {
    LocalDate date = DATE.matcher(text).matches() ? strictDate(text) : null;
    if (date == null || date.getYear() < 1) {
      throw new IllegalArgumentException("not a YYYY-MM-DD date: '" + text + "'");
    }
    return date;
  }

  public static String formatTimestamp(Instant value) {
    return TIMESTAMP_FORMAT.format(
        LocalDateTime.ofInstant(value.truncatedTo(ChronoUnit.MILLIS), ZoneOffset.UTC));
  }

  public static Instant parseTimestamp(String text) {
    LocalDateTime local = TIMESTAMP.matcher(text).matches() ? strictTimestamp(text) : null;
    if (local == null || local.getYear() < 1) {
      throw new IllegalArgumentException("not a UTC timestamp with milliseconds: '" + text + "'");
    }
    return local.toInstant(ZoneOffset.UTC);
  }

  private static @Nullable LocalDate strictDate(String text) {
    try {
      return LocalDate.parse(text, DATE_FORMAT);
    } catch (DateTimeParseException e) {
      return null;
    }
  }

  private static @Nullable LocalDateTime strictTimestamp(String text) {
    try {
      return LocalDateTime.parse(text, TIMESTAMP_FORMAT);
    } catch (DateTimeParseException e) {
      return null;
    }
  }
}
