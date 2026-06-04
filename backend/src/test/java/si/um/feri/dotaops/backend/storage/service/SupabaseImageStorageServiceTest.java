package si.um.feri.dotaops.backend.storage.service;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import si.um.feri.dotaops.backend.common.error.BadRequestException;
import si.um.feri.dotaops.backend.common.error.ConflictException;
import si.um.feri.dotaops.backend.config.properties.SupabaseStorageProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SupabaseImageStorageServiceTest {

    private static final UUID PROFILE_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID TEAM_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");

    @Test
    void storeProfileAvatarUploadsImageToSupabaseBucket() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SupabaseImageStorageService service = new SupabaseImageStorageService(properties(), builder);
        MockMultipartFile avatar = new MockMultipartFile(
                "avatar",
                "avatar.png",
                "image/png",
                new byte[] {1, 2, 3});

        server.expect(requestTo(allOf(
                        startsWith("https://project.supabase.co/storage/v1/object/dotaops-images/profiles/"
                                + PROFILE_ID + "/avatars/"),
                        endsWith(".png"))))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("apikey", "service-role-key"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer service-role-key"))
                .andExpect(header("x-upsert", "true"))
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(new byte[] {1, 2, 3}))
                .andRespond(withSuccess());

        StoredImage stored = service.storeProfileAvatar(PROFILE_ID, avatar);

        assertThat(stored.path())
                .startsWith("profiles/" + PROFILE_ID + "/avatars/")
                .endsWith(".png");
        assertThat(stored.publicUrl())
                .startsWith("https://project.supabase.co/storage/v1/object/public/dotaops-images/profiles/"
                        + PROFILE_ID + "/avatars/")
                .endsWith(".png");
        assertThat(stored.contentType()).isEqualTo("image/png");
        server.verify();
    }

    @Test
    void storeProfileAvatarRejectsUnsupportedImageType() {
        SupabaseImageStorageService service = new SupabaseImageStorageService(properties(), RestClient.builder());
        MockMultipartFile avatar = new MockMultipartFile(
                "avatar",
                "avatar.svg",
                "image/svg+xml",
                "<svg />".getBytes());

        assertThatThrownBy(() -> service.storeProfileAvatar(PROFILE_ID, avatar))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Avatar must be a png, jpeg, webp or gif image.");
    }

    @Test
    void storeTeamLogoUploadsImageToTeamFolder() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SupabaseImageStorageService service = new SupabaseImageStorageService(properties(), builder);
        MockMultipartFile logo = new MockMultipartFile(
                "logo",
                "logo.webp",
                "image/webp",
                new byte[] {4, 5, 6});

        server.expect(requestTo(allOf(
                        startsWith("https://project.supabase.co/storage/v1/object/dotaops-images/teams/"
                                + TEAM_ID + "/logo/"),
                        endsWith(".webp"))))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.parseMediaType("image/webp")))
                .andExpect(content().bytes(new byte[] {4, 5, 6}))
                .andRespond(withSuccess());

        StoredImage stored = service.storeTeamLogo(TEAM_ID, logo);

        assertThat(stored.path())
                .startsWith("teams/" + TEAM_ID + "/logo/")
                .endsWith(".webp");
        assertThat(stored.publicUrl())
                .startsWith("https://project.supabase.co/storage/v1/object/public/dotaops-images/teams/"
                        + TEAM_ID + "/logo/")
                .endsWith(".webp");
        server.verify();
    }

    @Test
    void storeTeamBannerRejectsNonImageFile() {
        SupabaseImageStorageService service = new SupabaseImageStorageService(properties(), RestClient.builder());
        MockMultipartFile banner = new MockMultipartFile(
                "banner",
                "banner.txt",
                "text/plain",
                "not an image".getBytes());

        assertThatThrownBy(() -> service.storeTeamBanner(TEAM_ID, banner))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Team banner must be a png, jpeg or webp image.");
    }

    @Test
    void createSignedUploadUrlUsesSupabaseUploadSignEndpoint() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SupabaseImageStorageService service = new SupabaseImageStorageService(properties(), builder);
        String path = "profiles/" + PROFILE_ID + "/avatar.png";

        server.expect(requestTo("https://project.supabase.co/storage/v1/object/upload/sign/avatars/" + path))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("apikey", "service-role-key"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer service-role-key"))
                .andExpect(header("x-upsert", "true"))
                .andRespond(withSuccess(
                        "{\"url\":\"/object/upload/sign/avatars/" + path + "?token=signed-token\"}",
                        MediaType.APPLICATION_JSON));

        SignedStorageUpload upload = service.createSignedUploadUrl("avatars", path);

        assertThat(upload.bucket()).isEqualTo("avatars");
        assertThat(upload.path()).isEqualTo(path);
        assertThat(upload.signedUrl())
                .isEqualTo("https://project.supabase.co/storage/v1/object/upload/sign/avatars/"
                        + path + "?token=signed-token")
                .doesNotContain("service-role-key");
        assertThat(upload.token()).isEqualTo("signed-token");
        assertThat(upload.publicUrl())
                .isEqualTo("https://project.supabase.co/storage/v1/object/public/avatars/" + path);
        assertThat(upload.expiresInSeconds()).isEqualTo(7200);
        server.verify();
    }

    @Test
    void storeProfileAvatarRequiresSupabaseStorageConfiguration() {
        SupabaseImageStorageService service = new SupabaseImageStorageService(
                new SupabaseStorageProperties(null, "service-role-key", "dotaops-images", "avatars", "team-assets"),
                RestClient.builder());
        MockMultipartFile avatar = new MockMultipartFile(
                "avatar",
                "avatar.png",
                "image/png",
                new byte[] {1, 2, 3});

        assertThatThrownBy(() -> service.storeProfileAvatar(PROFILE_ID, avatar))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Supabase image storage is not configured.");
    }

    private static SupabaseStorageProperties properties() {
        return new SupabaseStorageProperties(
                "https://project.supabase.co/",
                "service-role-key",
                "dotaops-images",
                "avatars",
                "team-assets");
    }
}
