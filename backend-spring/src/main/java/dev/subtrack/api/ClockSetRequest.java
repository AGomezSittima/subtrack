package dev.subtrack.api;

import dev.subtrack.persistence.IsoText;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

record ClockSetRequest(@NotNull @ApiTimestamp @Nullable String now) {

  Instant instant() {
    return IsoText.parseTimestamp(Objects.requireNonNull(now));
  }
}
