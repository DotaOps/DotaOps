package si.um.feri.dotaops.backend.team.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import si.um.feri.dotaops.backend.tournament.domain.TournamentSettings;

@Repository
public class TeamRosterLimitRepository {

    private final JdbcTemplate jdbcTemplate;

    public TeamRosterLimitRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int resolveRosterLimit(UUID teamId) {
        Integer activeTournamentLimit = jdbcTemplate.queryForObject(
                """
                select min(coalesce(nullif(t.settings->>'teamSize', '')::integer, ?))
                from public.tournament_registrations tr
                join public.tournaments t on t.id = tr.tournament_id
                where tr.team_id = ?
                  and tr.status in ('pending', 'approved', 'waitlisted')
                  and t.status not in ('finished', 'archived')
                """,
                Integer.class,
                TournamentSettings.DEFAULT_TEAM_SIZE,
                teamId);

        return Optional.ofNullable(activeTournamentLimit)
                .orElse(TournamentSettings.DEFAULT_TEAM_SIZE);
    }
}
