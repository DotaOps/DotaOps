package si.um.feri.dotaops.backend.analytics.service;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import si.um.feri.dotaops.backend.analytics.repository.AnalyticsLookupRepository;
import si.um.feri.dotaops.backend.auth.domain.AuthenticatedActor;
import si.um.feri.dotaops.backend.auth.domain.ProfileRole;
import si.um.feri.dotaops.backend.auth.service.CurrentUserProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AnalyticsLookupServiceTest {

    private static final UUID AUTH_USER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PROFILE_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID TEAM_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID HERO_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");

    private final AnalyticsLookupRepository lookupRepository = mock(AnalyticsLookupRepository.class);
    private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
    private final AnalyticsLookupService service = new AnalyticsLookupService(lookupRepository, currentUserProvider);

    @Test
    void organizerTournamentLookupIsScopedToManageableTournaments() {
        when(currentUserProvider.requireActor()).thenReturn(actor(ProfileRole.ORGANIZER));
        when(lookupRepository.findManageableTournaments(PROFILE_ID, false, 10))
                .thenReturn(List.of(new AnalyticsLookupRepository.TournamentLookup(
                        UUID.randomUUID(),
                        "Mid Wars",
                        "published")));

        assertThat(service.organizerTournaments(10)).hasSize(1);

        verify(lookupRepository).findManageableTournaments(PROFILE_ID, false, 10);
    }

    @Test
    void organizerCannotUseCurrentPlayerTeamsLookup() {
        when(currentUserProvider.requireActor()).thenReturn(actor(ProfileRole.ORGANIZER));

        assertThatThrownBy(() -> service.currentPlayerTeams(10))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Player profile role is required.");
    }

    @Test
    void teamPlayersRequireTeamMembershipOrOrganizerScope() {
        when(currentUserProvider.requireActor()).thenReturn(actor(ProfileRole.PLAYER));
        when(lookupRepository.isActiveTeamMember(TEAM_ID, PROFILE_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.teamPlayers(TEAM_ID, 10))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Only team members, relevant organizers or admins can list team players.");
    }

    @Test
    void heroesLookupReturnsPublicReferenceDataWithoutCurrentUser() {
        when(lookupRepository.findHeroes(10)).thenReturn(List.of(new AnalyticsLookupRepository.HeroLookup(
                HERO_ID,
                1,
                "npc_dota_hero_antimage",
                "Anti-Mage",
                null,
                null)));

        var response = service.heroes(10);

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().heroId()).isEqualTo(HERO_ID);
    }

    private AuthenticatedActor actor(ProfileRole role) {
        return new AuthenticatedActor(AUTH_USER_ID, PROFILE_ID, "profile@example.test", null, role);
    }
}
