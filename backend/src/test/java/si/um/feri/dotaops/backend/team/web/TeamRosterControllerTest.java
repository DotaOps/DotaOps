package si.um.feri.dotaops.backend.team.web;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
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
import si.um.feri.dotaops.backend.auth.domain.AuthenticatedProfile;
import si.um.feri.dotaops.backend.auth.domain.ProfileRole;
import si.um.feri.dotaops.backend.auth.repository.AuthenticatedProfileRepository;
import si.um.feri.dotaops.backend.auth.service.SupabaseJwtTestSupport;
import si.um.feri.dotaops.backend.team.domain.TeamInvitationStatus;
import si.um.feri.dotaops.backend.team.domain.TeamMemberRole;
import si.um.feri.dotaops.backend.team.service.TeamRosterService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = {
        BackendApplication.class,
        TeamRosterControllerTest.TeamRosterControllerTestConfig.class
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "dotaops.supabase.auth.jwt-secret=" + SupabaseJwtTestSupport.SECRET,
        "dotaops.supabase.auth.issuer=" + SupabaseJwtTestSupport.ISSUER,
        "dotaops.supabase.auth.audience=" + SupabaseJwtTestSupport.AUDIENCE
})
class TeamRosterControllerTest {

    private static final UUID AUTH_USER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID ORGANIZER_AUTH_USER_ID = UUID.fromString("12121212-1212-4121-8121-121212121212");
    private static final UUID PROFILE_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID TEAM_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID MEMBER_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID INVITATION_ID = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-05-12T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TeamRosterService teamRosterService;

    @Autowired
    private AuthenticatedProfileRepository authenticatedProfileRepository;

    @BeforeEach
    void setUp() {
        Mockito.reset(teamRosterService, authenticatedProfileRepository);
        when(authenticatedProfileRepository.findByAuthUserId(AUTH_USER_ID))
                .thenReturn(Optional.of(authenticatedProfile()));
        when(authenticatedProfileRepository.findByAuthUserId(ORGANIZER_AUTH_USER_ID))
                .thenReturn(Optional.of(organizerAuthenticatedProfile()));
    }

    @Test
    void listMembersWorksWithoutJwt() throws Exception {
        when(teamRosterService.listActiveMembers(TEAM_ID)).thenReturn(List.of(memberResponse(true)));

        mockMvc.perform(get("/api/teams/" + TEAM_ID + "/members"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data[0].id").value(MEMBER_ID.toString()))
                .andExpect(jsonPath("$.data[0].role").value("support"))
                .andExpect(jsonPath("$.data[0].active").value(true));
    }

    @Test
    void createMemberRequiresJwt() throws Exception {
        mockMvc.perform(post("/api/teams/" + TEAM_ID + "/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "profileId": "22222222-2222-4222-8222-222222222222",
                                  "role": "support"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void createMemberReturnsCreatedMember() throws Exception {
        when(teamRosterService.addMember(eq(TEAM_ID), any(AddTeamMemberRequest.class)))
                .thenReturn(memberResponse(true));

        mockMvc.perform(post("/api/teams/" + TEAM_ID + "/members")
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "profileId": "22222222-2222-4222-8222-222222222222",
                                  "role": "carry"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/teams/" + TEAM_ID + "/members/" + MEMBER_ID))
                .andExpect(jsonPath("$.data.id").value(MEMBER_ID.toString()));
    }

    @Test
    void teamInvitationsRequireJwt() throws Exception {
        mockMvc.perform(get("/api/teams/" + TEAM_ID + "/invitations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void currentTeamRequiresJwt() throws Exception {
        mockMvc.perform(get("/api/me/team"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void currentTeamReturnsAggregate() throws Exception {
        when(teamRosterService.getCurrentTeam()).thenReturn(new CurrentTeamResponse(
                teamResponse(),
                List.of(memberResponse(true)),
                true,
                true,
                "Resolved from current captain ownership."));

        mockMvc.perform(get("/api/me/team")
                        .header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.team.id").value(TEAM_ID.toString()))
                .andExpect(jsonPath("$.data.members[0].id").value(MEMBER_ID.toString()))
                .andExpect(jsonPath("$.data.captain").value(true))
                .andExpect(jsonPath("$.data.isTeamOwner").value(true))
                .andExpect(jsonPath("$.data.currentUserTeamRole").value("owner"))
                .andExpect(jsonPath("$.data.canManageTeam").value(true))
                .andExpect(jsonPath("$.data.canManageRoster").value(true))
                .andExpect(jsonPath("$.data.canInvitePlayers").value(true))
                .andExpect(jsonPath("$.data.canViewAnalytics").value(true));
    }

    @Test
    void listCurrentUserInvitationsReturnsInvites() throws Exception {
        when(teamRosterService.listCurrentUserInvitations("pending")).thenReturn(List.of(invitationResponse(
                TeamInvitationStatus.PENDING,
                null)));

        mockMvc.perform(get("/api/me/team-invitations")
                        .param("status", "pending")
                        .header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(INVITATION_ID.toString()))
                .andExpect(jsonPath("$.data[0].proposedRole").value("mid"))
                .andExpect(jsonPath("$.data[0].status").value("pending"));
    }

    @Test
    void acceptInvitationReturnsAcceptedInvitation() throws Exception {
        when(teamRosterService.acceptInvitation(INVITATION_ID)).thenReturn(invitationResponse(
                TeamInvitationStatus.ACCEPTED,
                NOW));

        mockMvc.perform(post("/api/team-invitations/" + INVITATION_ID + "/accept")
                        .header("Authorization", bearerToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(INVITATION_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("accepted"))
                .andExpect(jsonPath("$.data.acceptedAt").exists());
    }

    @Test
    void transferOwnershipRequiresJwt() throws Exception {
        mockMvc.perform(post("/api/teams/" + TEAM_ID + "/transfer-ownership")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "newOwnerProfileId": "44444444-4444-4444-8444-444444444444"
                                }
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void organizerCannotTransferOwnership() throws Exception {
        mockMvc.perform(post("/api/teams/" + TEAM_ID + "/transfer-ownership")
                        .header("Authorization", bearerToken(ORGANIZER_AUTH_USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "newOwnerProfileId": "44444444-4444-4444-8444-444444444444"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void transferOwnershipReturnsUpdatedCurrentTeamCapabilities() throws Exception {
        when(teamRosterService.transferOwnership(eq(TEAM_ID), any(TransferTeamOwnershipRequest.class)))
                .thenReturn(new CurrentTeamResponse(
                        teamResponse(),
                        List.of(memberResponse(true)),
                        false,
                        false,
                        "Resolved from active team membership."));

        mockMvc.perform(post("/api/teams/" + TEAM_ID + "/transfer-ownership")
                        .header("Authorization", bearerToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "newOwnerProfileId": "44444444-4444-4444-8444-444444444444"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isTeamOwner").value(false))
                .andExpect(jsonPath("$.data.canTransferOwnership").value(false));
    }

    private static String bearerToken() throws Exception {
        return bearerToken(AUTH_USER_ID);
    }

    private static String bearerToken(UUID authUserId) throws Exception {
        return "Bearer " + SupabaseJwtTestSupport.token(authUserId, Instant.now());
    }

    private static AuthenticatedProfile authenticatedProfile() {
        return new AuthenticatedProfile(
                PROFILE_ID,
                AUTH_USER_ID,
                "MidPulse",
                ProfileRole.PLAYER);
    }

    private static AuthenticatedProfile organizerAuthenticatedProfile() {
        return new AuthenticatedProfile(
                UUID.fromString("13131313-1313-4131-8131-131313131313"),
                ORGANIZER_AUTH_USER_ID,
                "Organizer",
                ProfileRole.ORGANIZER);
    }

    private static TeamMemberResponse memberResponse(boolean active) {
        return new TeamMemberResponse(
                MEMBER_ID,
                TEAM_ID,
                PROFILE_ID,
                "MidPulse",
                "Mid Pulse",
                null,
                TeamMemberRole.SUPPORT,
                false,
                active,
                NOW,
                active ? null : NOW,
                NOW);
    }

    private static TeamResponse teamResponse() {
        return new TeamResponse(
                TEAM_ID,
                "Ancient Stack",
                "AS",
                "ancient-stack",
                PROFILE_ID,
                "MidPulse",
                "EU",
                null,
                null,
                NOW,
                NOW);
    }

    private static TeamInvitationResponse invitationResponse(
            TeamInvitationStatus status,
            OffsetDateTime acceptedAt
    ) {
        return new TeamInvitationResponse(
                INVITATION_ID,
                TEAM_ID,
                "Ancient Stack",
                "ancient-stack",
                PROFILE_ID,
                "Captain",
                PROFILE_ID,
                "MidPulse",
                null,
                TeamMemberRole.MID,
                status,
                NOW.plusDays(1),
                acceptedAt,
                NOW,
                NOW);
    }

    @TestConfiguration
    static class TeamRosterControllerTestConfig {

        @Bean
        @Primary
        TeamRosterService teamRosterService() {
            return Mockito.mock(TeamRosterService.class);
        }

        @Bean
        @Primary
        AuthenticatedProfileRepository authenticatedProfileRepository() {
            return Mockito.mock(AuthenticatedProfileRepository.class);
        }
    }
}
