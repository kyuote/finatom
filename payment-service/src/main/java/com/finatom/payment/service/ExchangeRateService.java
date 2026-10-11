package com.finatom.payment.service;

import java.math.BigDecimal;
import java.util.Map;

public class ExchangeRateService {

    private static final Map<String, BigDecimal> RATES_TO_USD = Map.of(
        "USD", BigDecimal.ONE,
        "EUR", new BigDecimal("1.08"),
        "GBP", new BigDecimal("1.27")
    );

    public BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency) {
        if (fromCurrency.equals(toCurrency)) return amount;

        BigDecimal fromRate = RATES_TO_USD.getOrDefault(fromCurrency, BigDecimal.ONE);
        BigDecimal toRate   = RATES_TO_USD.getOrDefault(toCurrency, BigDecimal.ONE);

        return amount.multiply(fromRate).divide(toRate, 4, java.math.RoundingMode.HALF_UP);
    }
}
