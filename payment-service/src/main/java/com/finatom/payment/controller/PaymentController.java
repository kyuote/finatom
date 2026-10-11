package com.finatom.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finatom.payment.api.CreatePaymentRequest;
import com.finatom.payment.api.ErrorResponse;
import com.finatom.payment.exception.InsufficientFundsException;
import com.finatom.payment.exception.KycNotVerifiedException;
import com.finatom.payment.exception.WalletNotFoundException;
import com.finatom.payment.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static spark.Spark.exception;
import static spark.Spark.get;
import static spark.Spark.post;

public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final PaymentService paymentService;
    private final ObjectMapper mapper;

    public PaymentController(PaymentService paymentService, ObjectMapper mapper) {
        this.paymentService = paymentService;
        this.mapper = mapper;
    }

    public void registerRoutes() {
        post("/payments", (req, res) -> {
            var request = mapper.readValue(req.body(), CreatePaymentRequest.class);
            var payment = paymentService.createPayment(
                request.fromWalletId(),
                request.toWalletId(),
                request.amount(),
                request.currency(),
                request.idempotencyKey()
            );
            res.type("application/json");
            res.status(201);
            return mapper.writeValueAsString(payment);
        });

        get("/health", (req, res) -> {
            res.type("application/json");
            return "{\"status\":\"UP\"}";
        });

        registerExceptionHandlers();
    }

    private void registerExceptionHandlers() {
        exception(WalletNotFoundException.class, (e, req, res) -> {
            res.status(404);
            res.type("application/json");
            res.body(toJson(new ErrorResponse("WALLET_NOT_FOUND", e.getMessage())));
        });

        exception(InsufficientFundsException.class, (e, req, res) -> {
            res.status(422);
            res.type("application/json");
            res.body(toJson(new ErrorResponse("INSUFFICIENT_FUNDS", e.getMessage())));
        });

        exception(KycNotVerifiedException.class, (e, req, res) -> {
            res.status(403);
            res.type("application/json");
            res.body(toJson(new ErrorResponse("KYC_NOT_VERIFIED", e.getMessage())));
        });

        exception(IllegalArgumentException.class, (e, req, res) -> {
            res.status(400);
            res.type("application/json");
            res.body(toJson(new ErrorResponse("BAD_REQUEST", e.getMessage())));
        });

        exception(Exception.class, (e, req, res) -> {
            log.error("Unhandled exception: {} {}", req.requestMethod(), req.pathInfo(), e);
            res.status(500);
            res.type("application/json");
            res.body(toJson(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred")));
        });
    }

    private String toJson(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{\"error\":\"INTERNAL_ERROR\"}";
        }
    }
}
