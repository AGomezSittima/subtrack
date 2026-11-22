package dev.subtrack.clock;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class SettableClockTest {

  private static final Instant FALLBACK_NOW = Instant.parse("2026-01-15T10:00:00Z");
  private final SettableClock clock = new SettableClock(Clock.fixed(FALLBACK_NOW, AppZone.ZONE));

  @Test
  void followsItsFallbackUntilPinned() {
    assertThat(clock.instant()).isEqualTo(FALLBACK_NOW);
  }

  @Test
  void aPinnedInstantStaysUntilPinnedAgainOrUnpinned() {
    Instant pinned = Instant.parse("2026-11-10T15:18:00Z");

    clock.pin(pinned);
    assertThat(clock.instant()).isEqualTo(pinned);

    clock.unpin();
    assertThat(clock.instant()).isEqualTo(FALLBACK_NOW);
  }

  @Test
  void todayIsTheDateInMadrid() {
    clock.pin(Instant.parse("2026-03-28T23:30:00Z"));
    assertThat(LocalDate.now(clock)).isEqualTo(LocalDate.of(2026, 3, 29));

    clock.pin(Instant.parse("2026-07-31T21:59:00Z"));
    assertThat(LocalDate.now(clock)).isEqualTo(LocalDate.of(2026, 7, 31));
  }

  @Test
  void aViewInAnotherZoneSharesThePinnedInstant() {
    Clock utc = clock.withZone(java.time.ZoneOffset.UTC);

    clock.pin(Instant.parse("2026-03-28T23:30:00Z"));

    assertThat(utc.instant()).isEqualTo(clock.instant());
    assertThat(LocalDate.now(utc)).isEqualTo(LocalDate.of(2026, 3, 28));
  }
}
