package dev.subtrack.service;

import dev.subtrack.clock.HomeZone;
import dev.subtrack.persistence.SettingEntity;
import dev.subtrack.repository.SettingKey;
import dev.subtrack.repository.SettingRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class SettingsService {

  private static final List<SettingKey> USER_KEYS =
      List.of(
          SettingKey.DEFAULT_REMINDER_DAYS,
          SettingKey.TRIAL_REMINDER_DAYS,
          SettingKey.YEARLY_REMINDER_DAYS,
          SettingKey.ENDING_REMINDER_DAYS,
          SettingKey.PAYMENT_HORIZON_DAYS,
          SettingKey.LOGO_DEV_TOKEN,
          SettingKey.LOCALE,
          SettingKey.THEME,
          SettingKey.FONT_SIZE,
          SettingKey.REDUCED_MOTION,
          SettingKey.WEEK_START,
          SettingKey.LOCK_POLICY,
          SettingKey.HOME_TIME_ZONE,
          SettingKey.DISPLAY_TIME_ZONE);

  private final SettingRepository settings;
  private final Clock clock;
  private final HomeZone homeZone;

  public SettingsService(SettingRepository settings, Clock clock, HomeZone homeZone) {
    this.settings = settings;
    this.clock = clock;
    this.homeZone = homeZone;
  }

  @Transactional
  public UserSettings get() {
    return settings(rows());
  }

  @Transactional
  public UserSettings update(UserSettings changed) {
    Instant now = clock.instant();
    Map<SettingKey, @Nullable String> values = values(changed);
    Map<SettingKey, SettingEntity> rows = rows();
    rows.forEach((key, row) -> {
      String value = values.get(key);
      if (!Objects.equals(row.getValue(), value)) {
        row.setValue(value, now);
      }
    });
    UserSettings saved = settings(rows);
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            homeZone.set(saved.homeTimeZone());
          }
        });
    return saved;
  }

  private Map<SettingKey, SettingEntity> rows() {
    Map<String, SettingKey> wanted = new HashMap<>();
    USER_KEYS.forEach(key -> wanted.put(key.code(), key));
    Map<SettingKey, SettingEntity> rows = new EnumMap<>(SettingKey.class);
    settings.findByKeyIn(wanted.keySet()).forEach(row -> rows.put(wanted.get(row.getKey()), row));
    if (rows.size() != USER_KEYS.size()) {
      throw new IllegalStateException(
          "missing setting rows: "
              + Arrays.toString(
                  USER_KEYS.stream().filter(key -> !rows.containsKey(key)).toArray()));
    }
    return rows;
  }

  private static UserSettings settings(Map<SettingKey, SettingEntity> rows) {
    return new UserSettings(
        integer(rows, SettingKey.DEFAULT_REMINDER_DAYS),
        integer(rows, SettingKey.TRIAL_REMINDER_DAYS),
        integer(rows, SettingKey.YEARLY_REMINDER_DAYS),
        integer(rows, SettingKey.ENDING_REMINDER_DAYS),
        integer(rows, SettingKey.PAYMENT_HORIZON_DAYS),
        rows.get(SettingKey.LOGO_DEV_TOKEN).getValue(),
        rows.get(SettingKey.LOCALE).getValue(),
        text(rows, SettingKey.THEME),
        text(rows, SettingKey.FONT_SIZE),
        text(rows, SettingKey.REDUCED_MOTION),
        integer(rows, SettingKey.WEEK_START),
        text(rows, SettingKey.LOCK_POLICY),
        rows.get(SettingKey.HOME_TIME_ZONE).getValue(),
        text(rows, SettingKey.DISPLAY_TIME_ZONE));
  }

  private static int integer(Map<SettingKey, SettingEntity> rows, SettingKey key) {
    return Integer.parseInt(text(rows, key));
  }

  private static String text(Map<SettingKey, SettingEntity> rows, SettingKey key) {
    return Objects.requireNonNull(rows.get(key).getValue(), () -> "setting " + key + " is null");
  }

  private static Map<SettingKey, @Nullable String> values(UserSettings settings) {
    Map<SettingKey, @Nullable String> values = new EnumMap<>(SettingKey.class);
    values.put(SettingKey.DEFAULT_REMINDER_DAYS, String.valueOf(settings.defaultReminderDays()));
    values.put(SettingKey.TRIAL_REMINDER_DAYS, String.valueOf(settings.trialReminderDays()));
    values.put(SettingKey.YEARLY_REMINDER_DAYS, String.valueOf(settings.yearlyReminderDays()));
    values.put(SettingKey.ENDING_REMINDER_DAYS, String.valueOf(settings.endingReminderDays()));
    values.put(SettingKey.PAYMENT_HORIZON_DAYS, String.valueOf(settings.paymentHorizonDays()));
    values.put(SettingKey.LOGO_DEV_TOKEN, settings.logoDevToken());
    values.put(SettingKey.LOCALE, settings.locale());
    values.put(SettingKey.THEME, settings.theme());
    values.put(SettingKey.FONT_SIZE, settings.fontSize());
    values.put(SettingKey.REDUCED_MOTION, settings.reducedMotion());
    values.put(SettingKey.WEEK_START, String.valueOf(settings.weekStart()));
    values.put(SettingKey.LOCK_POLICY, settings.lockPolicy());
    values.put(SettingKey.HOME_TIME_ZONE, settings.homeTimeZone());
    values.put(SettingKey.DISPLAY_TIME_ZONE, settings.displayTimeZone());
    return values;
  }
}
