package com.finatom.payment;

import com.finatom.payment.config.PaymentAppConfig;
import com.typesafe.config.ConfigFactory;

import static spark.Spark.get;
import static spark.Spark.port;

public class PaymentApplication {

    public static void main(String[] args) {
        var cfg    = ConfigFactory.load();
        var config = new PaymentAppConfig(cfg);

        port(cfg.getInt("server.port"));
        config.accessLogFilter.register();
        config.controller.registerRoutes();

        get("/metrics", (req, res) -> {
            res.type("text/plain; version=0.0.4");
            return config.registry.scrape();
        });
    }
}
