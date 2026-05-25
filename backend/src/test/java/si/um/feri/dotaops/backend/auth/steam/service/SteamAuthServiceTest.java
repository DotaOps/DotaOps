package si.um.feri.dotaops.backend.auth.steam.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import si.um.feri.dotaops.backend.auth.domain.AuthenticatedProfile;
import si.um.feri.dotaops.backend.auth.domain.ProfileRole;
import si.um.feri.dotaops.backend.auth.service.CurrentUserProvider;
import si.um.feri.dotaops.backend.auth.service.SupabasePrincipal;
import si.um.feri.dotaops.backend.auth.steam.domain.SteamLoginStateContext;
import si.um.feri.dotaops.backend.auth.steam.domain.SteamPlayerSummary;
import si.um.feri.dotaops.backend.auth.steam.domain.SteamProfileUpsertResult;
import si.um.feri.dotaops.backend.auth.steam.repository.SteamLoginStateRepository;
import si.um.feri.dotaops.backend.auth.steam.repository.SteamProfileRepository;
import si.um.feri.dotaops.backend.common.error.ConflictException;
import si.um.feri.dotaops.backend.common.error.BadRequestException;
import si.um.feri.dotaops.backend.common.error.RateLimitExceededException;
import si.um.feri.dotaops.backend.common.security.ClientIpAddressResolver;
import si.um.feri.dotaops.backend.common.security.RequestRateLimiter;
import si.um.feri.dotaops.backend.config.properties.SteamAuthProperties;
import si.um.feri.dotaops.backend.profile.service.SteamProfileBootstrapService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SteamAuthServiceTest {

    private static final UUID STATE_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID AUTH_USER_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID PROFILE_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID EXTERNAL_ACCOUNT_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final String STEAM_ID = "76561198000000001";
    private static final String CLAIMED_ID = "https://steamcommunity.com/openid/id/" + STEAM_ID;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-12T00:00:00Z"), ZoneOffset.UTC);

    private final SteamLoginStateRepository loginStateRepository = mock(SteamLoginStateRepository.class);
    private final SteamProfileRepository profileRepository = mock(SteamProfileRepository.class);
    private final SteamOpenIdClient openIdClient = mock(SteamOpenIdClient.class);
    private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
    private final SteamProfileBootstrapService profileBootstrapService = mock(SteamProfileBootstrapService.class);
    private final ClientIpAddressResolver clientIpAddressResolver = new ClientIpAddressResolver();
    private final RequestRateLimiter requestRateLimiter = mock(RequestRateLimiter.class);
    private final SteamAuthService steamAuthService = new SteamAuthService(
            properties("http://localhost:3000/auth/steam/callback"),
            loginStateRepository,
            profileRepository,
            openIdClient,
            currentUserProvider,
            profileBootstrapService,
            clientIpAddressResolver,
            requestRateLimiter,
            fixedRandom(),
            CLOCK);

    @Test
    void beginLoginStoresHashedStateAndBuildsSteamRedirect() {
        when(currentUserProvider.currentUser()).thenReturn(Optional.empty());
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("User-Agent", "JUnit");

        var redirectUri = steamAuthService.beginLogin(request);

        ArgumentCaptor<String> stateHash = ArgumentCaptor.forClass(String.class);
        verify(requestRateLimiter).checkSteamLogin("127.0.0.1");
        verify(loginStateRepository).create(
                stateHash.capture(),
                eq("http://localhost:3000/auth/steam/callback"),
                eq(null),
                eq(null),
                eq("127.0.0.1"),
                eq("JUnit"),
                eq(java.time.OffsetDateTime.parse("2026-05-12T00:10:00Z")));

        assertThat(stateHash.getValue()).hasSize(43);
        assertThat(redirectUri.toString())
                .startsWith("https://steamcommunity.com/openid/login?")
                .contains("openid.mode=checkid_setup")
                .contains("openid.claimed_id=http://specs.openid.net/auth/2.0/identifier_select")
                .contains("openid.return_to=");
    }

    @Test
    void beginLoginRateLimitsBeforeCreatingLoginState() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        doThrow(new RateLimitExceededException("Too many Steam login attempts from this IP address. Try again later."))
                .when(requestRateLimiter)
                .checkSteamLogin("127.0.0.1");

        assertThatThrownBy(() -> steamAuthService.beginLogin(request))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessage("Too many Steam login attempts from this IP address. Try again later.");

        verify(loginStateRepository, never()).create(anyString(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void beginProfileLinkStoresAuthenticatedProfileContext() {
        when(currentUserProvider.requireProfile()).thenReturn(authenticatedProfile());
        when(currentUserProvider.currentUser()).thenReturn(Optional.of(principal()));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        request.addHeader("User-Agent", "JUnit");

        steamAuthService.beginProfileLink(request);

        verify(currentUserProvider).requireProfile();
        verify(loginStateRepository).create(
                anyString(),
                eq("http://localhost:3000/auth/steam/callback"),
                eq(PROFILE_ID),
                eq(AUTH_USER_ID),
                eq("127.0.0.1"),
                eq("JUnit"),
                eq(java.time.OffsetDateTime.parse("2026-05-12T00:10:00Z")));
    }

    @Test
    void beginProfileLinkRequiresProfileBeforeCreatingState() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(currentUserProvider.requireProfile()).thenThrow(new AccessDeniedException("DotaOps profile is required."));

        assertThatThrownBy(() -> steamAuthService.beginProfileLink(request))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("DotaOps profile is required.");

        verify(loginStateRepository, never()).create(anyString(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void completeCallbackIsTransactionalSoFailedCallbacksDoNotBurnState() throws Exception {
        assertThat(SteamAuthService.class
                .getMethod("completeCallback", MultiValueMap.class)
                .isAnnotationPresent(Transactional.class))
                .isTrue();
    }

    @Test
    void completeCallbackVerifiesSteamAssertionAndLinksExistingProfile() {
        String state = "state-token";
        MultiValueMap<String, String> params = validCallbackParams(state);
        when(loginStateRepository.consume(anyString())).thenReturn(Optional.of(stateContext()));
        when(openIdClient.verifyAuthentication(params)).thenReturn(true);
        when(openIdClient.fetchPlayerSummary(STEAM_ID)).thenReturn(Optional.of(new SteamPlayerSummary(
                STEAM_ID,
                "Dota Player",
                "https://cdn.example.test/avatar.png",
                "https://steamcommunity.com/profiles/" + STEAM_ID + "/")));
        when(profileRepository.linkSteamAccountToProfile(
                eq(PROFILE_ID),
                eq(STEAM_ID),
                eq("Dota Player"),
                eq("https://cdn.example.test/avatar.png"),
                eq("https://steamcommunity.com/profiles/" + STEAM_ID + "/"),
                eq(CLAIMED_ID))).thenReturn(EXTERNAL_ACCOUNT_ID);

        var result = steamAuthService.completeCallback(params);

        assertThat(result.steamId()).isEqualTo(STEAM_ID);
        assertThat(result.profileId()).isEqualTo(PROFILE_ID);
        assertThat(result.externalAccountId()).isEqualTo(EXTERNAL_ACCOUNT_ID);
        assertThat(result.newProfile()).isFalse();
        assertThat(result.newExternalAccount()).isFalse();
        assertThat(result.redirectUri().toString())
                .startsWith("http://localhost:3000/auth/steam/callback?steamLogin=success")
                .contains("steamId=" + STEAM_ID)
                .contains("profileId=" + PROFILE_ID);
        verify(profileBootstrapService).bootstrapAfterSteamLogin(
                eq(PROFILE_ID),
                eq(STEAM_ID),
                eq(new SteamPlayerSummary(
                        STEAM_ID,
                        "Dota Player",
                        "https://cdn.example.test/avatar.png",
                        "https://steamcommunity.com/profiles/" + STEAM_ID + "/")));
    }

    @Test
    void completeCallbackStillReturnsSessionWhenBootstrapSchedulingFails() {
        String state = "state-token";
        MultiValueMap<String, String> params = validCallbackParams(state);
        SteamPlayerSummary summary = new SteamPlayerSummary(
                STEAM_ID,
                "Dota Player",
                "https://cdn.example.test/avatar.png",
                "https://steamcommunity.com/profiles/" + STEAM_ID + "/");
        when(loginStateRepository.consume(anyString())).thenReturn(Optional.of(stateContext()));
        when(openIdClient.verifyAuthentication(params)).thenReturn(true);
        when(openIdClient.fetchPlayerSummary(STEAM_ID)).thenReturn(Optional.of(summary));
        when(profileRepository.linkSteamAccountToProfile(
                eq(PROFILE_ID),
                eq(STEAM_ID),
                eq("Dota Player"),
                eq("https://cdn.example.test/avatar.png"),
                eq("https://steamcommunity.com/profiles/" + STEAM_ID + "/"),
                eq(CLAIMED_ID))).thenReturn(EXTERNAL_ACCOUNT_ID);
        doThrow(new IllegalStateException("executor rejected"))
                .when(profileBootstrapService)
                .bootstrapAfterSteamLogin(PROFILE_ID, STEAM_ID, summary);

        var result = steamAuthService.completeCallback(params);

        assertThat(result.profileId()).isEqualTo(PROFILE_ID);
        assertThat(result.steamId()).isEqualTo(STEAM_ID);
    }

    @Test
    void completeCallbackReturnsConflictWhenSteamBelongsToAnotherActiveProfile() {
        MultiValueMap<String, String> params = validCallbackParams("state-token");
        when(loginStateRepository.consume(anyString())).thenReturn(Optional.of(stateContext()));
        when(openIdClient.verifyAuthentication(params)).thenReturn(true);
        when(openIdClient.fetchPlayerSummary(STEAM_ID)).thenReturn(Optional.empty());
        when(profileRepository.linkSteamAccountToProfile(
                eq(PROFILE_ID),
                eq(STEAM_ID),
                eq(null),
                eq(null),
                eq("https://steamcommunity.com/profiles/" + STEAM_ID + "/"),
                eq(CLAIMED_ID))).thenThrow(new DataIntegrityViolationException(
                        "Steam account is already linked to a different profile."));

        assertThatThrownBy(() -> steamAuthService.completeCallback(params))
                .isInstanceOf(ConflictException.class)
                .hasMessage("This Steam account is already connected to another DotaOps profile.");
    }

    @Test
    void completeCallbackUpsertsProfileForUnlinkedSteamLogin() {
        MultiValueMap<String, String> params = validCallbackParams("state-token");
        when(loginStateRepository.consume(anyString())).thenReturn(Optional.of(new SteamLoginStateContext(
                STATE_ID,
                "http://localhost:3000/auth/steam/callback",
                null,
                null,
                "127.0.0.1",
                "JUnit")));
        when(openIdClient.verifyAuthentication(params)).thenReturn(true);
        when(openIdClient.fetchPlayerSummary(STEAM_ID)).thenReturn(Optional.empty());
        when(profileRepository.upsertSteamProfile(
                eq(STEAM_ID),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq("https://steamcommunity.com/profiles/" + STEAM_ID + "/"),
                eq(CLAIMED_ID))).thenReturn(new SteamProfileUpsertResult(
                PROFILE_ID,
                EXTERNAL_ACCOUNT_ID,
                true,
                true));

        var result = steamAuthService.completeCallback(params);

        assertThat(result.newProfile()).isTrue();
        assertThat(result.externalAccountId()).isEqualTo(EXTERNAL_ACCOUNT_ID);
        verify(profileRepository, never()).linkSteamAccountToProfile(
                any(),
                anyString(),
                any(),
                any(),
                any(),
                anyString());
    }

    @Test
    void completeCallbackRejectsInvalidSteamAssertion() {
        MultiValueMap<String, String> params = validCallbackParams("state-token");
        when(loginStateRepository.consume(anyString())).thenReturn(Optional.of(stateContext()));
        when(openIdClient.verifyAuthentication(params)).thenReturn(false);

        assertThatThrownBy(() -> steamAuthService.completeCallback(params))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Steam OpenID assertion is invalid.");

        verify(profileRepository, never()).upsertSteamProfile(
                anyString(),
                any(),
                any(),
                any(),
                any(),
                any(),
                anyString());
        verify(profileRepository, never()).linkSteamAccountToProfile(
                any(),
                anyString(),
                any(),
                any(),
                any(),
                anyString());
    }

    @Test
    void completeCallbackRejectsTamperedClaimedIdBeforeUpsert() {
        MultiValueMap<String, String> params = validCallbackParams("state-token");
        params.set("openid.claimed_id", "https://attacker.example/openid/id/" + STEAM_ID);
        params.set("openid.identity", "https://attacker.example/openid/id/" + STEAM_ID);
        when(loginStateRepository.consume(anyString())).thenReturn(Optional.of(stateContext()));

        assertThatThrownBy(() -> steamAuthService.completeCallback(params))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Steam OpenID claimed id is invalid.");

        verify(openIdClient, never()).verifyAuthentication(params);
        verify(profileRepository, never()).upsertSteamProfile(
                anyString(),
                any(),
                any(),
                any(),
                any(),
                any(),
                anyString());
        verify(profileRepository, never()).linkSteamAccountToProfile(
                any(),
                anyString(),
                any(),
                any(),
                any(),
                anyString());
    }

    @Test
    void completeCallbackRejectsMissingState() {
        assertThatThrownBy(() -> steamAuthService.completeCallback(new LinkedMultiValueMap<>()))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Missing Steam OpenID parameter: state.");
    }

    private static MultiValueMap<String, String> validCallbackParams(String state) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("state", state);
        params.add("openid.mode", "id_res");
        params.add("openid.ns", "http://specs.openid.net/auth/2.0");
        params.add("openid.op_endpoint", "https://steamcommunity.com/openid/login");
        params.add("openid.claimed_id", CLAIMED_ID);
        params.add("openid.identity", CLAIMED_ID);
        params.add("openid.return_to", "http://localhost:8080/api/auth/steam/callback?state=" + state);
        params.add("openid.response_nonce", "2026-05-12T00:00:00Zabcdef");
        params.add("openid.assoc_handle", "1234567890");
        params.add("openid.signed", "signed,op_endpoint,claimed_id,identity,return_to,response_nonce,assoc_handle");
        params.add("openid.sig", "signature");
        return params;
    }

    private static SteamLoginStateContext stateContext() {
        return new SteamLoginStateContext(
                STATE_ID,
                "http://localhost:3000/auth/steam/callback",
                PROFILE_ID,
                AUTH_USER_ID,
                "127.0.0.1",
                "JUnit");
    }

    private static SteamAuthProperties properties(String frontendRedirectUrl) {
        return new SteamAuthProperties(
                "https://steamcommunity.com/openid/login",
                "http://localhost:8080",
                "http://localhost:8080/api/auth/steam/callback",
                "steam-api-key",
                "https://api.steampowered.com/ISteamUser/GetPlayerSummaries/v0002/",
                frontendRedirectUrl,
                java.time.Duration.ofMinutes(10));
    }

    private static SecureRandom fixedRandom() {
        return new SecureRandom() {
            @Override
            public void nextBytes(byte[] bytes) {
                Arrays.fill(bytes, (byte) 1);
            }
        };
    }

    private static SupabasePrincipal principal() {
        return new SupabasePrincipal(
                AUTH_USER_ID,
                "player@example.com",
                Optional.of(authenticatedProfile()),
                null);
    }

    private static AuthenticatedProfile authenticatedProfile() {
        return new AuthenticatedProfile(PROFILE_ID, AUTH_USER_ID, "player", ProfileRole.PLAYER);
    }
}
