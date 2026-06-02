package si.um.feri.dotaops.backend.integration;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import si.um.feri.dotaops.backend.BackendApplication;
import si.um.feri.dotaops.backend.auth.service.SupabaseJwtTestSupport;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.hasItem;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = BackendApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@EnabledIfEnvironmentVariable(named = "SUPABASE_DB_URL", matches = ".+")
@TestPropertySource(properties = {
        "dotaops.supabase.auth.jwt-secret=" + SupabaseJwtTestSupport.SECRET,
        "dotaops.supabase.auth.issuer=" + SupabaseJwtTestSupport.ISSUER,
        "dotaops.supabase.auth.audience=" + SupabaseJwtTestSupport.AUDIENCE
})
class TeamRosterApiIntegrationTest extends PostgresIntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();


    private UUID captainAuthUserId;
    private UUID captainProfileId;
    private UUID inviteeAuthUserId;
    private UUID inviteeProfileId;

    @BeforeEach
    void seedProfiles() {
        captainAuthUserId = UUID.randomUUID();
        inviteeAuthUserId = UUID.randomUUID();

        captainProfileId = upsertProfile(captainAuthUserId, "player");
        inviteeProfileId = upsertProfile(inviteeAuthUserId, "player");
    }

    @Test
    void rosterInvitationAndSoftDeactivateFlowWorksThroughRestApi() throws Exception {
        String suffix = uniqueSuffix();

        UUID teamId = extractDataId(mockMvc.perform(post("/api/teams")
                        .header("Authorization", bearerToken(captainAuthUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Roster Smoke %s",
                                  "slug": "roster-smoke-%s",
                                  "region": "EU"
                                }
                                """.formatted(suffix, suffix)))
                .andExpect(status().isCreated())
                .andReturn());

        mockMvc.perform(get("/api/me/team")
                        .header("Authorization", bearerToken(captainAuthUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.team.id").value(teamId.toString()))
                .andExpect(jsonPath("$.data.captain").value(true))
                .andExpect(jsonPath("$.data.canManageRoster").value(true))
                .andExpect(jsonPath("$.data.canTransferOwnership").value(true))
                .andExpect(jsonPath("$.data.participantsCount").value(1))
                .andExpect(jsonPath("$.data.slotsFilled").value(1))
                .andExpect(jsonPath("$.data.slotsRemaining").value(4));

        UUID memberId = extractDataId(mockMvc.perform(post("/api/teams/" + teamId + "/members")
                        .header("Authorization", bearerToken(captainAuthUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "profileId": "%s",
                                  "role": "mid"
                                }
                                """.formatted(inviteeProfileId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("mid"))
                .andReturn());

        mockMvc.perform(get("/api/teams/" + teamId + "/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].profileId").value(hasItem(captainProfileId.toString())))
                .andExpect(jsonPath("$.data[*].profileId").value(hasItem(inviteeProfileId.toString())))
                .andExpect(jsonPath("$.data[*].active").value(hasItem(true)));

        mockMvc.perform(delete("/api/teams/" + teamId + "/members/" + memberId)
                        .header("Authorization", bearerToken(captainAuthUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false))
                .andExpect(jsonPath("$.data.leftAt").exists());

        UUID invitationId = extractDataId(mockMvc.perform(post("/api/teams/" + teamId + "/invitations")
                        .header("Authorization", bearerToken(captainAuthUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "inviteeProfileId": "%s",
                                  "proposedRole": "support"
                                }
                                """.formatted(inviteeProfileId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("pending"))
                .andReturn());

        mockMvc.perform(post("/api/teams/" + teamId + "/invitations")
                        .header("Authorization", bearerToken(captainAuthUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "inviteeProfileId": "%s",
                                  "proposedRole": "support"
                                }
                                """.formatted(inviteeProfileId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        mockMvc.perform(get("/api/me/team-invitations")
                        .param("status", "pending")
                        .header("Authorization", bearerToken(inviteeAuthUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(invitationId.toString()));

        mockMvc.perform(post("/api/team-invitations/" + invitationId + "/accept")
                        .header("Authorization", bearerToken(inviteeAuthUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("accepted"));

        mockMvc.perform(get("/api/teams/" + teamId + "/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].profileId").value(hasItem(inviteeProfileId.toString())))
                .andExpect(jsonPath("$.data[*].role").value(hasItem("support")))
                .andExpect(jsonPath("$.data[*].active").value(hasItem(true)));
    }

    @Test
    void ownerCanTransferOwnershipToActivePlayerMemberAndAuditIsRecorded() throws Exception {
        String suffix = uniqueSuffix();

        UUID teamId = extractDataId(mockMvc.perform(post("/api/teams")
                        .header("Authorization", bearerToken(captainAuthUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Transfer Smoke %s",
                                  "slug": "transfer-smoke-%s",
                                  "region": "EU"
                                }
                                """.formatted(suffix, suffix)))
                .andExpect(status().isCreated())
                .andReturn());

        mockMvc.perform(post("/api/teams/" + teamId + "/members")
                        .header("Authorization", bearerToken(captainAuthUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "profileId": "%s",
                                  "role": "mid"
                                }
                                """.formatted(inviteeProfileId)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/teams/" + teamId + "/transfer-ownership")
                        .header("Authorization", bearerToken(captainAuthUserId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "newOwnerProfileId": "%s"
                                }
                                """.formatted(inviteeProfileId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.team.captainProfileId").value(inviteeProfileId.toString()))
                .andExpect(jsonPath("$.data.isTeamOwner").value(false))
                .andExpect(jsonPath("$.data.canManageRoster").value(false))
                .andExpect(jsonPath("$.data.canInvitePlayers").value(false))
                .andExpect(jsonPath("$.data.canTransferOwnership").value(false))
                .andExpect(jsonPath("$.data.participantsCount").value(2));

        mockMvc.perform(get("/api/me/team")
                        .header("Authorization", bearerToken(inviteeAuthUserId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.team.captainProfileId").value(inviteeProfileId.toString()))
                .andExpect(jsonPath("$.data.isTeamOwner").value(true))
                .andExpect(jsonPath("$.data.canManageRoster").value(true))
                .andExpect(jsonPath("$.data.canInvitePlayers").value(true))
                .andExpect(jsonPath("$.data.canTransferOwnership").value(true))
                .andExpect(jsonPath("$.data.members[*].profileId").value(hasItem(captainProfileId.toString())))
                .andExpect(jsonPath("$.data.members[*].profileId").value(hasItem(inviteeProfileId.toString())));

        Integer activeMembers = jdbcTemplate.queryForObject(
                "select count(*) from public.team_members where team_id = ? and is_active",
                Integer.class,
                teamId);
        assertThat(activeMembers).isEqualTo(2);

        Map<String, Object> auditRow = jdbcTemplate.queryForMap(
                """
                select actor_profile_id, previous_row::text as previous_row, new_row::text as new_row
                from public.audit_log
                where table_name = 'public.teams'
                  and record_id = ?
                  and action = 'update'::public.dotaops_audit_action
                order by created_at desc, id desc
                limit 1
                """,
                teamId);
        assertThat(auditRow.get("actor_profile_id")).isEqualTo(captainProfileId);
        assertThat(auditRow.get("previous_row").toString()).contains(captainProfileId.toString());
        assertThat(auditRow.get("new_row").toString()).contains(inviteeProfileId.toString());
    }

    private UUID extractDataId(org.springframework.test.web.servlet.MvcResult result) throws Exception {
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());

        return UUID.fromString(response.path("data").path("id").asText());
    }

    private static String bearerToken(UUID authUserId) throws Exception {
        return "Bearer " + SupabaseJwtTestSupport.token(authUserId, Instant.now());
    }
}
