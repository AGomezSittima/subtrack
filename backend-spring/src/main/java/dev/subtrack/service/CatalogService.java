package dev.subtrack.service;

import dev.subtrack.persistence.CatalogServiceEntity;
import dev.subtrack.persistence.StoredPlan;
import dev.subtrack.repository.CatalogRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {

  private final CatalogRepository catalog;
  private final Clock clock;

  public CatalogService(CatalogRepository catalog, Clock clock) {
    this.catalog = catalog;
    this.clock = clock;
  }

  @Transactional
  public List<CatalogEntry> search(@Nullable String q, boolean includeHidden) {
    return catalog.search(q, includeHidden).stream().map(CatalogService::entry).toList();
  }

  @Transactional
  public CatalogEntry create(NewCatalogEntry entry) {
    check(repeatedPlans(entry.plans()));
    CatalogServiceEntity saved =
        catalog.save(
            new CatalogServiceEntity(
                entry.name(), entry.domain(), entry.category(), entry.plans(), clock.instant()));
    return entry(saved);
  }

  @Transactional
  public CatalogEntry update(long id, CatalogChanges changes) {
    CatalogServiceEntity service = catalog.findById(id).orElseThrow(NotFoundException::new);
    List<String> invalid =
        changes.plans() == null ? new ArrayList<>() : repeatedPlans(changes.plans());
    if (Boolean.TRUE.equals(changes.hidden()) && !service.isBuiltin()) {
      invalid.add("/hidden");
    }
    check(invalid);
    if (changes.name() != null) {
      service.setName(changes.name());
    }
    if (changes.domain() != null) {
      service.setDomain(changes.domain().orElse(null));
    }
    if (changes.category() != null) {
      service.setCategory(changes.category());
    }
    if (changes.plans() != null) {
      service.setPlans(changes.plans());
    }
    if (changes.hidden() != null) {
      service.setHidden(changes.hidden());
    }
    service.setUpdatedAt(clock.instant());
    return entry(service);
  }

  @Transactional
  public void delete(long id) {
    CatalogServiceEntity service = catalog.findById(id).orElseThrow(NotFoundException::new);
    if (service.isBuiltin()) {
      throw new BuiltinNotDeletableException();
    }
    catalog.delete(service);
  }

  private static List<String> repeatedPlans(List<StoredPlan> plans) {
    Set<List<Object>> seen = new HashSet<>();
    List<String> repeated = new ArrayList<>();
    for (int index = 0; index < plans.size(); index++) {
      StoredPlan plan = plans.get(index);
      if (!seen.add(List.of(plan.plan(), plan.intervalCount(), plan.intervalUnit()))) {
        repeated.add("/plans/" + index);
      }
    }
    return repeated;
  }

  private static void check(List<String> pointers) {
    if (!pointers.isEmpty()) {
      throw new InvalidFieldsException(pointers);
    }
  }

  private static CatalogEntry entry(CatalogServiceEntity service) {
    return new CatalogEntry(
        service.getId(),
        service.getName(),
        service.getDomain(),
        service.getCategory(),
        service.getPlans(),
        service.isBuiltin(),
        service.isHidden(),
        service.getCreatedAt(),
        service.getUpdatedAt());
  }
}
