package si.um.feri.dotaops.backend.audit.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import si.um.feri.dotaops.backend.audit.domain.AdminAuditAction;
import si.um.feri.dotaops.backend.audit.domain.AdminAuditLogRecord;

@Repository
public class AdminAuditLogRepository {

    private final JdbcTemplate jdbcTemplate;

    public AdminAuditLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AdminAuditLogRecord> findAuditLogs(AdminAuditLogFilters filters, int size, long offset) {
        QueryParts queryParts = filteredWhere(filters);
        List<Object> parameters = new ArrayList<>(queryParts.parameters());
        parameters.add(size);
        parameters.add(offset);

        return jdbcTemplate.query(
                """
                select
                  al.id,
                  al.created_at,
                  al.actor_profile_id,
                  actor.nickname as actor_nickname,
                  al.action::text as action,
                  al.table_name,
                  al.record_id,
                  al.previous_row::text as previous_row,
                  al.new_row::text as new_row
                from public.audit_log al
                left join public.profiles actor on actor.id = al.actor_profile_id
                """ + queryParts.sql() + """
                order by al.created_at desc, al.id desc
                limit ? offset ?
                """,
                this::mapAuditLog,
                parameters.toArray());
    }

    public long countAuditLogs(AdminAuditLogFilters filters) {
        QueryParts queryParts = filteredWhere(filters);
        Long count = jdbcTemplate.queryForObject(
                """
                select count(*)
                from public.audit_log al
                left join public.profiles actor on actor.id = al.actor_profile_id
                """ + queryParts.sql(),
                Long.class,
                queryParts.parameters().toArray());

        return count == null ? 0 : count;
    }

    private QueryParts filteredWhere(AdminAuditLogFilters filters) {
        List<String> clauses = new ArrayList<>();
        List<Object> parameters = new ArrayList<>();

        if (filters.tableName() != null) {
            clauses.add("(lower(al.table_name) = lower(?) or lower(al.table_name) = 'public.' || lower(?))");
            parameters.add(filters.tableName());
            parameters.add(filters.tableName());
        }

        if (filters.recordId() != null) {
            clauses.add("al.record_id = ?");
            parameters.add(filters.recordId());
        }

        if (filters.actor() != null) {
            clauses.add("(cast(al.actor_profile_id as text) = ? or actor.nickname ilike '%' || ? || '%')");
            parameters.add(filters.actor());
            parameters.add(filters.actor());
        }

        if (filters.action() != null) {
            clauses.add("al.action = cast(? as public.dotaops_audit_action)");
            parameters.add(filters.action().databaseValue());
        }

        if (filters.from() != null) {
            clauses.add("al.created_at >= ?");
            parameters.add(filters.from());
        }

        if (filters.to() != null) {
            clauses.add("al.created_at <= ?");
            parameters.add(filters.to());
        }

        return new QueryParts(
                clauses.isEmpty() ? "" : "where " + String.join("\n  and ", clauses) + "\n",
                parameters);
    }

    private AdminAuditLogRecord mapAuditLog(ResultSet resultSet, int rowNumber) throws SQLException {
        return new AdminAuditLogRecord(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("actor_profile_id", UUID.class),
                resultSet.getString("actor_nickname"),
                AdminAuditAction.fromDatabaseValue(resultSet.getString("action")),
                resultSet.getString("table_name"),
                resultSet.getObject("record_id", UUID.class),
                resultSet.getString("previous_row"),
                resultSet.getString("new_row"));
    }

    private record QueryParts(String sql, List<Object> parameters) {
    }
}
