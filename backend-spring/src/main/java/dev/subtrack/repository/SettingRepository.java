package dev.subtrack.repository;

import dev.subtrack.persistence.IsoText;
import dev.subtrack.persistence.SettingEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.data.repository.Repository;

public interface SettingRepository extends Repository<SettingEntity, Long> {

  Optional<SettingEntity> findByKey(String key);

  List<SettingEntity> findByKeyIn(Collection<String> keys);

  default @Nullable String value(SettingKey key) {
    return findByKey(key.code()).orElseThrow().getValue();
  }

  default @Nullable Instant timestamp(SettingKey key) {
    String text = value(key);
    return text == null ? null : IsoText.parseTimestamp(text);
  }
}
