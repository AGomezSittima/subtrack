package dev.subtrack.api;

import java.util.Set;

public enum CodeSet {
  CATEGORY(
      Set.of(
          "music",
          "video",
          "vpn",
          "cloud",
          "ai",
          "software",
          "domains",
          "gaming",
          "news",
          "education",
          "other")),
  INTERVAL_UNIT(Set.of("day", "week", "month", "year")),
  LOCALE(Set.of("en", "es")),
  THEME(Set.of("system", "light", "dark")),
  FONT_SIZE(Set.of("sm", "md", "lg")),
  REDUCED_MOTION(Set.of("system", "always")),
  LOCK_POLICY(Set.of("on_open", "idle_5", "idle_15", "idle_30", "idle_60", "manual"));

  private final Set<String> codes;

  CodeSet(Set<String> codes) {
    this.codes = codes;
  }

  boolean contains(String code) {
    return codes.contains(code);
  }
}
