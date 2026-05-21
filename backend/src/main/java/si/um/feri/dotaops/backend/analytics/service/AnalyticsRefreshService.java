package si.um.feri.dotaops.backend.analytics.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import si.um.feri.dotaops.backend.analytics.web.AnalyticsRefreshResponse;

@Service
public class AnalyticsRefreshService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnalyticsRefreshService.class);

    private final JdbcTemplate jdbcTemplate;

    public AnalyticsRefreshService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public AnalyticsRefreshResponse refreshNow(String reason) {
        OffsetDateTime requestedAt = OffsetDateTime.now(ZoneOffset.UTC);

        try {
            jdbcTemplate.execute("select private.refresh_dotaops_analytics()");
            OffsetDateTime completedAt = OffsetDateTime.now(ZoneOffset.UTC);

            return new AnalyticsRefreshResponse(
                    "COMPLETED",
                    reason,
                    requestedAt,
                    completedAt,
                    Duration.between(requestedAt, completedAt).toMillis(),
                    "Analytics refresh completed.");
        } catch (DataAccessException exception) {
            OffsetDateTime completedAt = OffsetDateTime.now(ZoneOffset.UTC);
            LOGGER.warn("Analytics refresh failed for reason '{}'.", safeReason(reason), exception);

            return new AnalyticsRefreshResponse(
                    "FAILED",
                    reason,
                    requestedAt,
                    completedAt,
                    Duration.between(requestedAt, completedAt).toMillis(),
                    "Analytics refresh failed.");
        }
    }

    @Async("analyticsRefreshTaskExecutor")
    public void requestRefreshAfterSuccessfulImport(String dotaMatchId) {
        requestRefreshSafely("match import ready: " + dotaMatchId);
    }

    private void requestRefreshSafely(String reason) {
        AnalyticsRefreshResponse response = refreshNow(reason);
        if (!"COMPLETED".equals(response.status())) {
            LOGGER.warn("Analytics refresh request ended with status {} for reason '{}'.", response.status(), safeReason(reason));
        }
    }

    private String safeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "unspecified";
        }

        return reason.length() > 120 ? reason.substring(0, 120) : reason;
    }
}
