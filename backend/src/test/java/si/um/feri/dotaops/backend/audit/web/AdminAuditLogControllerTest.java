package si.um.feri.dotaops.backend.audit.web;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import si.um.feri.dotaops.backend.BackendApplication;
import si.um.feri.dotaops.backend.audit.service.AdminAuditLogService;
import si.um.feri.dotaops.backend.auth.domain.AuthenticatedProfile;
import si.um.feri.dotaops.backend.auth.domain.ProfileRole;
import si.um.feri.dotaops.backend.auth.repository.AuthenticatedProfileRepository;
import si.um.feri.dotaops.backend.auth.service.SupabaseJwtTestSupport;
import si.um.feri.dotaops.backend.common.pagination.PageMeta;
import si.um.feri.dotaops.backend.common.pagination.PageResponse;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = {
        BackendApplication.class,
        AdminAuditLogControllerTest.AdminAuditLogControllerTestConfig.class
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "dotaops.supabase.auth.jwt-secret=" + SupabaseJwtTestSupport.SECRET,
        "dotaops.supabase.auth.issuer=" + SupabaseJwtTestSupport.ISSUER,
        "dotaops.supabase.auth.audience=" + SupabaseJwtTestSupport.AUDIENCE,
        "dotaops.steam.session.jwt-secret=" + SupabaseJwtTestSupport.SECRET,
        "dotaops.steam.session.ttl=1h"
})
class AdminAuditLogControllerTest {

    private static final UUID ADMIN_AUTH_USER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID ORGANIZER_AUTH_USER_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID PLAYER_AUTH_USER_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID ADMIN_PROFILE_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID RECORD_ID = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID AUDIT_ID = UUID.fromString("66666666-6666-4666-8666-666666666666");
    private static final OffsetDateTime CREATED_AT = OffsetDateTime.parse("2026-05-21T12:00:00Z");
    private static final OffsetDateTime FROM = OffsetDateTime.parse("2026-05-01T00:00:00Z");
    private static final OffsetDateTime TO = OffsetDateTime.parse("2026-06-01T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminAuditLogService auditLogService;

    @Autowired
    private AuthenticatedProfileRepository authenticatedProfileRepository;

    @BeforeEach
    void setUp() {
        Mockito.reset(auditLogService, authenticatedProfileRepository);
        when(authenticatedProfileRepository.findByAuthUserId(ADMIN_AUTH_USER_ID))
                .thenReturn(Optional.of(profile(ADMIN_AUTH_USER_ID, ADMIN_PROFILE_ID, ProfileRole.ADMIN)));
        when(authenticatedProfileRepository.findByAuthUserId(ORGANIZER_AUTH_USER_ID))
                .thenReturn(Optional.of(profile(
                        ORGANIZER_AUTH_USER_ID,
                        UUID.fromString("77777777-7777-4777-8777-777777777777"),
                        ProfileRole.ORGANIZER)));
        when(authenticatedProfileRepository.findByAuthUserId(PLAYER_AUTH_USER_ID))
                .thenReturn(Optional.of(profile(
                        PLAYER_AUTH_USER_ID,
                        UUID.fromString("88888888-8888-4888-8888-888888888888"),
                        ProfileRole.PLAYER)));
    }

    @Test
    void adminCanListSanitizedAuditLogsWithFiltersAndPagination() throws Exception {
        when(auditLogService.listAuditLogs(
                "public.teams",
                RECORD_ID,
                ADMIN_PROFILE_ID,
                null,
                "update",
                FROM,
                TO,
                1,
                10)).thenReturn(new PageResponse<>(
                List.of(new AdminAuditLogItem(
                        AUDIT_ID,
                        CREATED_AT,
                        new AdminAuditActor(ADMIN_PROFILE_ID, "Admin", "System Admin", "admin"),
                        "update",
                        "public.teams",
                        RECORD_ID,
                        "Team updated",
                        List.of("name"),
                        Map.of("name", "Before"),
                        Map.of("name", "After"))),
                new PageMeta(1, 10, 14, 2, false, true)));

        mockMvc.perform(get("/api/admin/audit-logs")
                        .param("tableName", "public.teams")
                        .param("recordId", RECORD_ID.toString())
                        .param("actorProfileId", ADMIN_PROFILE_ID.toString())
                        .param("action", "update")
                        .param("from", FROM.toString())
                        .param("to", TO.toString())
                        .param("page", "1")
                        .param("size", "10")
                        .header("Authorization", bearerToken(ADMIN_AUTH_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data.items[0].id").value(AUDIT_ID.toString()))
                .andExpect(jsonPath("$.data.items[0].actor.profileId").value(ADMIN_PROFILE_ID.toString()))
                .andExpect(jsonPath("$.data.items[0].actor.nickname").value("Admin"))
                .andExpect(jsonPath("$.data.items[0].actor.displayName").value("System Admin"))
                .andExpect(jsonPath("$.data.items[0].actor.role").value("admin"))
                .andExpect(jsonPath("$.data.items[0].action").value("update"))
                .andExpect(jsonPath("$.data.items[0].table").value("public.teams"))
                .andExpect(jsonPath("$.data.items[0].summary").value("Team updated"))
                .andExpect(jsonPath("$.data.items[0].changedFields[0]").value("name"))
                .andExpect(jsonPath("$.data.items[0].previousRow.name").value("Before"))
                .andExpect(jsonPath("$.data.items[0].newRow.name").value("After"))
                .andExpect(jsonPath("$.data.items[0].actorAuthUserId").doesNotExist())
                .andExpect(jsonPath("$.data.page.page").value(1))
                .andExpect(jsonPath("$.data.page.size").value(10))
                .andExpect(jsonPath("$.data.page.totalElements").value(14));

        verify(auditLogService).listAuditLogs(
                "public.teams",
                RECORD_ID,
                ADMIN_PROFILE_ID,
                null,
                "update",
                FROM,
                TO,
                1,
                10);
    }

    @Test
    void legacyTableAndActorFiltersRemainSupportedWithDefaultPagination() throws Exception {
        when(auditLogService.listAuditLogs("teams", null, null, "Admin", null, null, null, 0, 20))
                .thenReturn(new PageResponse<>(List.of(), new PageMeta(0, 20, 0, 0, false, false)));

        mockMvc.perform(get("/api/admin/audit-logs")
                        .param("table", "teams")
                        .param("actor", "Admin")
                        .header("Authorization", bearerToken(ADMIN_AUTH_USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page.page").value(0))
                .andExpect(jsonPath("$.data.page.size").value(20));

        verify(auditLogService).listAuditLogs("teams", null, null, "Admin", null, null, null, 0, 20);
    }

    @Test
    void invalidActorProfileIdIsRejected() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs")
                        .param("actorProfileId", "not-a-uuid")
                        .header("Authorization", bearerToken(ADMIN_AUTH_USER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void pageSizeAboveMaximumIsRejected() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs")
                        .param("size", "101")
                        .header("Authorization", bearerToken(ADMIN_AUTH_USER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void unauthenticatedUserCannotListAuditLogs() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void organizerCannotListAuditLogs() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs")
                        .header("Authorization", bearerToken(ORGANIZER_AUTH_USER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void playerCannotListAuditLogs() throws Exception {
        mockMvc.perform(get("/api/admin/audit-logs")
                        .header("Authorization", bearerToken(PLAYER_AUTH_USER_ID)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private static String bearerToken(UUID authUserId) throws Exception {
        return "Bearer " + SupabaseJwtTestSupport.token(authUserId, Instant.now());
    }

    private static AuthenticatedProfile profile(UUID authUserId, UUID profileId, ProfileRole role) {
        return new AuthenticatedProfile(profileId, authUserId, role.databaseValue(), role);
    }

    @TestConfiguration
    static class AdminAuditLogControllerTestConfig {

        @Bean
        @Primary
        AdminAuditLogService adminAuditLogService() {
            return Mockito.mock(AdminAuditLogService.class);
        }

        @Bean
        @Primary
        AuthenticatedProfileRepository authenticatedProfileRepository() {
            return Mockito.mock(AuthenticatedProfileRepository.class);
        }
    }
}
