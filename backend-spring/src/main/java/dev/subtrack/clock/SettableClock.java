package dev.subtrack.clock;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

public final class SettableClock extends Clock {

  private final Clock fallback;
  private final AtomicReference<@Nullable Instant> pinned;

  public SettableClock(Clock fallback) {
    this(fallback, new AtomicReference<>());
  }

  private SettableClock(Clock fallback, AtomicReference<@Nullable Instant> pinned) {
    this.fallback = fallback;
    this.pinned = pinned;
  }

  public void pin(Instant instant) {
    pinned.set(instant);
  }

  public void unpin() {
    pinned.set(null);
  }

  @Override
  public Instant instant() {
    Instant instant = pinned.get();
    return instant == null ? fallback.instant() : instant;
  }

  @Override
  public ZoneId getZone() {
    return fallback.getZone();
  }

  @Override
  public Clock withZone(ZoneId zone) {
    return new SettableClock(fallback.withZone(zone), pinned);
  }
}
