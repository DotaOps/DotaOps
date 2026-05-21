package si.um.feri.dotaops.backend.integration;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import si.um.feri.dotaops.backend.opendota.domain.MatchImportStatus;
import si.um.feri.dotaops.backend.opendota.domain.MatchPlayerImport;
import si.um.feri.dotaops.backend.opendota.domain.OpenDotaErrorCode;
import si.um.feri.dotaops.backend.opendota.repository.MatchImportRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("integration")
@EnabledIfEnvironmentVariable(named = "SUPABASE_DB_URL", matches = ".+")
class MatchImportRepositoryIntegrationTest extends PostgresIntegrationTestSupport {

    @Autowired
    private MatchImportRepository matchImportRepository;

    @Test
    void uniqueDotaMatchIdPreventsDuplicateImportRows() {
        UUID requestedBy = upsertProfile(UUID.randomUUID(), "organizer");
        String dotaMatchId = numericDotaMatchId();

        matchImportRepository.createQueued(dotaMatchId, requestedBy);

        assertThatThrownBy(() -> matchImportRepository.createQueued(dotaMatchId, requestedBy))
                .isInstanceOf(DataIntegrityViolationException.class);
        Integer importCount = jdbcTemplate.queryForObject(
                "select count(*) from public.match_imports where dota_match_id = ?",
                Integer.class,
                dotaMatchId);

        assertThat(importCount).isOne();
    }

    @Test
    void eventsAreWrittenAndReadInLifecycleOrderWithErrorCode() {
        UUID requestedBy = upsertProfile(UUID.randomUUID(), "organizer");
        String dotaMatchId = numericDotaMatchId();

        var queued = matchImportRepository.createQueued(dotaMatchId, requestedBy);
        var processing = matchImportRepository.markProcessing(
                queued.id(),
                requestedBy,
                "processing")
                .orElseThrow();
        matchImportRepository.markError(
                        processing.id(),
                        OpenDotaErrorCode.RATE_LIMITED,
                        "OpenDota rate limit exceeded.")
                .orElseThrow();

        var events = matchImportRepository.findEvents(queued.id());

        assertThat(events)
                .extracting(event -> event.eventType())
                .containsExactly(MatchImportStatus.QUEUED, MatchImportStatus.PROCESSING, MatchImportStatus.ERROR);
        assertThat(events.getLast().errorCode()).isEqualTo(OpenDotaErrorCode.RATE_LIMITED);
    }

    @Test
    void matchGameImportStatusFollowsMatchImportStatus() {
        UUID requestedBy = upsertProfile(UUID.randomUUID(), "organizer");
        String dotaMatchId = numericDotaMatchId();
        UUID matchGameId = insertMatchGame(requestedBy, dotaMatchId);

        var queued = matchImportRepository.createQueued(dotaMatchId, requestedBy);
        var processing = matchImportRepository.markProcessing(
                queued.id(),
                requestedBy,
                "processing")
                .orElseThrow();
        matchImportRepository.markError(
                        processing.id(),
                        OpenDotaErrorCode.PROVIDER_TIMEOUT,
                        "OpenDota request timed out.")
                .orElseThrow();

        String importStatus = jdbcTemplate.queryForObject(
                "select import_status::text from public.match_games where id = ?",
                String.class,
                matchGameId);

        assertThat(importStatus).isEqualTo("error");
    }

    @Test
    void markReadyReplacesPlayersInsteadOfDuplicatingThem() {
        UUID requestedBy = upsertProfile(UUID.randomUUID(), "organizer");
        String dotaMatchId = numericDotaMatchId();
        var queued = matchImportRepository.createQueued(dotaMatchId, requestedBy);

        matchImportRepository.markReady(queued.id(), "{}", "{}", players()).orElseThrow();
        matchImportRepository.markReady(queued.id(), "{}", "{}", players()).orElseThrow();

        Integer playerCount = jdbcTemplate.queryForObject(
                "select count(*) from public.match_players where match_import_id = ?",
                Integer.class,
                queued.id());

        assertThat(playerCount).isEqualTo(1);
    }

    private UUID insertMatchGame(UUID organizerProfileId, String dotaMatchId) {
        String suffix = uniqueSuffix();
        UUID tournamentId = jdbcTemplate.queryForObject(
                """
                insert into public.tournaments (
                  slug,
                  title,
                  status,
                  format,
                  organizer_profile_id,
                  starts_at,
                  is_public,
                  max_teams
                )
                values (
                  ?,
                  ?,
                  'published'::public.dotaops_tournament_status,
                  'single_elimination'::public.dotaops_tournament_format,
                  ?,
                  now() + interval '14 days',
                  true,
                  8
                )
                returning id
                """,
                UUID.class,
                "match-import-" + suffix,
                "Match Import " + suffix,
                organizerProfileId);
        UUID matchId = jdbcTemplate.queryForObject(
                """
                insert into public.matches (
                  tournament_id,
                  round_name,
                  round_number,
                  status,
                  best_of
                )
                values (?, 'Final', 1, 'scheduled'::public.dotaops_match_status, 1)
                returning id
                """,
                UUID.class,
                tournamentId);

        return jdbcTemplate.queryForObject(
                """
                insert into public.match_games (
                  match_id,
                  game_number,
                  dota_match_id
                )
                values (?, 1, ?)
                returning id
                """,
                UUID.class,
                matchId,
                dotaMatchId);
    }

    private static List<MatchPlayerImport> players() {
        return List.of(new MatchPlayerImport(
                "39734273",
                null,
                0,
                true,
                true,
                8,
                2,
                12,
                100,
                8,
                500,
                650,
                12000,
                20000,
                500,
                300,
                20,
                1900,
                "{}"));
    }

    private static String numericDotaMatchId() {
        long value = Math.floorMod(UUID.randomUUID().getLeastSignificantBits(), 1_000_000_000_000_000_000L);
        return Long.toString(value + 1_000_000_000L);
    }
}
