package dev.subtrack.billing;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public final class BillingDates {

  private BillingDates() {}

  public static LocalDate billingDate(LocalDate anchor, Interval interval, int n) {
    if (n < 0) {
      throw new BillingInputException(BillingErrorCode.NEGATIVE_INDEX);
    }
    long steps = (long) n * interval.count();
    return switch (interval.unit()) {
      case DAY -> anchor.plusDays(steps);
      case WEEK -> anchor.plusWeeks(steps);
      case MONTH -> anchor.plusMonths(steps);
      case YEAR -> anchor.plusMonths(steps * Interval.MONTHS_IN_A_YEAR);
    };
  }

  public static List<LocalDate> billingDates(LocalDate anchor, Interval interval, int count) {
    return IntStream.rangeClosed(1, count).mapToObj(n -> billingDate(anchor, interval, n)).toList();
  }

  public static List<LocalDate> billingDatesBetween(
      LocalDate anchor, Interval interval, LocalDate start, LocalDate end) {
    List<LocalDate> dates = new ArrayList<>();
    for (int n = 0; ; n++) {
      LocalDate due = billingDate(anchor, interval, n);
      if (due.isAfter(end)) {
        return dates;
      }
      if (!due.isBefore(start)) {
        dates.add(due);
      }
    }
  }
}
