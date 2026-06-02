package si.um.feri.dotaops.backend.audit.service;

import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuditJsonSanitizerTest {

    private final AuditJsonSanitizer sanitizer = new AuditJsonSanitizer();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void recursivelyRedactsSensitiveFieldsAndDropsFieldsOutsideProjection() {
        var sanitized = objectMapper.valueToTree(sanitizer.projectSanitizedObject(
                """
                {
                  "description": {
                    "label": "safe",
                    "password": "hidden",
                    "nested": {
                      "provider_token": "hidden",
                      "accessToken": "hidden",
                      "metadata": {
                        "steam_api_key": "hidden",
                        "public": "visible"
                      }
                    },
                    "entries": [
                      {"webhook_url": "hidden"},
                      {"name": "visible"}
                    ]
                  },
                  "raw_response": {
                    "access_token": "must-not-be-returned"
                  }
                }
                """,
                Set.of("description")));

        assertThat(sanitized.path("description").path("label").asText()).isEqualTo("safe");
        assertThat(sanitized.path("description").path("password").asText()).isEqualTo("[REDACTED]");
        assertThat(sanitized.path("description").path("nested").path("provider_token").asText())
                .isEqualTo("[REDACTED]");
        assertThat(sanitized.path("description").path("nested").path("accessToken").asText())
                .isEqualTo("[REDACTED]");
        assertThat(sanitized.path("description").path("nested").path("metadata").path("steam_api_key").asText())
                .isEqualTo("[REDACTED]");
        assertThat(sanitized.path("description").path("nested").path("metadata").path("public").asText())
                .isEqualTo("visible");
        assertThat(sanitized.path("description").path("entries").path(0).path("webhook_url").asText())
                .isEqualTo("[REDACTED]");
        assertThat(sanitized.has("raw_response")).isFalse();
    }
}
