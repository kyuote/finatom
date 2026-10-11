package com.finatom.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finatom.payment.service.PaymentService;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static spark.Spark.get;
import static spark.Spark.post;

public class PaymentController {

    private final PaymentService paymentService;
    private final ObjectMapper mapper;

    public PaymentController(PaymentService paymentService, ObjectMapper mapper) {
        this.paymentService = paymentService;
        this.mapper = mapper;
    }

    public void registerRoutes() {
        post("/payments", (req, res) -> {
            var body = mapper.readValue(req.body(), Map.class);
            var payment = paymentService.createPayment(
                UUID.fromString((String) body.get("fromWalletId")),
                UUID.fromString((String) body.get("toWalletId")),
                new BigDecimal(body.get("amount").toString()),
                (String) body.get("currency"),
                (String) body.get("idempotencyKey")
            );
            res.type("application/json");
            res.status(201);
            return mapper.writeValueAsString(payment);
        });

        get("/health", (req, res) -> {
            res.type("application/json");
            return "{\"status\":\"UP\"}";
        });
    }
}
