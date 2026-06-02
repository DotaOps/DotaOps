package si.um.feri.dotaops.backend.analytics.web;

import java.time.OffsetDateTime;
import java.util.UUID;

import si.um.feri.dotaops.backend.analytics.repository.RoleBasedAnalyticsRepository.RecentImport;

public record RecentImportResponse(
        UUID id,
        String dotaMatchId,
        String status,
        String errorCode,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt
) {

    public static RecentImportResponse from(RecentImport recentImport) {
        return new RecentImportResponse(
                recentImport.id(),
                recentImport.dotaMatchId(),
                recentImport.status(),
                recentImport.errorCode(),
                recentImport.createdAt(),
                recentImport.completedAt());
    }
}
