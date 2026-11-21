package dev.subtrack.billing;

import java.util.List;
import java.util.Set;

public record PaymentPlan(
    SubscriptionStatus status, List<Payment> payments, Set<Long> updatedIds, Set<Long> deletedIds) {

  public PaymentPlan {
    payments = List.copyOf(payments);
    updatedIds = Set.copyOf(updatedIds);
    deletedIds = Set.copyOf(deletedIds);
  }

  public List<Payment> inserts() {
    return payments.stream().filter(payment -> payment.id() == null).toList();
  }
}
