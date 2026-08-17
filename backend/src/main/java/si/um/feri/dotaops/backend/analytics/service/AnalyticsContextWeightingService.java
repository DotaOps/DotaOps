package si.um.feri.dotaops.backend.analytics.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;

import si.um.feri.dotaops.backend.analytics.domain.ContextWeightClassification;
import si.um.feri.dotaops.backend.analytics.domain.ContextWeightInput;
import si.um.feri.dotaops.backend.analytics.domain.ContextWeightReason;
import si.um.feri.dotaops.backend.analytics.domain.ContextWeightResult;

@Service
public class AnalyticsContextWeightingService {

    private static final BigDecimal FULL_WEIGHT = new BigDecimal("1.00");
    private static final BigDecimal MIN_WEIGHT = new BigDecimal("0.35");
    private static final BigDecimal SUPPORT_PROTECTION_MULTIPLIER = new BigDecimal("0.60");
    private static final BigDecimal LOW_KDA_HARD = new BigDecimal("0.60");
    private static final BigDecimal LOW_KDA_MEDIUM = new BigDecimal("1.00");
    private static final BigDecimal LOW_KDA_LIGHT = new BigDecimal("1.50");

    public ContextWeightResult evaluate(ContextWeightInput input) {
        if (input == null || hasInsufficientBaseline(input)) {
            return new ContextWeightResult(
                    FULL_WEIGHT,
                    ContextWeightClassification.LOW_CONFIDENCE,
                    List.of(ContextWeightReason.INSUFFICIENT_BASELINE),
                    "Not enough match context is available for reliable context weighting.");
        }

        Set<ContextWeightReason> reasons = new LinkedHashSet<>();
        BigDecimal penalty = BigDecimal.ZERO;

        penalty = penalty.add(deathPenalty(input, reasons));
        penalty = penalty.add(kdaPenalty(input, reasons));
        penalty = penalty.add(objectivePenalty(input, reasons));
        boolean teamDisadvantage = hasTeamScoreDisadvantage(input);
        if (teamDisadvantage) {
            reasons.add(ContextWeightReason.TEAM_SCORE_DISADVANTAGE);
            penalty = penalty.add(new BigDecimal("0.18"));
        }
        if (hasLowNetWorthOrLevel(input)) {
            reasons.add(ContextWeightReason.LOW_NET_WORTH_OR_LEVEL);
            penalty = penalty.add(new BigDecimal("0.08"));
        }
        if (isStompLoss(input, reasons, teamDisadvantage)) {
            reasons.add(ContextWeightReason.STOMP_LOSS_CONTEXT);
            penalty = penalty.add(new BigDecimal("0.15"));
        }
        if (hasSupportImpact(input) && penalty.compareTo(BigDecimal.ZERO) > 0) {
            reasons.add(ContextWeightReason.SUPPORT_IMPACT_PROTECTED);
            penalty = penalty.multiply(SUPPORT_PROTECTION_MULTIPLIER);
        }

        BigDecimal weight = FULL_WEIGHT.subtract(penalty);
        if (weight.compareTo(MIN_WEIGHT) < 0) {
            weight = MIN_WEIGHT;
        }
        if (weight.compareTo(FULL_WEIGHT) > 0) {
            weight = FULL_WEIGHT;
        }
        weight = weight.setScale(2, RoundingMode.HALF_UP);

        ContextWeightClassification classification = classification(input, reasons, weight);
        return new ContextWeightResult(
                weight,
                classification,
                new ArrayList<>(reasons),
                messageFor(classification));
    }

    private boolean hasInsufficientBaseline(ContextWeightInput input) {
        return input.deaths() == null
                && input.kda() == null
                && input.goldPerMin() == null
                && input.xpPerMin() == null
                && input.heroDamage() == null
                && input.towerDamage() == null
                && input.won() == null;
    }

    private BigDecimal deathPenalty(ContextWeightInput input, Set<ContextWeightReason> reasons) {
        if (input.deaths() == null || input.deaths() < 7) {
            return BigDecimal.ZERO;
        }

        reasons.add(ContextWeightReason.HIGH_DEATHS);
        if (input.deaths() >= 14) {
            return new BigDecimal("0.30");
        }
        if (input.deaths() >= 10) {
            return new BigDecimal("0.20");
        }
        return new BigDecimal("0.08");
    }

    private BigDecimal kdaPenalty(ContextWeightInput input, Set<ContextWeightReason> reasons) {
        if (input.kda() == null || input.kda().compareTo(LOW_KDA_LIGHT) >= 0) {
            return BigDecimal.ZERO;
        }

        reasons.add(ContextWeightReason.LOW_KDA);
        if (input.kda().compareTo(LOW_KDA_HARD) < 0) {
            return new BigDecimal("0.30");
        }
        if (input.kda().compareTo(LOW_KDA_MEDIUM) < 0) {
            return new BigDecimal("0.20");
        }
        return new BigDecimal("0.08");
    }

    private BigDecimal objectivePenalty(ContextWeightInput input, Set<ContextWeightReason> reasons) {
        Integer towerDamage = input.towerDamage();
        if (towerDamage == null || towerDamage > 750) {
            return BigDecimal.ZERO;
        }

        boolean lowHeroDamage = input.heroDamage() != null && input.heroDamage() < 8000;
        boolean lowAssists = input.assists() != null && input.assists() < 6;
        boolean veryLowTowerDamage = towerDamage <= 250;
        if (!veryLowTowerDamage && !lowHeroDamage && !lowAssists) {
            return BigDecimal.ZERO;
        }

        reasons.add(ContextWeightReason.LOW_OBJECTIVE_PRESSURE);
        return veryLowTowerDamage ? new BigDecimal("0.12") : new BigDecimal("0.06");
    }

    private boolean hasTeamScoreDisadvantage(ContextWeightInput input) {
        if (input.teamSide() == null || input.radiantScore() == null || input.direScore() == null) {
            return false;
        }

        String side = input.teamSide().trim().toUpperCase();
        Integer ownScore = switch (side) {
            case "RADIANT" -> input.radiantScore();
            case "DIRE" -> input.direScore();
            default -> null;
        };
        Integer opponentScore = switch (side) {
            case "RADIANT" -> input.direScore();
            case "DIRE" -> input.radiantScore();
            default -> null;
        };
        if (ownScore == null || opponentScore == null || opponentScore <= 0) {
            return false;
        }

        return opponentScore - ownScore >= 20 || ownScore * 10 <= opponentScore * 6;
    }

    private boolean hasLowNetWorthOrLevel(ContextWeightInput input) {
        return input.netWorth() != null
                && input.level() != null
                && input.netWorth() < 9000
                && input.level() <= 12;
    }

    private boolean isStompLoss(
            ContextWeightInput input,
            Set<ContextWeightReason> reasons,
            boolean teamDisadvantage
    ) {
        if (!Boolean.FALSE.equals(input.won())) {
            return false;
        }

        return teamDisadvantage
                || (reasons.contains(ContextWeightReason.HIGH_DEATHS)
                && reasons.contains(ContextWeightReason.LOW_KDA));
    }

    private boolean hasSupportImpact(ContextWeightInput input) {
        return (input.assists() != null && input.assists() >= 12)
                || (input.heroHealing() != null && input.heroHealing() >= 800);
    }

    private ContextWeightClassification classification(
            ContextWeightInput input,
            Set<ContextWeightReason> reasons,
            BigDecimal weight
    ) {
        if (reasons.contains(ContextWeightReason.STOMP_LOSS_CONTEXT)
                || (Boolean.FALSE.equals(input.won())
                && reasons.contains(ContextWeightReason.TEAM_SCORE_DISADVANTAGE))) {
            return ContextWeightClassification.STOMP_LOSS;
        }
        if (weight.compareTo(FULL_WEIGHT) < 0) {
            return ContextWeightClassification.ROUGH_GAME;
        }
        return ContextWeightClassification.NORMAL;
    }

    private String messageFor(ContextWeightClassification classification) {
        return switch (classification) {
            case NORMAL -> "No rough-game adjustment was applied.";
            case ROUGH_GAME -> "This match has rough-game context, so trend insights reduce its influence.";
            case STOMP_LOSS -> "This match looks like a stomp loss, so trend insights reduce its influence more strongly.";
            case LOW_CONFIDENCE -> "Not enough match context is available for reliable context weighting.";
        };
    }
}
