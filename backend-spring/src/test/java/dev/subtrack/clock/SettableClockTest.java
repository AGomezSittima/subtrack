package dev.subtrack.clock;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SettableClockTest {

  private static final Instant FALLBACK_NOW = Instant.parse("2026-01-15T10:00:00Z");
  private final SettableClock clock = new SettableClock(Clock.fixed(FALLBACK_NOW, ZoneOffset.UTC));

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
  void aViewInAnotherZoneSharesThePinnedInstant() {
    Clock madrid = clock.withZone(ZoneId.of("Europe/Madrid"));

    clock.pin(Instant.parse("2026-03-28T23:30:00Z"));

    assertThat(madrid.instant()).isEqualTo(clock.instant());
    assertThat(LocalDate.now(madrid)).isEqualTo(LocalDate.of(2026, 3, 29));
  }
}
