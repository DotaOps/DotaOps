package si.um.feri.dotaops.backend.analytics.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import si.um.feri.dotaops.backend.analytics.domain.AnalyticsFilters;

@Repository
public class RoleBasedAnalyticsRepository {

    private final JdbcTemplate jdbcTemplate;

    public RoleBasedAnalyticsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public OrganizerAnalyticsCounts findOrganizerCounts(UUID profileId, boolean admin) {
        return findOrganizerCounts(profileId, admin, new AnalyticsFilters(null, null, null, null, 100));
    }

    public OrganizerAnalyticsCounts findOrganizerCounts(UUID profileId, boolean admin, AnalyticsFilters filters) {
        return jdbcTemplate.queryForObject(
                """
                with manageable_tournaments as (
                  select t.id, t.status
                  from public.tournaments t
                  where (
                      ? = true
                      or t.organizer_profile_id = ?
                      or exists (
                        select 1
                        from public.tournament_staff ts
                        where ts.tournament_id = t.id
                          and ts.profile_id = ?
                          and ts.staff_role in ('owner', 'organizer')
                      )
                    )
                    and (cast(? as uuid) is null or t.id = ?)
                ),
                manageable_matches as (
                  select m.id
                  from public.matches m
                  join manageable_tournaments mt on mt.id = m.tournament_id
                ),
                filtered_match_games as (
                  select mg.id
                  from public.match_games mg
                  join manageable_matches mm on mm.id = mg.match_id
                  join public.matches m on m.id = mg.match_id
                  where (cast(? as timestamptz) is null or """ + analyticsTimestampExpression("mg", "m") + """
                    >= ?)
                    and (cast(? as timestamptz) is null or """ + analyticsTimestampExpression("mg", "m") + """
                    < ?)
                    and (
                      cast(? as uuid) is null
                      or exists (
                        select 1
                        from public.match_players mp
                        where mp.match_game_id = mg.id
                          and mp.team_id = ?
                      )
                    )
                    and (
                      cast(? as uuid) is null
                      or exists (
                        select 1
                        from public.match_players mp
                        where mp.match_game_id = mg.id
                          and mp.profile_id = ?
                      )
                    )
                    and (
                      cast(? as uuid) is null
                      or exists (
                        select 1
                        from public.match_players mp
                        where mp.match_game_id = mg.id
                          and mp.hero_id = ?
                      )
                    )
                ),
                filtered_imports as (
                  select mi.id
                  from public.match_imports mi
                  left join public.match_games mg on mg.id = mi.match_game_id
                  join manageable_matches mm on mm.id = coalesce(mi.match_id, mg.match_id)
                  where (cast(? as timestamptz) is null or coalesce(mi.completed_at, mi.started_at, mi.requested_at, mi.created_at) >= ?)
                    and (cast(? as timestamptz) is null or coalesce(mi.completed_at, mi.started_at, mi.requested_at, mi.created_at) < ?)
                    and (
                      cast(? as uuid) is null
                      or exists (
                        select 1
                        from public.match_players mp
                        where (mp.match_import_id = mi.id or mp.match_game_id = mi.match_game_id)
                          and mp.team_id = ?
                      )
                    )
                    and (
                      cast(? as uuid) is null
                      or exists (
                        select 1
                        from public.match_players mp
                        where (mp.match_import_id = mi.id or mp.match_game_id = mi.match_game_id)
                          and mp.profile_id = ?
                      )
                    )
                    and (
                      cast(? as uuid) is null
                      or exists (
                        select 1
                        from public.match_players mp
                        where (mp.match_import_id = mi.id or mp.match_game_id = mi.match_game_id)
                          and mp.hero_id = ?
                      )
                    )
                )
                select
                  (select count(*) from manageable_tournaments) as tournaments,
                  (
                    select count(*)
                    from public.tournament_registrations tr
                    join manageable_tournaments mt on mt.id = tr.tournament_id
                    where tr.status = 'pending'
                      and (cast(? as uuid) is null or tr.team_id = ?)
                  ) as pending_registrations,
                  (
                    select count(*)
                    from public.tournament_registrations tr
                    join manageable_tournaments mt on mt.id = tr.tournament_id
                    where tr.status = 'approved'
                      and (cast(? as uuid) is null or tr.team_id = ?)
                  ) as approved_registrations,
                  (
                    select count(*)
                    from manageable_tournaments mt
                    where mt.status in ('registration', 'published', 'live')
                  ) as active_published_tournaments,
                  (
                    select count(*)
                    from public.match_games mg
                    join filtered_match_games fmg on fmg.id = mg.id
                    where mg.import_status = 'ready'
                  ) as processed_match_games,
                  (
                    select count(*)
                    from filtered_imports
                  ) as import_jobs
                """,
                (resultSet, rowNumber) -> new OrganizerAnalyticsCounts(
                        resultSet.getLong("tournaments"),
                        resultSet.getLong("pending_registrations"),
                        resultSet.getLong("approved_registrations"),
                        resultSet.getLong("active_published_tournaments"),
                        resultSet.getLong("processed_match_games"),
                        resultSet.getLong("import_jobs")),
                admin,
                profileId,
                profileId,
                filters.tournamentId(),
                filters.tournamentId(),
                filters.from(),
                filters.from(),
                filters.to(),
                filters.to(),
                filters.teamId(),
                filters.teamId(),
                filters.profileId(),
                filters.profileId(),
                filters.heroId(),
                filters.heroId(),
                filters.from(),
                filters.from(),
                filters.to(),
                filters.to(),
                filters.teamId(),
                filters.teamId(),
                filters.profileId(),
                filters.profileId(),
                filters.heroId(),
                filters.heroId(),
                filters.teamId(),
                filters.teamId(),
                filters.teamId(),
                filters.teamId());
    }

    public TournamentOperationalMetrics findTournamentOperationalMetrics(UUID tournamentId) {
        return findTournamentOperationalMetrics(
                tournamentId,
                new AnalyticsFilters(tournamentId, null, null, null, 100));
    }

    public TournamentOperationalMetrics findTournamentOperationalMetrics(UUID tournamentId, AnalyticsFilters filters) {
        return jdbcTemplate.queryForObject(
                """
                select
                  count(distinct mg.id) filter (where mg.import_status = 'ready') as games_processed,
                  count(distinct m.id) filter (
                    where not exists (
                      select 1
                      from public.match_games ready_game
                      where ready_game.match_id = m.id
                        and ready_game.import_status = 'ready'
                    )
                  ) as matches_without_import,
                  coalesce(
                    round(
                      count(distinct mg.id) filter (where mg.import_status = 'ready')::numeric
                      / nullif(count(distinct mg.id), 0)
                      * 100,
                      2
                    ),
                    0
                  ) as import_coverage_percent,
                  round(avg(mg.duration_seconds))::integer as avg_duration_seconds
                from public.matches m
                left join public.match_games mg on mg.match_id = m.id
                where m.tournament_id = ?
                  and (cast(? as timestamptz) is null or """ + analyticsTimestampExpression("mg", "m") + """
                  >= ?)
                  and (cast(? as timestamptz) is null or """ + analyticsTimestampExpression("mg", "m") + """
                  < ?)
                  and (
                    cast(? as uuid) is null
                    or m.team_a_id = ?
                    or m.team_b_id = ?
                    or exists (
                      select 1
                      from public.match_players mp
                      where (mp.match_game_id = mg.id or mp.match_id = m.id)
                        and mp.team_id = ?
                    )
                  )
                  and (
                    cast(? as uuid) is null
                    or exists (
                      select 1
                      from public.match_players mp
                      where (mp.match_game_id = mg.id or mp.match_id = m.id)
                        and mp.profile_id = ?
                    )
                  )
                  and (
                    cast(? as uuid) is null
                    or exists (
                      select 1
                      from public.match_players mp
                      where (mp.match_game_id = mg.id or mp.match_id = m.id)
                        and mp.hero_id = ?
                    )
                  )
                """,
                (resultSet, rowNumber) -> new TournamentOperationalMetrics(
                        resultSet.getInt("games_processed"),
                        resultSet.getInt("matches_without_import"),
                        resultSet.getBigDecimal("import_coverage_percent"),
                        resultSet.getObject("avg_duration_seconds", Integer.class)),
                tournamentId,
                filters.from(),
                filters.from(),
                filters.to(),
                filters.to(),
                filters.teamId(),
                filters.teamId(),
                filters.teamId(),
                filters.teamId(),
                filters.profileId(),
                filters.profileId(),
                filters.heroId(),
                filters.heroId());
    }

    public List<RecentImport> findRecentImports(UUID tournamentId, int limit) {
        return findRecentImports(
                tournamentId,
                new AnalyticsFilters(tournamentId, null, null, null, limit));
    }

    public List<RecentImport> findRecentImports(UUID tournamentId, AnalyticsFilters filters) {
        return jdbcTemplate.query(
                """
                select
                  mi.id,
                  mi.dota_match_id,
                  mi.status::text as status,
                  mi.error_code,
                  mi.created_at,
                  mi.completed_at
                from public.match_imports mi
                left join public.match_games mg on mg.id = mi.match_game_id
                join public.matches m on m.id = coalesce(mi.match_id, mg.match_id)
                where m.tournament_id = ?
                  and (cast(? as timestamptz) is null or coalesce(mi.completed_at, mi.started_at, mi.requested_at, mi.created_at) >= ?)
                  and (cast(? as timestamptz) is null or coalesce(mi.completed_at, mi.started_at, mi.requested_at, mi.created_at) < ?)
                  and (
                    cast(? as uuid) is null
                    or exists (
                      select 1
                      from public.match_players mp
                      where (mp.match_import_id = mi.id or mp.match_game_id = mi.match_game_id)
                        and mp.team_id = ?
                    )
                  )
                  and (
                    cast(? as uuid) is null
                    or exists (
                      select 1
                      from public.match_players mp
                      where (mp.match_import_id = mi.id or mp.match_game_id = mi.match_game_id)
                        and mp.profile_id = ?
                    )
                  )
                  and (
                    cast(? as uuid) is null
                    or exists (
                      select 1
                      from public.match_players mp
                      where (mp.match_import_id = mi.id or mp.match_game_id = mi.match_game_id)
                        and mp.hero_id = ?
                    )
                  )
                order by mi.created_at desc, mi.id desc
                limit ?
                """,
                this::mapRecentImport,
                tournamentId,
                filters.from(),
                filters.from(),
                filters.to(),
                filters.to(),
                filters.teamId(),
                filters.teamId(),
                filters.profileId(),
                filters.profileId(),
                filters.heroId(),
                filters.heroId(),
                filters.limit());
    }

    private String analyticsTimestampExpression(String matchGameAlias, String matchAlias) {
        return " coalesce("
                + matchGameAlias + ".started_at, "
                + matchGameAlias + ".finished_at, "
                + matchAlias + ".started_at, "
                + matchAlias + ".finished_at, "
                + matchAlias + ".scheduled_at, "
                + matchGameAlias + ".created_at, "
                + matchAlias + ".created_at)";
    }

    private RecentImport mapRecentImport(ResultSet resultSet, int rowNumber) throws SQLException {
        return new RecentImport(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("dota_match_id"),
                resultSet.getString("status"),
                resultSet.getString("error_code"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("completed_at", OffsetDateTime.class));
    }

    public record OrganizerAnalyticsCounts(
            long tournaments,
            long pendingRegistrations,
            long approvedRegistrations,
            long activePublishedTournaments,
            long processedMatchGames,
            long importJobs
    ) {
    }

    public record TournamentOperationalMetrics(
            int gamesProcessed,
            int matchesWithoutImport,
            BigDecimal importCoveragePercent,
            Integer avgDurationSeconds
    ) {
    }

    public record RecentImport(
            UUID id,
            String dotaMatchId,
            String status,
            String errorCode,
            OffsetDateTime createdAt,
            OffsetDateTime completedAt
    ) {
    }
}
