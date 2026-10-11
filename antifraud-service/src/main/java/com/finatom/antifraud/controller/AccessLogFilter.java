package com.finatom.antifraud.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static spark.Spark.after;
import static spark.Spark.before;

public class AccessLogFilter {
    private static final Logger log = LoggerFactory.getLogger(AccessLogFilter.class);

    public void register() {
        before((req, res) -> req.attribute("startTime", System.currentTimeMillis()));
        after((req, res) -> {
            long ms = System.currentTimeMillis() - (long) req.attribute("startTime");
            log.info("{} {} -> {} ({}ms)", req.requestMethod(), req.pathInfo(), res.status(), ms);
        });
    }
}
