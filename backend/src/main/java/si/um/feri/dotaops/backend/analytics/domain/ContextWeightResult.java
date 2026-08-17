package si.um.feri.dotaops.backend.analytics.domain;

import java.math.BigDecimal;
import java.util.List;

public record ContextWeightResult(
        BigDecimal weight,
        ContextWeightClassification classification,
        List<ContextWeightReason> reasons,
        String message
) {

    public ContextWeightResult {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }

    public boolean isAdjusted() {
        return weight.compareTo(BigDecimal.ONE) < 0;
    }
}
