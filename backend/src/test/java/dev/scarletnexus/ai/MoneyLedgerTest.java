package dev.scarletnexus.ai;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class MoneyLedgerTest {
  private MoneyLedger.Price price(String input, String reserve, String limit) {
    return new MoneyLedger.Price(
        "USD",
        "test-only",
        "model",
        "v1",
        LocalDate.of(2026, 1, 1),
        new BigDecimal(input),
        new BigDecimal("4"),
        new BigDecimal(reserve),
        new BigDecimal(limit));
  }

  @Test
  void computesDecimalCostAndRoundsUpAtEightPlaces() {
    assertEquals(
        0, price("2", "0.01", "1").calculate(1000, 500).compareTo(new BigDecimal("0.004")));
    assertEquals(
        0, price("0.000001", "0.01", "1").calculate(1, 0).compareTo(new BigDecimal("0.00000001")));
  }

  @Test
  void rejectsNegativeRatesOverPrecisionAndOversizedReservations() {
    assertThrows(IllegalArgumentException.class, () -> price("-1", "1", "1"));
    assertThrows(IllegalArgumentException.class, () -> price("0.0000000000001", "1", "1"));
    assertThrows(IllegalArgumentException.class, () -> price("2", "2", "1"));
    assertThrows(IllegalArgumentException.class, () -> price("2", "0.000000001", "1"));
  }

  @Test
  void providerCannotBeEnabledWithoutMoneyPolicy() {
    var env =
        new org.springframework.mock.env.MockEnvironment().withProperty("app.ai.enabled", "true");
    assertThrows(IllegalArgumentException.class, () -> new MoneyLedger(null, env, null));
  }
}
