package dev.subtrack.service;

import dev.subtrack.clock.HomeZone;
import dev.subtrack.repository.SettingKey;
import dev.subtrack.repository.SettingRepository;
import java.time.Instant;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HealthService {

  private final SettingRepository settings;
  private final HomeZone homeZone;

  public HealthService(SettingRepository settings, HomeZone homeZone) {
    this.settings = settings;
    this.homeZone = homeZone;
  }

  public LocalDate today() {
    return homeZone.today();
  }

  @Transactional
  public @Nullable Instant lastSchedulerRun() {
    return settings.timestamp(SettingKey.LAST_SCHEDULER_RUN);
  }
}
