package si.um.feri.dotaops.backend.storage.web;

import com.fasterxml.jackson.annotation.JsonAlias;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConfirmStorageUploadRequest(
        @NotBlank
        @Size(max = 128)
        String bucket,

        @NotBlank
        @Size(max = 512)
        String path,

        @JsonAlias("public_url")
        @Size(max = 2048)
        String publicUrl
) {
}
