package dev.subtrack.service;

import dev.subtrack.repository.SettingKey;
import dev.subtrack.repository.SettingRepository;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HealthService {

  private final SettingRepository settings;

  public HealthService(SettingRepository settings) {
    this.settings = settings;
  }

  @Transactional
  public @Nullable Instant lastSchedulerRun() {
    return settings.timestamp(SettingKey.LAST_SCHEDULER_RUN);
  }
}
