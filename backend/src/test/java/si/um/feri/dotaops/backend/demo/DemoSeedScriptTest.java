package si.um.feri.dotaops.backend.demo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class DemoSeedScriptTest {

    @Test
    void demoSeedSqlContainsSafetyMarkersAndIdempotentWrites() throws IOException {
        String seed = classpathText("db/demo/demo-seed.sql");

        assertThat(seed)
                .contains("DO NOT RUN ON PRODUCTION")
                .contains("@dotaops.local")
                .contains("DotaOps Demo Cup")
                .contains("npc_dota_hero_ringmaster")
                .contains("npc_dota_hero_kez")
                .contains("npc_dota_hero_largo")
                .contains("on conflict")
                .contains("private.refresh_dotaops_analytics()");
        assertThat(seed)
                .doesNotContain("SUPABASE_SERVICE_ROLE_KEY")
                .doesNotContain("service_role_key")
                .doesNotContainPattern("(?i)@(gmail|yahoo|hotmail|outlook)\\.");
    }

    @Test
    void resetSqlDeletesStableDemoRecordsWithoutTruncate() throws IOException {
        String reset = classpathText("db/demo/reset-demo-seed.sql");

        assertThat(reset)
                .contains("DO NOT RUN ON PRODUCTION")
                .contains("profile:player-30")
                .contains("tournament:demo-cup")
                .contains("team:radiant-wolves");
        assertThat(reset.toLowerCase()).doesNotContain("truncate");
    }

    @Test
    void verifySqlCoversRequiredDemoEntities() throws IOException {
        String verify = classpathText("db/demo/verify-demo-seed.sql");

        assertThat(verify)
                .contains("demo organizer profile exists")
                .contains("approved demo cup registrations exist")
                .contains("dota hero reference catalog exists")
                .contains("playoff bracket matches exist")
                .contains("match games exist")
                .contains("match players exist")
                .contains("analytics player data exists");
    }

    @Test
    void powershellWrapperRequiresExplicitConfirmation() throws IOException {
        Path wrapper = Path.of(System.getProperty("user.dir"))
                .resolve("../scripts/seed-demo.ps1")
                .normalize();
        String script = Files.readString(wrapper, StandardCharsets.UTF_8);

        assertThat(script)
                .contains("ConfirmDemoSeed")
                .contains("ResetFirst")
                .contains("AllowProductionTarget")
                .contains("ON_ERROR_STOP=1");
    }

    private String classpathText(String location) throws IOException {
        ClassPathResource resource = new ClassPathResource(location);

        assertThat(resource.exists())
                .as("Expected classpath resource %s to exist", location)
                .isTrue();

        return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
}
