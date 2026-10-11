package com.finatom.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import redis.clients.jedis.JedisPool;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class ExchangeRateService {

    private static final String FRANKFURTER_URL = "https://api.frankfurter.app/latest";
    private static final int CACHE_TTL_SECONDS = 3600;

    private final JedisPool jedisPool;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Counter cacheHits;
    private final Counter cacheMisses;

    public ExchangeRateService(JedisPool jedisPool, MeterRegistry registry) {
        this.jedisPool = jedisPool;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.cacheHits   = Counter.builder("exchange_rate.cache.hits.total")
            .description("Exchange rate Redis cache hits").register(registry);
        this.cacheMisses = Counter.builder("exchange_rate.cache.misses.total")
            .description("Exchange rate Redis cache misses (API call made)").register(registry);
    }

    public BigDecimal convert(BigDecimal amount, String from, String to) {
        if (from.equals(to)) return amount;
        BigDecimal rate = getRate(from, to);
        return amount.multiply(rate).setScale(4, RoundingMode.HALF_UP);
    }

    private BigDecimal getRate(String from, String to) {
        String cacheKey = "rate:" + from + ":" + to;

        try (var jedis = jedisPool.getResource()) {
            String cached = jedis.get(cacheKey);
            if (cached != null) {
                cacheHits.increment();
                return new BigDecimal(cached);
            }
        }

        cacheMisses.increment();
        BigDecimal rate = fetchRateFromApi(from, to);

        try (var jedis = jedisPool.getResource()) {
            jedis.setex(cacheKey, CACHE_TTL_SECONDS, rate.toPlainString());
        }

        return rate;
    }

    private BigDecimal fetchRateFromApi(String from, String to) {
        try {
            var request = HttpRequest.newBuilder()
                .uri(URI.create(FRANKFURTER_URL + "?from=" + from + "&to=" + to))
                .GET()
                .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new IllegalStateException("Frankfurter API returned " + response.statusCode());
            }

            var root = objectMapper.readTree(response.body());
            return root.get("rates").get(to).decimalValue();
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to fetch exchange rate " + from + " -> " + to, e);
        }
    }
}
