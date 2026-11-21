package dev.subtrack.billing;

public record AllocationItem(PartKey part, long priceCents, Interval interval) {}
