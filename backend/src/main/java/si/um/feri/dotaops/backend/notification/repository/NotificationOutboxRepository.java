package si.um.feri.dotaops.backend.notification.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import si.um.feri.dotaops.backend.common.error.BadRequestException;
import si.um.feri.dotaops.backend.notification.domain.NotificationChannel;
import si.um.feri.dotaops.backend.notification.domain.NotificationOutbox;
import si.um.feri.dotaops.backend.notification.domain.NotificationStatus;
import si.um.feri.dotaops.backend.notification.domain.NotificationType;

@Repository
public class NotificationOutboxRepository {

    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NotificationOutboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public NotificationOutbox create(CreateNotificationCommand command) {
        OffsetDateTime nextAttemptAt = command.nextAttemptAt();
        return jdbcTemplate.queryForObject(
                """
                insert into public.notification_outbox (
                  recipient_profile_id,
                  type,
                  channel,
                  title,
                  message,
                  subject,
                  body,
                  payload,
                  status,
                  attempt_count,
                  attempts,
                  next_attempt_at,
                  available_at
                )
                values (
                  ?,
                  cast(? as public.dotaops_notification_type),
                  cast(? as public.dotaops_notification_channel),
                  ?,
                  ?,
                  ?,
                  ?,
                  cast(? as jsonb),
                  'queued',
                  0,
                  0,
                  coalesce(cast(? as timestamptz), now()),
                  coalesce(cast(? as timestamptz), now())
                )
                """ + returningSql(),
                this::mapNotification,
                command.recipientProfileId(),
                command.type().databaseValue(),
                command.channel().databaseValue(),
                command.title(),
                command.message(),
                command.title(),
                command.message(),
                toJson(command.payload()),
                nextAttemptAt,
                nextAttemptAt);
    }

    public List<NotificationOutbox> findInAppByRecipientProfileId(UUID recipientProfileId, int limit) {
        return jdbcTemplate.query(
                selectSql() + """
                where recipient_profile_id = ?
                  and channel = 'in_app'
                order by created_at desc, id desc
                limit ?
                """,
                this::mapNotification,
                recipientProfileId,
                limit);
    }

    public Optional<NotificationOutbox> findById(UUID notificationId) {
        return jdbcTemplate.query(
                        selectSql() + """
                        where id = ?
                        limit 1
                        """,
                        this::mapNotification,
                        notificationId)
                .stream()
                .findFirst();
    }

    public Optional<NotificationOutbox> markRead(UUID notificationId, UUID recipientProfileId) {
        return jdbcTemplate.query(
                        """
                        update public.notification_outbox
                        set
                          read_at = coalesce(read_at, now()),
                          updated_at = now()
                        where id = ?
                          and recipient_profile_id = ?
                          and channel = 'in_app'
                        """ + returningSql(),
                        this::mapNotification,
                        notificationId,
                        recipientProfileId)
                .stream()
                .findFirst();
    }

    public int markAllRead(UUID recipientProfileId) {
        return jdbcTemplate.update(
                """
                update public.notification_outbox
                set
                  read_at = coalesce(read_at, now()),
                  updated_at = now()
                where recipient_profile_id = ?
                  and channel = 'in_app'
                  and read_at is null
                """,
                recipientProfileId);
    }

    public List<NotificationOutbox> findDueQueued(OffsetDateTime now, int limit) {
        return jdbcTemplate.query(
                selectSql() + """
                where status = 'queued'
                  and coalesce(next_attempt_at, available_at, created_at) <= ?
                order by created_at asc, id asc
                limit ?
                """,
                this::mapNotification,
                now,
                limit);
    }

    public Optional<NotificationOutbox> markProcessing(UUID notificationId) {
        return jdbcTemplate.query(
                        """
                        update public.notification_outbox
                        set
                          status = 'processing',
                          updated_at = now()
                        where id = ?
                          and status = 'queued'
                        """ + returningSql(),
                        this::mapNotification,
                        notificationId)
                .stream()
                .findFirst();
    }

    public Optional<NotificationOutbox> markDelivered(UUID notificationId) {
        return jdbcTemplate.query(
                        """
                        update public.notification_outbox
                        set
                          status = 'delivered',
                          last_error = null,
                          processed_at = now(),
                          sent_at = now(),
                          updated_at = now()
                        where id = ?
                        """ + returningSql(),
                        this::mapNotification,
                        notificationId)
                .stream()
                .findFirst();
    }

    public Optional<NotificationOutbox> markDeliveryFailure(
            UUID notificationId,
            String lastError,
            OffsetDateTime nextAttemptAt,
            int maxAttempts
    ) {
        return jdbcTemplate.query(
                        """
                        update public.notification_outbox
                        set
                          attempt_count = coalesce(attempt_count, attempts, 0) + 1,
                          attempts = coalesce(attempts, attempt_count, 0) + 1,
                          last_error = ?,
                          status = cast(
                            case
                              when coalesce(attempt_count, attempts, 0) + 1 >= ? then 'failed'
                              else 'queued'
                            end
                            as public.dotaops_delivery_status
                          ),
                          next_attempt_at = case
                            when coalesce(attempt_count, attempts, 0) + 1 >= ? then coalesce(next_attempt_at, now())
                            else ?
                          end,
                          available_at = case
                            when coalesce(attempt_count, attempts, 0) + 1 >= ? then coalesce(available_at, now())
                            else ?
                          end,
                          processed_at = case
                            when coalesce(attempt_count, attempts, 0) + 1 >= ? then now()
                            else null
                          end,
                          updated_at = now()
                        where id = ?
                        """ + returningSql(),
                        this::mapNotification,
                        truncate(lastError, 4000),
                        maxAttempts,
                        maxAttempts,
                        nextAttemptAt,
                        maxAttempts,
                        nextAttemptAt,
                        maxAttempts,
                        notificationId)
                .stream()
                .findFirst();
    }

    private String selectSql() {
        return """
                select
                  id,
                  recipient_profile_id,
                  type::text as type,
                  channel::text as channel,
                  coalesce(title, subject) as title,
                  coalesce(message, body) as message,
                  payload::text as payload,
                  status::text as status,
                  coalesce(attempt_count, attempts, 0)::integer as attempt_count,
                  last_error,
                  coalesce(next_attempt_at, available_at, created_at) as next_attempt_at,
                  coalesce(processed_at, sent_at) as processed_at,
                  read_at,
                  created_at,
                  updated_at
                from public.notification_outbox
                """;
    }

    private String returningSql() {
        return """
                returning
                  id,
                  recipient_profile_id,
                  type::text as type,
                  channel::text as channel,
                  coalesce(title, subject) as title,
                  coalesce(message, body) as message,
                  payload::text as payload,
                  status::text as status,
                  coalesce(attempt_count, attempts, 0)::integer as attempt_count,
                  last_error,
                  coalesce(next_attempt_at, available_at, created_at) as next_attempt_at,
                  coalesce(processed_at, sent_at) as processed_at,
                  read_at,
                  created_at,
                  updated_at
                """;
    }

    private NotificationOutbox mapNotification(ResultSet resultSet, int rowNumber) throws SQLException {
        return new NotificationOutbox(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("recipient_profile_id", UUID.class),
                NotificationType.fromDatabaseValue(resultSet.getString("type")),
                NotificationChannel.fromDatabaseValue(resultSet.getString("channel")),
                resultSet.getString("title"),
                resultSet.getString("message"),
                fromJson(resultSet.getString("payload")),
                NotificationStatus.fromDatabaseValue(resultSet.getString("status")),
                resultSet.getInt("attempt_count"),
                resultSet.getString("last_error"),
                resultSet.getObject("next_attempt_at", OffsetDateTime.class),
                resultSet.getObject("processed_at", OffsetDateTime.class),
                resultSet.getObject("read_at", OffsetDateTime.class),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class));
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (JsonProcessingException exception) {
            throw new BadRequestException("Notification payload could not be serialized.");
        }
    }

    private Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }

        try {
            return objectMapper.readValue(json, PAYLOAD_TYPE);
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }

        return value.substring(0, maxLength);
    }
}
