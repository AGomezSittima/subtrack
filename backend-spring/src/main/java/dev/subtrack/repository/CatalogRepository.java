package dev.subtrack.repository;

import dev.subtrack.persistence.CatalogServiceEntity;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface CatalogRepository extends Repository<CatalogServiceEntity, Long> {

  @Query(
      nativeQuery = true,
      value =
          "SELECT * FROM catalog_service"
              + " WHERE (:includeHidden OR hidden IS 0)"
              + " AND (:q IS NULL OR instr(lower(name), lower(:q)) > 0)"
              + " ORDER BY name COLLATE NOCASE, id")
  List<CatalogServiceEntity> search(
      @Param("q") @Nullable String q, @Param("includeHidden") boolean includeHidden);

  Optional<CatalogServiceEntity> findById(long id);

  CatalogServiceEntity save(CatalogServiceEntity service);

  void delete(CatalogServiceEntity service);
}
