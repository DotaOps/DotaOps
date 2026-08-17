package si.um.feri.dotaops.backend.analytics.service;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import si.um.feri.dotaops.backend.analytics.domain.ContextWeightClassification;
import si.um.feri.dotaops.backend.analytics.domain.ContextWeightInput;
import si.um.feri.dotaops.backend.analytics.domain.ContextWeightReason;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsContextWeightingServiceTest {

    private final AnalyticsContextWeightingService service = new AnalyticsContextWeightingService();

    @Test
    void normalGameKeepsFullWeight() {
        var result = service.evaluate(input(
                8,
                3,
                14,
                "7.33",
                640,
                720,
                28000,
                5400,
                0,
                true));

        assertThat(result.weight()).isEqualByComparingTo("1.00");
        assertThat(result.classification()).isEqualTo(ContextWeightClassification.NORMAL);
        assertThat(result.reasons()).isEmpty();
        assertThat(result.isAdjusted()).isFalse();
    }

    @Test
    void stompLossWithHighDeathsReceivesMinimumWeightAndReasons() {
        var result = service.evaluate(new ContextWeightInput(
                2,
                16,
                2,
                new BigDecimal("0.50"),
                220,
                280,
                3000,
                100,
                0,
                40,
                1,
                5200,
                10,
                false,
                "RADIANT",
                8,
                42));

        assertThat(result.weight()).isEqualByComparingTo("0.35");
        assertThat(result.classification()).isEqualTo(ContextWeightClassification.STOMP_LOSS);
        assertThat(result.reasons()).contains(
                ContextWeightReason.HIGH_DEATHS,
                ContextWeightReason.LOW_KDA,
                ContextWeightReason.LOW_OBJECTIVE_PRESSURE,
                ContextWeightReason.TEAM_SCORE_DISADVANTAGE,
                ContextWeightReason.STOMP_LOSS_CONTEXT,
                ContextWeightReason.LOW_NET_WORTH_OR_LEVEL);
        assertThat(result.message()).contains("stomp loss");
    }

    @Test
    void supportImpactProtectsRoughGameFromFullPenalty() {
        var protectedSupport = service.evaluate(input(
                1,
                11,
                24,
                "1.20",
                260,
                340,
                5000,
                100,
                1800,
                false));
        var exposedCore = service.evaluate(input(
                1,
                11,
                2,
                "1.20",
                260,
                340,
                5000,
                100,
                0,
                false));

        assertThat(protectedSupport.weight()).isGreaterThan(exposedCore.weight());
        assertThat(protectedSupport.weight()).isEqualByComparingTo("0.67");
        assertThat(protectedSupport.reasons()).contains(ContextWeightReason.SUPPORT_IMPACT_PROTECTED);
        assertThat(protectedSupport.classification()).isEqualTo(ContextWeightClassification.STOMP_LOSS);
    }

    @Test
    void missingBaselineReturnsLowConfidenceWithoutPenalty() {
        var result = service.evaluate(new ContextWeightInput(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null));

        assertThat(result.weight()).isEqualByComparingTo("1.00");
        assertThat(result.classification()).isEqualTo(ContextWeightClassification.LOW_CONFIDENCE);
        assertThat(result.reasons()).containsExactly(ContextWeightReason.INSUFFICIENT_BASELINE);
    }

    @Test
    void extremePenaltyNeverDropsBelowFloor() {
        var result = service.evaluate(new ContextWeightInput(
                0,
                24,
                0,
                new BigDecimal("0.10"),
                120,
                140,
                1000,
                0,
                0,
                5,
                0,
                2500,
                6,
                false,
                "DIRE",
                60,
                6));

        assertThat(result.weight()).isEqualByComparingTo("0.35");
        assertThat(result.isAdjusted()).isTrue();
    }

    @Test
    void evaluateDoesNotMutateRawInputMetrics() {
        var input = new ContextWeightInput(
                1,
                20,
                1,
                new BigDecimal("0.20"),
                150,
                160,
                1200,
                0,
                0,
                12,
                0,
                null,
                null,
                false,
                null,
                null,
                null);

        service.evaluate(input);

        assertThat(input.kills()).isOne();
        assertThat(input.deaths()).isEqualTo(20);
        assertThat(input.kda()).isEqualByComparingTo("0.20");
        assertThat(input.goldPerMin()).isEqualTo(150);
        assertThat(input.towerDamage()).isZero();
    }

    private ContextWeightInput input(
            int kills,
            int deaths,
            int assists,
            String kda,
            int goldPerMin,
            int xpPerMin,
            int heroDamage,
            int towerDamage,
            int heroHealing,
            boolean won
    ) {
        return new ContextWeightInput(
                kills,
                deaths,
                assists,
                new BigDecimal(kda),
                goldPerMin,
                xpPerMin,
                heroDamage,
                towerDamage,
                heroHealing,
                180,
                6,
                null,
                null,
                won,
                null,
                null,
                null);
    }
}
