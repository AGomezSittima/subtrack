package dev.subtrack.repository;

import dev.subtrack.persistence.SessionEntity;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface SessionRepository extends Repository<SessionEntity, Long> {

  Optional<SessionEntity> findByTokenHash(String tokenHash);

  @Modifying
  @Query("update SessionEntity s set s.lastActivityAt = :at where s.id = :id")
  void touch(@Param("id") long id, @Param("at") Instant at);
}
