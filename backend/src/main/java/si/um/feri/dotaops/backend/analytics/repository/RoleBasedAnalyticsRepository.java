package si.um.feri.dotaops.backend.analytics.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RoleBasedAnalyticsRepository {

    private final JdbcTemplate jdbcTemplate;

    public RoleBasedAnalyticsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public OrganizerAnalyticsCounts findOrganizerCounts(UUID profileId, boolean admin) {
        return jdbcTemplate.queryForObject(
                """
                with manageable_tournaments as (
                  select t.id, t.status
                  from public.tournaments t
                  where ? = true
                     or t.organizer_profile_id = ?
                     or exists (
                       select 1
                       from public.tournament_staff ts
                       where ts.tournament_id = t.id
                         and ts.profile_id = ?
                         and ts.staff_role in ('owner', 'organizer')
                     )
                ),
                manageable_matches as (
                  select m.id
                  from public.matches m
                  join manageable_tournaments mt on mt.id = m.tournament_id
                )
                select
                  (select count(*) from manageable_tournaments) as tournaments,
                  (
                    select count(*)
                    from public.tournament_registrations tr
                    join manageable_tournaments mt on mt.id = tr.tournament_id
                    where tr.status = 'pending'
                  ) as pending_registrations,
                  (
                    select count(*)
                    from public.tournament_registrations tr
                    join manageable_tournaments mt on mt.id = tr.tournament_id
                    where tr.status = 'approved'
                  ) as approved_registrations,
                  (
                    select count(*)
                    from manageable_tournaments mt
                    where mt.status in ('registration', 'published', 'live')
                  ) as active_published_tournaments,
                  (
                    select count(*)
                    from public.match_games mg
                    join manageable_matches mm on mm.id = mg.match_id
                    where mg.import_status = 'ready'
                  ) as processed_match_games,
                  (
                    select count(*)
                    from public.match_imports mi
                    left join public.match_games mg on mg.id = mi.match_game_id
                    join manageable_matches mm on mm.id = coalesce(mi.match_id, mg.match_id)
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
                profileId);
    }

    public TournamentOperationalMetrics findTournamentOperationalMetrics(UUID tournamentId) {
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
                """,
                (resultSet, rowNumber) -> new TournamentOperationalMetrics(
                        resultSet.getInt("games_processed"),
                        resultSet.getInt("matches_without_import"),
                        resultSet.getBigDecimal("import_coverage_percent"),
                        resultSet.getObject("avg_duration_seconds", Integer.class)),
                tournamentId);
    }

    public List<RecentImport> findRecentImports(UUID tournamentId, int limit) {
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
                order by mi.created_at desc, mi.id desc
                limit ?
                """,
                this::mapRecentImport,
                tournamentId,
                limit);
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
