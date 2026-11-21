package dev.subtrack.billing;

import java.util.Locale;

public class BillingInputException extends IllegalArgumentException {

  private static final long serialVersionUID = 1L;

  private final BillingErrorCode code;

  public BillingInputException(BillingErrorCode code) {
    super(code.name().toLowerCase(Locale.ROOT));
    this.code = code;
  }

  public BillingErrorCode code() {
    return code;
  }
}
