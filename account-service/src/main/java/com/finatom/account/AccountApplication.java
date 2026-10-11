package com.finatom.account;

import com.finatom.account.config.AccountAppConfig;
import com.typesafe.config.ConfigFactory;

import static spark.Spark.get;
import static spark.Spark.port;

public class AccountApplication {

    public static void main(String[] args) throws Exception {
        var cfg    = ConfigFactory.load();
        var config = new AccountAppConfig(cfg);

        Runtime.getRuntime().addShutdownHook(new Thread(config.grpcServer::shutdown));

        port(cfg.getInt("server.port"));
        config.accessLogFilter.register();
        config.controller.registerRoutes();

        get("/health", (req, res) -> "OK");
    }
}
