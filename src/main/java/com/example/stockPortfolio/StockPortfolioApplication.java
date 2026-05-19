package com.example.stockPortfolio;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;
import lombok.extern.slf4j.Slf4j;

import java.net.Socket;
import java.net.URI;

@SpringBootApplication
@EnableScheduling
@EnableCaching
@Slf4j
public class StockPortfolioApplication {

	public static void main(String[] args) {
		Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
		dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));

		// REMOVED H2 FALLBACK: We want to use Neon DB permanently as requested.
		SpringApplication.run(StockPortfolioApplication.class, args);
	}

	private static void applyLocalDatabaseFallbackIfNeeded() {
		// Honour any explicitly-set profile, with one safety net: if the user
		// (or Docker compose) asked for "prod", we MUST NOT silently degrade
		// to in-memory H2. Production data integrity > startup convenience.
		String activeProfile = System.getProperty("spring.profiles.active");
		if (activeProfile == null) {
			activeProfile = System.getenv("SPRING_PROFILES_ACTIVE");
		}
		if (activeProfile != null) {
			if (activeProfile.contains("prod")) {
				log.info("[startup] Profile 'prod' active — H2 fallback DISABLED. App will fail-fast if Postgres is unreachable.");
			}
			return;
		}

		String dbUrl = System.getProperty("DB_URL");
		if (dbUrl == null || !dbUrl.startsWith("jdbc:postgresql://")) {
			return;
		}

		HostAndPort hostAndPort = parsePostgresHostAndPort(dbUrl);
		if (hostAndPort == null) {
			return;
		}

		boolean reachable = canConnect(hostAndPort.host(), hostAndPort.port(), 300);
		if (reachable) {
			return;
		}

        log.warn("====================================================================");
        log.warn("[startup] Postgres UNREACHABLE at {}:{} — activating in-memory H2 fallback.",
                hostAndPort.host(), hostAndPort.port());
        log.warn("[startup] DATA WILL NOT PERSIST across restarts. Dev convenience only.");
        log.warn("[startup] To disable, set SPRING_PROFILES_ACTIVE=prod and ensure Postgres is reachable.");
        log.warn("====================================================================");
        System.setProperty("spring.profiles.active", "h2");
    }

	private static boolean canConnect(String host, int port, int timeoutMs) {
		try (Socket socket = new Socket()) {
			socket.connect(new java.net.InetSocketAddress(host, port), timeoutMs);
			return true;
		} catch (Exception ignored) {
			return false;
		}
	}

	private static HostAndPort parsePostgresHostAndPort(String jdbcUrl) {
		try {
			String withoutPrefix = jdbcUrl.substring("jdbc:".length());
			URI uri = URI.create(withoutPrefix);
			String host = uri.getHost();
			int port = uri.getPort();
			if (host == null || port < 1) return null;
			return new HostAndPort(host, port);
		} catch (Exception ignored) {
			return null;
		}
	}

	private record HostAndPort(String host, int port) {}

    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void onReady() {
        log.info("[startup] FinPlay backend READY — Tomcat serving on port 8080. Schedulers begin in 30-60 s.");
    }
}
