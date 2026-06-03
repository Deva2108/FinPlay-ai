package com.example.stockPortfolio;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;
import lombok.extern.slf4j.Slf4j;

// @EnableJpaRepositories was removed: it is redundant with Spring Boot's
// JpaRepositoriesAutoConfiguration, which scans the same base package
// automatically. The explicit annotation had no effect in production but
// caused @WebMvcTest slices to attempt JPA bootstrap (requiring
// entityManagerFactory) which the web-layer-only context does not provide,
// crashing all @WebMvcTest tests with "No bean named 'entityManagerFactory'".
@SpringBootApplication(exclude = {
    org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration.class
})
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

		SpringApplication app = new SpringApplication(StockPortfolioApplication.class);
		// Measurement only: buffers per-step startup timings for GET /actuator/startup.
		// Observes the existing boot sequence; does not change bean order or set.
		app.setApplicationStartup(new BufferingApplicationStartup(2048));
		app.run(args);
	}

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void onReady() {
        String port = env.getProperty("local.server.port",
                      env.getProperty("server.port", "8080"));
        log.info("[startup] FinPlay backend READY — port={} liveness=/actuator/health/liveness schedulers-in=30-60s", port);
    }
}
