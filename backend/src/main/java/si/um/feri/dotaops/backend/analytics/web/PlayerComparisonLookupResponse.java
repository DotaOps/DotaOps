package si.um.feri.dotaops.backend.analytics.web;

import java.util.List;

public record PlayerComparisonLookupResponse(
        String query,
        boolean exactMatch,
        boolean ambiguous,
        List<PlayerComparisonCandidateResponse> candidates
) {
}
