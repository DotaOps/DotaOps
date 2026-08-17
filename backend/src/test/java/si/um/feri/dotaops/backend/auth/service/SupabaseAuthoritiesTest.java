package si.um.feri.dotaops.backend.auth.service;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import si.um.feri.dotaops.backend.auth.domain.AuthenticatedProfile;
import si.um.feri.dotaops.backend.auth.domain.ProfileRole;

import static org.assertj.core.api.Assertions.assertThat;

class SupabaseAuthoritiesTest {

    private static final UUID AUTH_USER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PROFILE_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");

    @Test
    void playerGetsAuthenticatedAndPlayerAuthorities() {
        Set<String> authorities = authoritiesFor(ProfileRole.PLAYER);

        assertThat(authorities).containsExactlyInAnyOrder("ROLE_AUTHENTICATED", "ROLE_PLAYER");
    }

    @Test
    void adminGetsOnlyAuthenticatedAndAdminAuthorities() {
        Set<String> authorities = authoritiesFor(ProfileRole.ADMIN);

        assertThat(authorities).containsExactlyInAnyOrder(
                "ROLE_AUTHENTICATED",
                "ROLE_ADMIN");
    }

    @Test
    void legacyCaptainDatabaseRoleIsTreatedAsPlayer() {
        assertThat(ProfileRole.fromDatabaseValue("captain")).isEqualTo(ProfileRole.PLAYER);
    }

    private Set<String> authoritiesFor(ProfileRole role) {
        return SupabaseAuthorities.from(Optional.of(new AuthenticatedProfile(
                        PROFILE_ID,
                        AUTH_USER_ID,
                        "Test",
                        role)))
                .stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }
}
