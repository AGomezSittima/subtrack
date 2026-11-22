package dev.subtrack.api;

import java.net.URI;

public enum ProblemType {
  MALFORMED_REQUEST("malformed-request", 400, "Malformed request"),
  UNAUTHORIZED("unauthorized", 401, "Unauthorized"),
  INVALID_PASSCODE("invalid-passcode", 401, "Invalid passcode"),
  NOT_FOUND("not-found", 404, "Not found"),
  METHOD_NOT_ALLOWED("method-not-allowed", 405, "Method not allowed"),
  PASSCODE_NOT_SET("passcode-not-set", 409, "Passcode not set"),
  INVALID_STATUS_TRANSITION("invalid-status-transition", 409, "Invalid status transition"),
  PAYMENT_NOT_EXPECTED("payment-not-expected", 409, "Payment not expected"),
  PAYMENT_NOT_PAID("payment-not-paid", 409, "Payment not paid"),
  PRICE_CHANGE_EXISTS("price-change-exists", 409, "Price change exists"),
  UNSUPPORTED_MEDIA_TYPE("unsupported-media-type", 415, "Unsupported media type"),
  VALIDATION_ERROR("validation-error", 422, "Validation error"),
  UNSUPPORTED_FORMAT_VERSION("unsupported-format-version", 422, "Unsupported format version"),
  TOO_MANY_ATTEMPTS("too-many-attempts", 429, "Too many attempts"),
  INTERNAL_ERROR("internal-error", 500, "Internal error");

  private final String code;
  private final int status;
  private final String title;

  ProblemType(String code, int status, String title) {
    this.code = code;
    this.status = status;
    this.title = title;
  }

  public URI uri() {
    return URI.create("urn:subtrack:problem:" + code);
  }

  public int status() {
    return status;
  }

  public String title() {
    return title;
  }
}
