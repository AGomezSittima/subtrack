package dev.subtrack.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IsoTextTest {

  @Test
  void datesAreWrittenAsYearMonthDay() {
    assertThat(IsoText.formatDate(LocalDate.of(2026, 12, 1))).isEqualTo("2026-12-01");
    assertThat(IsoText.formatDate(LocalDate.of(999, 1, 9))).isEqualTo("0999-01-09");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"2026-1-31", "2026-02-30", "2026-13-01", "31/01/2026", "2026-01-31T00:00", ""})
  void nonCanonicalDatesAreRejected(String text) {
    assertThatThrownBy(() -> IsoText.parseDate(text))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("date");
  }

  @Test
  void datesRoundTrip() {
    assertThat(IsoText.parseDate("2024-02-29")).isEqualTo(LocalDate.of(2024, 2, 29));
  }

  @Test
  void timestampsAreWrittenInUtcWithMillisecondsAndZ() {
    assertThat(IsoText.formatTimestamp(Instant.parse("2026-11-10T15:18:00Z")))
        .isEqualTo("2026-11-10T15:18:00.000Z");
    assertThat(
            IsoText.formatTimestamp(
                OffsetDateTime.of(2026, 7, 1, 2, 0, 0, 123_999_000, ZoneOffset.ofHours(2))
                    .toInstant()))
        .isEqualTo("2026-07-01T00:00:00.123Z");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "2026-11-10T15:18:00Z",
        "2026-11-10T15:18:00.000",
        "2026-11-10T15:18:00.000+01:00",
        "2026-11-10 15:18:00.000Z",
        "2026-11-10T15:18:00.000000Z",
        "2026-11-10T25:00:00.000Z",
        "2026-11-10T23:59:60.000Z",
        "0000-01-01T00:00:00.000Z",
        "2026-02-30T00:00:00.000Z",
        "2026-11-10"
      })
  void nonCanonicalTimestampsAreRejected(String text) {
    assertThatThrownBy(() -> IsoText.parseTimestamp(text))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("timestamp");
  }

  @Test
  void timestampsRoundTrip() {
    Instant parsed = IsoText.parseTimestamp("2026-11-10T15:18:00.123Z");

    assertThat(parsed).isEqualTo(Instant.parse("2026-11-10T15:18:00.123Z"));
    assertThat(IsoText.formatTimestamp(parsed)).isEqualTo("2026-11-10T15:18:00.123Z");
  }
}
