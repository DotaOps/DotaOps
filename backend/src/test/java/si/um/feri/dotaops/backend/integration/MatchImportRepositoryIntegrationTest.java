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
import si.um.feri.dotaops.backend.opendota.domain.MatchGameImport;
import si.um.feri.dotaops.backend.opendota.domain.MatchPlayerImport;
import si.um.feri.dotaops.backend.opendota.domain.NormalizedMatchImport;
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

    @Test
    void markReadyLinksKnownHeroAndStoresOpenDotaHeroId() {
        UUID requestedBy = upsertProfile(UUID.randomUUID(), "organizer");
        String dotaMatchId = numericDotaMatchId();
        UUID heroId = insertHero(1);
        var queued = matchImportRepository.createQueued(dotaMatchId, requestedBy);

        matchImportRepository.markReady(queued.id(), "{}", "{}", players(1)).orElseThrow();

        UUID storedHeroId = jdbcTemplate.queryForObject(
                "select hero_id from public.match_players where match_import_id = ?",
                UUID.class,
                queued.id());
        Integer storedDotaHeroId = jdbcTemplate.queryForObject(
                "select dota_hero_id from public.match_players where match_import_id = ?",
                Integer.class,
                queued.id());

        assertThat(storedHeroId).isEqualTo(heroId);
        assertThat(storedDotaHeroId).isOne();
    }

    @Test
    void markReadyKeepsUnknownOpenDotaHeroIdWithoutFailing() {
        UUID requestedBy = upsertProfile(UUID.randomUUID(), "organizer");
        String dotaMatchId = numericDotaMatchId();
        var queued = matchImportRepository.createQueued(dotaMatchId, requestedBy);

        matchImportRepository.markReady(queued.id(), "{}", "{}", players(999_999)).orElseThrow();

        UUID storedHeroId = jdbcTemplate.queryForObject(
                "select hero_id from public.match_players where match_import_id = ?",
                UUID.class,
                queued.id());
        Integer storedDotaHeroId = jdbcTemplate.queryForObject(
                "select dota_hero_id from public.match_players where match_import_id = ?",
                Integer.class,
                queued.id());

        assertThat(storedHeroId).isNull();
        assertThat(storedDotaHeroId).isEqualTo(999_999);
    }

    @Test
    void markReadyStoresMatchGameRawAndNormalizedPayloads() {
        UUID requestedBy = upsertProfile(UUID.randomUUID(), "organizer");
        String dotaMatchId = numericDotaMatchId();
        var queued = matchImportRepository.createQueued(dotaMatchId, requestedBy);

        matchImportRepository.markReady(queued.id(), normalized(dotaMatchId, players(1))).orElseThrow();

        var row = jdbcTemplate.queryForMap(
                """
                select
                  raw_response::text as raw_response,
                  normalized_payload::text as normalized_payload,
                  duration_seconds,
                  radiant_win,
                  winner_side
                from public.match_games
                where dota_match_id = ?
                """,
                dotaMatchId);

        assertThat(row.get("raw_response").toString()).contains("\"match_id\": " + dotaMatchId);
        assertThat(row.get("normalized_payload").toString()).contains("\"playersNormalized\": 1");
        assertThat(row.get("duration_seconds")).isEqualTo(1900);
        assertThat(row.get("radiant_win")).isEqualTo(true);
        assertThat(row.get("winner_side")).isEqualTo("RADIANT");
    }

    @Test
    void markReadyUpsertsPlayersByMatchGameAndPlayerSlot() {
        UUID requestedBy = upsertProfile(UUID.randomUUID(), "organizer");
        String dotaMatchId = numericDotaMatchId();
        var queued = matchImportRepository.createQueued(dotaMatchId, requestedBy);

        matchImportRepository.markReady(queued.id(), normalized(dotaMatchId, players(1, 8))).orElseThrow();
        matchImportRepository.markReady(queued.id(), normalized(dotaMatchId, players(1, 11))).orElseThrow();

        var row = jdbcTemplate.queryForMap(
                """
                select count(*) as player_count, max(kills) as kills
                from public.match_players
                where match_import_id = ?
                """,
                queued.id());

        assertThat(row.get("player_count")).isEqualTo(1L);
        assertThat(row.get("kills")).isEqualTo(11);
    }

    @Test
    void markReadyLinksKnownProfileByDotaAccountIdWithoutCreatingProfiles() {
        UUID requestedBy = upsertProfile(UUID.randomUUID(), "organizer");
        UUID playerProfileId = upsertProfile(UUID.randomUUID(), "player");
        String steamId = uniqueSteamId64();
        jdbcTemplate.update(
                """
                update public.profiles
                set opendota_account_id = ?,
                    steam_id = ?
                where id = ?
                """,
                39734273L,
                steamId,
                playerProfileId);
        String dotaMatchId = numericDotaMatchId();
        var queued = matchImportRepository.createQueued(dotaMatchId, requestedBy);

        matchImportRepository.markReady(queued.id(), normalized(dotaMatchId, players(1))).orElseThrow();

        var row = jdbcTemplate.queryForMap(
                """
                select profile_id, dota_account_id, steam_account_id
                from public.match_players
                where match_import_id = ?
                """,
                queued.id());

        assertThat(row.get("profile_id")).isEqualTo(playerProfileId);
        assertThat(row.get("dota_account_id")).isEqualTo(39734273L);
        assertThat(row.get("steam_account_id")).isEqualTo(steamId);
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
        return players(null);
    }

    private static List<MatchPlayerImport> players(Integer dotaHeroId) {
        return players(dotaHeroId, 8);
    }

    private static List<MatchPlayerImport> players(Integer dotaHeroId, int kills) {
        return List.of(new MatchPlayerImport(
                39734273L,
                null,
                dotaHeroId,
                0,
                "RADIANT",
                true,
                true,
                kills,
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
                "{}",
                "{}"));
    }

    private static NormalizedMatchImport normalized(String dotaMatchId, List<MatchPlayerImport> players) {
        return new NormalizedMatchImport(
                new MatchGameImport(
                        dotaMatchId,
                        1900,
                        null,
                        null,
                        true,
                        22,
                        7,
                        42,
                        31,
                        "RADIANT",
                        "{\"match_id\": " + dotaMatchId + "}",
                        "{\"source\":\"OPENDOTA\",\"version\":1,\"playersNormalized\":" + players.size() + "}"),
                players);
    }

    private UUID insertHero(int dotaHeroId) {
        return jdbcTemplate.queryForObject(
                """
                insert into public.heroes (
                  dota_hero_id,
                  name,
                  localized_name,
                  slug,
                  roles,
                  image_url,
                  icon_url
                )
                values (?, ?, ?, ?, '{}', ?, ?)
                on conflict (dota_hero_id) do update
                set localized_name = excluded.localized_name
                returning id
                """,
                UUID.class,
                dotaHeroId,
                "npc_dota_hero_antimage",
                "Anti-Mage",
                "antimage-" + uniqueSuffix(),
                "https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/antimage.png",
                "https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/antimage.png");
    }

    private static String numericDotaMatchId() {
        long value = Math.floorMod(UUID.randomUUID().getLeastSignificantBits(), 1_000_000_000_000_000_000L);
        return Long.toString(value + 1_000_000_000L);
    }
}
