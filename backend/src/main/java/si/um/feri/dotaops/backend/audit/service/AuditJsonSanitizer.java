package si.um.feri.dotaops.backend.audit.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class AuditJsonSanitizer {

    private static final String REDACTED = "[REDACTED]";
    private static final Set<String> SENSITIVE_FIELD_NAMES = Set.of(
            "password",
            "password_hash",
            "hashed_password",
            "authorization",
            "cookie",
            "set_cookie",
            "token",
            "access_token",
            "refresh_token",
            "jwt",
            "secret",
            "client_secret",
            "private_key",
            "api_key",
            "steam_api_key",
            "email_verification_token",
            "reset_token",
            "discord_webhook",
            "webhook_url",
            "provider_token",
            "session");

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, Object> projectSanitizedObject(String json, Set<String> includedFields) {
        JsonNode parsed = parseObject(json);
        if (parsed == null) {
            return null;
        }

        Map<String, Object> projected = new LinkedHashMap<>();
        for (String field : includedFields) {
            if (parsed.has(field)) {
                projected.put(field, sanitize(parsed.get(field)));
            }
        }

        return Collections.unmodifiableMap(projected);
    }

    Object sanitize(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }

        if (node.isTextual()) {
            return node.textValue();
        }

        if (node.isBoolean()) {
            return node.booleanValue();
        }

        if (node.isNumber()) {
            return node.numberValue();
        }

        if (node.isValueNode()) {
            return node.asText();
        }

        if (node.isArray()) {
            List<Object> sanitized = new ArrayList<>();
            node.forEach(item -> sanitized.add(sanitize(item)));
            return Collections.unmodifiableList(sanitized);
        }

        Map<String, Object> sanitized = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.properties().iterator();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            sanitized.put(
                    field.getKey(),
                    isSensitiveField(field.getKey()) ? REDACTED : sanitize(field.getValue()));
        }
        return Collections.unmodifiableMap(sanitized);
    }

    private JsonNode parseObject(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }

        try {
            JsonNode parsed = objectMapper.readTree(json);
            return parsed != null && parsed.isObject() ? parsed : objectMapper.createObjectNode();
        } catch (Exception ignored) {
            return objectMapper.createObjectNode();
        }
    }

    private boolean isSensitiveField(String fieldName) {
        String normalized = fieldName.trim()
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .toLowerCase(Locale.ROOT)
                .replace('-', '_');
        String compact = normalized.replace("_", "");
        return SENSITIVE_FIELD_NAMES.contains(normalized)
                || SENSITIVE_FIELD_NAMES.stream()
                        .map(field -> field.replace("_", ""))
                        .anyMatch(compact::equals)
                || normalized.endsWith("_password")
                || normalized.endsWith("_secret")
                || normalized.endsWith("_token")
                || normalized.endsWith("_api_key")
                || normalized.endsWith("_webhook")
                || normalized.endsWith("_webhook_url")
                || normalized.endsWith("_session");
    }
}
