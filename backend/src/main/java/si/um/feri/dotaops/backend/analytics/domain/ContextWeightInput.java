package si.um.feri.dotaops.backend.analytics.domain;

import java.math.BigDecimal;

public record ContextWeightInput(
        Integer kills,
        Integer deaths,
        Integer assists,
        BigDecimal kda,
        Integer goldPerMin,
        Integer xpPerMin,
        Integer heroDamage,
        Integer towerDamage,
        Integer heroHealing,
        Integer lastHits,
        Integer denies,
        Integer netWorth,
        Integer level,
        Boolean won,
        String teamSide,
        Integer radiantScore,
        Integer direScore
) {
}
