package com.finatom.account;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.javalin.Javalin;
import io.javalin.apibuilder.ApiBuilder;
import org.flywaydb.core.Flyway;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class AccountApplication {

  public static void main(String[] args) {
    Config cfg = ConfigFactory.load();

    // DataSource
    HikariConfig hikari = new HikariConfig();
    hikari.setJdbcUrl(cfg.getString("db.url"));
    hikari.setUsername(cfg.getString("db.user"));
    hikari.setPassword(cfg.getString("db.password"));
    hikari.setMaximumPoolSize(cfg.getInt("db.pool-size"));
    HikariDataSource dataSource = new HikariDataSource(hikari);

    // Flyway migrations
    Flyway.configure().dataSource(dataSource).load().migrate();

    // Redis
    JedisPool jedisPool = new JedisPool(
      new JedisPoolConfig(),
      cfg.getString("redis.host"),
      cfg.getInt("redis.port")
    );

    // HTTP server
    int port = cfg.getInt("server.port");
    Javalin.create(config -> {
      config.routes.apiBuilder(() -> {
        ApiBuilder.get("/health", ctx -> ctx.result("{\"status\":\"UP\"}").contentType("application/json"));
      });
    }).start(port);
  }
}
