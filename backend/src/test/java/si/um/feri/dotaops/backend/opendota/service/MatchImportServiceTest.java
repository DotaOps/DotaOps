package si.um.feri.dotaops.backend.opendota.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

import si.um.feri.dotaops.backend.auth.domain.AuthenticatedActor;
import si.um.feri.dotaops.backend.auth.domain.ProfileRole;
import si.um.feri.dotaops.backend.auth.service.CurrentUserProvider;
import si.um.feri.dotaops.backend.common.error.BadRequestException;
import si.um.feri.dotaops.backend.common.error.RateLimitExceededException;
import si.um.feri.dotaops.backend.common.security.RequestRateLimiter;
import si.um.feri.dotaops.backend.opendota.domain.MatchImport;
import si.um.feri.dotaops.backend.opendota.domain.MatchImportStatus;
import si.um.feri.dotaops.backend.opendota.domain.MatchPlayerImport;
import si.um.feri.dotaops.backend.opendota.domain.OpenDotaErrorCode;
import si.um.feri.dotaops.backend.opendota.domain.OpenDotaRawMatchResponse;
import si.um.feri.dotaops.backend.opendota.domain.OpenDotaRawPlayerResponse;
import si.um.feri.dotaops.backend.opendota.repository.MatchImportRepository;
import si.um.feri.dotaops.backend.opendota.web.CreateMatchImportRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchImportServiceTest {

    private static final UUID IMPORT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID REQUESTED_BY = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final String DOTA_MATCH_ID = "7894561230";

    private final MatchImportRepository matchImportRepository = mock(MatchImportRepository.class);
    private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
    private final OpenDotaClient openDotaClient = mock(OpenDotaClient.class);
    private final RequestRateLimiter requestRateLimiter = mock(RequestRateLimiter.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MatchImportService service = new MatchImportService(
            matchImportRepository,
            currentUserProvider,
            openDotaClient,
            requestRateLimiter);

    @Test
    void importMatchCreatesProcessingRecordFetchesOpenDotaAndStoresReadyPayload() throws Exception {
        MatchImport processing = matchImport(MatchImportStatus.PROCESSING, null);
        MatchImport ready = matchImport(MatchImportStatus.READY, null);
        when(currentUserProvider.requireActor()).thenReturn(actor(ProfileRole.ORGANIZER));
        when(matchImportRepository.findByDotaMatchId(DOTA_MATCH_ID)).thenReturn(Optional.empty());
        when(matchImportRepository.createProcessing(DOTA_MATCH_ID, REQUESTED_BY)).thenReturn(processing);
        when(openDotaClient.fetchMatch(Long.parseLong(DOTA_MATCH_ID))).thenReturn(rawMatch());
        when(matchImportRepository.markReady(eq(IMPORT_ID), anyString(), anyString(), anyList()))
                .thenReturn(Optional.of(ready));

        var response = service.importMatch(new CreateMatchImportRequest(DOTA_MATCH_ID), "203.0.113.10");

        assertThat(response.status()).isEqualTo(MatchImportStatus.READY);
        verify(requestRateLimiter).checkMatchImport(REQUESTED_BY, "203.0.113.10");
        verify(matchImportRepository).createProcessing(DOTA_MATCH_ID, REQUESTED_BY);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MatchPlayerImport>> playersCaptor = ArgumentCaptor.forClass(List.class);
        verify(matchImportRepository).markReady(eq(IMPORT_ID), anyString(), anyString(), playersCaptor.capture());
        assertThat(playersCaptor.getValue()).hasSize(1);
        assertThat(playersCaptor.getValue().getFirst().steamAccountId()).isEqualTo("39734273");
        assertThat(playersCaptor.getValue().getFirst().winner()).isTrue();
    }

    @Test
    void importMatchReturnsExistingReadyImportWithoutFetchingAgain() {
        MatchImport ready = matchImport(MatchImportStatus.READY, null);
        when(currentUserProvider.requireActor()).thenReturn(actor(ProfileRole.ORGANIZER));
        when(matchImportRepository.findByDotaMatchId(DOTA_MATCH_ID)).thenReturn(Optional.of(ready));

        var response = service.importMatch(new CreateMatchImportRequest(DOTA_MATCH_ID), "203.0.113.10");

        assertThat(response.status()).isEqualTo(MatchImportStatus.READY);
        verify(requestRateLimiter, never()).checkMatchImport(eq(REQUESTED_BY), anyString());
        verify(openDotaClient, never()).fetchMatch(Long.parseLong(DOTA_MATCH_ID));
    }

    @Test
    void importMatchMapsRateLimitToErrorStatus() {
        MatchImport processing = matchImport(MatchImportStatus.PROCESSING, null);
        MatchImport error = matchImport(
                MatchImportStatus.ERROR,
                OpenDotaErrorCode.RATE_LIMITED,
                "OpenDota rate limit exceeded.");
        when(currentUserProvider.requireActor()).thenReturn(actor(ProfileRole.ORGANIZER));
        when(matchImportRepository.findByDotaMatchId(DOTA_MATCH_ID)).thenReturn(Optional.empty());
        when(matchImportRepository.createProcessing(DOTA_MATCH_ID, REQUESTED_BY)).thenReturn(processing);
        when(openDotaClient.fetchMatch(Long.parseLong(DOTA_MATCH_ID))).thenThrow(new OpenDotaClientException(
                OpenDotaErrorCode.RATE_LIMITED,
                "OpenDota rate limit exceeded."));
        when(matchImportRepository.markError(
                IMPORT_ID,
                OpenDotaErrorCode.RATE_LIMITED,
                "OpenDota rate limit exceeded."))
                .thenReturn(Optional.of(error));

        var response = service.importMatch(new CreateMatchImportRequest(DOTA_MATCH_ID), "203.0.113.10");

        assertThat(response.status()).isEqualTo(MatchImportStatus.ERROR);
        assertThat(response.errorCode()).isEqualTo(OpenDotaErrorCode.RATE_LIMITED);
        assertThat(response.errorMessage()).isEqualTo("OpenDota rate limit exceeded.");
    }

    @Test
    void importMatchMapsNotFoundToErrorStatus() {
        MatchImport processing = matchImport(MatchImportStatus.PROCESSING, null);
        MatchImport error = matchImport(
                MatchImportStatus.ERROR,
                OpenDotaErrorCode.MATCH_NOT_FOUND,
                "OpenDota match was not found.");
        when(currentUserProvider.requireActor()).thenReturn(actor(ProfileRole.ORGANIZER));
        when(matchImportRepository.findByDotaMatchId(DOTA_MATCH_ID)).thenReturn(Optional.empty());
        when(matchImportRepository.createProcessing(DOTA_MATCH_ID, REQUESTED_BY)).thenReturn(processing);
        when(openDotaClient.fetchMatch(Long.parseLong(DOTA_MATCH_ID))).thenThrow(new OpenDotaClientException(
                OpenDotaErrorCode.MATCH_NOT_FOUND,
                "OpenDota match was not found."));
        when(matchImportRepository.markError(
                IMPORT_ID,
                OpenDotaErrorCode.MATCH_NOT_FOUND,
                "OpenDota match was not found."))
                .thenReturn(Optional.of(error));

        var response = service.importMatch(new CreateMatchImportRequest(DOTA_MATCH_ID), "203.0.113.10");

        assertThat(response.status()).isEqualTo(MatchImportStatus.ERROR);
        assertThat(response.errorCode()).isEqualTo(OpenDotaErrorCode.MATCH_NOT_FOUND);
        assertThat(response.errorMessage()).isEqualTo("OpenDota match was not found.");
    }

    @Test
    void retryExistingErrorImportDoesNotCreateDuplicateImportAndReplacesPlayersOnce() throws Exception {
        MatchImport existingError = matchImport(MatchImportStatus.ERROR, OpenDotaErrorCode.RATE_LIMITED, "Rate limited.");
        MatchImport processing = matchImport(MatchImportStatus.PROCESSING, null);
        MatchImport ready = matchImport(MatchImportStatus.READY, null);
        when(currentUserProvider.requireActor()).thenReturn(actor(ProfileRole.ORGANIZER));
        when(matchImportRepository.findByDotaMatchId(DOTA_MATCH_ID)).thenReturn(Optional.of(existingError));
        when(matchImportRepository.markProcessing(IMPORT_ID)).thenReturn(Optional.of(processing));
        when(openDotaClient.fetchMatch(Long.parseLong(DOTA_MATCH_ID))).thenReturn(rawMatch());
        when(matchImportRepository.markReady(eq(IMPORT_ID), anyString(), anyString(), anyList()))
                .thenReturn(Optional.of(ready));

        var response = service.importMatch(new CreateMatchImportRequest(DOTA_MATCH_ID), "203.0.113.10");

        assertThat(response.status()).isEqualTo(MatchImportStatus.READY);
        verify(matchImportRepository, never()).createProcessing(anyString(), any());
        verify(matchImportRepository).markProcessing(IMPORT_ID);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MatchPlayerImport>> playersCaptor = ArgumentCaptor.forClass(List.class);
        verify(matchImportRepository).markReady(eq(IMPORT_ID), anyString(), anyString(), playersCaptor.capture());
        assertThat(playersCaptor.getValue()).hasSize(1);
    }

    @Test
    void importMatchRejectsTooLargeMatchId() {
        assertThatThrownBy(() -> service.importMatch(
                new CreateMatchImportRequest("99999999999999999999"),
                "203.0.113.10"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Dota match id is too large.");
    }

    @Test
    void importMatchRejectsNonOrganizer() {
        when(currentUserProvider.requireActor()).thenReturn(actor(ProfileRole.PLAYER));

        assertThatThrownBy(() -> service.importMatch(new CreateMatchImportRequest(DOTA_MATCH_ID), "203.0.113.10"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Only organizers or admins can import matches.");

        verify(requestRateLimiter, never()).checkMatchImport(eq(REQUESTED_BY), anyString());
        verify(matchImportRepository, never()).findByDotaMatchId(DOTA_MATCH_ID);
        verify(openDotaClient, never()).fetchMatch(Long.parseLong(DOTA_MATCH_ID));
    }

    @Test
    void importMatchRateLimitsBeforeCreatingProcessingRecordOrFetchingOpenDota() {
        when(currentUserProvider.requireActor()).thenReturn(actor(ProfileRole.ORGANIZER));
        when(matchImportRepository.findByDotaMatchId(DOTA_MATCH_ID)).thenReturn(Optional.empty());
        doThrow(new RateLimitExceededException("Too many match import requests for this user. Try again later."))
                .when(requestRateLimiter)
                .checkMatchImport(REQUESTED_BY, "203.0.113.10");

        assertThatThrownBy(() -> service.importMatch(
                new CreateMatchImportRequest(DOTA_MATCH_ID),
                "203.0.113.10"))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessage("Too many match import requests for this user. Try again later.");

        verify(matchImportRepository, never()).createProcessing(anyString(), any());
        verify(openDotaClient, never()).fetchMatch(Long.parseLong(DOTA_MATCH_ID));
    }

    private static AuthenticatedActor actor(ProfileRole role) {
        return new AuthenticatedActor(
                UUID.fromString("33333333-3333-4333-8333-333333333333"),
                REQUESTED_BY,
                "organizer@example.com",
                null,
                role);
    }

    private static MatchImport matchImport(MatchImportStatus status, String errorMessage) {
        return matchImport(status, null, errorMessage);
    }

    private static MatchImport matchImport(
            MatchImportStatus status,
            OpenDotaErrorCode errorCode,
            String errorMessage
    ) {
        OffsetDateTime now = OffsetDateTime.parse("2026-05-12T00:00:00Z");

        return new MatchImport(
                IMPORT_ID,
                null,
                null,
                DOTA_MATCH_ID,
                status,
                REQUESTED_BY,
                errorCode,
                errorMessage,
                now,
                status == MatchImportStatus.PROCESSING ? null : now,
                now,
                now);
    }

    private OpenDotaRawMatchResponse rawMatch() throws Exception {
        JsonNode rawPayload = objectMapper.readTree("""
                {
                  "match_id": 7894561230,
                  "duration": 1900,
                  "radiant_win": true,
                  "players": [
                    {
                      "account_id": 39734273,
                      "player_slot": 0,
                      "hero_id": 1,
                      "kills": 8,
                      "deaths": 2,
                      "assists": 12
                    }
                  ]
                }
                """);
        JsonNode rawPlayer = rawPayload.path("players").get(0);

        return new OpenDotaRawMatchResponse(
                7894561230L,
                1900,
                null,
                true,
                List.of(new OpenDotaRawPlayerResponse(
                        39734273L,
                        0,
                        1,
                        8,
                        2,
                        12,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        rawPlayer)),
                rawPayload);
    }
}
