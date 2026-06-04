package si.um.feri.dotaops.backend.dashboard.repository;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DashboardRepository {

    private final JdbcTemplate jdbcTemplate;

    public DashboardRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long countPendingInvitations(UUID profileId, String normalizedEmail) {
        Long count = jdbcTemplate.queryForObject(
                """
                select count(*)
                from public.team_invitations ti
                where ti.status = 'pending'
                  and (
                    (
                      ti.invitee_profile_id is not null
                      and ti.invitee_email is not null
                      and ti.invitee_profile_id = ?
                      and cast(? as text) is not null
                      and lower(ti.invitee_email) = cast(? as text)
                    )
                    or (
                      ti.invitee_profile_id is not null
                      and ti.invitee_email is null
                      and ti.invitee_profile_id = ?
                    )
                    or (
                      ti.invitee_profile_id is null
                      and ti.invitee_email is not null
                      and cast(? as text) is not null
                      and lower(ti.invitee_email) = cast(? as text)
                    )
                  )
                """,
                Long.class,
                profileId,
                normalizedEmail,
                normalizedEmail,
                profileId,
                normalizedEmail,
                normalizedEmail);

        return count == null ? 0 : count;
    }

    public long countTournamentRegistrations(UUID teamId) {
        Long count = jdbcTemplate.queryForObject(
                """
                select count(*)
                from public.tournament_registrations tr
                where tr.team_id = ?
                """,
                Long.class,
                teamId);

        return count == null ? 0 : count;
    }

    public OrganizerDashboardCounts findOrganizerCounts(UUID profileId, boolean admin) {
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
                (resultSet, rowNumber) -> new OrganizerDashboardCounts(
                        resultSet.getLong("tournaments"),
                        resultSet.getLong("pending_registrations"),
                        resultSet.getLong("active_published_tournaments"),
                        resultSet.getLong("processed_match_games"),
                        resultSet.getLong("import_jobs")),
                admin,
                profileId,
                profileId);
    }

    public AdminDashboardCounts findAdminCounts() {
        return jdbcTemplate.queryForObject(
                """
                select
                  (select count(*) from public.profiles) as profiles,
                  (select count(*) from public.tournaments) as tournaments,
                  (
                    select count(*)
                    from public.tournament_registrations
                    where status = 'pending'
                  ) as pending_registrations,
                  (select count(*) from public.match_imports) as import_jobs
                """,
                (resultSet, rowNumber) -> new AdminDashboardCounts(
                        resultSet.getLong("profiles"),
                        resultSet.getLong("tournaments"),
                        resultSet.getLong("pending_registrations"),
                        resultSet.getLong("import_jobs")));
    }

    public record OrganizerDashboardCounts(
            long tournaments,
            long pendingRegistrations,
            long activePublishedTournaments,
            long processedMatchGames,
            long importJobs
    ) {
    }

    public record AdminDashboardCounts(
            long profiles,
            long tournaments,
            long pendingRegistrations,
            long importJobs
    ) {
    }
}
