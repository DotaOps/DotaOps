package si.um.feri.dotaops.backend.opendota.service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import si.um.feri.dotaops.backend.config.properties.IntegrationHttpProperties;
import si.um.feri.dotaops.backend.config.properties.OpenDotaProperties;
import si.um.feri.dotaops.backend.opendota.domain.OpenDotaErrorCode;
import si.um.feri.dotaops.backend.opendota.domain.OpenDotaMatchSummary;
import si.um.feri.dotaops.backend.opendota.domain.OpenDotaPlayerProfile;
import si.um.feri.dotaops.backend.opendota.domain.OpenDotaRawMatchResponse;
import si.um.feri.dotaops.backend.opendota.domain.OpenDotaRawPlayerResponse;

@Component
public class OpenDotaClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenDotaClient.class);

    private final OpenDotaProperties properties;
    private final IntegrationHttpProperties.Retry retry;
    private final RestClient restClient;
    private final RetrySleeper retrySleeper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    public OpenDotaClient(
            OpenDotaProperties properties,
            IntegrationHttpProperties integrationHttpProperties,
            @Qualifier("openDotaRestClientBuilder") RestClient.Builder restClientBuilder
    ) {
        this(properties, integrationHttpProperties, restClientBuilder, duration -> Thread.sleep(duration.toMillis()));
    }

    OpenDotaClient(
            OpenDotaProperties properties,
            IntegrationHttpProperties integrationHttpProperties,
            RestClient.Builder restClientBuilder,
            RetrySleeper retrySleeper
    ) {
        this.properties = properties;
        this.retry = integrationHttpProperties.opendota().retry();
        this.restClient = restClientBuilder.build();
        this.retrySleeper = retrySleeper;
    }

    public Optional<OpenDotaPlayerProfile> fetchPlayer(long accountId) {
        try {
            String body = restClient.get()
                    .uri(uri("/players/{accountId}", accountId))
                    .retrieve()
                    .body(String.class);
            JsonNode profile = objectMapper.readTree(body).path("profile");
            if (profile.isMissingNode() || profile.isNull()) {
                return Optional.empty();
            }

            return Optional.of(new OpenDotaPlayerProfile(
                    profile.path("account_id").asLong(accountId),
                    blankToNull(profile.path("personaname").asText(null)),
                    blankToNull(profile.path("avatarfull").asText(null)),
                    blankToNull(profile.path("profileurl").asText(null))));
        } catch (RestClientException exception) {
            LOGGER.warn("OpenDota player fetch failed for account {}.", accountId);
            return Optional.empty();
        } catch (Exception exception) {
            LOGGER.warn("OpenDota player payload could not be parsed for account {}.", accountId);
            return Optional.empty();
        }
    }

    public OpenDotaRawMatchResponse fetchMatch(long matchId) {
        if (matchId <= 0) {
            throw new OpenDotaClientException(
                    OpenDotaErrorCode.MATCH_NOT_FOUND,
                    "OpenDota match id must be positive.");
        }

        String body = fetchMatchBodyWithRetry(matchId);

        try {
            JsonNode match = objectMapper.readTree(body);
            if (!match.isObject()) {
                throw invalidResponse(matchId, "OpenDota match response must be a JSON object.");
            }

            if (match.hasNonNull("error")) {
                throw new OpenDotaClientException(
                        OpenDotaErrorCode.MATCH_NOT_FOUND,
                        "OpenDota match was not found.");
            }

            if (!match.hasNonNull("match_id")) {
                throw invalidResponse(matchId, "OpenDota match response did not contain match_id.");
            }

            return rawMatch(match);
        } catch (Exception exception) {
            if (exception instanceof OpenDotaClientException clientException) {
                throw clientException;
            }

            throw new OpenDotaClientException(
                    OpenDotaErrorCode.INVALID_PROVIDER_RESPONSE,
                    "OpenDota match response could not be parsed.",
                    exception);
        }
    }

    public List<OpenDotaMatchSummary> fetchRecentMatches(long accountId, int limit) {
        try {
            UriComponentsBuilder builder = uriBuilder("/players/{accountId}/matches", accountId);
            if (limit > 0) {
                builder.queryParam("limit", limit);
            }

            String body = restClient.get()
                    .uri(builder.build(accountId))
                    .retrieve()
                    .body(String.class);
            JsonNode matches = objectMapper.readTree(body);
            if (!matches.isArray()) {
                return List.of();
            }

            List<OpenDotaMatchSummary> result = new ArrayList<>();
            matches.forEach(match -> {
                if (match.hasNonNull("match_id")) {
                    result.add(new OpenDotaMatchSummary(
                            match.path("match_id").asLong(),
                            nullableLong(match, "start_time"),
                            nullableInt(match, "hero_id"),
                            nullableInt(match, "player_slot"),
                            nullableBoolean(match, "radiant_win")));
                }
            });
            return result;
        } catch (RestClientException exception) {
            LOGGER.warn("OpenDota recent matches fetch failed for account {}.", accountId);
            return List.of();
        } catch (Exception exception) {
            LOGGER.warn("OpenDota recent matches payload could not be parsed for account {}.", accountId);
            return List.of();
        }
    }

    private String fetchMatchBodyWithRetry(long matchId) {
        int maxAttempts = retry.maxAttempts();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return restClient.get()
                        .uri(uri("/matches/{matchId}", matchId))
                        .retrieve()
                        .body(String.class);
            } catch (RestClientResponseException exception) {
                OpenDotaClientException clientException = mapResponseException(exception);
                if (!shouldRetry(clientException.errorCode()) || attempt == maxAttempts) {
                    throw clientException;
                }

                sleepBeforeRetry(delayFor(exception));
            } catch (ResourceAccessException exception) {
                OpenDotaErrorCode errorCode = timeout(exception)
                        ? OpenDotaErrorCode.PROVIDER_TIMEOUT
                        : OpenDotaErrorCode.PROVIDER_UNAVAILABLE;
                OpenDotaClientException clientException = new OpenDotaClientException(
                        errorCode,
                        messageFor(errorCode),
                        exception);
                if (attempt == maxAttempts) {
                    throw clientException;
                }

                sleepBeforeRetry(retry.backoff());
            } catch (RestClientException exception) {
                OpenDotaClientException clientException = new OpenDotaClientException(
                        OpenDotaErrorCode.PROVIDER_UNAVAILABLE,
                        messageFor(OpenDotaErrorCode.PROVIDER_UNAVAILABLE),
                        exception);
                if (attempt == maxAttempts) {
                    throw clientException;
                }

                sleepBeforeRetry(retry.backoff());
            }
        }

        throw new OpenDotaClientException(
                OpenDotaErrorCode.PROVIDER_UNAVAILABLE,
                messageFor(OpenDotaErrorCode.PROVIDER_UNAVAILABLE));
    }

    private OpenDotaClientException mapResponseException(RestClientResponseException exception) {
        if (exception instanceof HttpClientErrorException.NotFound
                || exception.getStatusCode() == HttpStatus.NOT_FOUND) {
            return new OpenDotaClientException(
                    OpenDotaErrorCode.MATCH_NOT_FOUND,
                    "OpenDota match was not found.",
                    exception);
        }

        if (exception instanceof HttpClientErrorException.TooManyRequests
                || exception.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
            return new OpenDotaClientException(
                    OpenDotaErrorCode.RATE_LIMITED,
                    "OpenDota rate limit exceeded.",
                    exception);
        }

        if (exception instanceof HttpServerErrorException || exception.getStatusCode().is5xxServerError()) {
            return new OpenDotaClientException(
                    OpenDotaErrorCode.PROVIDER_UNAVAILABLE,
                    messageFor(OpenDotaErrorCode.PROVIDER_UNAVAILABLE),
                    exception);
        }

        return new OpenDotaClientException(
                OpenDotaErrorCode.PROVIDER_UNAVAILABLE,
                messageFor(OpenDotaErrorCode.PROVIDER_UNAVAILABLE),
                exception);
    }

    private boolean shouldRetry(OpenDotaErrorCode errorCode) {
        return errorCode == OpenDotaErrorCode.PROVIDER_UNAVAILABLE
                || errorCode == OpenDotaErrorCode.PROVIDER_TIMEOUT
                || errorCode == OpenDotaErrorCode.RATE_LIMITED;
    }

    private Duration delayFor(RestClientResponseException exception) {
        if (exception.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
            return retryAfter(exception).orElse(retry.backoff());
        }

        return retry.backoff();
    }

    private Optional<Duration> retryAfter(RestClientResponseException exception) {
        String value = exception.getResponseHeaders() == null
                ? null
                : exception.getResponseHeaders().getFirst("Retry-After");
        if (!StringUtils.hasText(value)) {
            return Optional.empty();
        }

        String normalized = value.trim();
        try {
            long seconds = Long.parseLong(normalized);
            return Optional.of(Duration.ofSeconds(Math.max(0, seconds)));
        } catch (NumberFormatException ignored) {
            try {
                Instant retryAt = ZonedDateTime
                        .parse(normalized, DateTimeFormatter.RFC_1123_DATE_TIME)
                        .toInstant();
                Duration delay = Duration.between(Instant.now(), retryAt);
                return Optional.of(delay.isNegative() ? Duration.ZERO : delay);
            } catch (DateTimeParseException ignoredDate) {
                return Optional.empty();
            }
        }
    }

    private void sleepBeforeRetry(Duration delay) {
        try {
            retrySleeper.sleep(delay);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new OpenDotaClientException(
                    OpenDotaErrorCode.PROVIDER_TIMEOUT,
                    "OpenDota request retry was interrupted.",
                    exception);
        }
    }

    private OpenDotaRawMatchResponse rawMatch(JsonNode match) throws Exception {
        List<OpenDotaRawPlayerResponse> players = new ArrayList<>();
        JsonNode rawPlayers = match.path("players");
        if (rawPlayers.isArray()) {
            rawPlayers.forEach(player -> {
                try {
                    players.add(objectMapper.treeToValue(player, OpenDotaRawPlayerResponse.class)
                            .withRawPayload(player));
                } catch (Exception exception) {
                    throw new IllegalArgumentException("OpenDota player response could not be parsed.", exception);
                }
            });
        }

        return new OpenDotaRawMatchResponse(
                nullableLong(match, "match_id"),
                nullableInt(match, "duration"),
                nullableLong(match, "start_time"),
                nullableBoolean(match, "radiant_win"),
                players,
                match);
    }

    private OpenDotaClientException invalidResponse(long matchId, String message) {
        LOGGER.warn("Invalid OpenDota match response for match {}.", matchId);
        return new OpenDotaClientException(OpenDotaErrorCode.INVALID_PROVIDER_RESPONSE, message);
    }

    private boolean timeout(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof java.net.SocketTimeoutException
                    || current instanceof java.net.http.HttpTimeoutException) {
                return true;
            }
            current = current.getCause();
        }

        return false;
    }

    private String messageFor(OpenDotaErrorCode errorCode) {
        return switch (errorCode) {
            case PROVIDER_TIMEOUT -> "OpenDota request timed out.";
            case PROVIDER_UNAVAILABLE -> "OpenDota provider is unavailable.";
            case RATE_LIMITED -> "OpenDota rate limit exceeded.";
            case MATCH_NOT_FOUND -> "OpenDota match was not found.";
            case INVALID_PROVIDER_RESPONSE -> "OpenDota response was invalid.";
        };
    }

    private URI uri(String path, long accountId) {
        return uriBuilder(path, accountId)
                .build(accountId);
    }

    private UriComponentsBuilder uriBuilder(String path, long accountId) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(properties.baseUrl())
                .path(path);
        if (StringUtils.hasText(properties.apiKey())) {
            builder.queryParam("api_key", properties.apiKey());
        }

        return builder;
    }

    private static Long nullableLong(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.path(field).asLong() : null;
    }

    private static Integer nullableInt(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.path(field).asInt() : null;
    }

    private static Boolean nullableBoolean(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.path(field).asBoolean() : null;
    }

    private static String blankToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        return value.trim();
    }

    @FunctionalInterface
    interface RetrySleeper {

        void sleep(Duration duration) throws InterruptedException;
    }
}
