package dev.subtrack.clock;

import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;

/** IANA zone names from the JDK's bundled tzdb; offsets such as {@code +01:00} are not names. */
public final class TimeZones {

  private static final Set<String> NAMES = Set.copyOf(ZoneId.getAvailableZoneIds());

  private TimeZones() {}

  public static boolean isKnown(String name) {
    return NAMES.contains(name);
  }

  public static Optional<ZoneId> find(String name) {
    return isKnown(name) ? Optional.of(ZoneId.of(name)) : Optional.empty();
  }
}
