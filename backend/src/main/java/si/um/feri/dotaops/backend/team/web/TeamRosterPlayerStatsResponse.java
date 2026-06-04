package si.um.feri.dotaops.backend.team.web;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import si.um.feri.dotaops.backend.analytics.web.PlayerMetricsResponse;

public record TeamRosterPlayerStatsResponse(
        int gamesPlayed,
        int wins,
        int losses,
        BigDecimal winRate,
        BigDecimal kda,
        BigDecimal avgKills,
        BigDecimal avgDeaths,
        BigDecimal avgAssists
) {

    private static final int SCALE = 2;

    public static TeamRosterPlayerStatsResponse from(List<PlayerMetricsResponse> metrics) {
        int gamesPlayed = metrics.stream().mapToInt(PlayerMetricsResponse::gamesPlayed).sum();
        int wins = metrics.stream().mapToInt(PlayerMetricsResponse::wins).sum();
        int losses = metrics.stream().mapToInt(PlayerMetricsResponse::losses).sum();
        int kills = metrics.stream().mapToInt(PlayerMetricsResponse::kills).sum();
        int deaths = metrics.stream().mapToInt(PlayerMetricsResponse::deaths).sum();
        int assists = metrics.stream().mapToInt(PlayerMetricsResponse::assists).sum();

        return new TeamRosterPlayerStatsResponse(
                gamesPlayed,
                wins,
                losses,
                divide(wins * 100, gamesPlayed),
                divide(kills + assists, Math.max(deaths, 1)),
                divide(kills, gamesPlayed),
                divide(deaths, gamesPlayed),
                divide(assists, gamesPlayed));
    }

    private static BigDecimal divide(int numerator, int denominator) {
        if (denominator == 0) {
            return BigDecimal.ZERO.setScale(SCALE);
        }

        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), SCALE, RoundingMode.HALF_UP);
    }
}
