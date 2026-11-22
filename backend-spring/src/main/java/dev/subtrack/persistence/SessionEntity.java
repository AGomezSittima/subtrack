package dev.subtrack.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "session")
public class SessionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  // sqlite-jdbc reports INTEGER PRIMARY KEY as INTEGER; Hibernate expects BIGINT for a Long.
  @Column(name = "id", columnDefinition = "INTEGER")
  private @Nullable Long id;

  @Column(name = "token_hash", nullable = false, unique = true)
  private String tokenHash = "";

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.EPOCH;

  @Column(name = "last_activity_at", nullable = false)
  private Instant lastActivityAt = Instant.EPOCH;

  protected SessionEntity() {}

  public SessionEntity(String tokenHash, Instant createdAt, Instant lastActivityAt) {
    this.tokenHash = tokenHash;
    this.createdAt = createdAt;
    this.lastActivityAt = lastActivityAt;
  }

  public long getId() {
    if (id == null) {
      throw new IllegalStateException("session is not saved yet");
    }
    return id;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getLastActivityAt() {
    return lastActivityAt;
  }
}
