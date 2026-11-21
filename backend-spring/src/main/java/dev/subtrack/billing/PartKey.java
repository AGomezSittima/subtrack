package dev.subtrack.billing;

import java.util.Arrays;

public sealed interface PartKey extends Comparable<PartKey> {

  record SubscriptionKey(long id) implements PartKey {}

  record LabelKey(String label) implements PartKey {}

  record NoLabel() implements PartKey {}

  @Override
  default int compareTo(PartKey other) {
    return switch (this) {
      case SubscriptionKey(long id) when other instanceof SubscriptionKey(long otherId) ->
          Long.compare(id, otherId);
      case LabelKey(String label) when other instanceof LabelKey(String otherLabel) ->
          Arrays.compare(label.codePoints().toArray(), otherLabel.codePoints().toArray());
      default -> Integer.compare(rank(), other.rank());
    };
  }

  private int rank() {
    return switch (this) {
      case SubscriptionKey _ -> 0;
      case LabelKey _ -> 1;
      case NoLabel _ -> 2;
    };
  }
}
