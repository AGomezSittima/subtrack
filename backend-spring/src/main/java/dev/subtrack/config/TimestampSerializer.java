package dev.subtrack.config;

import dev.subtrack.persistence.IsoText;
import java.time.Instant;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ser.std.StdSerializer;

class TimestampSerializer extends StdSerializer<Instant> {

  TimestampSerializer() {
    super(Instant.class);
  }

  @Override
  public void serialize(Instant value, JsonGenerator generator, SerializationContext context) {
    generator.writeString(IsoText.formatTimestamp(value));
  }
}
