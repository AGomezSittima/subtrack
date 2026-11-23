package dev.subtrack.clock;

import dev.subtrack.repository.SettingKey;
import dev.subtrack.repository.SettingRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** The home time zone, cached so that reading today never hits the database. */
@Component
public class HomeZone {

  private static final Logger LOG = LoggerFactory.getLogger(HomeZone.class);

  private final Clock clock;
  private final SettingRepository settings;
  private final AtomicReference<ZoneId> zone = new AtomicReference<>(ZoneOffset.UTC);

  public HomeZone(Clock clock, SettingRepository settings) {
    this.clock = clock;
    this.settings = settings;
  }

  public ZoneId zone() {
    return zone.get();
  }

  public LocalDate today() {
    return LocalDate.ofInstant(clock.instant(), zone.get());
  }

  public void set(@Nullable String name) {
    zone.set(name == null ? ZoneOffset.UTC : TimeZones.find(name).orElseThrow());
  }

  @EventListener(ApplicationStartedEvent.class)
  @Transactional
  public void reload() {
    String name = settings.value(SettingKey.HOME_TIME_ZONE);
    if (name != null && !TimeZones.isKnown(name)) {
      LOG.warn("Home time zone {} is not in this backend's zone database; using UTC", name);
      name = null;
    }
    set(name);
  }
}
