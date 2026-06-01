package si.um.feri.dotaops.backend.audit.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import si.um.feri.dotaops.backend.audit.domain.AdminAuditAction;
import si.um.feri.dotaops.backend.audit.domain.AdminAuditLogRecord;
import si.um.feri.dotaops.backend.audit.repository.AdminAuditLogFilters;
import si.um.feri.dotaops.backend.audit.repository.AdminAuditLogRepository;
import si.um.feri.dotaops.backend.common.error.BadRequestException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminAuditLogServiceTest {

    private static final UUID AUDIT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PROFILE_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID RECORD_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final OffsetDateTime FROM = OffsetDateTime.parse("2026-05-01T00:00:00Z");
    private static final OffsetDateTime TO = OffsetDateTime.parse("2026-06-01T00:00:00Z");

    private final AdminAuditLogRepository auditLogRepository = mock(AdminAuditLogRepository.class);
    private final AdminAuditLogService auditLogService = new AdminAuditLogService(auditLogRepository);

    @Test
    void listAuditLogsMapsOnlyWhitelistedChangedFieldNames() {
        AdminAuditLogFilters expectedFilters = new AdminAuditLogFilters(
                "teams",
                RECORD_ID,
                "operator",
                AdminAuditAction.UPDATE,
                FROM,
                TO);
        when(auditLogRepository.findAuditLogs(expectedFilters, 10, 20)).thenReturn(List.of(new AdminAuditLogRecord(
                AUDIT_ID,
                TO,
                PROFILE_ID,
                "Operator",
                AdminAuditAction.UPDATE,
                "public.teams",
                RECORD_ID,
                """
                {
                  "name": "Old team",
                  "region": "EU",
                  "token_hash": "must-not-leak",
                  "raw_response": {"secret": true}
                }
                """,
                """
                {
                  "name": "New team",
                  "region": "EU",
                  "token_hash": "changed-secret",
                  "raw_response": {"secret": false}
                }
                """)));
        when(auditLogRepository.countAuditLogs(expectedFilters)).thenReturn(21L);

        var response = auditLogService.listAuditLogs(
                " Teams ",
                RECORD_ID,
                " Operator ",
                "UPDATE",
                FROM,
                TO,
                2,
                10);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().summary()).isEqualTo("Team updated");
        assertThat(response.items().getFirst().changedFields()).containsExactly("name");
        assertThat(response.items().getFirst().actor().profileId()).isEqualTo(PROFILE_ID);
        assertThat(response.items().getFirst().actor().nickname()).isEqualTo("Operator");
        assertThat(response.page().page()).isEqualTo(2);
        assertThat(response.page().size()).isEqualTo(10);
        assertThat(response.page().totalElements()).isEqualTo(21);
        assertThat(response.page().totalPages()).isEqualTo(3);
    }

    @Test
    void insertAuditMapsSafePresentFieldsWithoutReturningValues() {
        AdminAuditLogFilters filters = new AdminAuditLogFilters(null, null, null, null, null, null);
        when(auditLogRepository.findAuditLogs(filters, 20, 0)).thenReturn(List.of(new AdminAuditLogRecord(
                AUDIT_ID,
                TO,
                null,
                null,
                AdminAuditAction.INSERT,
                "public.match_imports",
                RECORD_ID,
                null,
                """
                {
                  "status": "queued",
                  "attempt_count": 0,
                  "last_error": "private provider detail",
                  "error_code": null
                }
                """)));
        when(auditLogRepository.countAuditLogs(filters)).thenReturn(1L);

        var response = auditLogService.listAuditLogs(null, null, null, null, null, null, 0, 20);

        assertThat(response.items().getFirst().summary()).isEqualTo("Match import inserted");
        assertThat(response.items().getFirst().changedFields())
                .containsExactly("attempt_count", "error_code", "status");
        assertThat(response.items().getFirst().actor().profileId()).isNull();
        assertThat(response.items().getFirst().actor().nickname()).isNull();
    }

    @Test
    void listAuditLogsClampsPageSizeAndForwardsNormalizedFilters() {
        when(auditLogRepository.findAuditLogs(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(100),
                org.mockito.ArgumentMatchers.eq(0L))).thenReturn(List.of());
        when(auditLogRepository.countAuditLogs(org.mockito.ArgumentMatchers.any())).thenReturn(0L);

        auditLogService.listAuditLogs(" public.tournaments ", null, null, null, null, null, -2, 1000);

        ArgumentCaptor<AdminAuditLogFilters> captor = ArgumentCaptor.forClass(AdminAuditLogFilters.class);
        verify(auditLogRepository).findAuditLogs(captor.capture(), org.mockito.ArgumentMatchers.eq(100),
                org.mockito.ArgumentMatchers.eq(0L));
        assertThat(captor.getValue().tableName()).isEqualTo("public.tournaments");
    }

    @Test
    void invalidActionIsRejectedBeforeRepositoryCall() {
        assertThatThrownBy(() -> auditLogService.listAuditLogs(
                null, null, null, "export", null, null, 0, 20))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Unsupported audit action.");

        verifyNoInteractions(auditLogRepository);
    }

    @Test
    void invertedTimeRangeIsRejectedBeforeRepositoryCall() {
        assertThatThrownBy(() -> auditLogService.listAuditLogs(
                null, null, null, null, TO, FROM, 0, 20))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Audit time range is invalid.");

        verifyNoInteractions(auditLogRepository);
    }
}
