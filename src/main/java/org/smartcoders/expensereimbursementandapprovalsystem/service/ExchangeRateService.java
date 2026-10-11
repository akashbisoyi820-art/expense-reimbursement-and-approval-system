package org.smartcoders.expensereimbursementandapprovalsystem.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ExchangeRateService {

    public static final String BASE_CURRENCY = "INR";

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateService.class);
    private static final Duration CACHE_TTL = Duration.ofHours(6);

    private static final Map<String, String> CURRENCIES = new LinkedHashMap<>();

    static {
        CURRENCIES.put("INR", "Indian Rupee");
        CURRENCIES.put("USD", "US Dollar");
        CURRENCIES.put("EUR", "Euro");
        CURRENCIES.put("GBP", "British Pound");
        CURRENCIES.put("AED", "UAE Dirham");
        CURRENCIES.put("SGD", "Singapore Dollar");
        CURRENCIES.put("AUD", "Australian Dollar");
        CURRENCIES.put("CAD", "Canadian Dollar");
        CURRENCIES.put("JPY", "Japanese Yen");
    }

    public record CurrencyInfo(String code, String name) {}

    // How many INR one unit of the currency is worth, and the date of that rate
    public record Rate(String currency, BigDecimal rate, LocalDate date) {}

    public record RateQuote(String currency, String baseCurrency, BigDecimal rate, LocalDate rateDate) {}

    private record CachedRate(Rate rate, Instant fetchedAt) {}

    // Shape of the Frankfurter response: {"date":"...","base":"USD","quote":"INR","rate":83.6}
    private record ApiRate(String date, String base, String quote, BigDecimal rate) {}

    private final Map<String, CachedRate> cache = new ConcurrentHashMap<>();
    private final RestClient restClient;

    public ExchangeRateService(
            @Value("${exchange.api.base-url:https://api.frankfurter.dev}") String baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(5));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    public List<CurrencyInfo> getCurrencies() {
        List<CurrencyInfo> list = new ArrayList<>();
        CURRENCIES.forEach((code, name) -> list.add(new CurrencyInfo(code, name)));
        return list;
    }

    // Returns an upper-case supported code. A missing value means the base currency.
    public String normalize(String currency) {
        if (currency == null || currency.isBlank()) {
            return BASE_CURRENCY;
        }
        String code = currency.trim().toUpperCase();
        if (!CURRENCIES.containsKey(code)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unsupported currency '" + code + "'. Supported: " + String.join(", ", CURRENCIES.keySet()));
        }
        return code;
    }

    public Rate getRate(String currency) {
        String code = normalize(currency);

        if (BASE_CURRENCY.equals(code)) {
            return new Rate(code, BigDecimal.ONE.setScale(6), LocalDate.now());
        }

        CachedRate cached = cache.get(code);
        if (cached != null && Duration.between(cached.fetchedAt(), Instant.now()).compareTo(CACHE_TTL) < 0) {
            return cached.rate();
        }

        try {
            ApiRate response = restClient.get()
                    .uri("/v2/rate/{from}/{to}", code.toLowerCase(), BASE_CURRENCY.toLowerCase())
                    .retrieve()
                    .body(ApiRate.class);

            if (response == null || response.rate() == null || response.rate().signum() <= 0) {
                throw new IllegalStateException("Empty or invalid response");
            }

            LocalDate date = response.date() != null ? LocalDate.parse(response.date()) : LocalDate.now();
            Rate rate = new Rate(code, response.rate(), date);
            cache.put(code, new CachedRate(rate, Instant.now()));
            return rate;
        } catch (RuntimeException e) {
            log.warn("Could not fetch the {} to {} rate: {}", code, BASE_CURRENCY, e.getMessage());

            // The live service is down: fall back to the last rate we knew
            if (cached != null) {
                log.warn("Using the last known {} rate from {}", code, cached.rate().date());
                return cached.rate();
            }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Exchange rate service is unavailable right now. Please try again, or submit the claim in INR.");
        }
    }

    public BigDecimal toBase(BigDecimal amount, Rate rate) {
        return amount.multiply(rate.rate()).setScale(2, RoundingMode.HALF_UP);
    }

    public RateQuote quote(String currency) {
        Rate rate = getRate(currency);
        return new RateQuote(rate.currency(), BASE_CURRENCY, rate.rate(), rate.date());
    }
}