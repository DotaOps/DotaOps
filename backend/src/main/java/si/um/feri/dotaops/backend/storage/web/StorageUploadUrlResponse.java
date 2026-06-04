package si.um.feri.dotaops.backend.storage.web;

import java.util.Map;

import si.um.feri.dotaops.backend.storage.service.SignedStorageUpload;

public record StorageUploadUrlResponse(
        String bucket,
        String path,
        String uploadUrl,
        String uploadToken,
        String uploadMethod,
        Map<String, String> requiredHeaders,
        String publicUrl,
        long expiresInSeconds,
        long maxFileSizeBytes,
        String contentType,
        boolean upsert
) {

    public static StorageUploadUrlResponse from(
            SignedStorageUpload upload,
            long maxFileSizeBytes,
            String contentType
    ) {
        return new StorageUploadUrlResponse(
                upload.bucket(),
                upload.path(),
                upload.signedUrl(),
                upload.token(),
                "PUT",
                Map.of(
                        "cache-control", "max-age=3600",
                        "content-type", contentType,
                        "x-upsert", "true"),
                upload.publicUrl(),
                upload.expiresInSeconds(),
                maxFileSizeBytes,
                contentType,
                true);
    }
}
