package dev.subtrack.billing;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.IntStream;

public final class Allocations {

  private Allocations() {}

  public static Allocation allocate(List<AllocationItem> items) {
    Map<PartKey, Fraction> exact = new TreeMap<>();
    for (AllocationItem item : items) {
      exact.merge(
          item.part(), Costs.monthlyEquivalent(item.priceCents(), item.interval()), Fraction::plus);
    }
    List<PartKey> ordered = List.copyOf(exact.keySet());

    long totalCents =
        Costs.roundHalfUp(exact.values().stream().reduce(Fraction.ZERO, Fraction::plus));
    long[] floors = ordered.stream().mapToLong(part -> Costs.floorCents(exact.get(part))).toArray();
    long leftover = totalCents - Arrays.stream(floors).sum();
    List<Fraction> remainders =
        IntStream.range(0, ordered.size())
            .mapToObj(i -> exact.get(ordered.get(i)).minus(Fraction.of(floors[i])))
            .toList();
    IntStream.range(0, ordered.size())
        .boxed()
        .sorted(Comparator.comparing(remainders::get, Comparator.reverseOrder()))
        .limit(leftover)
        .forEach(i -> floors[i]++);

    List<AllocatedPart> parts = new ArrayList<>();
    for (int i = 0; i < ordered.size(); i++) {
      parts.add(new AllocatedPart(ordered.get(i), floors[i]));
    }
    return new Allocation(totalCents, parts);
  }
}
