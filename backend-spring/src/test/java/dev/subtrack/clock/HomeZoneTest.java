package dev.subtrack.clock;

import static org.assertj.core.api.Assertions.assertThat;

import dev.subtrack.support.TestDatabase;
import dev.subtrack.support.TestModeApiTest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;

class HomeZoneTest extends TestModeApiTest {

  @Autowired private HomeZone homeZone;

  @BeforeEach
  void nearMidnightUtc() {
    clock.pin(Instant.parse("2026-11-23T23:30:00Z"));
  }

  private static void store(@Nullable String name) {
    TestDatabase.execute(
        DATABASE,
        name == null
            ? "UPDATE setting SET value = NULL WHERE key = 'home_time_zone'"
            : "UPDATE setting SET value = '" + name + "' WHERE key = 'home_time_zone'");
  }

  @Test
  void withoutAHomeZoneTodayIsTheDateInUtc() {
    homeZone.reload();

    assertThat(homeZone.zone()).isEqualTo(ZoneOffset.UTC);
    assertThat(homeZone.today()).isEqualTo(LocalDate.of(2026, 11, 23));
  }

  @ParameterizedTest
  @CsvSource({"Europe/Madrid, 2026-11-24", "America/New_York, 2026-11-23"})
  void todayIsTheDateInTheStoredHomeZone(String name, LocalDate today) {
    store(name);
    homeZone.reload();

    assertThat(homeZone.today()).isEqualTo(today);
  }

  @Test
  void theZoneIsCachedUntilReloadedOrSet() {
    homeZone.reload();
    store("Europe/Madrid");

    assertThat(homeZone.today()).isEqualTo(LocalDate.of(2026, 11, 23));

    homeZone.set("Pacific/Auckland");
    assertThat(homeZone.today()).isEqualTo(LocalDate.of(2026, 11, 24));

    homeZone.set(null);
    assertThat(homeZone.today()).isEqualTo(LocalDate.of(2026, 11, 23));
  }

  @Test
  void todayFollowsTheClockAcrossADstChange() {
    homeZone.set("Europe/Madrid");

    clock.pin(Instant.parse("2026-03-28T22:59:00Z"));
    assertThat(homeZone.today()).isEqualTo(LocalDate.of(2026, 3, 28));
    clock.pin(Instant.parse("2026-03-28T23:00:00Z"));
    assertThat(homeZone.today()).isEqualTo(LocalDate.of(2026, 3, 29));
    clock.pin(Instant.parse("2026-10-24T21:59:00Z"));
    assertThat(homeZone.today()).isEqualTo(LocalDate.of(2026, 10, 24));
    clock.pin(Instant.parse("2026-10-24T22:00:00Z"));
    assertThat(homeZone.today()).isEqualTo(LocalDate.of(2026, 10, 25));
  }

  @Test
  void aStoredZoneThisBackendDoesNotKnowFallsBackToUtc() {
    store("Mars/Olympus");
    homeZone.reload();

    assertThat(homeZone.zone()).isEqualTo(ZoneOffset.UTC);
  }
}
