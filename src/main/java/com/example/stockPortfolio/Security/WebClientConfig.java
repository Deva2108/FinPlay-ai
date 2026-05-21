package com.example.stockPortfolio.Security;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Shared {@link WebClient.Builder} for reactive external integrations
 * (Alpha Vantage today; any future async HTTP work).
 *
 * <p>Timeouts mirror the existing {@code RestTemplateConfig} so behavior is
 * consistent across the codebase: 5 s connect, 10 s read/write, 10 s overall
 * response. Reactor Netty is the underlying client because it's truly
 * non-blocking — a Tomcat worker thread is freed the moment the request is
 * dispatched, instead of being held for the duration of the external call.
 * On Render's free tier (single tiny worker) this is the difference between
 * a smooth experience and an entire request queue stalled behind one slow API.
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5_000)
                .responseTimeout(Duration.ofSeconds(10))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(10, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(10, TimeUnit.SECONDS)));

        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient));
    }
}
