package dev.subtrack.billing;

import java.math.BigInteger;

public record Fraction(BigInteger numerator, BigInteger denominator)
    implements Comparable<Fraction> {

  public static final Fraction ZERO = of(0);
  public static final Fraction HALF = of(1, 2);

  public Fraction {
    if (denominator.signum() == 0) {
      throw new ArithmeticException("zero denominator");
    }
    if (denominator.signum() < 0) {
      numerator = numerator.negate();
      denominator = denominator.negate();
    }
    BigInteger gcd = numerator.gcd(denominator);
    if (!gcd.equals(BigInteger.ONE)) {
      numerator = numerator.divide(gcd);
      denominator = denominator.divide(gcd);
    }
  }

  public static Fraction of(long value) {
    return of(value, 1);
  }

  public static Fraction of(long numerator, long denominator) {
    return new Fraction(BigInteger.valueOf(numerator), BigInteger.valueOf(denominator));
  }

  public Fraction plus(Fraction other) {
    return new Fraction(
        numerator.multiply(other.denominator).add(other.numerator.multiply(denominator)),
        denominator.multiply(other.denominator));
  }

  public Fraction minus(Fraction other) {
    return plus(new Fraction(other.numerator.negate(), other.denominator));
  }

  public Fraction times(Fraction other) {
    return new Fraction(
        numerator.multiply(other.numerator), denominator.multiply(other.denominator));
  }

  public Fraction times(long factor) {
    return times(of(factor));
  }

  public Fraction dividedBy(long divisor) {
    return times(of(1, divisor));
  }

  public BigInteger floor() {
    return numerator.subtract(numerator.mod(denominator)).divide(denominator);
  }

  @Override
  public int compareTo(Fraction other) {
    return numerator.multiply(other.denominator).compareTo(other.numerator.multiply(denominator));
  }
}
