package si.um.feri.dotaops.backend.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dotaops.supabase.storage")
public record SupabaseStorageProperties(
        String url,
        String serviceRoleKey,
        String imagesBucket,
        String avatarsBucket,
        String teamAssetsBucket
) {

    public SupabaseStorageProperties {
        url = normalizeUrl(url);
        serviceRoleKey = normalizeBlank(serviceRoleKey);
        imagesBucket = normalizeBlank(imagesBucket);
        if (imagesBucket == null) {
            imagesBucket = "dotaops-images";
        }
        avatarsBucket = normalizeBlank(avatarsBucket);
        if (avatarsBucket == null) {
            avatarsBucket = "avatars";
        }
        teamAssetsBucket = normalizeBlank(teamAssetsBucket);
        if (teamAssetsBucket == null) {
            teamAssetsBucket = "team-assets";
        }
    }

    private static String normalizeUrl(String value) {
        String normalized = normalizeBlank(value);
        if (normalized == null) {
            return null;
        }

        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String normalizeBlank(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}
