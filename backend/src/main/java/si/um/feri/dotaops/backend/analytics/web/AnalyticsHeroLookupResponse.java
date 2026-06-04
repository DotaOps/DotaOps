package si.um.feri.dotaops.backend.analytics.web;

import java.util.UUID;

import si.um.feri.dotaops.backend.analytics.repository.AnalyticsLookupRepository;

public record AnalyticsHeroLookupResponse(
        UUID heroId,
        Integer dotaHeroId,
        String name,
        String localizedName,
        String imageUrl,
        String iconUrl
) {

    public static AnalyticsHeroLookupResponse from(AnalyticsLookupRepository.HeroLookup lookup) {
        return new AnalyticsHeroLookupResponse(
                lookup.heroId(),
                lookup.dotaHeroId(),
                lookup.name(),
                lookup.localizedName(),
                lookup.imageUrl(),
                lookup.iconUrl());
    }
}
