package dev.subtrack.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import org.hibernate.type.NumericBooleanConverter;
import org.jspecify.annotations.Nullable;

@Entity
@Table(name = "catalog_service")
public class CatalogServiceEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  // sqlite-jdbc reports INTEGER PRIMARY KEY as INTEGER; Hibernate expects BIGINT for a Long.
  @Column(name = "id", columnDefinition = "INTEGER")
  private @Nullable Long id;

  @Column(name = "builtin_key", unique = true)
  private @Nullable String builtinKey;

  @Column(name = "name", nullable = false)
  private String name = "";

  @Column(name = "domain")
  private @Nullable String domain;

  @Column(name = "category", nullable = false)
  private String category = "";

  @Convert(converter = CatalogPlansConverter.class)
  @Column(name = "default_plans", nullable = false)
  private List<StoredPlan> plans = List.of();

  @Convert(converter = NumericBooleanConverter.class)
  @Column(name = "hidden", nullable = false, columnDefinition = "INTEGER")
  private boolean hidden;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.EPOCH;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.EPOCH;

  protected CatalogServiceEntity() {}

  public CatalogServiceEntity(
      String name,
      @Nullable String domain,
      String category,
      List<StoredPlan> plans,
      Instant createdAt) {
    this.name = name;
    this.domain = domain;
    this.category = category;
    this.plans = List.copyOf(plans);
    this.createdAt = createdAt;
    this.updatedAt = createdAt;
  }

  public long getId() {
    if (id == null) {
      throw new IllegalStateException("catalogue service is not saved yet");
    }
    return id;
  }

  public boolean isBuiltin() {
    return builtinKey != null;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public @Nullable String getDomain() {
    return domain;
  }

  public void setDomain(@Nullable String domain) {
    this.domain = domain;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public List<StoredPlan> getPlans() {
    return plans;
  }

  public void setPlans(List<StoredPlan> plans) {
    this.plans = List.copyOf(plans);
  }

  public boolean isHidden() {
    return hidden;
  }

  public void setHidden(boolean hidden) {
    this.hidden = hidden;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
