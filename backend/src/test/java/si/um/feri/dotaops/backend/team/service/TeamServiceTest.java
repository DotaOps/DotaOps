package si.um.feri.dotaops.backend.team.service;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

import si.um.feri.dotaops.backend.auth.domain.AuthenticatedProfile;
import si.um.feri.dotaops.backend.auth.domain.ProfileRole;
import si.um.feri.dotaops.backend.auth.service.CurrentUserProvider;
import si.um.feri.dotaops.backend.common.error.BadRequestException;
import si.um.feri.dotaops.backend.common.error.ConflictException;
import si.um.feri.dotaops.backend.storage.service.StoredImage;
import si.um.feri.dotaops.backend.storage.service.SupabaseImageStorageService;
import si.um.feri.dotaops.backend.team.domain.Team;
import si.um.feri.dotaops.backend.team.repository.CreateTeamCommand;
import si.um.feri.dotaops.backend.team.repository.CreateTeamMemberCommand;
import si.um.feri.dotaops.backend.team.repository.TeamManualPlayerRepository;
import si.um.feri.dotaops.backend.team.repository.TeamMemberRepository;
import si.um.feri.dotaops.backend.team.repository.TeamRepository;
import si.um.feri.dotaops.backend.team.repository.UpdateTeamCommand;
import si.um.feri.dotaops.backend.team.web.CreateTeamRequest;
import si.um.feri.dotaops.backend.team.web.UpdateTeamRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TeamServiceTest {

    private static final UUID AUTH_USER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID CAPTAIN_PROFILE_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID OTHER_PROFILE_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID TEAM_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");

    private final TeamRepository teamRepository = mock(TeamRepository.class);
    private final TeamMemberRepository teamMemberRepository = mock(TeamMemberRepository.class);
    private final TeamManualPlayerRepository teamManualPlayerRepository = mock(TeamManualPlayerRepository.class);
    private final SupabaseImageStorageService imageStorageService = mock(SupabaseImageStorageService.class);
    private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
    private final TeamService teamService = new TeamService(
            teamRepository,
            teamMemberRepository,
            teamManualPlayerRepository,
            imageStorageService,
            currentUserProvider);

    @BeforeEach
    void setUpActiveCaptainMembership() {
        when(teamMemberRepository.existsActive(TEAM_ID, CAPTAIN_PROFILE_ID)).thenReturn(true);
    }

    @Test
    void createTeamSetsAuthenticatedUserAsCaptainAndGeneratesSlug() {
        when(currentUserProvider.requireProfile()).thenReturn(profile(CAPTAIN_PROFILE_ID, ProfileRole.PLAYER));
        when(teamRepository.create(any())).thenReturn(team("Ancient Stack", "ancient-stack", CAPTAIN_PROFILE_ID));

        teamService.createTeam(new CreateTeamRequest(
                "  Ancient Stack  ",
                " AS ",
                null,
                "  EU  ",
                null,
                "  Tier two squad  "));

        ArgumentCaptor<CreateTeamCommand> captor = ArgumentCaptor.forClass(CreateTeamCommand.class);
        verify(teamRepository).create(captor.capture());
        ArgumentCaptor<CreateTeamMemberCommand> memberCaptor = ArgumentCaptor.forClass(CreateTeamMemberCommand.class);
        verify(teamMemberRepository).create(memberCaptor.capture());

        assertThat(captor.getValue().name()).isEqualTo("Ancient Stack");
        assertThat(captor.getValue().tag()).isEqualTo("AS");
        assertThat(captor.getValue().slug()).isEqualTo("ancient-stack");
        assertThat(captor.getValue().captainProfileId()).isEqualTo(CAPTAIN_PROFILE_ID);
        assertThat(captor.getValue().createdBy()).isEqualTo(AUTH_USER_ID);
        assertThat(captor.getValue().region()).isEqualTo("EU");
        assertThat(captor.getValue().description()).isEqualTo("Tier two squad");
        assertThat(memberCaptor.getValue().profileId()).isEqualTo(CAPTAIN_PROFILE_ID);
        assertThat(memberCaptor.getValue().role()).isEqualTo(si.um.feri.dotaops.backend.team.domain.TeamMemberRole.SUPPORT);
    }

    @Test
    void createTeamNormalizesExplicitSlug() {
        when(currentUserProvider.requireProfile()).thenReturn(profile(CAPTAIN_PROFILE_ID, ProfileRole.PLAYER));
        when(teamRepository.create(any())).thenReturn(team("Ancient Stack", "ancient-stack", CAPTAIN_PROFILE_ID));

        teamService.createTeam(new CreateTeamRequest(
                "Ancient Stack",
                null,
                "Ancient-Stack",
                null,
                null,
                null));

        ArgumentCaptor<CreateTeamCommand> captor = ArgumentCaptor.forClass(CreateTeamCommand.class);
        verify(teamRepository).create(captor.capture());

        assertThat(captor.getValue().slug()).isEqualTo("ancient-stack");
    }

    @Test
    void createTeamMapsDuplicateSlugToBadRequest() {
        when(currentUserProvider.requireProfile()).thenReturn(profile(CAPTAIN_PROFILE_ID, ProfileRole.PLAYER));
        when(teamRepository.create(any())).thenThrow(new DataIntegrityViolationException(
                "duplicate",
                new RuntimeException("duplicate key value violates unique constraint \"teams_slug_key\"")));

        assertThatThrownBy(() -> teamService.createTeam(new CreateTeamRequest(
                "Ancient Stack",
                null,
                "ancient-stack",
                null,
                null,
                null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Team slug is already in use.");
    }

    @Test
    void createTeamMapsDuplicateNameToBadRequest() {
        when(currentUserProvider.requireProfile()).thenReturn(profile(CAPTAIN_PROFILE_ID, ProfileRole.PLAYER));
        when(teamRepository.create(any())).thenThrow(new DataIntegrityViolationException(
                "duplicate",
                new RuntimeException("duplicate key value violates unique constraint \"teams_name_key\"")));

        assertThatThrownBy(() -> teamService.createTeam(new CreateTeamRequest(
                "Ancient Stack",
                null,
                null,
                null,
                null,
                null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Team name is already in use.");
    }

    @Test
    void createTeamSupportsSteamOnlyProfileWithoutAuthUserId() {
        when(currentUserProvider.requireProfile()).thenReturn(steamOnlyProfile(CAPTAIN_PROFILE_ID));
        when(teamRepository.create(any())).thenReturn(team("Ancient Stack", "ancient-stack", CAPTAIN_PROFILE_ID));

        teamService.createTeam(new CreateTeamRequest(
                "Ancient Stack",
                null,
                null,
                null,
                null,
                null));

        ArgumentCaptor<CreateTeamCommand> captor = ArgumentCaptor.forClass(CreateTeamCommand.class);
        verify(teamRepository).create(captor.capture());

        assertThat(captor.getValue().captainProfileId()).isEqualTo(CAPTAIN_PROFILE_ID);
        assertThat(captor.getValue().createdBy()).isNull();
    }

    @Test
    void captainCanUpdateOwnTeam() {
        when(currentUserProvider.requireProfile()).thenReturn(profile(CAPTAIN_PROFILE_ID, ProfileRole.PLAYER));
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team("Ancient Stack", "ancient-stack", CAPTAIN_PROFILE_ID)));
        when(teamRepository.update(eq(TEAM_ID), any())).thenReturn(Optional.of(team("Ancient Core", "ancient-core", CAPTAIN_PROFILE_ID)));

        teamService.updateTeam(TEAM_ID, new UpdateTeamRequest(
                " Ancient Core ",
                null,
                "Ancient-Core",
                null,
                null,
                null));

        ArgumentCaptor<UpdateTeamCommand> captor = ArgumentCaptor.forClass(UpdateTeamCommand.class);
        verify(teamRepository).update(eq(TEAM_ID), captor.capture());

        assertThat(captor.getValue().namePresent()).isTrue();
        assertThat(captor.getValue().name()).isEqualTo("Ancient Core");
        assertThat(captor.getValue().slugPresent()).isTrue();
        assertThat(captor.getValue().slug()).isEqualTo("ancient-core");
    }

    @Test
    void inactiveCaptainCannotUpdateOwnTeam() {
        when(currentUserProvider.requireProfile()).thenReturn(profile(CAPTAIN_PROFILE_ID, ProfileRole.PLAYER));
        when(teamRepository.findById(TEAM_ID))
                .thenReturn(Optional.of(team("Ancient Stack", "ancient-stack", CAPTAIN_PROFILE_ID)));
        when(teamMemberRepository.existsActive(TEAM_ID, CAPTAIN_PROFILE_ID)).thenReturn(false);

        assertThatThrownBy(() -> teamService.updateTeam(TEAM_ID, new UpdateTeamRequest(
                "Ancient Core",
                null,
                null,
                null,
                null,
                null)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Only the team captain or an admin can update this team.");

        verify(teamRepository, never()).update(eq(TEAM_ID), any());
    }

    @Test
    void organizerCannotCreateTeam() {
        when(currentUserProvider.requireProfile()).thenReturn(profile(OTHER_PROFILE_ID, ProfileRole.ORGANIZER));

        assertThatThrownBy(() -> teamService.createTeam(new CreateTeamRequest(
                "Organizer Team",
                null,
                null,
                null,
                null,
                null)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Only players can create teams.");

        verify(teamRepository, never()).create(any());
    }

    @Test
    void playerCannotCreateSecondActiveTeam() {
        when(currentUserProvider.requireProfile()).thenReturn(profile(CAPTAIN_PROFILE_ID, ProfileRole.PLAYER));
        when(teamRepository.existsCurrentTeamForProfileExcluding(CAPTAIN_PROFILE_ID, null)).thenReturn(true);

        assertThatThrownBy(() -> teamService.createTeam(new CreateTeamRequest(
                "Second Team",
                null,
                null,
                null,
                null,
                null)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Profile already belongs to an active team.");

        verify(teamRepository, never()).create(any());
    }

    @Test
    void organizerCannotUpdateAnyTeam() {
        when(currentUserProvider.requireProfile()).thenReturn(profile(OTHER_PROFILE_ID, ProfileRole.ORGANIZER));
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team("Ancient Stack", "ancient-stack", CAPTAIN_PROFILE_ID)));

        assertThatThrownBy(() -> teamService.updateTeam(TEAM_ID, new UpdateTeamRequest(
                null,
                null,
                null,
                "NA",
                null,
                null)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Only the team captain or an admin can update this team.");

        verify(teamRepository, never()).update(eq(TEAM_ID), any());
    }

    @Test
    void adminCanUpdateAnyTeam() {
        when(currentUserProvider.requireProfile()).thenReturn(profile(OTHER_PROFILE_ID, ProfileRole.ADMIN));
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team("Ancient Stack", "ancient-stack", CAPTAIN_PROFILE_ID)));
        when(teamRepository.update(eq(TEAM_ID), any())).thenReturn(Optional.of(team("Ancient Stack", "ancient-stack", CAPTAIN_PROFILE_ID)));

        teamService.updateTeam(TEAM_ID, new UpdateTeamRequest(
                null,
                null,
                null,
                "NA",
                null,
                null));

        verify(teamRepository).update(eq(TEAM_ID), any());
    }

    @Test
    void nonCaptainCannotUpdateTeam() {
        when(currentUserProvider.requireProfile()).thenReturn(profile(OTHER_PROFILE_ID, ProfileRole.PLAYER));
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team("Ancient Stack", "ancient-stack", CAPTAIN_PROFILE_ID)));

        assertThatThrownBy(() -> teamService.updateTeam(TEAM_ID, new UpdateTeamRequest(
                "Ancient Core",
                null,
                null,
                null,
                null,
                null)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Only the team captain or an admin can update this team.");
    }

    @Test
    void updateTeamRequiresAtLeastOneField() {
        assertThatThrownBy(() -> teamService.updateTeam(TEAM_ID, new UpdateTeamRequest(
                null,
                null,
                null,
                null,
                null,
                null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("At least one team field must be provided.");
    }

    @Test
    void updateTeamClearsExplicitNullableFields() {
        UpdateTeamRequest request = new UpdateTeamRequest();
        request.setTag(null);
        request.setRegion(null);
        request.setLogoUrl(null);
        request.setDescription(null);

        when(currentUserProvider.requireProfile()).thenReturn(profile(CAPTAIN_PROFILE_ID, ProfileRole.PLAYER));
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team("Ancient Stack", "ancient-stack", CAPTAIN_PROFILE_ID)));
        when(teamRepository.update(eq(TEAM_ID), any())).thenReturn(Optional.of(team("Ancient Stack", "ancient-stack", CAPTAIN_PROFILE_ID)));

        teamService.updateTeam(TEAM_ID, request);

        ArgumentCaptor<UpdateTeamCommand> captor = ArgumentCaptor.forClass(UpdateTeamCommand.class);
        verify(teamRepository).update(eq(TEAM_ID), captor.capture());

        assertThat(captor.getValue().namePresent()).isFalse();
        assertThat(captor.getValue().tagPresent()).isTrue();
        assertThat(captor.getValue().tag()).isNull();
        assertThat(captor.getValue().regionPresent()).isTrue();
        assertThat(captor.getValue().region()).isNull();
        assertThat(captor.getValue().logoUrlPresent()).isTrue();
        assertThat(captor.getValue().logoUrl()).isNull();
        assertThat(captor.getValue().descriptionPresent()).isTrue();
        assertThat(captor.getValue().description()).isNull();
    }

    @Test
    void updateTeamRejectsExplicitNullRequiredName() {
        UpdateTeamRequest request = new UpdateTeamRequest();
        request.setName(null);

        when(currentUserProvider.requireProfile()).thenReturn(profile(CAPTAIN_PROFILE_ID, ProfileRole.PLAYER));
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team("Ancient Stack", "ancient-stack", CAPTAIN_PROFILE_ID)));

        assertThatThrownBy(() -> teamService.updateTeam(TEAM_ID, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Required team field is blank.");
    }

    @Test
    void captainCanUploadTeamLogo() {
        MockMultipartFile logo = new MockMultipartFile("logo", "logo.png", "image/png", new byte[] {1});
        when(currentUserProvider.requireProfile()).thenReturn(profile(CAPTAIN_PROFILE_ID, ProfileRole.PLAYER));
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team("Ancient Stack", "ancient-stack", CAPTAIN_PROFILE_ID)));
        when(imageStorageService.storeTeamLogo(TEAM_ID, logo))
                .thenReturn(new StoredImage("teams/" + TEAM_ID + "/logo/logo.png", "https://cdn.example.test/logo.png", "image/png"));
        when(teamRepository.updateLogoUrl(TEAM_ID, "https://cdn.example.test/logo.png"))
                .thenReturn(Optional.of(team("Ancient Stack", "ancient-stack", CAPTAIN_PROFILE_ID)));

        var response = teamService.uploadTeamLogo(TEAM_ID, logo);

        verify(imageStorageService).storeTeamLogo(TEAM_ID, logo);
        verify(teamRepository).updateLogoUrl(TEAM_ID, "https://cdn.example.test/logo.png");
        assertThat(response.logoUrl()).isEqualTo("https://cdn.example.test/logo.png");
    }

    @Test
    void nonCaptainCannotUploadTeamBanner() {
        MockMultipartFile banner = new MockMultipartFile("banner", "banner.png", "image/png", new byte[] {1});
        when(currentUserProvider.requireProfile()).thenReturn(profile(OTHER_PROFILE_ID, ProfileRole.PLAYER));
        when(teamRepository.findById(TEAM_ID)).thenReturn(Optional.of(team("Ancient Stack", "ancient-stack", CAPTAIN_PROFILE_ID)));

        assertThatThrownBy(() -> teamService.uploadTeamBanner(TEAM_ID, banner))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Only the team captain or an admin can update this team.");

        verify(imageStorageService, never()).storeTeamBanner(TEAM_ID, banner);
        verify(teamRepository, never()).updateBannerUrl(eq(TEAM_ID), any());
    }

    private static AuthenticatedProfile profile(UUID profileId, ProfileRole role) {
        return new AuthenticatedProfile(
                profileId,
                AUTH_USER_ID,
                "MidPulse",
                role);
    }

    private static AuthenticatedProfile steamOnlyProfile(UUID profileId) {
        return new AuthenticatedProfile(
                profileId,
                null,
                "steam_player",
                ProfileRole.PLAYER);
    }

    private static Team team(String name, String slug, UUID captainProfileId) {
        OffsetDateTime now = OffsetDateTime.parse("2026-05-12T00:00:00Z");

        return new Team(
                TEAM_ID,
                name,
                "AS",
                slug,
                captainProfileId,
                "MidPulse",
                "EU",
                "https://cdn.example.test/logo.png",
                "Tier two squad",
                AUTH_USER_ID,
                now,
                now);
    }
}
