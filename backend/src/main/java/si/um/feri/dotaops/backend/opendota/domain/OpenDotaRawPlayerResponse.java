package si.um.feri.dotaops.backend.opendota.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenDotaRawPlayerResponse(
        @JsonProperty("account_id")
        Long accountId,
        @JsonProperty("player_slot")
        Integer playerSlot,
        @JsonProperty("hero_id")
        Integer heroId,
        Integer kills,
        Integer deaths,
        Integer assists,
        @JsonProperty("last_hits")
        Integer lastHits,
        Integer denies,
        @JsonProperty("gold_per_min")
        Integer goldPerMin,
        @JsonProperty("xp_per_min")
        Integer xpPerMin,
        @JsonProperty("net_worth")
        Integer netWorth,
        @JsonProperty("hero_damage")
        Integer heroDamage,
        @JsonProperty("tower_damage")
        Integer towerDamage,
        @JsonProperty("hero_healing")
        Integer heroHealing,
        Integer level,
        @JsonIgnore
        JsonNode rawPayload
) {

    public OpenDotaRawPlayerResponse withRawPayload(JsonNode rawPayload) {
        return new OpenDotaRawPlayerResponse(
                accountId,
                playerSlot,
                heroId,
                kills,
                deaths,
                assists,
                lastHits,
                denies,
                goldPerMin,
                xpPerMin,
                netWorth,
                heroDamage,
                towerDamage,
                heroHealing,
                level,
                rawPayload);
    }
}
