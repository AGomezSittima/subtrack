package dev.subtrack.billing;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record NotificationParams(String name, LocalDate date, @Nullable Long amountCents) {}
