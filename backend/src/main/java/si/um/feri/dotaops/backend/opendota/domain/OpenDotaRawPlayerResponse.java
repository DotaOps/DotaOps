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
        @JsonProperty("item_0")
        Integer item0,
        @JsonProperty("item_1")
        Integer item1,
        @JsonProperty("item_2")
        Integer item2,
        @JsonProperty("item_3")
        Integer item3,
        @JsonProperty("item_4")
        Integer item4,
        @JsonProperty("item_5")
        Integer item5,
        @JsonProperty("backpack_0")
        Integer backpack0,
        @JsonProperty("backpack_1")
        Integer backpack1,
        @JsonProperty("backpack_2")
        Integer backpack2,
        @JsonProperty("item_neutral")
        Integer itemNeutral,
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
                item0,
                item1,
                item2,
                item3,
                item4,
                item5,
                backpack0,
                backpack1,
                backpack2,
                itemNeutral,
                rawPayload);
    }
}
