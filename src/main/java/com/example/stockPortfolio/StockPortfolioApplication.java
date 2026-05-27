package com.example.stockPortfolio;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;
import lombok.extern.slf4j.Slf4j;

@SpringBootApplication
@EnableScheduling
@EnableCaching
@Slf4j
public class StockPortfolioApplication {

    // `local.server.port` is populated by Spring Boot after the embedded Tomcat
    // connector binds — it is the actual port (e.g. Render's dynamic PORT value),
    // not the configured server.port expression. Available from ApplicationReadyEvent.
    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.core.env.Environment env;

    public static void main(String[] args) {
		Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
		dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));

		SpringApplication.run(StockPortfolioApplication.class, args);
	}

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void onReady() {
        String port = env.getProperty("local.server.port",
                      env.getProperty("server.port", "8080"));
        log.info("[startup] FinPlay backend READY — port={} liveness=/actuator/health/liveness schedulers-in=30-60s", port);
    }
}
