package si.um.feri.dotaops.backend.opendota.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import si.um.feri.dotaops.backend.opendota.domain.Hero;
import si.um.feri.dotaops.backend.opendota.domain.OpenDotaHeroResponse;
import si.um.feri.dotaops.backend.opendota.repository.HeroRepository;
import si.um.feri.dotaops.backend.opendota.repository.HeroUpsertCommand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HeroSyncServiceTest {

    private final OpenDotaClient openDotaClient = mock(OpenDotaClient.class);
    private final HeroRepository heroRepository = mock(HeroRepository.class);
    private final HeroSyncService service = new HeroSyncService(openDotaClient, heroRepository);

    @Test
    void syncHeroesInsertsNewHeroes() {
        when(openDotaClient.fetchHeroes()).thenReturn(List.of(hero("Anti-Mage")));
        when(heroRepository.existsByDotaHeroId(1)).thenReturn(false);
        when(heroRepository.upsert(any(HeroUpsertCommand.class))).thenReturn(storedHero("Anti-Mage"));

        var response = service.syncHeroes();

        assertThat(response.syncedCount()).isOne();
        assertThat(response.insertedCount()).isOne();
        assertThat(response.updatedCount()).isZero();
        assertThat(response.startedAt()).isNotNull();
        assertThat(response.finishedAt()).isNotNull();
    }

    @Test
    void syncHeroesUpdatesExistingHeroesWithoutDuplicating() {
        when(openDotaClient.fetchHeroes()).thenReturn(List.of(hero("Anti-Mage")));
        when(heroRepository.existsByDotaHeroId(1)).thenReturn(true);
        when(heroRepository.upsert(any(HeroUpsertCommand.class))).thenReturn(storedHero("Anti-Mage"));

        var response = service.syncHeroes();

        assertThat(response.syncedCount()).isOne();
        assertThat(response.insertedCount()).isZero();
        assertThat(response.updatedCount()).isOne();
    }

    @Test
    void syncHeroesMapsRolesAndAssetUrlsFromOpenDotaName() {
        when(openDotaClient.fetchHeroes()).thenReturn(List.of(new OpenDotaHeroResponse(
                108,
                "npc_dota_hero_abyssal_underlord",
                "Underlord",
                List.of("Support", "Nuker"))));
        when(heroRepository.existsByDotaHeroId(108)).thenReturn(false);
        when(heroRepository.upsert(any(HeroUpsertCommand.class))).thenReturn(storedHero("Underlord"));
        ArgumentCaptor<HeroUpsertCommand> commandCaptor = ArgumentCaptor.forClass(HeroUpsertCommand.class);

        service.syncHeroes();

        org.mockito.Mockito.verify(heroRepository).upsert(commandCaptor.capture());
        HeroUpsertCommand command = commandCaptor.getValue();
        assertThat(command.dotaHeroId()).isEqualTo(108);
        assertThat(command.name()).isEqualTo("npc_dota_hero_abyssal_underlord");
        assertThat(command.localizedName()).isEqualTo("Underlord");
        assertThat(command.slug()).isEqualTo("abyssal-underlord");
        assertThat(command.roles()).containsExactly("Support", "Nuker");
        assertThat(command.imageUrl())
                .isEqualTo("https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/abyssal_underlord.png");
        assertThat(command.iconUrl())
                .isEqualTo("https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/abyssal_underlord.png");
    }

    @Test
    void syncHeroesUpdatesChangedLocalizedNameOnSameDotaHeroId() {
        when(openDotaClient.fetchHeroes()).thenReturn(List.of(hero("Anti-Mage Updated")));
        when(heroRepository.existsByDotaHeroId(1)).thenReturn(true);
        when(heroRepository.upsert(any(HeroUpsertCommand.class))).thenReturn(storedHero("Anti-Mage Updated"));
        ArgumentCaptor<HeroUpsertCommand> commandCaptor = ArgumentCaptor.forClass(HeroUpsertCommand.class);

        service.syncHeroes();

        org.mockito.Mockito.verify(heroRepository).upsert(commandCaptor.capture());
        assertThat(commandCaptor.getValue().dotaHeroId()).isEqualTo(1);
        assertThat(commandCaptor.getValue().localizedName()).isEqualTo("Anti-Mage Updated");
    }

    private static OpenDotaHeroResponse hero(String localizedName) {
        return new OpenDotaHeroResponse(
                1,
                "npc_dota_hero_antimage",
                localizedName,
                List.of("Carry", "Escape"));
    }

    private static Hero storedHero(String localizedName) {
        OffsetDateTime now = OffsetDateTime.parse("2026-05-21T00:00:00Z");

        return new Hero(
                UUID.fromString("11111111-1111-4111-8111-111111111111"),
                1,
                "npc_dota_hero_antimage",
                localizedName,
                "antimage",
                List.of("Carry", "Escape"),
                "https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/antimage.png",
                "https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes/icons/antimage.png",
                now,
                now);
    }
}
