package dev.subtrack.clock;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TimeZonesTest {

  @ParameterizedTest
  @ValueSource(
      strings = {"Europe/Lisbon", "America/Argentina/Buenos_Aires", "Etc/UTC", "Etc/GMT+5", "UTC"})
  void ianaNamesAreKnown(String name) {
    assertThat(TimeZones.find(name)).contains(ZoneId.of(name));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"device", "+01:00", "GMT+1", "UTC+1", "Z", "europe/madrid", "Mars/Olympus"})
  void offsetsAndUnknownNamesAreNotZones(String name) {
    assertThat(TimeZones.isKnown(name)).isFalse();
    assertThat(TimeZones.find(name)).isEmpty();
  }
}
