package si.um.feri.dotaops.backend.tournament.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import si.um.feri.dotaops.backend.auth.domain.AuthenticatedActor;
import si.um.feri.dotaops.backend.auth.domain.ProfileRole;
import si.um.feri.dotaops.backend.auth.service.CurrentUserProvider;
import si.um.feri.dotaops.backend.common.error.BadRequestException;
import si.um.feri.dotaops.backend.common.error.ConflictException;
import si.um.feri.dotaops.backend.common.security.DatabaseActorContext;
import si.um.feri.dotaops.backend.tournament.domain.BracketMatch;
import si.um.feri.dotaops.backend.tournament.domain.BracketParticipant;
import si.um.feri.dotaops.backend.tournament.domain.MatchSlotSourceType;
import si.um.feri.dotaops.backend.tournament.domain.Tournament;
import si.um.feri.dotaops.backend.tournament.domain.TournamentFormat;
import si.um.feri.dotaops.backend.tournament.domain.TournamentMatch;
import si.um.feri.dotaops.backend.tournament.domain.TournamentSettings;
import si.um.feri.dotaops.backend.tournament.domain.TournamentStatus;
import si.um.feri.dotaops.backend.tournament.dto.GenerateBracketRequest;
import si.um.feri.dotaops.backend.tournament.repository.CreateBracketMatchCommand;
import si.um.feri.dotaops.backend.tournament.repository.CreateMatchSlotCommand;
import si.um.feri.dotaops.backend.tournament.repository.TournamentBracketRepository;
import si.um.feri.dotaops.backend.tournament.repository.TournamentRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TournamentBracketServiceTest {

    private static final UUID AUTH_USER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID ORGANIZER_PROFILE_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID PLAYER_PROFILE_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID TOURNAMENT_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-06-01T18:00:00Z");

    private final TournamentBracketRepository bracketRepository = mock(TournamentBracketRepository.class);
    private final TournamentRepository tournamentRepository = mock(TournamentRepository.class);
    private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
    private final DatabaseActorContext databaseActorContext = mock(DatabaseActorContext.class);
    private final MatchAdvancementService matchAdvancementService = mock(MatchAdvancementService.class);
    private final TournamentBracketService service = new TournamentBracketService(
            bracketRepository,
            tournamentRepository,
            currentUserProvider,
            databaseActorContext,
            matchAdvancementService);

    private final List<CreateBracketMatchCommand> createdMatches = new ArrayList<>();
    private final List<CreateMatchSlotCommand> createdSlots = new ArrayList<>();
    private final List<BracketMatch> returnedMatches = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(currentUserProvider.requireActor()).thenReturn(actor(ORGANIZER_PROFILE_ID, ProfileRole.ORGANIZER));
        when(tournamentRepository.findById(TOURNAMENT_ID)).thenReturn(Optional.of(tournament()));
        when(tournamentRepository.canManage(TOURNAMENT_ID, ORGANIZER_PROFILE_ID, false)).thenReturn(true);
        when(bracketRepository.bracketExists(TOURNAMENT_ID, "Playoffs")).thenReturn(false);
        when(bracketRepository.findBracket(TOURNAMENT_ID, "Playoffs")).thenAnswer(invocation -> returnedMatches);
        doAnswer(invocation -> {
            CreateBracketMatchCommand command = invocation.getArgument(0);
            UUID matchId = new UUID(0L, createdMatches.size() + 1L);
            createdMatches.add(command);
            BracketMatch match = new BracketMatch(
                    matchId,
                    command.tournamentId(),
                    null,
                    command.roundNumber(),
                    command.bracketPosition(),
                    command.stageName(),
                    command.roundName(),
                    command.status(),
                    command.teamAId(),
                    null,
                    command.teamBId(),
                    null,
                    0,
                    0,
                    command.winnerTeamId(),
                    null,
                    command.bestOf(),
                    null,
                    null,
                    command.finishedAt(),
                    null,
                    null,
                    List.of());
            returnedMatches.add(match);
            return match;
        }).when(bracketRepository).createMatch(any(CreateBracketMatchCommand.class));
        doAnswer(invocation -> {
            createdSlots.add(invocation.getArgument(0));
            return null;
        }).when(bracketRepository).createSlot(any(CreateMatchSlotCommand.class));
    }

    @Test
    void fourTeamBracketCreatesSeedOneVsFourAndTwoVsThree() {
        when(bracketRepository.findApprovedParticipants(TOURNAMENT_ID)).thenReturn(participants(4));

        var response = service.generateBracket(TOURNAMENT_ID, new GenerateBracketRequest(null, false));

        assertThat(response.bracketSize()).isEqualTo(4);
        assertThat(createdMatches).hasSize(3);
        assertThat(createdMatches.get(0).roundName()).isEqualTo("Semifinal");
        assertThat(createdMatches.get(2).roundName()).isEqualTo("Final");
        assertSeedPair(0, 1, 4);
        assertSeedPair(2, 2, 3);
        assertWinnerSlot(4, 1L);
        assertWinnerSlot(5, 2L);
    }

    @Test
    void eightTeamBracketUsesStandardSeedPlacement() {
        when(bracketRepository.findApprovedParticipants(TOURNAMENT_ID)).thenReturn(participants(8));

        service.generateBracket(TOURNAMENT_ID, new GenerateBracketRequest("Playoffs", false));

        assertThat(createdMatches).hasSize(7);
        assertThat(createdMatches.get(0).roundName()).isEqualTo("Quarterfinal");
        assertSeedPair(0, 1, 8);
        assertSeedPair(2, 4, 5);
        assertSeedPair(4, 2, 7);
        assertSeedPair(6, 3, 6);
        assertThat(createdMatches.stream().filter(match -> match.roundNumber() == 2)).hasSize(2);
        assertThat(createdMatches.stream().filter(match -> match.roundNumber() == 3)).hasSize(1);
    }

    @Test
    void sixTeamBracketCreatesEightSlotStructureWithTwoByes() {
        when(bracketRepository.findApprovedParticipants(TOURNAMENT_ID)).thenReturn(participants(6));

        service.generateBracket(TOURNAMENT_ID, new GenerateBracketRequest("Playoffs", false));

        assertThat(createdMatches).hasSize(7);
        assertSeedByePair(0, 1);
        assertSeedPair(2, 4, 5);
        assertSeedByePair(4, 2);
        assertSeedPair(6, 3, 6);
        assertThat(createdSlots.stream().filter(slot -> slot.sourceType() == MatchSlotSourceType.BYE)).hasSize(2);
        assertThat(createdMatches.get(0).status()).isEqualTo("finished");
        assertThat(createdMatches.get(0).winnerTeamId()).isEqualTo(teamId(1));
        assertThat(createdMatches.get(2).winnerTeamId()).isEqualTo(teamId(2));
        verify(matchAdvancementService, times(2))
                .advanceAfterResult(any(TournamentMatch.class), any(TournamentMatch.class), eq(ORGANIZER_PROFILE_ID));
    }

    @Test
    void everyGeneratedMatchHasAtMostTwoSlots() {
        when(bracketRepository.findApprovedParticipants(TOURNAMENT_ID)).thenReturn(participants(8));

        service.generateBracket(TOURNAMENT_ID, new GenerateBracketRequest("Playoffs", false));

        for (int index = 0; index < createdMatches.size(); index++) {
            UUID matchId = new UUID(0L, index + 1L);
            long matchSlots = createdSlots.stream()
                    .filter(slot -> slot.matchId().equals(matchId))
                    .count();
            assertThat(matchSlots).isEqualTo(2);
        }
    }

    @Test
    void repeatedGenerationWithoutForceIsRejected() {
        when(bracketRepository.bracketExists(TOURNAMENT_ID, "Playoffs")).thenReturn(true);

        assertThatThrownBy(() -> service.generateBracket(TOURNAMENT_ID, new GenerateBracketRequest("Playoffs", false)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Bracket already exists for this tournament stage.");
        verify(bracketRepository, never()).deleteBracket(TOURNAMENT_ID, "Playoffs");
        verify(bracketRepository, never()).createMatch(any(CreateBracketMatchCommand.class));
    }

    @Test
    void forceRegenerateDeletesExistingBracketBeforeRebuild() {
        when(bracketRepository.bracketExists(TOURNAMENT_ID, "Playoffs")).thenReturn(true);
        when(bracketRepository.hasBlockingMatchesForRegeneration(TOURNAMENT_ID, "Playoffs")).thenReturn(false);
        when(bracketRepository.findApprovedParticipants(TOURNAMENT_ID)).thenReturn(participants(4));

        service.generateBracket(TOURNAMENT_ID, new GenerateBracketRequest("Playoffs", true));

        verify(bracketRepository).deleteBracket(TOURNAMENT_ID, "Playoffs");
        assertThat(createdMatches).hasSize(3);
    }

    @Test
    void blockingLiveOrFinishedMatchesPreventForceRegenerate() {
        when(bracketRepository.bracketExists(TOURNAMENT_ID, "Playoffs")).thenReturn(true);
        when(bracketRepository.hasBlockingMatchesForRegeneration(TOURNAMENT_ID, "Playoffs")).thenReturn(true);

        assertThatThrownBy(() -> service.generateBracket(TOURNAMENT_ID, new GenerateBracketRequest("Playoffs", true)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Bracket cannot be regenerated after non-bye matches are live or finished.");
        verify(bracketRepository, never()).deleteBracket(TOURNAMENT_ID, "Playoffs");
    }

    @Test
    void tournamentNeedsAtLeastTwoApprovedTeams() {
        when(bracketRepository.findApprovedParticipants(TOURNAMENT_ID)).thenReturn(participants(1));

        assertThatThrownBy(() -> service.generateBracket(TOURNAMENT_ID, new GenerateBracketRequest("Playoffs", false)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Tournament must have at least 2 approved teams before generating a bracket.");
    }

    @Test
    void duplicateSeedsAreRejected() {
        when(bracketRepository.findApprovedParticipants(TOURNAMENT_ID)).thenReturn(List.of(
                participant(1, 1),
                participant(2, 1)));

        assertThatThrownBy(() -> service.generateBracket(TOURNAMENT_ID, new GenerateBracketRequest("Playoffs", false)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Duplicate registration seeds make deterministic bracket generation impossible.");
    }

    @Test
    void nonOrganizerCannotGenerateBracket() {
        when(currentUserProvider.requireActor()).thenReturn(actor(PLAYER_PROFILE_ID, ProfileRole.PLAYER));
        when(tournamentRepository.canManage(TOURNAMENT_ID, PLAYER_PROFILE_ID, false)).thenReturn(true);

        assertThatThrownBy(() -> service.generateBracket(TOURNAMENT_ID, new GenerateBracketRequest("Playoffs", false)))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Only the tournament owner, tournament organizers, or admins can generate brackets.");
        verify(bracketRepository, never()).findApprovedParticipants(TOURNAMENT_ID);
    }

    private void assertSeedPair(int slotStartIndex, int leftSeed, int rightSeed) {
        CreateMatchSlotCommand left = createdSlots.get(slotStartIndex);
        CreateMatchSlotCommand right = createdSlots.get(slotStartIndex + 1);

        assertThat(left.sourceType()).isEqualTo(MatchSlotSourceType.SEED);
        assertThat(right.sourceType()).isEqualTo(MatchSlotSourceType.SEED);
        assertThat(left.seedNumber()).isEqualTo(leftSeed);
        assertThat(right.seedNumber()).isEqualTo(rightSeed);
        assertThat(left.teamId()).isEqualTo(teamId(leftSeed));
        assertThat(right.teamId()).isEqualTo(teamId(rightSeed));
    }

    private void assertSeedByePair(int slotStartIndex, int seed) {
        CreateMatchSlotCommand left = createdSlots.get(slotStartIndex);
        CreateMatchSlotCommand right = createdSlots.get(slotStartIndex + 1);

        assertThat(left.sourceType()).isEqualTo(MatchSlotSourceType.SEED);
        assertThat(left.seedNumber()).isEqualTo(seed);
        assertThat(left.teamId()).isEqualTo(teamId(seed));
        assertThat(right.sourceType()).isEqualTo(MatchSlotSourceType.BYE);
        assertThat(right.teamId()).isNull();
    }

    private void assertWinnerSlot(int slotIndex, long sourceMatchIdSuffix) {
        CreateMatchSlotCommand slot = createdSlots.get(slotIndex);

        assertThat(slot.sourceType()).isEqualTo(MatchSlotSourceType.WINNER);
        assertThat(slot.sourceMatchId()).isEqualTo(new UUID(0L, sourceMatchIdSuffix));
    }

    private static List<BracketParticipant> participants(int count) {
        List<BracketParticipant> participants = new ArrayList<>();
        for (int seed = 1; seed <= count; seed++) {
            participants.add(participant(seed, seed));
        }

        return participants;
    }

    private static BracketParticipant participant(int index, Integer seedNumber) {
        return new BracketParticipant(
                registrationId(index),
                teamId(index),
                "Team " + index,
                seedNumber,
                NOW.plusMinutes(index));
    }

    private static UUID registrationId(int index) {
        return new UUID(1L, index);
    }

    private static UUID teamId(int index) {
        return new UUID(2L, index);
    }

    private static AuthenticatedActor actor(UUID profileId, ProfileRole role) {
        return new AuthenticatedActor(
                AUTH_USER_ID,
                profileId,
                "user@example.test",
                null,
                role);
    }

    private static Tournament tournament() {
        return new Tournament(
                TOURNAMENT_ID,
                "bracket-cup",
                "Bracket Cup",
                TournamentStatus.PUBLISHED,
                TournamentFormat.SINGLE_ELIMINATION,
                ORGANIZER_PROFILE_ID,
                "Organizer",
                "Qualifier",
                "Rules",
                "TBD",
                8,
                NOW.plusDays(3),
                NOW.plusDays(4),
                NOW.minusDays(2),
                NOW.plusDays(1),
                true,
                AUTH_USER_ID,
                "UTC",
                null,
                null,
                NOW.minusDays(1),
                new TournamentSettings(8, 2, 5, 1, TournamentFormat.SINGLE_ELIMINATION, false, true),
                0,
                NOW.minusDays(5),
                NOW.minusDays(1));
    }
}
