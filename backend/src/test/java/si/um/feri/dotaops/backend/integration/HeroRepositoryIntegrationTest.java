package si.um.feri.dotaops.backend.integration;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import si.um.feri.dotaops.backend.opendota.repository.HeroRepository;
import si.um.feri.dotaops.backend.opendota.repository.HeroUpsertCommand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("integration")
@EnabledIfEnvironmentVariable(named = "SUPABASE_DB_URL", matches = ".+")
class HeroRepositoryIntegrationTest extends PostgresIntegrationTestSupport {

    @Autowired
    private HeroRepository heroRepository;

    @Test
    void uniqueDotaHeroIdPreventsDuplicateHeroRows() {
        int dotaHeroId = uniqueDotaHeroId();
        HeroUpsertCommand command = command(dotaHeroId, "Anti-Mage");

        heroRepository.upsert(command);

        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                insert into public.heroes (dota_hero_id, name, localized_name, roles)
                values (?, ?, ?, '{}')
                """,
                dotaHeroId,
                "npc_dota_hero_duplicate",
                "Duplicate"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void upsertInsertsAndUpdatesExistingHeroByDotaHeroId() {
        int dotaHeroId = uniqueDotaHeroId();

        var inserted = heroRepository.upsert(command(dotaHeroId, "Anti-Mage"));
        var updated = heroRepository.upsert(command(dotaHeroId, "Anti-Mage Updated"));

        Integer heroCount = jdbcTemplate.queryForObject(
                "select count(*) from public.heroes where dota_hero_id = ?",
                Integer.class,
                dotaHeroId);

        assertThat(heroCount).isOne();
        assertThat(updated.id()).isEqualTo(inserted.id());
        assertThat(updated.localizedName()).isEqualTo("Anti-Mage Updated");
        assertThat(updated.updatedAt()).isAfterOrEqualTo(inserted.updatedAt());
    }

    @Test
    void upsertPersistsRolesAndAssetUrls() {
        int dotaHeroId = uniqueDotaHeroId();

        var hero = heroRepository.upsert(command(dotaHeroId, "Anti-Mage"));

        assertThat(hero.roles()).containsExactly("Carry", "Escape");
        assertThat(hero.imageUrl()).endsWith("/antimage.png");
        assertThat(hero.iconUrl()).endsWith("/icons/antimage.png");
    }

    private static HeroUpsertCommand command(int dotaHeroId, String localizedName) {
        return new HeroUpsertCommand(
                dotaHeroId,
                "npc_dota_hero_antimage",
                localizedName,
                "antimage-" + dotaHeroId,
                List.of("Carry", "Escape"),
                "https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/antimage.png",
                "https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/antimage.png");
    }

    private static int uniqueDotaHeroId() {
        return (int) Math.floorMod(java.util.UUID.randomUUID().getLeastSignificantBits(), 100_000) + 10_000;
    }
}
