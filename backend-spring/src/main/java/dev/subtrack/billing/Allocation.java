package dev.subtrack.billing;

import java.util.List;

public record Allocation(long totalCents, List<AllocatedPart> parts) {

  public Allocation {
    parts = List.copyOf(parts);
  }
}
