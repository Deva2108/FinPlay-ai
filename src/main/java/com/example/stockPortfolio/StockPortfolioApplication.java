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

    @org.springframework.context.annotation.Bean
    public org.springframework.context.ApplicationListener<org.springframework.boot.context.event.ApplicationReadyEvent>
    seedUniverse(com.example.stockPortfolio.MarketManagement.StockUniverseRepo repo) {
        return event -> {
            log.info("[startup] FinPlay backend READY — Tomcat serving on port 8080. Schedulers begin in 30-60 s.");
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                log.info("[warmup] Async default universe population started...");

                // US Tech (USD)
                upsertStock(repo, "AAPL", "Apple Inc.", "Tech", "US", "USD", "Mega Cap", false);
                upsertStock(repo, "MSFT", "Microsoft", "Tech", "US", "USD", "Mega Cap", false);
                upsertStock(repo, "NVDA", "NVIDIA", "Tech", "US", "USD", "Mega Cap", false);
                upsertStock(repo, "TSLA", "Tesla, Inc.", "Automotive", "US", "USD", "Mega Cap", false);
                upsertStock(repo, "META", "Meta Platforms", "Tech", "US", "USD", "Mega Cap", false);
                upsertStock(repo, "AMZN", "Amazon", "Retail", "US", "USD", "Mega Cap", false);
                upsertStock(repo, "GOOGL", "Alphabet Inc.", "Tech", "US", "USD", "Mega Cap", false);

                // US Finance/Energy (USD)
                upsertStock(repo, "JPM", "JPMorgan Chase", "Finance", "US", "USD", "Mega Cap", false);
                upsertStock(repo, "XOM", "Exxon Mobil", "Energy", "US", "USD", "Mega Cap", false);

                // India Bluechips (INR)
                upsertStock(repo, "RELIANCE.NS", "Reliance Industries", "Energy", "INDIA", "INR", "Mega Cap", false);
                upsertStock(repo, "TCS.NS", "TCS", "Tech", "INDIA", "INR", "Mega Cap", false);
                upsertStock(repo, "INFY.NS", "Infosys", "Tech", "INDIA", "INR", "Large Cap", false);
                upsertStock(repo, "HDFCBANK.NS", "HDFC Bank", "Finance", "INDIA", "INR", "Mega Cap", false);
                upsertStock(repo, "ICICIBANK.NS", "ICICI Bank", "Finance", "INDIA", "INR", "Large Cap", false);
                upsertStock(repo, "SBIN.NS", "State Bank of India", "Finance", "INDIA", "INR", "Large Cap", false);
                upsertStock(repo, "TATAMOTORS.NS", "Tata Motors", "Automotive", "INDIA", "INR", "Large Cap", false);
                upsertStock(repo, "ZOMATO.NS", "Zomato", "Tech", "INDIA", "INR", "Mid Cap", false);

                // Indices
                upsertStock(repo, "^NSEI", "NIFTY 50", "Index", "INDIA", "INR", "Index", true);
                upsertStock(repo, "^BSESN", "SENSEX", "Index", "INDIA", "INR", "Index", true);
                upsertStock(repo, "SPY", "S&P 500", "Index", "US", "USD", "Index", true);
                upsertStock(repo, "QQQ", "NASDAQ 100", "Index", "US", "USD", "Index", true);

                log.info("[warmup] Async default universe population completed.");
            });
        };
    }

    private void upsertStock(com.example.stockPortfolio.MarketManagement.StockUniverseRepo repo, String sym, String name, String sector, String market, String currency, String cap, boolean isIdx) {
        if (repo.findBySymbol(sym).isPresent()) return;
        
        repo.save(com.example.stockPortfolio.MarketManagement.StockUniverse.builder()
                .symbol(sym).name(name).sector(sector).market(market).currency(currency).marketCap(cap).isIndex(isIdx).build());
    }
}
