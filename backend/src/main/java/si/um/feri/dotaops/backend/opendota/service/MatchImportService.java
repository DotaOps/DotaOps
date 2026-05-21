package si.um.feri.dotaops.backend.opendota.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import si.um.feri.dotaops.backend.analytics.service.AnalyticsRefreshService;
import si.um.feri.dotaops.backend.opendota.domain.MatchImport;
import si.um.feri.dotaops.backend.opendota.domain.MatchImportStatus;
import si.um.feri.dotaops.backend.opendota.domain.NormalizedMatchImport;
import si.um.feri.dotaops.backend.opendota.domain.OpenDotaErrorCode;
import si.um.feri.dotaops.backend.opendota.domain.OpenDotaRawMatchResponse;
import si.um.feri.dotaops.backend.opendota.repository.MatchImportRepository;
import si.um.feri.dotaops.backend.opendota.web.CreateMatchImportRequest;
import si.um.feri.dotaops.backend.opendota.web.MatchImportEventResponse;
import si.um.feri.dotaops.backend.opendota.web.MatchImportResponse;

@Service
public class MatchImportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MatchImportService.class);

    private final MatchImportRepository matchImportRepository;
    private final CurrentUserProvider currentUserProvider;
    private final OpenDotaClient openDotaClient;
    private final RequestRateLimiter requestRateLimiter;
    private final OpenDotaMatchNormalizationService normalizationService;
    private final AnalyticsRefreshService analyticsRefreshService;

    public MatchImportService(
            MatchImportRepository matchImportRepository,
            CurrentUserProvider currentUserProvider,
            OpenDotaClient openDotaClient,
            RequestRateLimiter requestRateLimiter,
            OpenDotaMatchNormalizationService normalizationService,
            AnalyticsRefreshService analyticsRefreshService
    ) {
        this.matchImportRepository = matchImportRepository;
        this.currentUserProvider = currentUserProvider;
        this.openDotaClient = openDotaClient;
        this.requestRateLimiter = requestRateLimiter;
        this.normalizationService = normalizationService;
        this.analyticsRefreshService = analyticsRefreshService;
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
            NormalizedMatchImport normalizedMatch = normalizationService.normalize(Long.toString(dotaMatchId), rawMatch);
            MatchImport readyImport = matchImportRepository.markReady(
                            importId,
                            normalizedMatch)
                    .orElseThrow(() -> new ResourceNotFoundException("Match import", "id", importId));
            requestAnalyticsRefresh(Long.toString(dotaMatchId));
            return readyImport;
        } catch (Exception exception) {
            return matchImportRepository.markError(
                            importId,
                            OpenDotaErrorCode.INVALID_PROVIDER_RESPONSE,
                            "OpenDota match payload could not be normalized.")
                    .orElseThrow(() -> new ResourceNotFoundException("Match import", "id", importId));
        }
    }

    private void requestAnalyticsRefresh(String dotaMatchId) {
        try {
            analyticsRefreshService.requestRefreshAfterSuccessfulImport(dotaMatchId);
        } catch (RuntimeException exception) {
            LOGGER.warn("Analytics refresh could not be scheduled after match import {}.", dotaMatchId, exception);
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
