package com.finatom.account.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finatom.account.api.CreateAccountRequest;
import com.finatom.account.api.ErrorResponse;
import com.finatom.account.exception.AccountNotFoundException;
import com.finatom.account.exception.WalletNotFoundException;
import com.finatom.account.service.AccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

import static spark.Spark.exception;
import static spark.Spark.get;
import static spark.Spark.post;

public class AccountController {
    private static final Logger log = LoggerFactory.getLogger(AccountController.class);

    private final AccountService accountService;
    private final ObjectMapper mapper;

    public AccountController(AccountService accountService, ObjectMapper mapper) {
        this.accountService = accountService;
        this.mapper = mapper;
    }

    public void registerRoutes() {
        post("/accounts", (req, res) -> {
            var body = mapper.readValue(req.body(), CreateAccountRequest.class);
            var account = accountService.createAccount(body.name(), body.email());
            res.type("application/json");
            res.status(201);
            return mapper.writeValueAsString(account);
        });

        get("/accounts/:id", (req, res) -> {
            var id = UUID.fromString(req.params("id"));
            var account = accountService.getAccount(id);
            res.type("application/json");
            return mapper.writeValueAsString(account);
        });

        get("/accounts/:id/wallets", (req, res) -> {
            var id = UUID.fromString(req.params("id"));
            var wallets = accountService.getWallets(id);
            res.type("application/json");
            return mapper.writeValueAsString(wallets);
        });

        registerExceptionHandlers();
    }

    private void registerExceptionHandlers() {
        exception(AccountNotFoundException.class, (e, req, res) -> {
            res.status(404);
            res.type("application/json");
            res.body(toJson(new ErrorResponse("NOT_FOUND", e.getMessage())));
        });

        exception(WalletNotFoundException.class, (e, req, res) -> {
            res.status(404);
            res.type("application/json");
            res.body(toJson(new ErrorResponse("NOT_FOUND", e.getMessage())));
        });

        exception(IllegalArgumentException.class, (e, req, res) -> {
            res.status(400);
            res.type("application/json");
            res.body(toJson(new ErrorResponse("BAD_REQUEST", e.getMessage())));
        });

        exception(Exception.class, (e, req, res) -> {
            log.error("Unhandled exception on {} {}", req.requestMethod(), req.pathInfo(), e);
            res.status(500);
            res.type("application/json");
            res.body(toJson(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred")));
        });
    }

    private String toJson(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{\"error\":\"SERIALIZATION_ERROR\"}";
        }
    }
}
