package si.um.feri.dotaops.backend.opendota.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import si.um.feri.dotaops.backend.auth.domain.AuthenticatedActor;
import si.um.feri.dotaops.backend.auth.service.CurrentUserProvider;
import si.um.feri.dotaops.backend.common.error.BadRequestException;
import si.um.feri.dotaops.backend.common.error.ResourceNotFoundException;
import si.um.feri.dotaops.backend.common.security.RequestRateLimiter;
import si.um.feri.dotaops.backend.opendota.domain.MatchImport;
import si.um.feri.dotaops.backend.opendota.domain.MatchImportStatus;
import si.um.feri.dotaops.backend.opendota.domain.MatchPlayerImport;
import si.um.feri.dotaops.backend.opendota.domain.OpenDotaErrorCode;
import si.um.feri.dotaops.backend.opendota.domain.OpenDotaRawMatchResponse;
import si.um.feri.dotaops.backend.opendota.domain.OpenDotaRawPlayerResponse;
import si.um.feri.dotaops.backend.opendota.repository.MatchImportRepository;
import si.um.feri.dotaops.backend.opendota.web.CreateMatchImportRequest;
import si.um.feri.dotaops.backend.opendota.web.MatchImportEventResponse;
import si.um.feri.dotaops.backend.opendota.web.MatchImportResponse;

@Service
public class MatchImportService {

    private final MatchImportRepository matchImportRepository;
    private final CurrentUserProvider currentUserProvider;
    private final OpenDotaClient openDotaClient;
    private final RequestRateLimiter requestRateLimiter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MatchImportService(
            MatchImportRepository matchImportRepository,
            CurrentUserProvider currentUserProvider,
            OpenDotaClient openDotaClient,
            RequestRateLimiter requestRateLimiter
    ) {
        this.matchImportRepository = matchImportRepository;
        this.currentUserProvider = currentUserProvider;
        this.openDotaClient = openDotaClient;
        this.requestRateLimiter = requestRateLimiter;
    }

    public MatchImportResponse importMatch(CreateMatchImportRequest request, String clientIp) {
        String dotaMatchId = normalizeDotaMatchId(request.dotaMatchId());
        long parsedMatchId = parseDotaMatchId(dotaMatchId);
        AuthenticatedActor actor = currentUserProvider.requireActor();
        if (!actor.isOrganizer()) {
            throw new AccessDeniedException("Only organizers or admins can import matches.");
        }
        UUID requestedBy = actor.requireProfileId();

        Optional<MatchImport> existing = matchImportRepository.findByDotaMatchId(dotaMatchId);
        if (existing.isPresent()
                && (existing.orElseThrow().status() == MatchImportStatus.READY
                || existing.orElseThrow().status() == MatchImportStatus.PROCESSING)) {
            return responseFrom(existing.orElseThrow());
        }

        requestRateLimiter.checkMatchImport(requestedBy, clientIp);

        MatchImport queuedImport = existing.orElseGet(() -> createQueuedImport(dotaMatchId, requestedBy));
        if (queuedImport.status() == MatchImportStatus.READY || queuedImport.status() == MatchImportStatus.PROCESSING) {
            return responseFrom(queuedImport);
        }

        boolean retry = queuedImport.status() == MatchImportStatus.ERROR;
        MatchImport startedImport = matchImportRepository
                .markProcessing(
                        queuedImport.id(),
                        requestedBy,
                        retry
                                ? "Match import retry requested; processing restarted."
                                : "Match import processing started.")
                .orElseGet(() -> matchImportRepository.findById(queuedImport.id())
                        .orElseThrow(() -> new ResourceNotFoundException("Match import", "id", queuedImport.id())));
        if (startedImport.status() != MatchImportStatus.PROCESSING) {
            return responseFrom(startedImport);
        }

        return responseFrom(fetchAndStoreMatch(startedImport.id(), parsedMatchId));
    }

    public MatchImportResponse retryImport(UUID importId, String clientIp) {
        MatchImport matchImport = matchImportRepository.findById(importId)
                .orElseThrow(() -> new ResourceNotFoundException("Match import", "id", importId));

        return importMatch(new CreateMatchImportRequest(matchImport.dotaMatchId()), clientIp);
    }

    @Transactional(readOnly = true)
    public MatchImportResponse getImport(UUID importId) {
        return responseFrom(matchImportRepository.findById(importId)
                .orElseThrow(() -> new ResourceNotFoundException("Match import", "id", importId)));
    }

    @Transactional(readOnly = true)
    public MatchImportResponse getImportByDotaMatchId(String dotaMatchId) {
        String normalized = normalizeDotaMatchId(dotaMatchId);

        return responseFrom(matchImportRepository.findByDotaMatchId(normalized)
                .orElseThrow(() -> new ResourceNotFoundException("Match import", "dotaMatchId", normalized)));
    }

    @Transactional(readOnly = true)
    public List<MatchImportEventResponse> getImportEvents(UUID importId) {
        if (matchImportRepository.findById(importId).isEmpty()) {
            throw new ResourceNotFoundException("Match import", "id", importId);
        }

        return eventResponses(importId);
    }

    private MatchImport createQueuedImport(String dotaMatchId, UUID requestedBy) {
        try {
            return matchImportRepository.createQueued(dotaMatchId, requestedBy);
        } catch (DataIntegrityViolationException exception) {
            return matchImportRepository.findByDotaMatchId(dotaMatchId)
                    .orElseThrow(() -> new BadRequestException("Match import already exists but could not be loaded."));
        }
    }

    private MatchImport fetchAndStoreMatch(UUID importId, long dotaMatchId) {
        OpenDotaRawMatchResponse rawMatch;
        try {
            rawMatch = openDotaClient.fetchMatch(dotaMatchId);
        } catch (OpenDotaClientException exception) {
            return matchImportRepository.markError(importId, exception.errorCode(), exception.getMessage())
                    .orElseThrow(() -> new ResourceNotFoundException("Match import", "id", importId));
        } catch (RuntimeException exception) {
            return matchImportRepository.markError(
                            importId,
                            null,
                            "Match import failed unexpectedly.")
                    .orElseThrow(() -> new ResourceNotFoundException("Match import", "id", importId));
        }

        try {
            return matchImportRepository.markReady(
                            importId,
                            objectMapper.writeValueAsString(rawMatch.rawPayload()),
                            objectMapper.writeValueAsString(normalizeMatchPayload(rawMatch)),
                            extractPlayers(rawMatch))
                    .orElseThrow(() -> new ResourceNotFoundException("Match import", "id", importId));
        } catch (Exception exception) {
            return matchImportRepository.markError(
                            importId,
                            OpenDotaErrorCode.INVALID_PROVIDER_RESPONSE,
                            "OpenDota match payload could not be normalized.")
                    .orElseThrow(() -> new ResourceNotFoundException("Match import", "id", importId));
        }
    }

    private MatchImportResponse responseFrom(MatchImport matchImport) {
        return MatchImportResponse.from(matchImport, eventResponses(matchImport.id()));
    }

    private List<MatchImportEventResponse> eventResponses(UUID importId) {
        return matchImportRepository.findEvents(importId)
                .stream()
                .map(MatchImportEventResponse::from)
                .toList();
    }

    private ObjectNode normalizeMatchPayload(OpenDotaRawMatchResponse rawMatch) {
        ObjectNode normalized = objectMapper.createObjectNode();
        putLong(normalized, "match_id", rawMatch.matchId());
        putLong(normalized, "start_time", rawMatch.startTime());
        putInt(normalized, "duration", rawMatch.duration());
        putBoolean(normalized, "radiant_win", rawMatch.radiantWin());

        ArrayNode players = normalized.putArray("players");
        rawMatch.players().forEach(player -> {
            ObjectNode normalizedPlayer = players.addObject();
            putLong(normalizedPlayer, "account_id", player.accountId());
            putInt(normalizedPlayer, "player_slot", player.playerSlot());
            putInt(normalizedPlayer, "hero_id", player.heroId());
            putInt(normalizedPlayer, "kills", player.kills());
            putInt(normalizedPlayer, "deaths", player.deaths());
            putInt(normalizedPlayer, "assists", player.assists());
            putInt(normalizedPlayer, "last_hits", player.lastHits());
            putInt(normalizedPlayer, "denies", player.denies());
            putInt(normalizedPlayer, "gold_per_min", player.goldPerMin());
            putInt(normalizedPlayer, "xp_per_min", player.xpPerMin());
        });

        return normalized;
    }

    private List<MatchPlayerImport> extractPlayers(OpenDotaRawMatchResponse rawMatch) throws Exception {
        Integer durationSeconds = rawMatch.duration();
        Boolean radiantWin = rawMatch.radiantWin();
        List<MatchPlayerImport> players = new ArrayList<>();

        for (OpenDotaRawPlayerResponse player : rawMatch.players()) {
            if (player.playerSlot() == null) {
                continue;
            }

            int playerSlot = player.playerSlot();
            Boolean radiant = playerSlot < 128;
            Boolean winner = radiantWin == null ? null : radiantWin.equals(radiant);

            players.add(new MatchPlayerImport(
                    player.accountId() == null ? null : Long.toString(player.accountId()),
                    player.heroId(),
                    playerSlot,
                    radiant,
                    winner,
                    nonNegativeInt(player.kills()),
                    nonNegativeInt(player.deaths()),
                    nonNegativeInt(player.assists()),
                    nonNegativeInt(player.lastHits()),
                    nonNegativeInt(player.denies()),
                    player.goldPerMin(),
                    player.xpPerMin(),
                    player.netWorth(),
                    player.heroDamage(),
                    player.towerDamage(),
                    player.heroHealing(),
                    player.level(),
                    durationSeconds,
                    objectMapper.writeValueAsString(player.rawPayload())));
        }

        return players;
    }

    private void putLong(ObjectNode target, String fieldName, Long value) {
        if (value != null) {
            target.put(fieldName, value);
        }
    }

    private void putInt(ObjectNode target, String fieldName, Integer value) {
        if (value != null) {
            target.put(fieldName, value);
        }
    }

    private void putBoolean(ObjectNode target, String fieldName, Boolean value) {
        if (value != null) {
            target.put(fieldName, value);
        }
    }

    private int nonNegativeInt(Integer value) {
        if (value == null) {
            return 0;
        }

        return Math.max(0, value);
    }

    private String normalizeDotaMatchId(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BadRequestException("Dota match id is required.");
        }

        String normalized = value.trim();
        if (!normalized.matches("^[0-9]{1,20}$")) {
            throw new BadRequestException("Dota match id must contain digits only.");
        }

        return normalized;
    }

    private long parseDotaMatchId(String value) {
        try {
            long matchId = Long.parseLong(value);
            if (matchId <= 0) {
                throw new BadRequestException("Dota match id must be positive.");
            }

            return matchId;
        } catch (NumberFormatException exception) {
            throw new BadRequestException("Dota match id is too large.");
        }
    }
}
