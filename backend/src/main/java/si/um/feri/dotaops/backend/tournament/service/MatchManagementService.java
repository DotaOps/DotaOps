package si.um.feri.dotaops.backend.tournament.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import si.um.feri.dotaops.backend.notification.service.NotificationOutboxService;
import si.um.feri.dotaops.backend.tournament.domain.MatchStatus;
import si.um.feri.dotaops.backend.tournament.domain.Tournament;
import si.um.feri.dotaops.backend.tournament.domain.TournamentMatch;
import si.um.feri.dotaops.backend.tournament.dto.CancelMatchRequest;
import si.um.feri.dotaops.backend.tournament.dto.MatchResponse;
import si.um.feri.dotaops.backend.tournament.dto.ScheduleMatchRequest;
import si.um.feri.dotaops.backend.tournament.dto.SubmitMatchResultRequest;
import si.um.feri.dotaops.backend.tournament.repository.MatchRepository;
import si.um.feri.dotaops.backend.tournament.repository.TournamentRepository;

@Service
public class MatchManagementService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MatchManagementService.class);

    private final MatchRepository matchRepository;
    private final TournamentRepository tournamentRepository;
    private final CurrentUserProvider currentUserProvider;
    private final DatabaseActorContext databaseActorContext;
    private final MatchAdvancementService matchAdvancementService;
    private final NotificationOutboxService notificationOutboxService;

    public MatchManagementService(
            MatchRepository matchRepository,
            TournamentRepository tournamentRepository,
            CurrentUserProvider currentUserProvider,
            DatabaseActorContext databaseActorContext,
            MatchAdvancementService matchAdvancementService,
            NotificationOutboxService notificationOutboxService
    ) {
        this.matchRepository = matchRepository;
        this.tournamentRepository = tournamentRepository;
        this.currentUserProvider = currentUserProvider;
        this.databaseActorContext = databaseActorContext;
        this.matchAdvancementService = matchAdvancementService;
        this.notificationOutboxService = notificationOutboxService;
    }

    @Transactional(readOnly = true)
    public MatchResponse getPublicMatch(UUID matchId) {
        return matchRepository.findPublicById(matchId)
                .map(MatchResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Match", "id", matchId));
    }

    @Transactional(readOnly = true)
    public List<MatchResponse> listPublicTournamentMatches(UUID tournamentId) {
        return matchRepository.findPublicByTournamentId(tournamentId)
                .stream()
                .map(MatchResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<MatchResponse> listOrganizerTournamentMatches(UUID tournamentId) {
        AuthenticatedActor actor = currentUserProvider.requireActor();
        tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament", "id", tournamentId));
        ensureCanManage(actor, tournamentId);

        return matchRepository.findOrganizerByTournamentId(tournamentId)
                .stream()
                .map(MatchResponse::from)
                .toList();
    }

    @Transactional
    public MatchResponse scheduleMatch(UUID matchId, ScheduleMatchRequest request) {
        if (request == null || request.scheduledAt() == null) {
            throw new BadRequestException("Match scheduledAt is required.");
        }

        AuthenticatedActor actor = currentUserProvider.requireActor();
        TournamentMatch match = findMatch(matchId);
        ensureCanManage(actor, match.tournamentId());
        ensureCanSchedule(match);
        databaseActorContext.apply(actor);

        TournamentMatch updated = matchRepository.schedule(match.id(), request.scheduledAt())
                .orElseThrow(() -> new ResourceNotFoundException("Match", "id", matchId));
        notifyMatchScheduled(updated);
        return MatchResponse.from(updated);
    }

    @Transactional
    public MatchResponse startMatch(UUID matchId) {
        AuthenticatedActor actor = currentUserProvider.requireActor();
        TournamentMatch match = findMatch(matchId);
        ensureCanManage(actor, match.tournamentId());
        ensureCanStart(match);
        databaseActorContext.apply(actor);

        return matchRepository.start(match.id(), now())
                .map(MatchResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Match", "id", matchId));
    }

    @Transactional
    public MatchResponse cancelMatch(UUID matchId, CancelMatchRequest request) {
        AuthenticatedActor actor = currentUserProvider.requireActor();
        TournamentMatch match = findMatch(matchId);
        ensureCanManage(actor, match.tournamentId());
        ensureCanCancel(match);
        databaseActorContext.apply(actor);

        return matchRepository.cancel(match.id(), normalizeReason(request == null ? null : request.reason()), now())
                .map(MatchResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Match", "id", matchId));
    }

    @Transactional
    public MatchResponse finishMatch(UUID matchId) {
        AuthenticatedActor actor = currentUserProvider.requireActor();
        TournamentMatch match = findMatch(matchId);
        ensureCanManage(actor, match.tournamentId());
        ensureCanFinish(match);
        validateExistingFinishedResult(match);
        databaseActorContext.apply(actor);

        TournamentMatch updated = matchRepository.finish(match.id(), now())
                .orElseThrow(() -> new ResourceNotFoundException("Match", "id", matchId));
        matchAdvancementService.advanceAfterResult(match, updated, actor.profileId());
        return MatchResponse.from(updated);
    }

    @Transactional
    public MatchResponse submitResult(UUID matchId, SubmitMatchResultRequest request) {
        if (request == null || request.scoreA() == null || request.scoreB() == null || request.winnerTeamId() == null) {
            throw new BadRequestException("Match scoreA, scoreB, and winnerTeamId are required.");
        }

        AuthenticatedActor actor = currentUserProvider.requireActor();
        TournamentMatch match = findMatch(matchId);
        ensureCanManage(actor, match.tournamentId());
        ensureCanSubmitResult(match);
        validateResult(match, request.scoreA(), request.scoreB(), request.winnerTeamId());
        databaseActorContext.apply(actor);

        TournamentMatch updated = matchRepository.submitResult(
                        match.id(),
                        request.scoreA(),
                        request.scoreB(),
                        request.winnerTeamId(),
                        now())
                .orElseThrow(() -> new ResourceNotFoundException("Match", "id", matchId));
        matchAdvancementService.advanceAfterResult(match, updated, actor.profileId());

        return MatchResponse.from(updated);
    }

    private TournamentMatch findMatch(UUID matchId) {
        return matchRepository.findById(matchId)
                .orElseThrow(() -> new ResourceNotFoundException("Match", "id", matchId));
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

        throw new AccessDeniedException("Only the tournament owner, tournament organizers, or admins can manage matches.");
    }

    private void ensureCanSchedule(TournamentMatch match) {
        if (match.status() == MatchStatus.FINISHED) {
            throw new ConflictException("Finished matches cannot be rescheduled.");
        }

        if (match.status() == MatchStatus.CANCELLED) {
            throw new ConflictException("Cancelled matches cannot be rescheduled.");
        }

        if (match.status() == MatchStatus.LIVE) {
            throw new ConflictException("Live matches cannot be rescheduled.");
        }
    }

    private void ensureCanStart(TournamentMatch match) {
        if (match.status() == MatchStatus.FINISHED) {
            throw new ConflictException("Finished matches cannot be started.");
        }

        if (match.status() == MatchStatus.CANCELLED) {
            throw new ConflictException("Cancelled matches cannot be started.");
        }

        if (match.status() == MatchStatus.LIVE) {
            throw new ConflictException("Match is already live.");
        }

        if (match.scheduledAt() != null && match.scheduledAt().isAfter(now())) {
            throw new ConflictException("Match cannot be started before its scheduled time.");
        }

        ensureRealTeams(match);
    }

    private void ensureCanCancel(TournamentMatch match) {
        if (match.status() == MatchStatus.FINISHED) {
            throw new ConflictException("Finished matches cannot be cancelled.");
        }

        if (match.status() == MatchStatus.CANCELLED) {
            throw new ConflictException("Match is already cancelled.");
        }
    }

    private void ensureCanFinish(TournamentMatch match) {
        if (match.status() == MatchStatus.CANCELLED) {
            throw new ConflictException("Cancelled matches cannot be finished.");
        }

        if (match.status() == MatchStatus.FINISHED) {
            throw new ConflictException("Match is already finished.");
        }
    }

    private void ensureCanSubmitResult(TournamentMatch match) {
        if (match.status() == MatchStatus.CANCELLED) {
            throw new ConflictException("Cancelled matches cannot receive results.");
        }
    }

    private void validateExistingFinishedResult(TournamentMatch match) {
        validateResult(match, match.scoreA(), match.scoreB(), match.winnerTeamId());
    }

    private void validateResult(TournamentMatch match, int scoreA, int scoreB, UUID winnerTeamId) {
        ensureRealTeams(match);
        validateBestOf(match.bestOf());

        int requiredWins = (match.bestOf() / 2) + 1;
        if (scoreA < 0 || scoreB < 0) {
            throw new BadRequestException("Match scores must be non-negative.");
        }

        if (scoreA == 0 && scoreB == 0) {
            throw new BadRequestException("Match result cannot be 0:0.");
        }

        if (scoreA == scoreB) {
            throw new BadRequestException("Draw results are not supported for this match format.");
        }

        if (Math.max(scoreA, scoreB) != requiredWins) {
            throw new BadRequestException("Match result must give the winner exactly %d game wins.".formatted(requiredWins));
        }

        if (Math.min(scoreA, scoreB) >= requiredWins) {
            throw new BadRequestException("Match result cannot exceed the required number of wins.");
        }

        if (scoreA + scoreB > match.bestOf()) {
            throw new BadRequestException("Match result cannot exceed bestOf.");
        }

        if (!winnerTeamId.equals(match.teamAId()) && !winnerTeamId.equals(match.teamBId())) {
            throw new BadRequestException("winnerTeamId must be one of the teams in the match.");
        }

        UUID scoreWinner = scoreA > scoreB ? match.teamAId() : match.teamBId();
        if (!winnerTeamId.equals(scoreWinner)) {
            throw new BadRequestException("winnerTeamId must match the submitted score winner.");
        }
    }

    private void validateBestOf(int bestOf) {
        if (bestOf <= 0 || bestOf % 2 == 0) {
            throw new BadRequestException("Match bestOf must be a positive odd number.");
        }
    }

    private void ensureRealTeams(TournamentMatch match) {
        if (match.teamAId() == null || match.teamBId() == null) {
            throw new ConflictException("Match must have two teams before it can be managed.");
        }
    }

    private String normalizeReason(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    private void notifyMatchScheduled(TournamentMatch match) {
        try {
            String tournamentTitle = tournamentRepository.findById(match.tournamentId())
                    .map(Tournament::title)
                    .orElse("DotaOps turnir");
            matchRepository.findTeamCaptainsByMatchId(match.id())
                    .forEach(recipient -> notificationOutboxService.createMatchScheduledNotification(
                            match,
                            tournamentTitle,
                            recipient));
        } catch (RuntimeException exception) {
            LOGGER.warn("Match scheduled notification enqueue failed for match {}.", match.id(), exception);
        }
    }
}
