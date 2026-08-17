package si.um.feri.dotaops.backend.auth.domain;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthenticatedActorTest {

    private static final UUID AUTH_USER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PROFILE_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");

    @Test
    void globalRolePredicatesAreNonhierarchical() {
        AuthenticatedActor admin = actor(ProfileRole.ADMIN);
        AuthenticatedActor organizer = actor(ProfileRole.ORGANIZER);
        AuthenticatedActor player = actor(ProfileRole.PLAYER);

        assertThat(admin.isAdmin()).isTrue();
        assertThat(admin.isOrganizer()).isFalse();
        assertThat(admin.isPlayer()).isFalse();
        assertThat(organizer.isAdmin()).isFalse();
        assertThat(organizer.isOrganizer()).isTrue();
        assertThat(organizer.isPlayer()).isFalse();
        assertThat(player.isAdmin()).isFalse();
        assertThat(player.isOrganizer()).isFalse();
        assertThat(player.isPlayer()).isTrue();
    }

    private static AuthenticatedActor actor(ProfileRole role) {
        return new AuthenticatedActor(AUTH_USER_ID, PROFILE_ID, "player@example.test", null, role);
    }
}
