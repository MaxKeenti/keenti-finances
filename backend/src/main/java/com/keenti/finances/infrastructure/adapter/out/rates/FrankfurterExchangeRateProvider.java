package com.keenti.finances.infrastructure.adapter.out.rates;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.keenti.finances.domain.model.Currencies;
import com.keenti.finances.domain.model.ExchangeRates;
import com.keenti.finances.domain.port.out.ExchangeRateProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * European Central Bank reference rates through the Frankfurter API.
 *
 * <p>The ECB publishes once per working day, so each base is cached for a few
 * hours. A failed refresh keeps serving the last good rates only while they are
 * still inside that window; after it, the failure reaches the caller, which
 * reports the Converted Total as unavailable.
 */
@ApplicationScoped
public class FrankfurterExchangeRateProvider implements ExchangeRateProvider {

    private static final Logger LOG = Logger.getLogger(FrankfurterExchangeRateProvider.class);
    private static final Duration CACHE_FOR = Duration.ofHours(6);
    private static final Duration TIMEOUT = Duration.ofSeconds(4);

    @ConfigProperty(name = "keenti.exchange-rates.url")
    Optional<String> baseUrl;

    @Inject
    ObjectMapper objectMapper;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    private record Cached(ExchangeRates rates, Instant fetchedAt) {}

    @Override
    public ExchangeRates latest(String base) {
        if (!Currencies.isSupported(base)) {
            throw new IllegalArgumentException("Unsupported currency: " + base);
        }
        Cached cached = cache.get(base);
        if (cached != null && cached.fetchedAt().plus(CACHE_FOR).isAfter(Instant.now())) {
            return cached.rates();
        }
        ExchangeRates fresh = fetch(base);
        cache.put(base, new Cached(fresh, Instant.now()));
        return fresh;
    }

    private ExchangeRates fetch(String base) {
        String url = baseUrl.filter(value -> !value.isBlank())
            .orElseThrow(() -> new IllegalStateException("Exchange rates are not configured"));
        String symbols = Currencies.SUPPORTED.stream()
            .filter(code -> !code.equals(base))
            .collect(Collectors.joining(","));
        HttpRequest request = HttpRequest.newBuilder(
                URI.create(url.replaceAll("/+$", "") + "/latest?base=" + base + "&symbols=" + symbols))
            .timeout(TIMEOUT)
            .GET()
            .build();
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Exchange rate source answered " + response.statusCode());
            }
            JsonNode body = objectMapper.readTree(response.body());
            Map<String, BigDecimal> rates = new HashMap<>();
            body.path("rates").fields().forEachRemaining(entry ->
                rates.put(entry.getKey(), entry.getValue().decimalValue()));
            ExchangeRates parsed = new ExchangeRates(base, LocalDate.parse(body.path("date").asText()), rates);
            LOG.infof("exchange-rates.fetch base=%s date=%s count=%d", base, parsed.date(), rates.size());
            return parsed;
        } catch (IOException e) {
            throw new IllegalStateException("Exchange rate source unreachable", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Exchange rate request interrupted", e);
        }
    }
}
