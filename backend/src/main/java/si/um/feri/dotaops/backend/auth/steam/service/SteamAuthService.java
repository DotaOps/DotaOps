package si.um.feri.dotaops.backend.auth.steam.service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import si.um.feri.dotaops.backend.auth.service.CurrentUserProvider;
import si.um.feri.dotaops.backend.auth.service.SupabasePrincipal;
import si.um.feri.dotaops.backend.auth.steam.domain.SteamAuthResult;
import si.um.feri.dotaops.backend.auth.steam.domain.SteamLoginStateContext;
import si.um.feri.dotaops.backend.auth.steam.domain.SteamPlayerSummary;
import si.um.feri.dotaops.backend.auth.steam.domain.SteamProfileUpsertResult;
import si.um.feri.dotaops.backend.auth.steam.repository.SteamLoginStateRepository;
import si.um.feri.dotaops.backend.auth.steam.repository.SteamProfileRepository;
import si.um.feri.dotaops.backend.common.error.BadRequestException;
import si.um.feri.dotaops.backend.common.error.ConflictException;
import si.um.feri.dotaops.backend.common.security.ClientIpAddressResolver;
import si.um.feri.dotaops.backend.common.security.RequestRateLimiter;
import si.um.feri.dotaops.backend.config.properties.SteamAuthProperties;
import si.um.feri.dotaops.backend.profile.service.SteamProfileBootstrapService;

@Service
public class SteamAuthService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SteamAuthService.class);

    private static final String OPENID_NS = "http://specs.openid.net/auth/2.0";
    private static final String IDENTIFIER_SELECT = "http://specs.openid.net/auth/2.0/identifier_select";
    private static final Pattern STEAM_CLAIMED_ID_PATTERN = Pattern.compile(
            "^https?://steamcommunity\\.com/openid/id/([0-9]{17})$");

    private final SteamAuthProperties properties;
    private final SteamLoginStateRepository loginStateRepository;
    private final SteamProfileRepository profileRepository;
    private final SteamOpenIdClient openIdClient;
    private final CurrentUserProvider currentUserProvider;
    private final SteamProfileBootstrapService profileBootstrapService;
    private final ClientIpAddressResolver clientIpAddressResolver;
    private final RequestRateLimiter requestRateLimiter;
    private final SecureRandom secureRandom;
    private final Clock clock;

    @Autowired
    public SteamAuthService(
            SteamAuthProperties properties,
            SteamLoginStateRepository loginStateRepository,
            SteamProfileRepository profileRepository,
            SteamOpenIdClient openIdClient,
            CurrentUserProvider currentUserProvider,
            SteamProfileBootstrapService profileBootstrapService,
            ClientIpAddressResolver clientIpAddressResolver,
            RequestRateLimiter requestRateLimiter
    ) {
        this(
                properties,
                loginStateRepository,
                profileRepository,
                openIdClient,
                currentUserProvider,
                profileBootstrapService,
                clientIpAddressResolver,
                requestRateLimiter,
                new SecureRandom(),
                Clock.systemUTC());
    }

    SteamAuthService(
            SteamAuthProperties properties,
            SteamLoginStateRepository loginStateRepository,
            SteamProfileRepository profileRepository,
            SteamOpenIdClient openIdClient,
            CurrentUserProvider currentUserProvider,
            SteamProfileBootstrapService profileBootstrapService,
            ClientIpAddressResolver clientIpAddressResolver,
            RequestRateLimiter requestRateLimiter,
            SecureRandom secureRandom,
            Clock clock
    ) {
        this.properties = properties;
        this.loginStateRepository = loginStateRepository;
        this.profileRepository = profileRepository;
        this.openIdClient = openIdClient;
        this.currentUserProvider = currentUserProvider;
        this.profileBootstrapService = profileBootstrapService;
        this.clientIpAddressResolver = clientIpAddressResolver;
        this.requestRateLimiter = requestRateLimiter;
        this.secureRandom = secureRandom;
        this.clock = clock;
    }

    @Transactional
    public URI beginLogin(HttpServletRequest request) {
        String clientIp = clientIpAddressResolver.resolve(request);
        requestRateLimiter.checkSteamLogin(clientIp);

        String rawState = generateState();
        String stateHash = hashState(rawState);
        URI returnTo = returnUrlWithState(rawState);
        Optional<SupabasePrincipal> currentUser = currentUserProvider.currentUser();
        UUID profileId = currentUser.flatMap(SupabasePrincipal::profile)
                .map(profile -> profile.profileId())
                .orElse(null);
        UUID authUserId = currentUser.map(SupabasePrincipal::authUserId).orElse(null);

        loginStateRepository.create(
                stateHash,
                normalizeOptional(properties.frontendRedirectUrl()),
                profileId,
                authUserId,
                clientIp,
                normalizeOptional(request.getHeader("User-Agent")),
                OffsetDateTime.now(clock).plus(properties.stateTtl()));

        return UriComponentsBuilder.fromUriString(properties.openidEndpoint())
                .queryParam("openid.ns", OPENID_NS)
                .queryParam("openid.mode", "checkid_setup")
                .queryParam("openid.return_to", returnTo.toString())
                .queryParam("openid.realm", properties.realm())
                .queryParam("openid.identity", IDENTIFIER_SELECT)
                .queryParam("openid.claimed_id", IDENTIFIER_SELECT)
                .build()
                .encode()
                .toUri();
    }

    @Transactional
    public URI beginProfileLink(HttpServletRequest request) {
        currentUserProvider.requireProfile();
        return beginLogin(request);
    }

    @Transactional
    public SteamAuthResult completeCallback(MultiValueMap<String, String> callbackParams) {
        String rawState = requiredParam(callbackParams, "state");
        SteamLoginStateContext state = loginStateRepository.consume(hashState(rawState))
                .orElseThrow(() -> new BadRequestException("Steam login state is invalid or expired."));

        validateOpenIdCallbackShape(callbackParams, rawState);
        if (!openIdClient.verifyAuthentication(callbackParams)) {
            throw new BadCredentialsException("Steam OpenID assertion is invalid.");
        }

        String claimedId = requiredParam(callbackParams, "openid.claimed_id");
        String steamId = extractSteamId(claimedId);
        String profileUrlFallback = "https://steamcommunity.com/profiles/" + steamId + "/";
        SteamPlayerSummary summary = openIdClient.fetchPlayerSummary(steamId)
                .orElse(new SteamPlayerSummary(steamId, null, null, profileUrlFallback));
        String profileUrl = StringUtils.hasText(summary.profileUrl()) ? summary.profileUrl() : profileUrlFallback;
        String personaName = summary.personaName();

        SteamProfileUpsertResult upsertResult = linkOrUpsertSteamProfile(
                state,
                steamId,
                personaName,
                summary.avatarUrl(),
                profileUrl,
                claimedId);

        scheduleProfileBootstrap(upsertResult.profileId(), steamId, summary);

        return new SteamAuthResult(
                steamId,
                upsertResult.profileId(),
                upsertResult.externalAccountId(),
                upsertResult.newProfile(),
                upsertResult.newExternalAccount(),
                personaName,
                summary.avatarUrl(),
                profileUrl,
                buildFrontendRedirect(state.returnTo(), steamId, upsertResult));
    }

    private SteamProfileUpsertResult linkOrUpsertSteamProfile(
            SteamLoginStateContext state,
            String steamId,
            String personaName,
            String avatarUrl,
            String profileUrl,
            String claimedId
    ) {
        if (state.profileId() != null) {
            UUID externalAccountId;
            try {
                externalAccountId = profileRepository.linkSteamAccountToProfile(
                        state.profileId(),
                        steamId,
                        personaName,
                        avatarUrl,
                        profileUrl,
                        claimedId);
            } catch (DataAccessException exception) {
                if (isSteamProfileOwnershipConflict(exception)) {
                    throw new ConflictException(
                            "This Steam account is already connected to another DotaOps profile.");
                }

                throw exception;
            }

            return new SteamProfileUpsertResult(state.profileId(), externalAccountId, false, false);
        }

        return profileRepository.upsertSteamProfile(
                steamId,
                state.authUserId(),
                personaName,
                personaName,
                avatarUrl,
                profileUrl,
                claimedId);
    }

    private boolean isSteamProfileOwnershipConflict(DataAccessException exception) {
        String message = exception.getMostSpecificCause().getMessage();
        return message != null && message.contains("Steam account is already linked to a different profile.");
    }

    private void scheduleProfileBootstrap(UUID profileId, String steamId, SteamPlayerSummary summary) {
        try {
            profileBootstrapService.bootstrapAfterSteamLogin(profileId, steamId, summary);
        } catch (RuntimeException exception) {
            LOGGER.warn("Steam/OpenDota profile bootstrap could not be scheduled for profile {}.", profileId, exception);
        }
    }

    private void validateOpenIdCallbackShape(MultiValueMap<String, String> callbackParams, String rawState) {
        String mode = requiredParam(callbackParams, "openid.mode");
        if ("cancel".equals(mode)) {
            throw new BadRequestException("Steam login was cancelled.");
        }

        if (!"id_res".equals(mode)) {
            throw new BadRequestException("Steam OpenID callback mode is invalid.");
        }

        String claimedId = requiredParam(callbackParams, "openid.claimed_id");
        String identity = requiredParam(callbackParams, "openid.identity");
        String returnTo = requiredParam(callbackParams, "openid.return_to");

        if (!claimedId.equals(identity)) {
            throw new BadRequestException("Steam OpenID claimed identity mismatch.");
        }

        extractSteamId(claimedId);

        String expectedReturnTo = returnUrlWithState(rawState).toString();
        if (!expectedReturnTo.equals(returnTo)) {
            throw new BadRequestException("Steam OpenID return URL is invalid.");
        }
    }

    private URI buildFrontendRedirect(
            String returnTo,
            String steamId,
            SteamProfileUpsertResult upsertResult
    ) {
        if (!StringUtils.hasText(returnTo)) {
            return null;
        }

        return UriComponentsBuilder.fromUriString(returnTo)
                .queryParam("steamLogin", "success")
                .queryParam("steamId", steamId)
                .queryParam("profileId", upsertResult.profileId())
                .queryParam("externalAccountId", upsertResult.externalAccountId())
                .queryParam("newProfile", upsertResult.newProfile())
                .queryParam("newExternalAccount", upsertResult.newExternalAccount())
                .build()
                .encode()
                .toUri();
    }

    private URI returnUrlWithState(String rawState) {
        return UriComponentsBuilder.fromUriString(properties.returnUrl())
                .queryParam("state", rawState)
                .build()
                .encode()
                .toUri();
    }

    private String extractSteamId(String claimedId) {
        Matcher matcher = STEAM_CLAIMED_ID_PATTERN.matcher(claimedId);
        if (!matcher.matches()) {
            throw new BadRequestException("Steam OpenID claimed id is invalid.");
        }

        return matcher.group(1);
    }

    private String requiredParam(MultiValueMap<String, String> params, String name) {
        String value = params.getFirst(name);
        if (!StringUtils.hasText(value)) {
            throw new BadRequestException("Missing Steam OpenID parameter: " + name + ".");
        }

        return value.trim();
    }

    private String generateState() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);

        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashState(String rawState) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawState.getBytes(StandardCharsets.UTF_8));

            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not hash Steam login state.", exception);
        }
    }

    private String normalizeOptional(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        return value.trim();
    }
}
