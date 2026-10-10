package com.finatom.account.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finatom.account.service.AccountService;

import java.util.Map;
import java.util.UUID;

import static spark.Spark.get;
import static spark.Spark.post;


public class AccountController {
    private final AccountService accountService;
    private final ObjectMapper mapper;

    public AccountController(AccountService accountService,
                             ObjectMapper mapper) {
        this.accountService = accountService;
        this.mapper = mapper;
    }

    public void registerRoutes() {
        post("/accounts", (req, res) -> {
            var body = mapper.readValue(req.body(), Map.class);
            var account = accountService.createAccount(
                (String) body.get("name"),
                (String) body.get("email")
            );
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

    }
}
