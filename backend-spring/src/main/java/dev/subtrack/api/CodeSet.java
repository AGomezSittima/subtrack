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
  INTERVAL_UNIT(Set.of("day", "week", "month", "year"));

  private final Set<String> codes;

  CodeSet(Set<String> codes) {
    this.codes = codes;
  }

  boolean contains(String code) {
    return codes.contains(code);
  }
}
