package si.um.feri.dotaops.backend.tournament.domain;

public record TournamentSettings(
        int maxTeams,
        int minTeams,
        int teamSize,
        int bestOf,
        TournamentFormat format,
        boolean checkInEnabled,
        boolean allowSubstitutes
) {

    public static final int DEFAULT_MAX_TEAMS = 8;
    public static final int DEFAULT_MIN_TEAMS = 2;
    public static final int DEFAULT_TEAM_SIZE = 5;
    public static final int DOTA_TEAM_SIZE = DEFAULT_TEAM_SIZE;
    public static final int DEFAULT_BEST_OF = 1;
    public static final boolean DEFAULT_CHECK_IN_ENABLED = false;
    public static final boolean DEFAULT_ALLOW_SUBSTITUTES = true;

    public static boolean isSupportedTeamSize(int teamSize) {
        return teamSize == 1 || teamSize == 3 || teamSize == DEFAULT_TEAM_SIZE;
    }

    public static TournamentSettings defaults(TournamentFormat format, Integer maxTeams) {
        int resolvedMaxTeams = maxTeams == null ? DEFAULT_MAX_TEAMS : maxTeams;
        return new TournamentSettings(
                resolvedMaxTeams,
                Math.min(DEFAULT_MIN_TEAMS, resolvedMaxTeams),
                DOTA_TEAM_SIZE,
                DEFAULT_BEST_OF,
                format == null ? TournamentFormat.SINGLE_ELIMINATION : format,
                DEFAULT_CHECK_IN_ENABLED,
                DEFAULT_ALLOW_SUBSTITUTES);
    }
}
