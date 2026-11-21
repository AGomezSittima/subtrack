package dev.subtrack.billing;

public record Interval(int count, IntervalUnit unit) {

  static final int MONTHS_IN_A_YEAR = 12;

  public Interval {
    if (count < 1) {
      throw new BillingInputException(BillingErrorCode.INTERVAL_COUNT_NOT_POSITIVE);
    }
  }

  public boolean isYearlyOrLonger() {
    return switch (unit) {
      case YEAR -> true;
      case MONTH -> count >= MONTHS_IN_A_YEAR;
      case DAY, WEEK -> false;
    };
  }
}
