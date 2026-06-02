package si.um.feri.dotaops.backend.analytics.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.um.feri.dotaops.backend.analytics.domain.AnalyticsFilters;
import si.um.feri.dotaops.backend.analytics.repository.AnalyticsRepository;
import si.um.feri.dotaops.backend.analytics.web.HeroMetricsResponse;
import si.um.feri.dotaops.backend.analytics.web.PlayerMetricsResponse;
import si.um.feri.dotaops.backend.analytics.web.TeamMetricsResponse;
import si.um.feri.dotaops.backend.analytics.web.TournamentMetricsResponse;
import si.um.feri.dotaops.backend.common.error.ResourceNotFoundException;

@Service
public class AnalyticsQueryService {

    private final AnalyticsRepository analyticsRepository;

    public AnalyticsQueryService(AnalyticsRepository analyticsRepository) {
        this.analyticsRepository = analyticsRepository;
    }

    @Transactional(readOnly = true)
    public List<PlayerMetricsResponse> playerMetrics(AnalyticsFilters filters) {
        return analyticsRepository.findPlayerMetrics(filters)
                .stream()
                .map(PlayerMetricsResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PlayerMetricsResponse> protectedPlayerMetrics(AnalyticsFilters filters) {
        return analyticsRepository.findProtectedPlayerMetrics(filters)
                .stream()
                .map(PlayerMetricsResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TeamMetricsResponse> teamMetrics(AnalyticsFilters filters) {
        return analyticsRepository.findTeamMetrics(filters)
                .stream()
                .map(TeamMetricsResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TeamMetricsResponse> protectedTeamMetrics(AnalyticsFilters filters) {
        return analyticsRepository.findProtectedTeamMetrics(filters)
                .stream()
                .map(TeamMetricsResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<HeroMetricsResponse> heroMetrics(AnalyticsFilters filters) {
        return analyticsRepository.findHeroMetrics(filters)
                .stream()
                .map(HeroMetricsResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<HeroMetricsResponse> protectedHeroMetrics(AnalyticsFilters filters) {
        return analyticsRepository.findProtectedHeroMetrics(filters)
                .stream()
                .map(HeroMetricsResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TournamentMetricsResponse> tournamentMetrics(AnalyticsFilters filters) {
        return analyticsRepository.findTournamentMetrics(filters)
                .stream()
                .map(TournamentMetricsResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TournamentMetricsResponse tournamentMetrics(UUID tournamentId) {
        return analyticsRepository.findTournamentMetricsById(tournamentId)
                .map(TournamentMetricsResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament analytics", "tournamentId", tournamentId));
    }

    @Transactional(readOnly = true)
    public java.util.Optional<TournamentMetricsResponse> protectedTournamentMetrics(UUID tournamentId) {
        return analyticsRepository.findProtectedTournamentMetricsById(tournamentId)
                .map(TournamentMetricsResponse::from);
    }
}
