package dev.subtrack.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import dev.subtrack.service.UserSettings;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigInteger;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

// Not a record: Jackson passes absent record components through the null handler, so they look like
// null.
final class SettingsRequest {

  @JsonProperty
  @JsonSetter(nulls = Nulls.FAIL)
  @NotNull @Min(0) @Max(365) private @Nullable BigInteger defaultReminderDays;

  @JsonProperty
  @JsonSetter(nulls = Nulls.FAIL)
  @NotNull @Min(0) @Max(365) private @Nullable BigInteger trialReminderDays;

  @JsonProperty
  @JsonSetter(nulls = Nulls.FAIL)
  @NotNull @Min(0) @Max(365) private @Nullable BigInteger yearlyReminderDays;

  @JsonProperty
  @JsonSetter(nulls = Nulls.FAIL)
  @NotNull @Min(0) @Max(365) private @Nullable BigInteger endingReminderDays;

  @JsonProperty
  @JsonSetter(nulls = Nulls.FAIL)
  @NotNull @Min(1) @Max(366) private @Nullable BigInteger paymentHorizonDays;

  @JsonProperty @NotNull private @Nullable Optional<@MinCodePoints(1) @MaxCodePoints(200) @TrimmedText String>
      logoDevToken;

  @JsonProperty @NotNull private @Nullable Optional<@OneOf(CodeSet.LOCALE) String> locale;

  @JsonProperty
  @JsonSetter(nulls = Nulls.FAIL)
  @NotNull @OneOf(CodeSet.THEME)
  private @Nullable String theme;

  @JsonProperty
  @JsonSetter(nulls = Nulls.FAIL)
  @NotNull @OneOf(CodeSet.FONT_SIZE)
  private @Nullable String fontSize;

  @JsonProperty
  @JsonSetter(nulls = Nulls.FAIL)
  @NotNull @OneOf(CodeSet.REDUCED_MOTION)
  private @Nullable String reducedMotion;

  @JsonProperty
  @JsonSetter(nulls = Nulls.FAIL)
  @NotNull @Min(1) @Max(7) private @Nullable BigInteger weekStart;

  @JsonProperty
  @JsonSetter(nulls = Nulls.FAIL)
  @NotNull @OneOf(CodeSet.LOCK_POLICY)
  private @Nullable String lockPolicy;

  @JsonProperty @NotNull private @Nullable Optional<
          @Pattern(regexp = TimeZoneNameValidator.SHAPE) @MinCodePoints(1)
          @MaxCodePoints(TimeZoneNameValidator.MAX_LENGTH) @TimeZoneName String>
      homeTimeZone;

  @JsonProperty @NotNull private @Nullable Optional<
          @Pattern(regexp = TimeZoneNameValidator.SHAPE) @MinCodePoints(1)
          @MaxCodePoints(TimeZoneNameValidator.MAX_LENGTH) @TimeZoneName String>
      displayTimeZone;

  UserSettings settings() {
    return new UserSettings(
        Objects.requireNonNull(defaultReminderDays).intValueExact(),
        Objects.requireNonNull(trialReminderDays).intValueExact(),
        Objects.requireNonNull(yearlyReminderDays).intValueExact(),
        Objects.requireNonNull(endingReminderDays).intValueExact(),
        Objects.requireNonNull(paymentHorizonDays).intValueExact(),
        Objects.requireNonNull(logoDevToken).orElse(null),
        Objects.requireNonNull(locale).orElse(null),
        Objects.requireNonNull(theme),
        Objects.requireNonNull(fontSize),
        Objects.requireNonNull(reducedMotion),
        Objects.requireNonNull(weekStart).intValueExact(),
        Objects.requireNonNull(lockPolicy),
        Objects.requireNonNull(homeTimeZone).orElse(null),
        Objects.requireNonNull(displayTimeZone).orElse(null));
  }
}
