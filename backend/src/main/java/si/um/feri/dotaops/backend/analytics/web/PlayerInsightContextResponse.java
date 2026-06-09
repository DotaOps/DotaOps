package si.um.feri.dotaops.backend.analytics.web;

import java.math.BigDecimal;
import java.util.List;

import si.um.feri.dotaops.backend.analytics.domain.ContextWeightClassification;
import si.um.feri.dotaops.backend.analytics.domain.ContextWeightReason;
import si.um.feri.dotaops.backend.analytics.domain.ContextWeightResult;

public record PlayerInsightContextResponse(
        BigDecimal weight,
        ContextWeightClassification classification,
        List<ContextWeightReason> reasons,
        String message
) {

    public static PlayerInsightContextResponse from(ContextWeightResult result) {
        return new PlayerInsightContextResponse(
                result.weight(),
                result.classification(),
                result.reasons(),
                result.message());
    }
}
