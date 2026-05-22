package si.um.feri.dotaops.backend.opendota.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import si.um.feri.dotaops.backend.common.error.BadRequestException;
import si.um.feri.dotaops.backend.opendota.domain.OpenDotaHeroResponse;
import si.um.feri.dotaops.backend.opendota.repository.HeroRepository;
import si.um.feri.dotaops.backend.opendota.repository.HeroUpsertCommand;
import si.um.feri.dotaops.backend.opendota.web.HeroSyncResponse;

@Service
public class HeroSyncService {

    private static final String OPEN_DOTA_HERO_NAME_PREFIX = "npc_dota_hero_";
    private static final String HERO_ASSET_BASE_URL =
            "https://cdn.cloudflare.steamstatic.com/apps/dota2/images/dota_react/heroes";

    private final OpenDotaClient openDotaClient;
    private final HeroRepository heroRepository;

    public HeroSyncService(OpenDotaClient openDotaClient, HeroRepository heroRepository) {
        this.openDotaClient = openDotaClient;
        this.heroRepository = heroRepository;
    }

    @Transactional
    public HeroSyncResponse syncHeroes() {
        OffsetDateTime startedAt = OffsetDateTime.now(ZoneOffset.UTC);
        int insertedCount = 0;
        int updatedCount = 0;

        for (OpenDotaHeroResponse hero : openDotaClient.fetchHeroes()) {
            HeroUpsertCommand command = toCommand(hero);
            boolean existing = heroRepository.existsByDotaHeroId(command.dotaHeroId());

            heroRepository.upsert(command);

            if (existing) {
                updatedCount++;
            } else {
                insertedCount++;
            }
        }

        OffsetDateTime finishedAt = OffsetDateTime.now(ZoneOffset.UTC);
        return new HeroSyncResponse(
                insertedCount + updatedCount,
                insertedCount,
                updatedCount,
                startedAt,
                finishedAt);
    }

    private HeroUpsertCommand toCommand(OpenDotaHeroResponse hero) {
        if (hero.id() == null || hero.id() <= 0) {
            throw new BadRequestException("OpenDota hero id is required.");
        }
        if (!StringUtils.hasText(hero.name())) {
            throw new BadRequestException("OpenDota hero name is required.");
        }
        if (!StringUtils.hasText(hero.localizedName())) {
            throw new BadRequestException("OpenDota localized hero name is required.");
        }

        String assetName = assetName(hero.name());
        String slug = slug(assetName);

        return new HeroUpsertCommand(
                hero.id(),
                hero.name().trim(),
                hero.localizedName().trim(),
                slug,
                hero.roles(),
                HERO_ASSET_BASE_URL + "/" + assetName + ".png",
                HERO_ASSET_BASE_URL + "/icons/" + assetName + ".png");
    }

    private String assetName(String openDotaName) {
        String normalized = openDotaName.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith(OPEN_DOTA_HERO_NAME_PREFIX)) {
            return normalized.substring(OPEN_DOTA_HERO_NAME_PREFIX.length());
        }

        return normalized.replaceAll("[^a-z0-9_]+", "_").replaceAll("^_+|_+$", "");
    }

    private String slug(String assetName) {
        String slug = assetName.replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (!StringUtils.hasText(slug)) {
            throw new BadRequestException("OpenDota hero slug could not be derived.");
        }

        return slug;
    }
}
