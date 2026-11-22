package dev.subtrack.security;

import dev.subtrack.persistence.SessionEntity;
import dev.subtrack.repository.SessionRepository;
import dev.subtrack.repository.SettingKey;
import dev.subtrack.repository.SettingRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LockEnforcer {

  private final SettingRepository settings;
  private final SessionRepository sessions;
  private final Clock clock;

  public LockEnforcer(SettingRepository settings, SessionRepository sessions, Clock clock) {
    this.settings = settings;
    this.sessions = sessions;
    this.clock = clock;
  }

  @Transactional
  public boolean allows(@Nullable String sessionToken, boolean background) {
    if (settings.value(SettingKey.PASSCODE_HASH) == null) {
      return true;
    }
    if (sessionToken == null || sessionToken.isEmpty()) {
      return false;
    }
    Optional<SessionEntity> found = sessions.findByTokenHash(SessionTokens.hash(sessionToken));
    if (found.isEmpty()) {
      return false;
    }
    SessionEntity session = found.get();
    Instant now = clock.instant();
    Duration limit = idleLimit(settings.value(SettingKey.LOCK_POLICY));
    if (limit != null && Duration.between(session.getLastActivityAt(), now).compareTo(limit) > 0) {
      return false;
    }
    if (!background) {
      sessions.touch(session.getId(), now);
    }
    return true;
  }

  private static @Nullable Duration idleLimit(@Nullable String policy) {
    return switch (String.valueOf(policy)) {
      case "on_open", "manual" -> null;
      case "idle_5" -> Duration.ofMinutes(5);
      case "idle_15" -> Duration.ofMinutes(15);
      case "idle_30" -> Duration.ofMinutes(30);
      case "idle_60" -> Duration.ofMinutes(60);
      default -> throw new IllegalStateException("unknown lock policy " + policy);
    };
  }
}
