package dev.subtrack.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "setting")
public class SettingEntity {

  @Id
  // sqlite-jdbc reports INTEGER PRIMARY KEY as INTEGER; Hibernate expects BIGINT for a Long.
  @Column(name = "id", columnDefinition = "INTEGER")
  private Long id = 0L;

  @Column(name = "key", nullable = false, unique = true)
  private String key = "";

  @Column(name = "value")
  private @Nullable String value;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.EPOCH;

  protected SettingEntity() {}

  public String getKey() {
    return key;
  }

  public @Nullable String getValue() {
    return value;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
