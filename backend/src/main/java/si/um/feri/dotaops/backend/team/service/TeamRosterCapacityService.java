package si.um.feri.dotaops.backend.team.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.um.feri.dotaops.backend.common.error.BadRequestException;
import si.um.feri.dotaops.backend.common.error.ConflictException;
import si.um.feri.dotaops.backend.team.domain.Team;
import si.um.feri.dotaops.backend.team.domain.TeamRosterCapacity;
import si.um.feri.dotaops.backend.team.repository.TeamManualPlayerRepository;
import si.um.feri.dotaops.backend.team.repository.TeamMemberRepository;
import si.um.feri.dotaops.backend.team.repository.TeamRosterLimitRepository;
import si.um.feri.dotaops.backend.tournament.domain.TournamentSettings;

@Service
public class TeamRosterCapacityService {

    private final TeamMemberRepository teamMemberRepository;
    private final TeamManualPlayerRepository teamManualPlayerRepository;
    private final TeamRosterLimitRepository teamRosterLimitRepository;

    public TeamRosterCapacityService(
            TeamMemberRepository teamMemberRepository,
            TeamManualPlayerRepository teamManualPlayerRepository,
            TeamRosterLimitRepository teamRosterLimitRepository
    ) {
        this.teamMemberRepository = teamMemberRepository;
        this.teamManualPlayerRepository = teamManualPlayerRepository;
        this.teamRosterLimitRepository = teamRosterLimitRepository;
    }

    @Transactional(readOnly = true)
    public TeamRosterCapacity resolve(Team team) {
        int rosterLimit = teamRosterLimitRepository.resolveRosterLimit(team.id());
        if (rosterLimit <= 0) {
            rosterLimit = TournamentSettings.DEFAULT_TEAM_SIZE;
        }

        int activeMembers = teamMemberRepository.countActiveByTeamId(team.id());
        int manualPlayers = teamManualPlayerRepository.countByTeamId(team.id());
        int captainFallback = team.captainProfileId() != null
                && !teamMemberRepository.existsActive(team.id(), team.captainProfileId())
                ? 1
                : 0;

        return new TeamRosterCapacity(activeMembers + manualPlayers + captainFallback, rosterLimit);
    }

    @Transactional(readOnly = true)
    public void ensureCanAdd(Team team, int additionalPlayers) {
        TeamRosterCapacity rosterCapacity = resolve(team);
        if (!rosterCapacity.canAdd(additionalPlayers)) {
            throw new BadRequestException(
                    "Team roster cannot exceed %d players.".formatted(rosterCapacity.capacity()));
        }
    }

    @Transactional(readOnly = true)
    public void ensureHasOpenSlot(Team team) {
        if (resolve(team).isFull()) {
            throw new ConflictException("Team roster is full.");
        }
    }
}
