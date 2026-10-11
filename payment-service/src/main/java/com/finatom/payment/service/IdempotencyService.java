package com.finatom.payment.service;

import redis.clients.jedis.JedisPool;

import java.util.Optional;

public class IdempotencyService {

    private static final int TTL_SECONDS = 86400; // 24 hours

    private final JedisPool jedisPool;

    public IdempotencyService(JedisPool jedisPool) {
        this.jedisPool = jedisPool;
    }

    public boolean exists(String key) {
        try (var jedis = jedisPool.getResource()) {
            return jedis.exists("idempotency:" + key);
        }
    }

    public Optional<String> get(String key) {
        try (var jedis = jedisPool.getResource()) {
            return Optional.ofNullable(jedis.get("idempotency:" + key));
        }
    }

    public void store(String key, String paymentId) {
        try (var jedis = jedisPool.getResource()) {
            jedis.setex("idempotency:" + key, TTL_SECONDS, paymentId);
        }
    }
}
