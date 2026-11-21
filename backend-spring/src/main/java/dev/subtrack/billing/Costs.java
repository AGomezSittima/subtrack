package dev.subtrack.billing;

public final class Costs {

  private Costs() {}

  public static Fraction monthlyEquivalent(long priceCents, Interval interval) {
    if (priceCents < 0) {
      throw new BillingInputException(BillingErrorCode.NEGATIVE_CENTS);
    }
    return Fraction.of(priceCents)
        .times(monthsPerUnit(interval.unit()))
        .dividedBy(interval.count());
  }

  public static Fraction yearlyEquivalent(long priceCents, Interval interval) {
    return monthlyEquivalent(priceCents, interval).times(Interval.MONTHS_IN_A_YEAR);
  }

  public static long roundHalfUp(Fraction cents) {
    return cents.plus(Fraction.HALF).floor().longValueExact();
  }

  public static long floorCents(Fraction cents) {
    return cents.floor().longValueExact();
  }

  private static Fraction monthsPerUnit(IntervalUnit unit) {
    return switch (unit) {
      case DAY -> Fraction.of(365, Interval.MONTHS_IN_A_YEAR);
      case WEEK -> Fraction.of(52, Interval.MONTHS_IN_A_YEAR);
      case MONTH -> Fraction.of(1);
      case YEAR -> Fraction.of(1, Interval.MONTHS_IN_A_YEAR);
    };
  }
}
