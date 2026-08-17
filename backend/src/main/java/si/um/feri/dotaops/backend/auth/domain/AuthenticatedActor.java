package si.um.feri.dotaops.backend.auth.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

import si.um.feri.dotaops.backend.auth.service.SupabasePrincipal;

public record AuthenticatedActor(
        UUID authUserId,
        UUID profileId,
        String email,
        String steamId,
        ProfileRole role
) {

    public AuthenticatedActor {
        role = role == null ? ProfileRole.VISITOR : role;
    }

    public static AuthenticatedActor from(SupabasePrincipal principal) {
        return new AuthenticatedActor(
                principal.authUserId(),
                principal.profile()
                        .map(AuthenticatedProfile::profileId)
                        .orElse(null),
                principal.email(),
                principal.steamId(),
                principal.role());
    }

    public Optional<UUID> optionalAuthUserId() {
        return Optional.ofNullable(authUserId);
    }

    public Optional<UUID> optionalProfileId() {
        return Optional.ofNullable(profileId);
    }

    public Optional<String> optionalSteamId() {
        return Optional.ofNullable(steamId);
    }

    public UUID requireProfileId() {
        return optionalProfileId()
                .orElseThrow(() -> new AccessDeniedException("DotaOps profile is required."));
    }

    public UUID requireAuthUserId() {
        return optionalAuthUserId()
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException(
                        "Authenticated Supabase user is required."));
    }

    public boolean isAdmin() {
        return role == ProfileRole.ADMIN;
    }

    public boolean isOrganizer() {
        return role == ProfileRole.ORGANIZER;
    }

    public boolean isPlayer() {
        return role == ProfileRole.PLAYER;
    }
}
