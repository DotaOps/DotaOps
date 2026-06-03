package si.um.feri.dotaops.backend.analytics.domain;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

import si.um.feri.dotaops.backend.common.error.BadRequestException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalyticsFiltersTest {

    @Test
    void limitDefaultsToTenAndCapsAtOneHundred() {
        assertThat(new AnalyticsFilters(null, null, null, null, 0).limit()).isEqualTo(10);
        assertThat(new AnalyticsFilters(null, null, null, null, 250).limit()).isEqualTo(100);
    }

    @Test
    void rejectsTimeRangeWhereFromIsAfterTo() {
        OffsetDateTime from = OffsetDateTime.parse("2026-06-01T00:00:00Z");
        OffsetDateTime to = OffsetDateTime.parse("2026-05-01T00:00:00Z");

        assertThatThrownBy(() -> new AnalyticsFilters(null, null, null, null, from, to, 10))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Analytics time range is invalid.");
    }
}
