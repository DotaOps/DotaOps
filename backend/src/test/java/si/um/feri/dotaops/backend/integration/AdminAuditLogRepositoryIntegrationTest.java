package si.um.feri.dotaops.backend.integration;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import si.um.feri.dotaops.backend.audit.domain.AdminAuditAction;
import si.um.feri.dotaops.backend.audit.repository.AdminAuditLogFilters;
import si.um.feri.dotaops.backend.audit.repository.AdminAuditLogRepository;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("integration")
@EnabledIfEnvironmentVariable(named = "SUPABASE_DB_URL", matches = ".+")
class AdminAuditLogRepositoryIntegrationTest extends PostgresIntegrationTestSupport {

    @Autowired
    private AdminAuditLogRepository auditLogRepository;

    @Test
    void filtersByTableRecordActorAndActionAndPaginatesNewestFirst() {
        UUID actorAuthUserId = UUID.randomUUID();
        UUID actorProfileId = upsertProfile(actorAuthUserId, "admin");
        UUID matchingRecordId = UUID.randomUUID();
        OffsetDateTime baseTime = OffsetDateTime.parse("2026-05-01T00:00:00Z");
        UUID olderAuditId = insertAuditLog(
                actorProfileId,
                matchingRecordId,
                "public.teams",
                "update",
                "{\"name\":\"Before\"}",
                "{\"name\":\"After\"}",
                baseTime);
        UUID newerAuditId = insertAuditLog(
                actorProfileId,
                matchingRecordId,
                "public.teams",
                "update",
                "{\"name\":\"After\"}",
                "{\"name\":\"Final\"}",
                baseTime.plusSeconds(1));
        insertAuditLog(
                actorProfileId,
                UUID.randomUUID(),
                "public.tournaments",
                "insert",
                null,
                "{\"title\":\"Other record\"}",
                baseTime.plusSeconds(2));

        String actorNickname = jdbcTemplate.queryForObject(
                "select nickname from public.profiles where id = ?",
                String.class,
                actorProfileId);
        AdminAuditLogFilters filters = new AdminAuditLogFilters(
                "teams",
                matchingRecordId,
                actorNickname,
                AdminAuditAction.UPDATE,
                null,
                null);

        assertThat(auditLogRepository.countAuditLogs(filters)).isEqualTo(2);
        assertThat(auditLogRepository.findAuditLogs(filters, 1, 0))
                .extracting(record -> record.id())
                .containsExactly(newerAuditId);
        assertThat(auditLogRepository.findAuditLogs(filters, 1, 1))
                .extracting(record -> record.id())
                .containsExactly(olderAuditId);
    }

    private UUID insertAuditLog(
            UUID actorProfileId,
            UUID recordId,
            String tableName,
            String action,
            String previousRow,
            String newRow,
            OffsetDateTime createdAt
    ) {
        return asServiceRole(() -> jdbcTemplate.queryForObject(
                """
                insert into public.audit_log (
                  actor_profile_id,
                  table_name,
                  record_id,
                  action,
                  previous_row,
                  new_row,
                  created_at
                )
                values (?, ?, ?, ?::public.dotaops_audit_action, ?::jsonb, ?::jsonb, ?)
                returning id
                """,
                UUID.class,
                actorProfileId,
                tableName,
                recordId,
                action,
                previousRow,
                newRow,
                createdAt));
    }
}
