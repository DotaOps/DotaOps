package si.um.feri.dotaops.backend.profile.service;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;

import si.um.feri.dotaops.backend.auth.domain.AuthenticatedProfile;
import si.um.feri.dotaops.backend.auth.domain.ProfileRole;
import si.um.feri.dotaops.backend.auth.domain.SupabaseJwtClaims;
import si.um.feri.dotaops.backend.auth.service.CurrentUserProvider;
import si.um.feri.dotaops.backend.auth.service.SupabasePrincipal;
import si.um.feri.dotaops.backend.common.error.BadRequestException;
import si.um.feri.dotaops.backend.common.error.ConflictException;
import si.um.feri.dotaops.backend.opendota.domain.OpenDotaPlayerProfile;
import si.um.feri.dotaops.backend.opendota.service.OpenDotaClient;
import si.um.feri.dotaops.backend.profile.domain.Profile;
import si.um.feri.dotaops.backend.profile.repository.CreateProfileCommand;
import si.um.feri.dotaops.backend.profile.repository.ProfileBootstrapRepository;
import si.um.feri.dotaops.backend.profile.repository.ProfileRepository;
import si.um.feri.dotaops.backend.profile.repository.UpdateProfileCommand;
import si.um.feri.dotaops.backend.profile.web.CreateProfileRequest;
import si.um.feri.dotaops.backend.profile.web.UpdateProfileRequest;
import si.um.feri.dotaops.backend.storage.service.StoredImage;
import si.um.feri.dotaops.backend.storage.service.SupabaseImageStorageService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ProfileServiceTest {

    private static final UUID AUTH_USER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PROFILE_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");

    private final ProfileRepository profileRepository = mock(ProfileRepository.class);
    private final ProfileBootstrapRepository profileBootstrapRepository = mock(ProfileBootstrapRepository.class);
    private final SupabaseImageStorageService imageStorageService = mock(SupabaseImageStorageService.class);
    private final OpenDotaClient openDotaClient = mock(OpenDotaClient.class);
    private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
    private final ProfileService profileService = new ProfileService(
            profileRepository,
            profileBootstrapRepository,
            imageStorageService,
            openDotaClient,
            currentUserProvider);

    @Test
    void getCurrentProfileUsesAuthenticatedProfileId() {
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile());
        when(profileRepository.findById(PROFILE_ID)).thenReturn(Optional.of(profile("CarryOne", "SI")));

        var response = profileService.getCurrentProfile();

        assertThat(response.id()).isEqualTo(PROFILE_ID);
        assertThat(response.nickname()).isEqualTo("CarryOne");
        assertThat(response.steamId64()).isEqualTo("76561190000000001");
        assertThat(response.opendotaAccountId()).isEqualTo(39734273L);
        verify(profileRepository).findById(PROFILE_ID);
    }

    @Test
    void createCurrentProfileUsesAuthenticatedUserAndNormalizesInput() {
        when(currentUserProvider.requireAuthUserId()).thenReturn(AUTH_USER_ID);
        when(profileRepository.findByAuthUserId(AUTH_USER_ID)).thenReturn(Optional.empty());
        when(profileRepository.create(org.mockito.ArgumentMatchers.any())).thenReturn(profile("CarryOne", "SI"));

        profileService.createCurrentProfile(new CreateProfileRequest(
                "  CarryOne  ",
                "  Carry One  ",
                " https://cdn.example.test/avatar.png ",
                "  Position one player  ",
                "si"));

        ArgumentCaptor<CreateProfileCommand> captor = ArgumentCaptor.forClass(CreateProfileCommand.class);
        verify(profileRepository).create(captor.capture());

        assertThat(captor.getValue().authUserId()).isEqualTo(AUTH_USER_ID);
        assertThat(captor.getValue().nickname()).isEqualTo("CarryOne");
        assertThat(captor.getValue().displayName()).isEqualTo("Carry One");
        assertThat(captor.getValue().avatarUrl()).isEqualTo("https://cdn.example.test/avatar.png");
        assertThat(captor.getValue().bio()).isEqualTo("Position one player");
        assertThat(captor.getValue().countryCode()).isEqualTo("SI");
        assertThat(captor.getValue().role()).isEqualTo(ProfileRole.PLAYER);
    }

    @Test
    void createCurrentProfileAllowsOrganizerSelfSelection() {
        when(currentUserProvider.requireAuthUserId()).thenReturn(AUTH_USER_ID);
        when(profileRepository.findByAuthUserId(AUTH_USER_ID)).thenReturn(Optional.empty());
        when(profileRepository.create(org.mockito.ArgumentMatchers.any()))
                .thenReturn(profile("OrganizerOne", "SI", ProfileRole.ORGANIZER));

        var result = profileService.createCurrentProfile(new CreateProfileRequest(
                "OrganizerOne",
                "Organizer One",
                null,
                null,
                "si",
                "organizer"));

        ArgumentCaptor<CreateProfileCommand> captor = ArgumentCaptor.forClass(CreateProfileCommand.class);
        verify(profileRepository).create(captor.capture());

        assertThat(result.created()).isTrue();
        assertThat(captor.getValue().role()).isEqualTo(ProfileRole.ORGANIZER);
    }

    @Test
    void createCurrentProfileUpdatesExistingProfileForOnboarding() {
        when(currentUserProvider.requireAuthUserId()).thenReturn(AUTH_USER_ID);
        when(profileRepository.findByAuthUserId(AUTH_USER_ID))
                .thenReturn(Optional.of(profile("CarryOne", "SI")));
        when(profileRepository.updateById(
                org.mockito.ArgumentMatchers.eq(PROFILE_ID),
                org.mockito.ArgumentMatchers.any())).thenReturn(Optional.of(profile("SupportTwo", "DE")));
        when(profileRepository.updateRoleById(PROFILE_ID, ProfileRole.ORGANIZER))
                .thenReturn(Optional.of(profile("SupportTwo", "DE", ProfileRole.ORGANIZER)));

        var result = profileService.createCurrentProfile(new CreateProfileRequest(
                "SupportTwo",
                null,
                null,
                null,
                "de",
                "organizer"));

        assertThat(result.created()).isFalse();
        assertThat(result.profile().role()).isEqualTo("organizer");
    }

    @Test
    void createCurrentProfileDoesNotDowngradeExistingOrganizerOnPlayerOnboarding() {
        when(currentUserProvider.requireAuthUserId()).thenReturn(AUTH_USER_ID);
        when(profileRepository.findByAuthUserId(AUTH_USER_ID))
                .thenReturn(Optional.of(profile("OrganizerOne", "SI", ProfileRole.ORGANIZER)));
        when(profileRepository.updateById(
                org.mockito.ArgumentMatchers.eq(PROFILE_ID),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.of(profile("OrganizerOne", "SI", ProfileRole.ORGANIZER)));

        var result = profileService.createCurrentProfile(new CreateProfileRequest(
                "OrganizerOne",
                "Organizer One",
                null,
                null,
                "si",
                "player"));

        assertThat(result.created()).isFalse();
        assertThat(result.profile().role()).isEqualTo("organizer");
        verify(profileRepository, never()).updateRoleById(PROFILE_ID, ProfileRole.PLAYER);
    }

    @Test
    void createCurrentProfileMapsDuplicateNicknameConstraint() {
        when(currentUserProvider.requireAuthUserId()).thenReturn(AUTH_USER_ID);
        when(profileRepository.findByAuthUserId(AUTH_USER_ID)).thenReturn(Optional.empty());
        when(profileRepository.create(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new DataIntegrityViolationException("profiles_nickname_ci_unique_idx"));

        assertThatThrownBy(() -> profileService.createCurrentProfile(new CreateProfileRequest(
                "CarryOne",
                null,
                null,
                null,
                null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Profile nickname is already in use.");
    }

    @Test
    void updateCurrentProfileRequiresAtLeastOneField() {
        assertThatThrownBy(() -> profileService.updateCurrentProfile(new UpdateProfileRequest(
                null,
                null,
                null,
                null,
                null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("At least one profile field must be provided.");
    }

    @Test
    void updateCurrentProfileNormalizesPatchFields() {
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile());
        when(profileRepository.findById(PROFILE_ID)).thenReturn(Optional.of(profile("CarryOne", "SI")));
        when(profileRepository.updateById(
                org.mockito.ArgumentMatchers.eq(PROFILE_ID),
                org.mockito.ArgumentMatchers.any())).thenReturn(Optional.of(profile("SupportTwo", "DE")));

        profileService.updateCurrentProfile(new UpdateProfileRequest(
                " SupportTwo ",
                null,
                null,
                "  Draft caller  ",
                "de"));

        ArgumentCaptor<UpdateProfileCommand> captor = ArgumentCaptor.forClass(UpdateProfileCommand.class);
        verify(profileRepository).updateById(org.mockito.ArgumentMatchers.eq(PROFILE_ID), captor.capture());

        assertThat(captor.getValue().nicknamePresent()).isTrue();
        assertThat(captor.getValue().nickname()).isEqualTo("SupportTwo");
        assertThat(captor.getValue().displayNamePresent()).isFalse();
        assertThat(captor.getValue().displayName()).isNull();
        assertThat(captor.getValue().bioPresent()).isTrue();
        assertThat(captor.getValue().bio()).isEqualTo("Draft caller");
        assertThat(captor.getValue().countryCodePresent()).isTrue();
        assertThat(captor.getValue().countryCode()).isEqualTo("DE");
    }

    @Test
    void updateCurrentProfileSupportsSteamOnlyProfileIdWithoutAuthUserId() {
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile());
        when(profileRepository.findById(PROFILE_ID)).thenReturn(Optional.of(profile("CarryOne", "SI")));
        when(profileRepository.updateById(
                org.mockito.ArgumentMatchers.eq(PROFILE_ID),
                org.mockito.ArgumentMatchers.any())).thenReturn(Optional.of(profile("SteamCarry", "SI")));

        profileService.updateCurrentProfile(new UpdateProfileRequest(
                "SteamCarry",
                null,
                null,
                null,
                null));

        verify(profileRepository).updateById(org.mockito.ArgumentMatchers.eq(PROFILE_ID), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void updateCurrentProfileClearsExplicitNullableFields() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setDisplayName(null);
        request.setAvatarUrl(null);
        request.setBio(null);
        request.setCountryCode(null);

        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile());
        when(profileRepository.findById(PROFILE_ID)).thenReturn(Optional.of(profile("CarryOne", "SI")));
        when(profileRepository.updateById(
                org.mockito.ArgumentMatchers.eq(PROFILE_ID),
                org.mockito.ArgumentMatchers.any())).thenReturn(Optional.of(profile("CarryOne", null)));

        profileService.updateCurrentProfile(request);

        ArgumentCaptor<UpdateProfileCommand> captor = ArgumentCaptor.forClass(UpdateProfileCommand.class);
        verify(profileRepository).updateById(org.mockito.ArgumentMatchers.eq(PROFILE_ID), captor.capture());

        assertThat(captor.getValue().nicknamePresent()).isFalse();
        assertThat(captor.getValue().displayNamePresent()).isTrue();
        assertThat(captor.getValue().displayName()).isNull();
        assertThat(captor.getValue().avatarUrlPresent()).isTrue();
        assertThat(captor.getValue().avatarUrl()).isNull();
        assertThat(captor.getValue().bioPresent()).isTrue();
        assertThat(captor.getValue().bio()).isNull();
        assertThat(captor.getValue().countryCodePresent()).isTrue();
        assertThat(captor.getValue().countryCode()).isNull();
    }

    @Test
    void updateCurrentProfileRejectsExplicitNullNickname() {
        UpdateProfileRequest request = new UpdateProfileRequest();
        request.setNickname(null);

        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile());
        when(profileRepository.findById(PROFILE_ID)).thenReturn(Optional.of(profile("CarryOne", "SI")));

        assertThatThrownBy(() -> profileService.updateCurrentProfile(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Required profile field is blank.");
    }

    @Test
    void updateCurrentProfileMapsDuplicateNicknameConstraint() {
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile());
        when(profileRepository.findById(PROFILE_ID)).thenReturn(Optional.of(profile("CarryOne", "SI")));
        when(profileRepository.updateById(
                org.mockito.ArgumentMatchers.eq(PROFILE_ID),
                org.mockito.ArgumentMatchers.any()))
                .thenThrow(new DataIntegrityViolationException("profiles_nickname_ci_unique_idx"));

        assertThatThrownBy(() -> profileService.updateCurrentProfile(new UpdateProfileRequest(
                "CarryOne",
                null,
                null,
                null,
                null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Profile nickname is already in use.");
    }

    @Test
    void updateCurrentAvatarStoresImageInSupabaseAndPersistsPublicUrl() {
        MockMultipartFile avatar = new MockMultipartFile(
                "avatar",
                "avatar.png",
                "image/png",
                new byte[] {1, 2, 3});
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile());
        when(profileRepository.findById(PROFILE_ID)).thenReturn(Optional.of(profile("CarryOne", "SI")));
        when(imageStorageService.storeProfileAvatar(PROFILE_ID, avatar))
                .thenReturn(new StoredImage(
                        "profiles/" + PROFILE_ID + "/avatars/avatar.png",
                        "https://project.supabase.co/storage/v1/object/public/dotaops-images/profiles/"
                                + PROFILE_ID + "/avatars/avatar.png",
                        "image/png"));
        when(profileRepository.updateById(
                org.mockito.ArgumentMatchers.eq(PROFILE_ID),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(Optional.of(profile("CarryOne", "SI")));

        var response = profileService.updateCurrentAvatar(avatar);

        assertThat(response.avatarUrl())
                .isEqualTo("https://project.supabase.co/storage/v1/object/public/dotaops-images/profiles/"
                        + PROFILE_ID + "/avatars/avatar.png");
        assertThat(response.persisted()).isTrue();

        ArgumentCaptor<UpdateProfileCommand> captor = ArgumentCaptor.forClass(UpdateProfileCommand.class);
        verify(profileRepository).updateById(org.mockito.ArgumentMatchers.eq(PROFILE_ID), captor.capture());
        assertThat(captor.getValue().avatarUrlPresent()).isTrue();
        assertThat(captor.getValue().avatarUrl()).isEqualTo(response.avatarUrl());
    }

    @Test
    void syncCurrentOpenDotaProfileUsesExistingOpenDotaAccountId() {
        OpenDotaPlayerProfile playerProfile = new OpenDotaPlayerProfile(
                39734273L,
                "CarryOne",
                "https://cdn.example.test/avatar.png",
                "https://steamcommunity.com/profiles/76561190000000001/");
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile());
        when(profileRepository.findById(PROFILE_ID)).thenReturn(Optional.of(profile("CarryOne", "SI")));
        when(openDotaClient.fetchPlayer(39734273L)).thenReturn(Optional.of(playerProfile));

        var response = profileService.syncCurrentOpenDotaProfile();

        assertThat(response.id()).isEqualTo(PROFILE_ID);
        verify(profileBootstrapRepository).markOpenDotaProfileSynced(PROFILE_ID, playerProfile);
    }

    @Test
    void syncCurrentOpenDotaProfileDerivesAccountIdFromSteamIdWhenMissing() {
        OpenDotaPlayerProfile playerProfile = new OpenDotaPlayerProfile(
                39734273L,
                "CarryOne",
                "https://cdn.example.test/avatar.png",
                "https://steamcommunity.com/profiles/76561198000000001/");
        Profile profile = profile("CarryOne", "SI", ProfileRole.PLAYER, "76561198000000001", null);
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile());
        when(profileRepository.findById(PROFILE_ID)).thenReturn(Optional.of(profile));
        when(openDotaClient.fetchPlayer(39734273L)).thenReturn(Optional.of(playerProfile));

        profileService.syncCurrentOpenDotaProfile();

        verify(openDotaClient).fetchPlayer(39734273L);
        verify(profileBootstrapRepository).markOpenDotaProfileSynced(PROFILE_ID, playerProfile);
    }

    @Test
    void syncCurrentOpenDotaProfileRequiresSteamOrOpenDotaIdentity() {
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile());
        when(profileRepository.findById(PROFILE_ID))
                .thenReturn(Optional.of(profile("CarryOne", "SI", ProfileRole.PLAYER, null, null)));

        assertThatThrownBy(profileService::syncCurrentOpenDotaProfile)
                .isInstanceOf(ConflictException.class)
                .hasMessage("A Steam account must be connected before OpenDota sync.");
    }

    @Test
    void syncCurrentOpenDotaProfileRecordsOpenDotaFailure() {
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile());
        when(profileRepository.findById(PROFILE_ID)).thenReturn(Optional.of(profile("CarryOne", "SI")));
        when(openDotaClient.fetchPlayer(39734273L)).thenReturn(Optional.empty());

        assertThatThrownBy(profileService::syncCurrentOpenDotaProfile)
                .isInstanceOf(ConflictException.class)
                .hasMessage("OpenDota profile could not be synced for the current Steam account.");

        verify(profileBootstrapRepository).markOpenDotaProfileSyncFailed(PROFILE_ID, 39734273L);
    }

    @Test
    void getCurrentProfileCreatesMissingSupabaseProfile() {
        SupabasePrincipal principal = new SupabasePrincipal(
                AUTH_USER_ID,
                "new.user@example.test",
                Optional.empty(),
                null);

        when(currentUserProvider.currentUser()).thenReturn(Optional.of(principal));
        when(profileRepository.findByAuthUserId(AUTH_USER_ID)).thenReturn(Optional.empty());
        when(profileRepository.findByNickname("new_user")).thenReturn(Optional.empty());
        when(profileRepository.create(org.mockito.ArgumentMatchers.any()))
                .thenReturn(profile("new_user", null));

        var response = profileService.getCurrentProfile();

        ArgumentCaptor<CreateProfileCommand> captor = ArgumentCaptor.forClass(CreateProfileCommand.class);
        verify(profileRepository).create(captor.capture());

        assertThat(response.nickname()).isEqualTo("new_user");
        assertThat(captor.getValue().authUserId()).isEqualTo(AUTH_USER_ID);
        assertThat(captor.getValue().role()).isEqualTo(ProfileRole.PLAYER);
    }

    @Test
    void missingSupabaseProfileDefaultsToPlayerDespiteSignupMetadata() {
        SupabaseJwtClaims claims = new SupabaseJwtClaims(
                AUTH_USER_ID,
                "organizer@example.test",
                "issuer",
                java.util.List.of("authenticated"),
                null,
                null,
                Map.of("user_metadata", Map.of("desired_role", "organizer")));
        SupabasePrincipal principal = new SupabasePrincipal(
                AUTH_USER_ID,
                "organizer@example.test",
                Optional.empty(),
                claims);

        when(currentUserProvider.currentUser()).thenReturn(Optional.of(principal));
        when(profileRepository.findByAuthUserId(AUTH_USER_ID)).thenReturn(Optional.empty());
        when(profileRepository.findByNickname("organizer")).thenReturn(Optional.empty());
        when(profileRepository.create(org.mockito.ArgumentMatchers.any()))
                .thenReturn(profile("organizer", null, ProfileRole.PLAYER));

        profileService.getCurrentProfile();

        ArgumentCaptor<CreateProfileCommand> captor = ArgumentCaptor.forClass(CreateProfileCommand.class);
        verify(profileRepository).create(captor.capture());

        assertThat(captor.getValue().role()).isEqualTo(ProfileRole.PLAYER);
    }

    @Test
    void createCurrentProfileRejectsAdminSelfSelection() {
        when(currentUserProvider.requireAuthUserId()).thenReturn(AUTH_USER_ID);
        when(profileRepository.findByAuthUserId(AUTH_USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.createCurrentProfile(new CreateProfileRequest(
                "Admin",
                null,
                null,
                null,
                null,
                "admin")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("ADMIN role cannot be self-selected.");

        verify(profileRepository).findByAuthUserId(AUTH_USER_ID);
        verifyNoMoreInteractions(profileRepository);
    }

    @Test
    void createCurrentProfileRejectsCaptainAsGlobalRole() {
        when(currentUserProvider.requireAuthUserId()).thenReturn(AUTH_USER_ID);
        when(profileRepository.findByAuthUserId(AUTH_USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> profileService.createCurrentProfile(new CreateProfileRequest(
                "Captain",
                null,
                null,
                null,
                null,
                "team_captain")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Team captain is assigned through team ownership, not as a global account role.");

        verify(profileRepository).findByAuthUserId(AUTH_USER_ID);
        verifyNoMoreInteractions(profileRepository);
    }

    @Test
    void getProfileByNicknameReturnsPublicProfile() {
        when(profileRepository.findByNickname("CarryOne")).thenReturn(Optional.of(profile("CarryOne", "SI")));

        var response = profileService.getProfileByNickname(" CarryOne ");

        assertThat(response.nickname()).isEqualTo("CarryOne");
        assertThat(response.id()).isEqualTo(PROFILE_ID);
    }

    private AuthenticatedProfile authenticatedProfile() {
        return new AuthenticatedProfile(
                PROFILE_ID,
                null,
                "steam_player",
                ProfileRole.PLAYER);
    }

    private Profile profile(String nickname, String countryCode) {
        return profile(nickname, countryCode, ProfileRole.PLAYER);
    }

    private Profile profile(String nickname, String countryCode, ProfileRole role) {
        return profile(nickname, countryCode, role, "76561190000000001", 39734273L);
    }

    private Profile profile(
            String nickname,
            String countryCode,
            ProfileRole role,
            String steamId,
            Long opendotaAccountId
    ) {
        OffsetDateTime now = OffsetDateTime.parse("2026-05-12T00:00:00Z");

        return new Profile(
                PROFILE_ID,
                AUTH_USER_ID,
                nickname,
                nickname,
                steamId,
                opendotaAccountId,
                role,
                "https://cdn.example.test/avatar.png",
                "Dota player",
                countryCode,
                now,
                now,
                now,
                now);
    }
}
