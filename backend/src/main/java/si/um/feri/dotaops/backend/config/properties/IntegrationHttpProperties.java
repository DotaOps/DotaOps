package si.um.feri.dotaops.backend.config.properties;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "integrations")
public record IntegrationHttpProperties(
        Client steam,
        Client opendota
) {

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(5);
    private static final int DEFAULT_RETRY_MAX_ATTEMPTS = 3;
    private static final Duration DEFAULT_RETRY_BACKOFF = Duration.ofMillis(250);

    public IntegrationHttpProperties {
        steam = normalize(steam);
        opendota = normalize(opendota);
    }

    private static Client normalize(Client client) {
        if (client == null) {
            return new Client(DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT, new Retry(
                    DEFAULT_RETRY_MAX_ATTEMPTS,
                    DEFAULT_RETRY_BACKOFF));
        }

        return new Client(
                client.connectTimeout() == null ? DEFAULT_CONNECT_TIMEOUT : client.connectTimeout(),
                client.readTimeout() == null ? DEFAULT_READ_TIMEOUT : client.readTimeout(),
                normalizeRetry(client.retry()));
    }

    private static Retry normalizeRetry(Retry retry) {
        if (retry == null) {
            return new Retry(DEFAULT_RETRY_MAX_ATTEMPTS, DEFAULT_RETRY_BACKOFF);
        }

        return new Retry(
                retry.maxAttempts() < 1 ? DEFAULT_RETRY_MAX_ATTEMPTS : retry.maxAttempts(),
                retry.backoff() == null ? DEFAULT_RETRY_BACKOFF : retry.backoff());
    }

    public record Client(
            Duration connectTimeout,
            Duration readTimeout,
            Retry retry
    ) {

        public Client(Duration connectTimeout, Duration readTimeout) {
            this(connectTimeout, readTimeout, null);
        }
    }

    public record Retry(
            int maxAttempts,
            Duration backoff
    ) {
    }
}
