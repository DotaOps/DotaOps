package si.um.feri.dotaops.backend.tournament.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import si.um.feri.dotaops.backend.tournament.domain.MatchSlotName;
import si.um.feri.dotaops.backend.tournament.domain.MatchSlotSourceType;
import si.um.feri.dotaops.backend.tournament.domain.MatchStatus;
import si.um.feri.dotaops.backend.tournament.domain.PublicGroupStanding;
import si.um.feri.dotaops.backend.tournament.domain.PublicManualPlayer;
import si.um.feri.dotaops.backend.tournament.domain.PublicTournamentGroup;
import si.um.feri.dotaops.backend.tournament.domain.PublicTournamentListItem;
import si.um.feri.dotaops.backend.tournament.domain.PublicTournamentMatch;
import si.um.feri.dotaops.backend.tournament.domain.PublicTournamentMatchSlot;
import si.um.feri.dotaops.backend.tournament.domain.PublicTournamentMetrics;
import si.um.feri.dotaops.backend.tournament.domain.PublicTournamentTeam;
import si.um.feri.dotaops.backend.tournament.domain.TournamentFormat;
import si.um.feri.dotaops.backend.tournament.domain.TournamentStatus;

@Repository
public class PublicTournamentRepository {

    private final JdbcTemplate jdbcTemplate;

    public PublicTournamentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<PublicTournamentListItem> findPublicTournaments(String search, int size, long offset) {
        String normalizedSearch = normalizeSearch(search);

        return jdbcTemplate.query(
                """
                select
                  t.id,
                  t.slug,
                  t.title,
                  t.status::text as status,
                  t.format::text as format,
                  t.description,
                  t.prize_pool,
                  t.starts_at,
                  t.ends_at,
                  t.registration_opens_at,
                  t.registration_closes_at,
                  t.timezone,
                  t.max_teams,
                  coalesce(nullif(t.settings->>'teamSize', '')::integer, 5)::integer as team_size,
                  coalesce(approved_teams.team_count, 0)::integer as team_count,
                  coalesce(groups.group_count, 0)::integer as group_count,
                  coalesce(matches.match_count, 0)::integer as match_count,
                  coalesce(matches.finished_match_count, 0)::integer as finished_match_count,
                  p.nickname as organizer_nickname,
                  t.published_at,
                  t.created_at
                from public.tournaments t
                left join public.profiles p on p.id = t.organizer_profile_id
                left join lateral (
                  select count(*) as team_count
                  from public.tournament_registrations tr
                  where tr.tournament_id = t.id
                    and tr.status = 'approved'
                ) approved_teams on true
                left join lateral (
                  select count(*) as group_count
                  from public.tournament_groups tg
                  where tg.tournament_id = t.id
                ) groups on true
                left join lateral (
                  select
                    count(*) as match_count,
                    count(*) filter (where m.status = 'finished') as finished_match_count
                  from public.matches m
                  where m.tournament_id = t.id
                ) matches on true
                where t.is_public = true
                  and t.status in ('registration', 'published', 'live', 'finished')
                  and (
                    cast(? as text) is null
                    or t.title ilike '%' || cast(? as text) || '%'
                    or t.slug ilike '%' || cast(? as text) || '%'
                    or t.description ilike '%' || cast(? as text) || '%'
                  )
                order by
                  case when t.ends_at is null or t.ends_at >= now() then 0 else 1 end asc,
                  t.starts_at asc,
                  t.created_at desc,
                  t.id desc
                limit ? offset ?
                """,
                this::mapListItem,
                normalizedSearch,
                normalizedSearch,
                normalizedSearch,
                normalizedSearch,
                size,
                offset);
    }

    public long countPublicTournaments(String search) {
        String normalizedSearch = normalizeSearch(search);
        Long count = jdbcTemplate.queryForObject(
                """
                select count(*)
                from public.tournaments t
                where t.is_public = true
                  and t.status in ('registration', 'published', 'live', 'finished')
                  and (
                    cast(? as text) is null
                    or t.title ilike '%' || cast(? as text) || '%'
                    or t.slug ilike '%' || cast(? as text) || '%'
                    or t.description ilike '%' || cast(? as text) || '%'
                  )
                """,
                Long.class,
                normalizedSearch,
                normalizedSearch,
                normalizedSearch,
                normalizedSearch);

        return count == null ? 0 : count;
    }

    public List<PublicTournamentTeam> findApprovedTeams(UUID tournamentId) {
        return jdbcTemplate.query(
                """
                select
                  tm.id,
                  tm.name,
                  tm.tag,
                  tm.slug,
                  tm.logo_url,
                  tm.banner_url,
                  tr.seed_number
                from public.tournament_registrations tr
                join public.teams tm on tm.id = tr.team_id
                where tr.tournament_id = ?
                  and tr.status = 'approved'
                order by tr.seed_number nulls last, tr.created_at asc, tm.name asc, tr.id asc
                """,
                this::mapTeam,
                tournamentId)
                .stream()
                .map(this::withManualPlayers)
                .toList();
    }

    public List<PublicTournamentGroup> findGroups(UUID tournamentId) {
        List<PublicTournamentGroup> groups = jdbcTemplate.query(
                """
                select
                  tg.id,
                  tg.tournament_id,
                  tg.name,
                  tg.sort_order
                from public.tournament_groups tg
                where tg.tournament_id = ?
                order by tg.sort_order asc, tg.name asc, tg.id asc
                """,
                (resultSet, rowNumber) -> new PublicTournamentGroup(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getObject("tournament_id", UUID.class),
                        resultSet.getString("name"),
                        resultSet.getInt("sort_order"),
                        List.of()),
                tournamentId);

        Map<UUID, List<PublicTournamentTeam>> teamsByGroupId = findGroupTeams(tournamentId)
                .stream()
                .collect(Collectors.groupingBy(
                        GroupTeamRow::groupId,
                        LinkedHashMap::new,
                        Collectors.mapping(GroupTeamRow::team, Collectors.toList())));

        return groups.stream()
                .map(group -> new PublicTournamentGroup(
                        group.id(),
                        group.tournamentId(),
                        group.name(),
                        group.sortOrder(),
                        teamsByGroupId.getOrDefault(group.id(), List.of())))
                .toList();
    }

    public List<PublicTournamentMatch> findMatches(UUID tournamentId) {
        return findMatches(tournamentId, null);
    }

    public List<PublicTournamentMatch> findMatches(UUID tournamentId, String stageName) {
        List<PublicTournamentMatch> matches = jdbcTemplate.query(
                """
                select
                  m.id,
                  m.tournament_id,
                  m.group_id,
                  tg.name as group_name,
                  m.round_number,
                  m.bracket_position,
                  m.stage_name,
                  m.round_name,
                  m.status::text as status,
                  m.best_of,
                  m.team_a_id,
                  ta.name as team_a_name,
                  ta.tag as team_a_tag,
                  ta.slug as team_a_slug,
                  ta.logo_url as team_a_logo_url,
                  ta.banner_url as team_a_banner_url,
                  tra.seed_number as team_a_seed_number,
                  m.team_b_id,
                  tb.name as team_b_name,
                  tb.tag as team_b_tag,
                  tb.slug as team_b_slug,
                  tb.logo_url as team_b_logo_url,
                  tb.banner_url as team_b_banner_url,
                  trb.seed_number as team_b_seed_number,
                  m.score_a,
                  m.score_b,
                  m.winner_team_id,
                  tw.name as winner_team_name,
                  tw.tag as winner_team_tag,
                  tw.slug as winner_team_slug,
                  tw.logo_url as winner_team_logo_url,
                  tw.banner_url as winner_team_banner_url,
                  trw.seed_number as winner_team_seed_number,
                  m.scheduled_at,
                  m.started_at,
                  m.finished_at,
                  m.cancelled_at,
                  m.cancellation_reason
                from public.matches m
                left join public.tournament_groups tg on tg.id = m.group_id
                left join public.teams ta on ta.id = m.team_a_id
                left join public.teams tb on tb.id = m.team_b_id
                left join public.teams tw on tw.id = m.winner_team_id
                left join public.tournament_registrations tra
                  on tra.tournament_id = m.tournament_id
                  and tra.team_id = m.team_a_id
                  and tra.status = 'approved'
                left join public.tournament_registrations trb
                  on trb.tournament_id = m.tournament_id
                  and trb.team_id = m.team_b_id
                  and trb.status = 'approved'
                left join public.tournament_registrations trw
                  on trw.tournament_id = m.tournament_id
                  and trw.team_id = m.winner_team_id
                  and trw.status = 'approved'
                where m.tournament_id = ?
                  and (cast(? as text) is null or m.stage_name = cast(? as text))
                order by
                  m.scheduled_at asc nulls last,
                  m.stage_name asc,
                  m.round_number asc,
                  m.bracket_position asc nulls last,
                  m.created_at asc,
                  m.id asc
                """,
                this::mapMatchWithoutSlots,
                tournamentId,
                stageName,
                stageName);

        Map<UUID, List<PublicTournamentMatchSlot>> slotsByMatchId = findSlots(tournamentId, stageName)
                .stream()
                .collect(Collectors.groupingBy(
                        PublicTournamentMatchSlot::matchId,
                        LinkedHashMap::new,
                        Collectors.toList()));

        return matches.stream()
                .map(match -> new PublicTournamentMatch(
                        match.id(),
                        match.tournamentId(),
                        match.groupId(),
                        match.groupName(),
                        match.roundNumber(),
                        match.bracketPosition(),
                        match.stageName(),
                        match.roundName(),
                        match.status(),
                        match.bestOf(),
                        match.teamA(),
                        match.teamB(),
                        match.scoreA(),
                        match.scoreB(),
                        match.winnerTeam(),
                        match.scheduledAt(),
                        match.startedAt(),
                        match.finishedAt(),
                        match.cancelledAt(),
                        match.cancellationReason(),
                        slotsByMatchId.getOrDefault(match.id(), List.of())))
                .toList();
    }

    public List<PublicGroupStanding> findStandings(UUID tournamentId) {
        return jdbcTemplate.query(
                """
                select
                  v.group_id,
                  tg.name as group_name,
                  v.tournament_id,
                  v.team_id,
                  v.team_name,
                  v.matches_played,
                  v.match_wins,
                  v.match_losses,
                  v.match_draws,
                  v.game_wins,
                  v.game_losses,
                  v.game_diff,
                  v.points,
                  v.rank
                from public.v_group_standings v
                join public.tournament_groups tg on tg.id = v.group_id
                where v.tournament_id = ?
                order by tg.sort_order asc, tg.name asc, v.rank asc, v.team_name asc
                """,
                this::mapStanding,
                tournamentId);
    }

    public PublicTournamentMetrics findMetrics(UUID tournamentId) {
        return jdbcTemplate.queryForObject(
                """
                select
                  ?::uuid as tournament_id,
                  coalesce(approved_teams.approved_team_count, 0)::integer as team_count,
                  coalesce(approved_teams.approved_team_count, 0)::integer as approved_team_count,
                  coalesce(groups.group_count, 0)::integer as group_count,
                  coalesce(matches.match_count, 0)::integer as match_count,
                  coalesce(matches.scheduled_match_count, 0)::integer as scheduled_match_count,
                  coalesce(matches.live_match_count, 0)::integer as live_match_count,
                  coalesce(matches.finished_match_count, 0)::integer as finished_match_count,
                  coalesce(matches.cancelled_match_count, 0)::integer as cancelled_match_count,
                  coalesce(matches.total_games_played, 0)::integer as total_games_played,
                  coalesce(matches.finished_match_count, 0)::integer as total_series_played,
                  case
                    when coalesce(matches.finished_match_count, 0) = 0 then null
                    else round((matches.total_games_played::numeric / matches.finished_match_count::numeric), 2)
                  end as average_games_per_finished_match,
                  matches.next_scheduled_match_at,
                  matches.last_result_at
                from (
                  select count(*) as approved_team_count
                  from public.tournament_registrations tr
                  where tr.tournament_id = ?
                    and tr.status = 'approved'
                ) approved_teams
                cross join (
                  select count(*) as group_count
                  from public.tournament_groups tg
                  where tg.tournament_id = ?
                ) groups
                cross join (
                  select
                    count(*) as match_count,
                    count(*) filter (where m.status in ('scheduled', 'ready')) as scheduled_match_count,
                    count(*) filter (where m.status = 'live') as live_match_count,
                    count(*) filter (where m.status = 'finished') as finished_match_count,
                    count(*) filter (where m.status = 'cancelled') as cancelled_match_count,
                    coalesce(sum(m.score_a + m.score_b) filter (where m.status = 'finished'), 0) as total_games_played,
                    min(m.scheduled_at) filter (
                      where m.status in ('scheduled', 'ready')
                        and m.scheduled_at is not null
                        and m.scheduled_at >= now()
                    ) as next_scheduled_match_at,
                    max(m.finished_at) filter (where m.status = 'finished') as last_result_at
                  from public.matches m
                  where m.tournament_id = ?
                ) matches
                """,
                this::mapMetrics,
                tournamentId,
                tournamentId,
                tournamentId,
                tournamentId);
    }

    private List<GroupTeamRow> findGroupTeams(UUID tournamentId) {
        return jdbcTemplate.query(
                """
                select
                  tgt.group_id,
                  tm.id,
                  tm.name,
                  tm.tag,
                  tm.slug,
                  tm.logo_url,
                  tm.banner_url,
                  tgt.seed_number
                from public.tournament_group_teams tgt
                join public.tournament_groups tg on tg.id = tgt.group_id
                join public.teams tm on tm.id = tgt.team_id
                where tg.tournament_id = ?
                order by tg.sort_order asc, tgt.seed_number nulls last, tm.name asc, tgt.created_at asc, tgt.id asc
                """,
                (resultSet, rowNumber) -> new GroupTeamRow(
                        resultSet.getObject("group_id", UUID.class),
                        withManualPlayers(mapTeam(resultSet, rowNumber))),
                tournamentId);
    }

    private List<PublicTournamentMatchSlot> findSlots(UUID tournamentId, String stageName) {
        return jdbcTemplate.query(
                """
                select
                  ms.id,
                  ms.match_id,
                  ms.slot::text as slot,
                  ms.source_type::text as source_type,
                  ms.team_id,
                  tm.name as team_name,
                  tm.tag as team_tag,
                  tm.slug as team_slug,
                  tm.logo_url as team_logo_url,
                  tm.banner_url as team_banner_url,
                  ms.seed_number,
                  ms.seed_number as team_seed_number,
                  ms.source_match_id
                from public.match_slots ms
                join public.matches m on m.id = ms.match_id
                left join public.teams tm on tm.id = ms.team_id
                where m.tournament_id = ?
                  and (cast(? as text) is null or m.stage_name = cast(? as text))
                order by m.round_number asc, m.bracket_position asc nulls last, ms.slot asc
                """,
                this::mapSlot,
                tournamentId,
                stageName,
                stageName);
    }

    private PublicTournamentListItem mapListItem(ResultSet resultSet, int rowNumber) throws SQLException {
        return new PublicTournamentListItem(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("slug"),
                resultSet.getString("title"),
                TournamentStatus.fromDatabaseValue(resultSet.getString("status")),
                TournamentFormat.fromDatabaseValue(resultSet.getString("format")),
                resultSet.getString("description"),
                resultSet.getString("prize_pool"),
                resultSet.getObject("starts_at", OffsetDateTime.class),
                resultSet.getObject("ends_at", OffsetDateTime.class),
                resultSet.getObject("registration_opens_at", OffsetDateTime.class),
                resultSet.getObject("registration_closes_at", OffsetDateTime.class),
                resultSet.getString("timezone"),
                resultSet.getInt("max_teams"),
                resultSet.getInt("team_size"),
                resultSet.getInt("team_count"),
                resultSet.getInt("group_count"),
                resultSet.getInt("match_count"),
                resultSet.getInt("finished_match_count"),
                resultSet.getString("organizer_nickname"),
                resultSet.getObject("published_at", OffsetDateTime.class),
                resultSet.getObject("created_at", OffsetDateTime.class));
    }

    private PublicTournamentTeam mapTeam(ResultSet resultSet, int rowNumber) throws SQLException {
        return new PublicTournamentTeam(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("name"),
                resultSet.getString("tag"),
                resultSet.getString("slug"),
                resultSet.getString("logo_url"),
                resultSet.getString("banner_url"),
                resultSet.getObject("seed_number", Integer.class),
                List.of());
    }

    private PublicTournamentMatch mapMatchWithoutSlots(ResultSet resultSet, int rowNumber) throws SQLException {
        return new PublicTournamentMatch(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("tournament_id", UUID.class),
                resultSet.getObject("group_id", UUID.class),
                resultSet.getString("group_name"),
                resultSet.getInt("round_number"),
                resultSet.getObject("bracket_position", Integer.class),
                resultSet.getString("stage_name"),
                resultSet.getString("round_name"),
                MatchStatus.fromDatabaseValue(resultSet.getString("status")),
                resultSet.getInt("best_of"),
                teamFromColumns(resultSet, "team_a"),
                teamFromColumns(resultSet, "team_b"),
                resultSet.getInt("score_a"),
                resultSet.getInt("score_b"),
                teamFromColumns(resultSet, "winner_team"),
                resultSet.getObject("scheduled_at", OffsetDateTime.class),
                resultSet.getObject("started_at", OffsetDateTime.class),
                resultSet.getObject("finished_at", OffsetDateTime.class),
                resultSet.getObject("cancelled_at", OffsetDateTime.class),
                resultSet.getString("cancellation_reason"),
                List.of());
    }

    private PublicTournamentMatchSlot mapSlot(ResultSet resultSet, int rowNumber) throws SQLException {
        MatchSlotSourceType sourceType = MatchSlotSourceType.fromDatabaseValue(resultSet.getString("source_type"));

        return new PublicTournamentMatchSlot(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("match_id", UUID.class),
                MatchSlotName.fromDatabaseValue(resultSet.getString("slot")).slotNumber(),
                sourceType,
                teamFromColumns(resultSet, "team"),
                resultSet.getObject("seed_number", Integer.class),
                resultSet.getObject("source_match_id", UUID.class),
                sourceType == MatchSlotSourceType.BYE);
    }

    private PublicGroupStanding mapStanding(ResultSet resultSet, int rowNumber) throws SQLException {
        return new PublicGroupStanding(
                resultSet.getObject("group_id", UUID.class),
                resultSet.getString("group_name"),
                resultSet.getObject("tournament_id", UUID.class),
                resultSet.getObject("team_id", UUID.class),
                resultSet.getString("team_name"),
                resultSet.getInt("matches_played"),
                resultSet.getInt("match_wins"),
                resultSet.getInt("match_losses"),
                resultSet.getInt("match_draws"),
                resultSet.getInt("game_wins"),
                resultSet.getInt("game_losses"),
                resultSet.getInt("game_diff"),
                resultSet.getInt("points"),
                resultSet.getInt("rank"));
    }

    private PublicTournamentMetrics mapMetrics(ResultSet resultSet, int rowNumber) throws SQLException {
        return new PublicTournamentMetrics(
                resultSet.getObject("tournament_id", UUID.class),
                resultSet.getInt("team_count"),
                resultSet.getInt("approved_team_count"),
                resultSet.getInt("group_count"),
                resultSet.getInt("match_count"),
                resultSet.getInt("scheduled_match_count"),
                resultSet.getInt("live_match_count"),
                resultSet.getInt("finished_match_count"),
                resultSet.getInt("cancelled_match_count"),
                resultSet.getInt("total_games_played"),
                resultSet.getInt("total_series_played"),
                resultSet.getBigDecimal("average_games_per_finished_match"),
                resultSet.getObject("next_scheduled_match_at", OffsetDateTime.class),
                resultSet.getObject("last_result_at", OffsetDateTime.class));
    }

    private PublicTournamentTeam teamFromColumns(ResultSet resultSet, String prefix) throws SQLException {
        UUID teamId = resultSet.getObject(prefix + "_id", UUID.class);
        if (teamId == null) {
            return null;
        }

        return new PublicTournamentTeam(
                teamId,
                resultSet.getString(prefix + "_name"),
                resultSet.getString(prefix + "_tag"),
                resultSet.getString(prefix + "_slug"),
                resultSet.getString(prefix + "_logo_url"),
                resultSet.getString(prefix + "_banner_url"),
                resultSet.getObject(prefix + "_seed_number", Integer.class),
                List.of());
    }

    private PublicTournamentTeam withManualPlayers(PublicTournamentTeam team) {
        if (team == null) {
            return null;
        }

        return new PublicTournamentTeam(
                team.id(),
                team.name(),
                team.tag(),
                team.slug(),
                team.logoUrl(),
                team.bannerUrl(),
                team.seedNumber(),
                findManualPlayers(team.id()));
    }

    private List<PublicManualPlayer> findManualPlayers(UUID teamId) {
        return jdbcTemplate.query(
                """
                select
                  tmp.id,
                  tmp.display_name,
                  tmp.nickname,
                  tmp.note
                from public.team_manual_players tmp
                where tmp.team_id = ?
                order by tmp.created_at asc, tmp.id asc
                """,
                (resultSet, rowNumber) -> new PublicManualPlayer(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("display_name"),
                        resultSet.getString("nickname"),
                        resultSet.getString("note")),
                teamId);
    }

    private String normalizeSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }

        return search.trim();
    }

    private record GroupTeamRow(UUID groupId, PublicTournamentTeam team) {
    }
}
