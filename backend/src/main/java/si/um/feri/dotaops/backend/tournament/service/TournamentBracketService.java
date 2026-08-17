package si.um.feri.dotaops.backend.tournament.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.um.feri.dotaops.backend.auth.domain.AuthenticatedActor;
import si.um.feri.dotaops.backend.auth.domain.ProfileRole;
import si.um.feri.dotaops.backend.auth.service.CurrentUserProvider;
import si.um.feri.dotaops.backend.common.error.BadRequestException;
import si.um.feri.dotaops.backend.common.error.ConflictException;
import si.um.feri.dotaops.backend.common.error.ResourceNotFoundException;
import si.um.feri.dotaops.backend.common.security.DatabaseActorContext;
import si.um.feri.dotaops.backend.tournament.domain.BracketMatch;
import si.um.feri.dotaops.backend.tournament.domain.BracketParticipant;
import si.um.feri.dotaops.backend.tournament.domain.MatchSlotName;
import si.um.feri.dotaops.backend.tournament.domain.MatchSlotSourceType;
import si.um.feri.dotaops.backend.tournament.domain.MatchStatus;
import si.um.feri.dotaops.backend.tournament.domain.Tournament;
import si.um.feri.dotaops.backend.tournament.domain.TournamentMatch;
import si.um.feri.dotaops.backend.tournament.dto.BracketResponse;
import si.um.feri.dotaops.backend.tournament.dto.GenerateBracketRequest;
import si.um.feri.dotaops.backend.tournament.repository.CreateBracketMatchCommand;
import si.um.feri.dotaops.backend.tournament.repository.CreateMatchSlotCommand;
import si.um.feri.dotaops.backend.tournament.repository.TournamentBracketRepository;
import si.um.feri.dotaops.backend.tournament.repository.TournamentRepository;

@Service
public class TournamentBracketService {

    private static final String DEFAULT_STAGE_NAME = "Playoffs";
    private static final String BRACKET_TYPE = "single_elimination";
    private static final int MAX_STAGE_NAME_LENGTH = 80;

    private final TournamentBracketRepository bracketRepository;
    private final TournamentRepository tournamentRepository;
    private final CurrentUserProvider currentUserProvider;
    private final DatabaseActorContext databaseActorContext;
    private final MatchAdvancementService matchAdvancementService;

    public TournamentBracketService(
            TournamentBracketRepository bracketRepository,
            TournamentRepository tournamentRepository,
            CurrentUserProvider currentUserProvider,
            DatabaseActorContext databaseActorContext,
            MatchAdvancementService matchAdvancementService
    ) {
        this.bracketRepository = bracketRepository;
        this.tournamentRepository = tournamentRepository;
        this.currentUserProvider = currentUserProvider;
        this.databaseActorContext = databaseActorContext;
        this.matchAdvancementService = matchAdvancementService;
    }

    @Transactional
    public BracketResponse generateBracket(UUID tournamentId, GenerateBracketRequest request) {
        String stageName = normalizeStageName(request == null ? null : request.stageName());
        boolean forceRegenerate = request != null && Boolean.TRUE.equals(request.forceRegenerate());
        AuthenticatedActor actor = currentUserProvider.requireActor();
        Tournament tournament = findTournament(tournamentId);
        ensureCanManage(actor, tournament.id());
        databaseActorContext.apply(actor);

        if (bracketRepository.bracketExists(tournament.id(), stageName)) {
            if (!forceRegenerate) {
                throw new ConflictException("Bracket already exists for this tournament stage.");
            }

            if (bracketRepository.hasBlockingMatchesForRegeneration(tournament.id(), stageName)) {
                throw new ConflictException("Bracket cannot be regenerated after non-bye matches are live or finished.");
            }

            bracketRepository.deleteBracket(tournament.id(), stageName);
        }

        List<SeededParticipant> participants = seededParticipants(bracketRepository.findApprovedParticipants(tournament.id()));
        if (participants.size() < 2) {
            throw new BadRequestException("Tournament must have at least 2 approved teams before generating a bracket.");
        }

        int bracketSize = nextPowerOfTwo(participants.size());
        Map<Integer, SeededParticipant> participantsBySeed = participants.stream()
                .collect(Collectors.toMap(SeededParticipant::effectiveSeed, Function.identity()));
        List<BracketMatch> generatedMatches = createMatches(
                tournament,
                stageName,
                bracketSize,
                participants.size(),
                participantsBySeed);
        advanceGeneratedByeMatches(generatedMatches, actor.profileId());
        generatedMatches = bracketRepository.findBracket(tournament.id(), stageName);

        return BracketResponse.from(tournament.id(), stageName, BRACKET_TYPE, bracketSize, generatedMatches);
    }

    @Transactional(readOnly = true)
    public BracketResponse getPublicBracket(UUID tournamentId, String requestedStageName) {
        String stageName = normalizeStageName(requestedStageName);
        if (!bracketRepository.publicTournamentExists(tournamentId)) {
            throw new ResourceNotFoundException("Tournament", "id", tournamentId);
        }

        List<BracketMatch> matches = bracketRepository.findBracket(tournamentId, stageName);
        return BracketResponse.from(tournamentId, stageName, BRACKET_TYPE, inferBracketSize(matches), matches);
    }

    @Transactional(readOnly = true)
    public BracketResponse getOrganizerBracket(UUID tournamentId, String requestedStageName) {
        String stageName = normalizeStageName(requestedStageName);
        AuthenticatedActor actor = currentUserProvider.requireActor();
        Tournament tournament = findTournament(tournamentId);
        ensureCanManage(actor, tournament.id());

        List<BracketMatch> matches = bracketRepository.findBracket(tournament.id(), stageName);
        return BracketResponse.from(tournament.id(), stageName, BRACKET_TYPE, inferBracketSize(matches), matches);
    }

    private List<BracketMatch> createMatches(
            Tournament tournament,
            String stageName,
            int bracketSize,
            int approvedTeamCount,
            Map<Integer, SeededParticipant> participantsBySeed
    ) {
        List<BracketMatch> allMatches = new ArrayList<>();
        List<BracketMatch> previousRoundMatches = createFirstRoundMatches(
                tournament,
                stageName,
                bracketSize,
                approvedTeamCount,
                participantsBySeed);
        allMatches.addAll(previousRoundMatches);

        int roundNumber = 2;
        while (previousRoundMatches.size() > 1) {
            List<BracketMatch> roundMatches = new ArrayList<>();
            for (int index = 0; index < previousRoundMatches.size(); index += 2) {
                int bracketPosition = (index / 2) + 1;
                BracketMatch leftSource = previousRoundMatches.get(index);
                BracketMatch rightSource = previousRoundMatches.get(index + 1);
                BracketMatch match = bracketRepository.createMatch(new CreateBracketMatchCommand(
                        tournament.id(),
                        stageName,
                        roundName(bracketSize, roundNumber),
                        roundNumber,
                        bracketPosition,
                        "scheduled",
                        tournament.settings().bestOf(),
                        null,
                        null,
                        null,
                        null));

                bracketRepository.createSlot(winnerSlot(match.id(), 1, leftSource.id()));
                bracketRepository.createSlot(winnerSlot(match.id(), 2, rightSource.id()));
                roundMatches.add(match);
            }

            allMatches.addAll(roundMatches);
            previousRoundMatches = roundMatches;
            roundNumber++;
        }

        return bracketRepository.findBracket(tournament.id(), stageName);
    }

    private List<BracketMatch> createFirstRoundMatches(
            Tournament tournament,
            String stageName,
            int bracketSize,
            int approvedTeamCount,
            Map<Integer, SeededParticipant> participantsBySeed
    ) {
        List<Integer> placement = seedPlacement(bracketSize);
        List<BracketMatch> matches = new ArrayList<>();
        for (int index = 0; index < placement.size(); index += 2) {
            SeededParticipant left = participantForSlot(placement.get(index), approvedTeamCount, participantsBySeed);
            SeededParticipant right = participantForSlot(placement.get(index + 1), approvedTeamCount, participantsBySeed);
            UUID teamAId = left == null ? null : left.participant().teamId();
            UUID teamBId = right == null ? null : right.participant().teamId();
            UUID winnerTeamId = byeWinner(left, right);
            boolean byeMatch = winnerTeamId != null;
            int bracketPosition = (index / 2) + 1;

            BracketMatch match = bracketRepository.createMatch(new CreateBracketMatchCommand(
                    tournament.id(),
                    stageName,
                    roundName(bracketSize, 1),
                    1,
                    bracketPosition,
                    byeMatch ? "finished" : "scheduled",
                    tournament.settings().bestOf(),
                    teamAId,
                    teamBId,
                    winnerTeamId,
                    byeMatch ? OffsetDateTime.now(ZoneOffset.UTC) : null));

            bracketRepository.createSlot(seedOrByeSlot(match.id(), 1, left));
            bracketRepository.createSlot(seedOrByeSlot(match.id(), 2, right));
            matches.add(match);
        }

        return matches;
    }

    private SeededParticipant participantForSlot(
            int seed,
            int approvedTeamCount,
            Map<Integer, SeededParticipant> participantsBySeed
    ) {
        if (seed > approvedTeamCount) {
            return null;
        }

        return participantsBySeed.get(seed);
    }

    private UUID byeWinner(SeededParticipant left, SeededParticipant right) {
        if (left != null && right == null) {
            return left.participant().teamId();
        }

        if (left == null && right != null) {
            return right.participant().teamId();
        }

        return null;
    }

    private CreateMatchSlotCommand seedOrByeSlot(UUID matchId, int slotNumber, SeededParticipant participant) {
        MatchSlotName slot = MatchSlotName.fromSlotNumber(slotNumber);
        if (participant == null) {
            return new CreateMatchSlotCommand(
                    matchId,
                    slot,
                    MatchSlotSourceType.BYE,
                    null,
                    null,
                    null,
                    null,
                    "BYE");
        }

        int storedSeed = participant.participant().seedNumber() == null
                ? participant.effectiveSeed()
                : participant.participant().seedNumber();

        return new CreateMatchSlotCommand(
                matchId,
                slot,
                MatchSlotSourceType.SEED,
                participant.participant().teamId(),
                null,
                participant.participant().registrationId(),
                storedSeed,
                "Seed %d".formatted(storedSeed));
    }

    private CreateMatchSlotCommand winnerSlot(UUID matchId, int slotNumber, UUID sourceMatchId) {
        return new CreateMatchSlotCommand(
                matchId,
                MatchSlotName.fromSlotNumber(slotNumber),
                MatchSlotSourceType.WINNER,
                null,
                sourceMatchId,
                null,
                null,
                "Winner of match");
    }

    private List<SeededParticipant> seededParticipants(List<BracketParticipant> participants) {
        HashSet<Integer> seenSeeds = new HashSet<>();
        for (BracketParticipant participant : participants) {
            Integer seedNumber = participant.seedNumber();
            if (seedNumber != null && !seenSeeds.add(seedNumber)) {
                throw new ConflictException("Duplicate registration seeds make deterministic bracket generation impossible.");
            }
        }

        List<SeededParticipant> seeded = new ArrayList<>();
        for (int index = 0; index < participants.size(); index++) {
            seeded.add(new SeededParticipant(participants.get(index), index + 1));
        }

        return seeded;
    }

    private List<Integer> seedPlacement(int bracketSize) {
        if (bracketSize == 1) {
            return List.of(1);
        }

        List<Integer> previous = seedPlacement(bracketSize / 2);
        List<Integer> placement = new ArrayList<>(bracketSize);
        for (Integer seed : previous) {
            placement.add(seed);
            placement.add(bracketSize + 1 - seed);
        }

        return placement;
    }

    private int nextPowerOfTwo(int value) {
        int bracketSize = 1;
        while (bracketSize < value) {
            bracketSize *= 2;
        }

        return bracketSize;
    }

    private String roundName(int bracketSize, int roundNumber) {
        int teamsRemaining = bracketSize >> (roundNumber - 1);
        return switch (teamsRemaining) {
            case 2 -> "Final";
            case 4 -> "Semifinal";
            case 8 -> "Quarterfinal";
            default -> "Round of " + teamsRemaining;
        };
    }

    private int inferBracketSize(List<BracketMatch> matches) {
        if (matches.isEmpty()) {
            return 0;
        }

        long firstRoundMatches = matches.stream()
                .filter(match -> match.roundNumber() == 1)
                .count();

        return (int) firstRoundMatches * 2;
    }

    private void advanceGeneratedByeMatches(List<BracketMatch> matches, UUID actorProfileId) {
        matches.stream()
                .filter(match -> "finished".equals(match.status()))
                .filter(match -> match.winnerTeamId() != null)
                .forEach(match -> {
                    TournamentMatch sourceMatch = toTournamentMatch(match);
                    matchAdvancementService.advanceAfterResult(sourceMatch, sourceMatch, actorProfileId);
                });
    }

    private TournamentMatch toTournamentMatch(BracketMatch match) {
        return new TournamentMatch(
                match.id(),
                match.tournamentId(),
                match.groupId(),
                match.roundNumber(),
                match.bracketPosition(),
                match.stageName(),
                match.roundName(),
                MatchStatus.fromDatabaseValue(match.status()),
                match.teamAId(),
                match.teamAName(),
                match.teamBId(),
                match.teamBName(),
                match.scoreA(),
                match.scoreB(),
                match.winnerTeamId(),
                match.winnerTeamName(),
                match.bestOf(),
                match.scheduledAt(),
                match.startedAt(),
                match.finishedAt(),
                match.cancelledAt(),
                match.cancellationReason(),
                null,
                null);
    }

    private Tournament findTournament(UUID tournamentId) {
        return tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament", "id", tournamentId));
    }

    private void ensureCanManage(AuthenticatedActor actor, UUID tournamentId) {
        UUID profileId = actor.requireProfileId();
        if (actor.isAdmin()) {
            return;
        }

        if (actor.role() == ProfileRole.ORGANIZER
                && tournamentRepository.canManage(tournamentId, profileId, false)) {
            return;
        }

        throw new AccessDeniedException("Only the tournament owner, tournament organizers, or admins can generate brackets.");
    }

    private String normalizeStageName(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT_STAGE_NAME;
        }

        String normalized = value.trim();
        if (normalized.length() > MAX_STAGE_NAME_LENGTH) {
            throw new BadRequestException("Bracket stage name must be at most 80 characters.");
        }

        return normalized;
    }

    private record SeededParticipant(
            BracketParticipant participant,
            int effectiveSeed
    ) {
    }
}
