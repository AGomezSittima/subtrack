package dev.subtrack.billing;

import java.time.LocalDate;

public record PriceChange(long priceCents, LocalDate effectiveFrom) {

  public PriceChange {
    if (priceCents < 0) {
      throw new BillingInputException(BillingErrorCode.NEGATIVE_CENTS);
    }
  }
}
