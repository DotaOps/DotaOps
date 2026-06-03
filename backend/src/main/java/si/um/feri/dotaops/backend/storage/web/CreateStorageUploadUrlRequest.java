package si.um.feri.dotaops.backend.storage.web;

import com.fasterxml.jackson.annotation.JsonAlias;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateStorageUploadUrlRequest(
        @JsonAlias("file_name")
        @NotBlank
        @Size(max = 255)
        String fileName,

        @JsonAlias("content_type")
        @NotBlank
        @Size(max = 100)
        String contentType,

        @JsonAlias("file_size_bytes")
        @NotNull
        @Positive
        Long fileSizeBytes
) {
}
