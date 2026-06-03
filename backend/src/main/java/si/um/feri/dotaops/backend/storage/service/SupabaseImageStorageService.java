package si.um.feri.dotaops.backend.storage.service;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

import si.um.feri.dotaops.backend.common.error.BadRequestException;
import si.um.feri.dotaops.backend.common.error.ConflictException;
import si.um.feri.dotaops.backend.config.properties.SupabaseStorageProperties;

@Service
public class SupabaseImageStorageService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SupabaseImageStorageService.class);
    private static final long SIGNED_UPLOAD_TTL_SECONDS = 2L * 60L * 60L;
    private static final long MAX_IMAGE_BYTES = 5L * 1024L * 1024L;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/gif",
            "image/jpeg",
            "image/png",
            "image/webp");
    private static final Set<String> ALLOWED_TEAM_IMAGE_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp");

    private final SupabaseStorageProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SupabaseImageStorageService(
            SupabaseStorageProperties properties,
            RestClient.Builder restClientBuilder
    ) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    public StoredImage storeProfileAvatar(UUID profileId, MultipartFile avatar) {
        return storeImage("profiles/%s/avatars".formatted(profileId), "Avatar", avatar);
    }

    public StoredImage storeTeamLogo(UUID teamId, MultipartFile logo) {
        return storeImage(
                "teams/%s/logo".formatted(teamId),
                "Team logo",
                logo,
                ALLOWED_TEAM_IMAGE_CONTENT_TYPES,
                "png, jpeg or webp");
    }

    public StoredImage storeTeamBanner(UUID teamId, MultipartFile banner) {
        return storeImage(
                "teams/%s/banner".formatted(teamId),
                "Team banner",
                banner,
                ALLOWED_TEAM_IMAGE_CONTENT_TYPES,
                "png, jpeg or webp");
    }

    public StoredImage storeImage(String folderPath, MultipartFile image) {
        return storeImage(folderPath, "Image", image);
    }

    public SignedStorageUpload createSignedUploadUrl(String bucket, String path) {
        StorageTarget target = storageTarget(bucket);
        String normalizedPath = normalizeObjectPath(path);

        try {
            String responseBody = restClient.post()
                    .uri(signedUploadUri(target, normalizedPath))
                    .header("apikey", target.serviceRoleKey())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + target.serviceRoleKey())
                    .header("x-upsert", "true")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of())
                    .retrieve()
                    .body(String.class);
            JsonNode response = parseJson(responseBody);
            String signedUrl = resolveSignedUploadUrl(target, response);
            String token = textValue(response, "token");
            if (!StringUtils.hasText(token)) {
                token = tokenFromSignedUrl(signedUrl);
            }
            if (!StringUtils.hasText(token)) {
                throw new ConflictException("Supabase signed upload token was not returned.");
            }

            return new SignedStorageUpload(
                    target.bucket(),
                    normalizedPath,
                    signedUrl,
                    token,
                    publicUrl(target, normalizedPath),
                    SIGNED_UPLOAD_TTL_SECONDS);
        } catch (RestClientException exception) {
            LOGGER.warn("Supabase signed upload URL creation failed for bucket {} and path {}.",
                    target.bucket(), normalizedPath, exception);
            throw new ConflictException("Supabase signed upload URL could not be created.");
        }
    }

    public String publicUrl(String bucket, String path) {
        StorageTarget target = storageTarget(bucket);
        return publicUrl(target, normalizeObjectPath(path));
    }

    private StoredImage storeImage(String folderPath, String label, MultipartFile image) {
        return storeImage(folderPath, label, image, ALLOWED_CONTENT_TYPES, "png, jpeg, webp or gif");
    }

    private StoredImage storeImage(
            String folderPath,
            String label,
            MultipartFile image,
            Set<String> allowedContentTypes,
            String allowedDescription
    ) {
        String contentType = validateImage(label, image, allowedContentTypes, allowedDescription);
        String path = "%s/%s%s".formatted(
                normalizeObjectPath(folderPath),
                UUID.randomUUID(),
                extensionFor(label, contentType));

        return upload(path, image, contentType);
    }

    private StoredImage upload(String path, MultipartFile image, String contentType) {
        StorageTarget target = storageTarget();
        String normalizedPath = normalizeObjectPath(path);
        byte[] bytes = readBytes(image);

        try {
            restClient.post()
                    .uri(uploadUri(target, normalizedPath))
                    .header("apikey", target.serviceRoleKey())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + target.serviceRoleKey())
                    .header(HttpHeaders.CACHE_CONTROL, "3600")
                    .header("x-upsert", "true")
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(bytes)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            LOGGER.warn("Supabase image upload failed for bucket {} and path {}.", target.bucket(), normalizedPath, exception);
            throw new ConflictException("Image could not be uploaded to Supabase Storage.");
        }

        return new StoredImage(normalizedPath, publicUrl(target, normalizedPath), contentType);
    }

    private String validateImage(
            String label,
            MultipartFile image,
            Set<String> allowedContentTypes,
            String allowedDescription
    ) {
        if (image == null || image.isEmpty()) {
            throw new BadRequestException(label + " file is required.");
        }

        if (image.getSize() > MAX_IMAGE_BYTES) {
            throw new BadRequestException(label + " image must be 5MB or smaller.");
        }

        String contentType = normalizeContentType(label, image.getContentType());
        if (!allowedContentTypes.contains(contentType)) {
            throw new BadRequestException(label + " must be a " + allowedDescription + " image.");
        }

        return contentType;
    }

    private byte[] readBytes(MultipartFile image) {
        try {
            return image.getBytes();
        } catch (IOException exception) {
            throw new BadRequestException("Image file could not be read.");
        }
    }

    private StorageTarget storageTarget() {
        return storageTarget(properties.imagesBucket());
    }

    private StorageTarget storageTarget(String bucket) {
        if (!StringUtils.hasText(properties.url())
                || !StringUtils.hasText(properties.serviceRoleKey())
                || !StringUtils.hasText(bucket)) {
            throw new ConflictException("Supabase image storage is not configured.");
        }

        return new StorageTarget(
                properties.url(),
                properties.serviceRoleKey(),
                bucket);
    }

    private URI uploadUri(StorageTarget target, String path) {
        return URI.create(target.url()
                + "/storage/v1/object/"
                + encodePath(target.bucket())
                + "/"
                + encodePath(path));
    }

    private URI signedUploadUri(StorageTarget target, String path) {
        return URI.create(target.url()
                + "/storage/v1/object/upload/sign/"
                + encodePath(target.bucket())
                + "/"
                + encodePath(path));
    }

    private String publicUrl(StorageTarget target, String path) {
        return target.url()
                + "/storage/v1/object/public/"
                + encodePath(target.bucket())
                + "/"
                + encodePath(path);
    }

    private String normalizeObjectPath(String path) {
        if (!StringUtils.hasText(path)) {
            throw new BadRequestException("Image path is required.");
        }

        String normalized = path.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        String[] segments = normalized.split("/");
        boolean invalid = Arrays.stream(segments)
                .anyMatch(segment -> !StringUtils.hasText(segment)
                        || ".".equals(segment)
                        || "..".equals(segment));
        if (invalid) {
            throw new BadRequestException("Image path is invalid.");
        }

        return String.join("/", segments);
    }

    private String resolveSignedUploadUrl(StorageTarget target, JsonNode response) {
        String signedUrl = textValue(response, "signedUrl");
        if (!StringUtils.hasText(signedUrl)) {
            signedUrl = textValue(response, "signedURL");
        }
        if (!StringUtils.hasText(signedUrl)) {
            signedUrl = textValue(response, "url");
        }
        if (!StringUtils.hasText(signedUrl)) {
            throw new ConflictException("Supabase signed upload URL was not returned.");
        }

        return resolveStorageUrl(target.url(), signedUrl);
    }

    private JsonNode parseJson(String responseBody) {
        try {
            JsonNode response = objectMapper.readTree(responseBody);
            if (response == null || !response.isObject()) {
                throw new ConflictException("Supabase signed upload URL response is invalid.");
            }

            return response;
        } catch (ConflictException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ConflictException("Supabase signed upload URL response is invalid.");
        }
    }

    private String resolveStorageUrl(String baseUrl, String value) {
        String normalized = value.trim();
        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            return normalized;
        }
        if (normalized.startsWith("/storage/v1/")) {
            return baseUrl + normalized;
        }
        if (normalized.startsWith("/object/")) {
            return baseUrl + "/storage/v1" + normalized;
        }
        if (normalized.startsWith("object/")) {
            return baseUrl + "/storage/v1/" + normalized;
        }
        if (normalized.startsWith("/")) {
            return baseUrl + normalized;
        }

        return baseUrl + "/storage/v1/" + normalized;
    }

    private String textValue(JsonNode response, String field) {
        if (response == null || !response.hasNonNull(field)) {
            return null;
        }

        String value = response.get(field).asText();
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String tokenFromSignedUrl(String signedUrl) {
        URI uri = URI.create(signedUrl);
        String query = uri.getRawQuery();
        if (!StringUtils.hasText(query)) {
            return null;
        }

        for (String parameter : query.split("&")) {
            int separator = parameter.indexOf('=');
            String name = separator >= 0 ? parameter.substring(0, separator) : parameter;
            if ("token".equals(name)) {
                String value = separator >= 0 ? parameter.substring(separator + 1) : "";
                return UriUtils.decode(value, StandardCharsets.UTF_8);
            }
        }

        return null;
    }

    private String encodePath(String path) {
        return Arrays.stream(path.split("/"))
                .map(segment -> UriUtils.encodePathSegment(segment, StandardCharsets.UTF_8))
                .collect(Collectors.joining("/"));
    }

    private String normalizeContentType(String label, String contentType) {
        if (!StringUtils.hasText(contentType)) {
            throw new BadRequestException(label + " content type is required.");
        }

        return contentType.trim().toLowerCase(Locale.ROOT);
    }

    private String extensionFor(String label, String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/jpeg" -> ".jpg";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> throw new BadRequestException(label + " content type is not supported.");
        };
    }

    private record StorageTarget(
            String url,
            String serviceRoleKey,
            String bucket
    ) {
    }
}
