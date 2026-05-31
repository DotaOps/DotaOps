package si.um.feri.dotaops.backend.notification.web;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import si.um.feri.dotaops.backend.BackendApplication;
import si.um.feri.dotaops.backend.auth.domain.AuthenticatedProfile;
import si.um.feri.dotaops.backend.auth.domain.ProfileRole;
import si.um.feri.dotaops.backend.auth.repository.AuthenticatedProfileRepository;
import si.um.feri.dotaops.backend.auth.service.SupabaseJwtTestSupport;
import si.um.feri.dotaops.backend.notification.domain.NotificationChannel;
import si.um.feri.dotaops.backend.notification.domain.NotificationStatus;
import si.um.feri.dotaops.backend.notification.domain.NotificationType;
import si.um.feri.dotaops.backend.notification.service.NotificationOutboxProcessor;
import si.um.feri.dotaops.backend.notification.service.NotificationService;

import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = {
        BackendApplication.class,
        NotificationControllerTest.NotificationControllerTestConfig.class
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
class NotificationControllerTest {

    private static final UUID AUTH_USER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PROFILE_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID NOTIFICATION_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-05-29T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationOutboxProcessor notificationOutboxProcessor;

    @Autowired
    private AuthenticatedProfileRepository authenticatedProfileRepository;

    @BeforeEach
    void setUp() {
        Mockito.reset(notificationService, notificationOutboxProcessor, authenticatedProfileRepository);
        when(authenticatedProfileRepository.findByAuthUserId(AUTH_USER_ID))
                .thenReturn(Optional.of(profile(ProfileRole.PLAYER)));
    }

    @Test
    void myNotificationsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/me/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserCanListNotifications() throws Exception {
        when(notificationService.listCurrentUserNotifications(isNull()))
                .thenReturn(List.of(notificationResponse()));

        mockMvc.perform(get("/api/me/notifications")
                        .header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(NOTIFICATION_ID.toString()))
                .andExpect(jsonPath("$.data[0].type").value("team_application_approved"))
                .andExpect(jsonPath("$.data[0].channel").value("in_app"))
                .andExpect(jsonPath("$.data[0].title").value("Ekipa odobrena"));
    }

    @Test
    void authenticatedUserCanMarkNotificationRead() throws Exception {
        when(notificationService.markRead(NOTIFICATION_ID)).thenReturn(notificationResponse());

        mockMvc.perform(post("/api/me/notifications/{id}/read", NOTIFICATION_ID)
                        .header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readAt").exists());
    }

    @Test
    void authenticatedUserCanMarkAllNotificationsRead() throws Exception {
        when(notificationService.markAllRead()).thenReturn(new NotificationsReadResponse(2));

        mockMvc.perform(post("/api/me/notifications/read-all")
                        .header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updatedCount").value(2));
    }

    @Test
    void adminProcessEndpointIsNotPublic() throws Exception {
        mockMvc.perform(post("/api/admin/notifications/outbox/process"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void playerCannotProcessOutbox() throws Exception {
        mockMvc.perform(post("/api/admin/notifications/outbox/process")
                        .header("Authorization", bearerToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanProcessOutbox() throws Exception {
        when(authenticatedProfileRepository.findByAuthUserId(AUTH_USER_ID))
                .thenReturn(Optional.of(profile(ProfileRole.ADMIN)));
        when(notificationOutboxProcessor.processQueued())
                .thenReturn(new NotificationOutboxProcessResponse(2, 1, 1));

        mockMvc.perform(post("/api/admin/notifications/outbox/process")
                        .header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.processedCount").value(2))
                .andExpect(jsonPath("$.data.deliveredCount").value(1))
                .andExpect(jsonPath("$.data.failedCount").value(1));
    }

    private static String bearerToken() throws Exception {
        return "Bearer " + SupabaseJwtTestSupport.token(AUTH_USER_ID, Instant.now());
    }

    private static AuthenticatedProfile profile(ProfileRole role) {
        return new AuthenticatedProfile(
                PROFILE_ID,
                AUTH_USER_ID,
                "Player",
                role);
    }

    private static NotificationResponse notificationResponse() {
        return new NotificationResponse(
                NOTIFICATION_ID,
                NotificationType.TEAM_APPLICATION_APPROVED,
                NotificationChannel.IN_APP,
                "Ekipa odobrena",
                "Tvoja ekipa je bila odobrena.",
                Map.of("teamId", "radiant"),
                NotificationStatus.DELIVERED,
                NOW,
                NOW.minusMinutes(5));
    }

    @TestConfiguration
    static class NotificationControllerTestConfig {

        @Bean
        @Primary
        NotificationService notificationService() {
            return Mockito.mock(NotificationService.class);
        }

        @Bean
        @Primary
        NotificationOutboxProcessor notificationOutboxProcessor() {
            return Mockito.mock(NotificationOutboxProcessor.class);
        }

        @Bean
        @Primary
        AuthenticatedProfileRepository authenticatedProfileRepository() {
            return Mockito.mock(AuthenticatedProfileRepository.class);
        }
    }
}
