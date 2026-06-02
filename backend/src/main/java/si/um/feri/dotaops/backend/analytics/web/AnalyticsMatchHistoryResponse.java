package si.um.feri.dotaops.backend.analytics.web;

import java.util.UUID;

public record AnalyticsMatchHistoryResponse(
        UUID matchId,
        UUID matchGameId,
        String dotaMatchId
) {
}
