package dev.subtrack.service;

import dev.subtrack.clock.HomeZone;
import dev.subtrack.clock.SettableClock;
import dev.subtrack.repository.TemplateRepository;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnBooleanProperty("subtrack.test-mode")
public class AdminService {

  private final TemplateRepository template;
  private final SettableClock clock;
  private final HomeZone homeZone;

  public AdminService(TemplateRepository template, SettableClock clock, HomeZone homeZone) {
    this.template = template;
    this.clock = clock;
    this.homeZone = homeZone;
  }

  public void reset() {
    template.restore();
    clock.unpin();
    homeZone.reload();
  }

  public void setClock(Instant now) {
    clock.pin(now);
  }
}
