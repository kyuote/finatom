package com.finatom.antifraud;

import com.finatom.antifraud.config.AntiFraudAppConfig;
import com.typesafe.config.ConfigFactory;

import static spark.Spark.get;
import static spark.Spark.port;

public class AntiFraudApplication {

    public static void main(String[] args) {
        var cfg    = ConfigFactory.load();
        var config = new AntiFraudAppConfig(cfg);

        Runtime.getRuntime().addShutdownHook(new Thread(config.consumer::stop));

        port(cfg.getInt("server.port"));
        config.accessLogFilter.register();

        get("/health", (req, res) -> "OK");
        get("/metrics", (req, res) -> {
            res.type("text/plain; version=0.0.4");
            return config.registry.scrape();
        });

        new Thread(config.consumer, "payment-event-consumer").start();
    }
}
