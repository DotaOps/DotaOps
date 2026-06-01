package si.um.feri.dotaops.backend.audit.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.um.feri.dotaops.backend.audit.domain.AdminAuditAction;
import si.um.feri.dotaops.backend.audit.domain.AdminAuditLogRecord;
import si.um.feri.dotaops.backend.audit.repository.AdminAuditLogFilters;
import si.um.feri.dotaops.backend.audit.repository.AdminAuditLogRepository;
import si.um.feri.dotaops.backend.audit.web.AdminAuditActor;
import si.um.feri.dotaops.backend.audit.web.AdminAuditLogItem;
import si.um.feri.dotaops.backend.common.error.BadRequestException;
import si.um.feri.dotaops.backend.common.pagination.PageResponse;

@Service
public class AdminAuditLogService {

    private static final Map<String, Set<String>> SAFE_CHANGED_FIELDS = Map.of(
            "public.teams", Set.of(
                    "name", "slug", "tag", "region", "description", "captain_profile_id", "updated_at"),
            "public.tournaments", Set.of(
                    "slug", "title", "description", "rules", "format", "status", "is_public",
                    "max_teams", "registration_opens_at", "registration_closes_at", "starts_at", "ends_at",
                    "check_in_opens_at", "check_in_closes_at", "published_at", "updated_at"),
            "public.tournament_registrations", Set.of(
                    "status", "seed_number", "reviewed_at", "checked_in_at", "updated_at"),
            "public.matches", Set.of(
                    "stage_name", "round_name", "round_number", "series_number", "bracket_position", "best_of",
                    "status", "scheduled_at", "started_at", "finished_at", "team_a_id", "team_b_id",
                    "score_a", "score_b", "winner_team_id", "updated_at"),
            "public.match_games", Set.of(
                    "dota_match_id", "game_number", "status", "winner_side", "duration_seconds", "started_at",
                    "finished_at", "radiant_team_id", "dire_team_id", "winner_team_id", "import_status", "updated_at"),
            "public.match_imports", Set.of(
                    "status", "source", "attempt_count", "error_code", "requested_at", "started_at", "completed_at",
                    "updated_at"),
            "public.match_players", Set.of(
                    "team_side", "is_winner", "hero_id", "kills", "deaths", "assists", "last_hits", "denies",
                    "gold_per_min", "xp_per_min", "hero_damage", "tower_damage", "hero_healing", "level",
                    "updated_at"));

    private static final Map<String, String> TABLE_LABELS = Map.of(
            "public.teams", "Team",
            "public.tournaments", "Tournament",
            "public.tournament_registrations", "Tournament registration",
            "public.matches", "Match",
            "public.match_games", "Match game",
            "public.match_imports", "Match import",
            "public.match_players", "Match player");

    private final AdminAuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AdminAuditLogService(AdminAuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminAuditLogItem> listAuditLogs(
            String table,
            UUID recordId,
            String actor,
            String action,
            OffsetDateTime from,
            OffsetDateTime to,
            int page,
            int size
    ) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);

        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("Audit time range is invalid.");
        }

        AdminAuditLogFilters filters = new AdminAuditLogFilters(
                normalizeOptional(table),
                recordId,
                normalizeOptional(actor),
                normalizeAction(action),
                from,
                to);
        long offset = (long) safePage * safeSize;
        List<AdminAuditLogItem> items = auditLogRepository.findAuditLogs(filters, safeSize, offset)
                .stream()
                .map(this::toItem)
                .toList();
        long total = auditLogRepository.countAuditLogs(filters);

        return PageResponse.from(new PageImpl<>(
                items,
                PageRequest.of(safePage, safeSize),
                total));
    }

    private AdminAuditLogItem toItem(AdminAuditLogRecord record) {
        return new AdminAuditLogItem(
                record.id(),
                record.createdAt(),
                new AdminAuditActor(record.actorProfileId(), record.actorNickname()),
                record.action().databaseValue(),
                record.tableName(),
                record.recordId(),
                summary(record),
                changedFields(record));
    }

    private String summary(AdminAuditLogRecord record) {
        String label = TABLE_LABELS.getOrDefault(record.tableName(), "Audit record");
        return label + " " + record.action().summaryVerb();
    }

    private List<String> changedFields(AdminAuditLogRecord record) {
        Set<String> allowedFields = SAFE_CHANGED_FIELDS.getOrDefault(record.tableName(), Set.of());
        JsonNode previousRow = parseObject(record.previousRowJson());
        JsonNode newRow = parseObject(record.newRowJson());
        List<String> changedFields = new ArrayList<>();

        for (String field : allowedFields.stream().sorted().toList()) {
            if (changed(record.action(), previousRow, newRow, field)) {
                changedFields.add(field);
            }
        }

        return List.copyOf(new LinkedHashSet<>(changedFields));
    }

    private boolean changed(AdminAuditAction action, JsonNode previousRow, JsonNode newRow, String field) {
        return switch (action) {
            case INSERT -> newRow.has(field);
            case DELETE -> previousRow.has(field);
            case UPDATE -> !previousRow.path(field).equals(newRow.path(field));
        };
    }

    private JsonNode parseObject(String json) {
        if (json == null || json.isBlank()) {
            return objectMapper.createObjectNode();
        }

        try {
            JsonNode node = objectMapper.readTree(json);
            return node != null && node.isObject() ? node : objectMapper.createObjectNode();
        } catch (Exception ignored) {
            return objectMapper.createObjectNode();
        }
    }

    private AdminAuditAction normalizeAction(String action) {
        return action == null || action.isBlank()
                ? null
                : AdminAuditAction.fromDatabaseValue(action);
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim().toLowerCase(Locale.ROOT);
    }
}
