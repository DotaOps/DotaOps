package si.um.feri.dotaops.backend.storage.service;

public record SignedStorageUpload(
        String bucket,
        String path,
        String signedUrl,
        String token,
        String publicUrl,
        long expiresInSeconds
) {
}
