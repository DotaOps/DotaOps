package si.um.feri.dotaops.backend.analytics.service;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AnalyticsRefreshServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final AnalyticsRefreshService service = new AnalyticsRefreshService(jdbcTemplate);

    @Test
    void refreshNowCallsPrivateRefreshFunction() {
        var response = service.refreshNow("manual test");

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.reason()).isEqualTo("manual test");
        assertThat(response.requestedAt()).isNotNull();
        assertThat(response.completedAt()).isNotNull();
        assertThat(response.durationMs()).isNotNull();
        verify(jdbcTemplate).execute("select private.refresh_dotaops_analytics()");
    }

    @Test
    void refreshNowReturnsFailedResponseWhenDatabaseCallFails() {
        doThrow(new DataAccessResourceFailureException("database unavailable"))
                .when(jdbcTemplate)
                .execute(anyString());

        var response = service.refreshNow("admin request");

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.message()).isEqualTo("Analytics refresh failed.");
        assertThat(response.reason()).isEqualTo("admin request");
        verify(jdbcTemplate).execute("select private.refresh_dotaops_analytics()");
    }

    @Test
    void importRefreshRequestUsesSameRefreshFunction() {
        service.requestRefreshAfterSuccessfulImport("7894561230");

        verify(jdbcTemplate).execute("select private.refresh_dotaops_analytics()");
    }
}
