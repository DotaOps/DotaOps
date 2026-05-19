package si.um.feri.dotaops.backend.config;

import java.lang.reflect.Field;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import si.um.feri.dotaops.backend.config.properties.IntegrationHttpProperties;

import static org.assertj.core.api.Assertions.assertThat;

class SteamHttpClientConfigTest {

    @Test
    void requestFactoryAppliesConfiguredTimeouts() throws Exception {
        SimpleClientHttpRequestFactory requestFactory = SteamHttpClientConfig.requestFactory(
                new IntegrationHttpProperties.Client(Duration.ofSeconds(3), Duration.ofSeconds(7)));

        assertThat(privateInt(requestFactory, "connectTimeout")).isEqualTo(3000);
        assertThat(privateInt(requestFactory, "readTimeout")).isEqualTo(7000);
    }

    @Test
    void integrationHttpPropertiesUseSafeDefaults() {
        IntegrationHttpProperties properties = new IntegrationHttpProperties(null, null);

        assertThat(properties.steam().connectTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(properties.steam().readTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.opendota().connectTimeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(properties.opendota().readTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.opendota().retry().maxAttempts()).isEqualTo(3);
        assertThat(properties.opendota().retry().backoff()).isEqualTo(Duration.ofMillis(250));
    }

    private static int privateInt(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(target);
    }
}
