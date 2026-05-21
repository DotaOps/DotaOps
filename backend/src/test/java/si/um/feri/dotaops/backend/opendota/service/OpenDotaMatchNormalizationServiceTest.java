package si.um.feri.dotaops.backend.opendota.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import si.um.feri.dotaops.backend.opendota.domain.OpenDotaRawMatchResponse;

import static org.assertj.core.api.Assertions.assertThat;

class OpenDotaMatchNormalizationServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OpenDotaMatchNormalizationService service = new OpenDotaMatchNormalizationService(objectMapper);

    @Test
    void normalizesMatchGameSummaryAndTenPlayers() throws Exception {
        OpenDotaRawMatchResponse rawMatch = rawMatch("""
                {
                  "match_id": 7894561230,
                  "duration": 2410,
                  "start_time": 1778544000,
                  "radiant_win": false,
                  "game_mode": 22,
                  "lobby_type": 7,
                  "radiant_score": 31,
                  "dire_score": 42,
                  "players": [
                    {"account_id": 1001, "player_slot": 0, "hero_id": 1, "kills": 1, "deaths": 2, "assists": 3, "last_hits": 10, "denies": 1, "gold_per_min": 400, "xp_per_min": 500, "hero_damage": 1000, "tower_damage": 200, "hero_healing": 50, "net_worth": 9000, "level": 12, "item_0": 50, "item_1": 63, "item_2": 147, "item_3": 0, "item_4": 0, "item_5": 0, "backpack_0": 36, "backpack_1": 0, "backpack_2": 0, "item_neutral": 674},
                    {"account_id": 1002, "player_slot": 1, "hero_id": 2, "kills": 2, "deaths": 3, "assists": 4},
                    {"account_id": 1003, "player_slot": 2, "hero_id": 3, "kills": 3, "deaths": 4, "assists": 5},
                    {"account_id": 1004, "player_slot": 3, "hero_id": 4, "kills": 4, "deaths": 5, "assists": 6},
                    {"account_id": 1005, "player_slot": 4, "hero_id": 5, "kills": 5, "deaths": 6, "assists": 7},
                    {"account_id": 1006, "player_slot": 128, "hero_id": 6, "kills": 6, "deaths": 7, "assists": 8},
                    {"account_id": 1007, "player_slot": 129, "hero_id": 7, "kills": 7, "deaths": 8, "assists": 9},
                    {"account_id": 1008, "player_slot": 130, "hero_id": 8, "kills": 8, "deaths": 9, "assists": 10},
                    {"account_id": 1009, "player_slot": 131, "hero_id": 9, "kills": 9, "deaths": 10, "assists": 11},
                    {"account_id": 1010, "player_slot": 132, "hero_id": 10, "kills": 10, "deaths": 11, "assists": 12}
                  ]
                }
                """);

        var normalized = service.normalize("7894561230", rawMatch);

        assertThat(normalized.matchGame().dotaMatchId()).isEqualTo("7894561230");
        assertThat(normalized.matchGame().durationSeconds()).isEqualTo(2410);
        assertThat(normalized.matchGame().startedAt()).isNotNull();
        assertThat(normalized.matchGame().finishedAt()).isEqualTo(normalized.matchGame().startedAt().plusSeconds(2410));
        assertThat(normalized.matchGame().winnerSide()).isEqualTo("DIRE");
        assertThat(normalized.matchGame().gameMode()).isEqualTo(22);
        assertThat(normalized.matchGame().radiantScore()).isEqualTo(31);
        assertThat(normalized.matchGame().direScore()).isEqualTo(42);
        assertThat(normalized.matchGame().rawResponse()).contains("\"match_id\":7894561230");
        assertThat(normalized.matchGame().normalizedPayload())
                .contains("\"source\":\"OPENDOTA\"")
                .contains("\"playersNormalized\":10")
                .contains("\"radiantPlayers\":5")
                .contains("\"direPlayers\":5")
                .contains("\"durationSeconds\":2410");
        assertThat(normalized.players()).hasSize(10);
        assertThat(normalized.players().getFirst().teamSide()).isEqualTo("RADIANT");
        assertThat(normalized.players().getFirst().winner()).isFalse();
        assertThat(normalized.players().getFirst().dotaAccountId()).isEqualTo(1001L);
        assertThat(normalized.players().getFirst().dotaHeroId()).isOne();
        assertThat(normalized.players().getFirst().heroDamage()).isEqualTo(1000);
        assertThat(normalized.players().getFirst().durationSeconds()).isEqualTo(2410);
        assertThat(normalized.players().getFirst().items())
                .contains("\"item_0\":50")
                .contains("\"backpack_0\":36")
                .contains("\"item_neutral\":674");
        assertThat(normalized.players().get(5).teamSide()).isEqualTo("DIRE");
        assertThat(normalized.players().get(5).winner()).isTrue();
    }

    @Test
    void missingAccountIdAndUnknownSlotDoNotAbortNormalization() throws Exception {
        OpenDotaRawMatchResponse rawMatch = rawMatch("""
                {
                  "match_id": 7894561230,
                  "duration": 1800,
                  "radiant_win": true,
                  "players": [
                    {"player_slot": 64, "hero_id": 1, "kills": -1, "deaths": 2, "assists": 3},
                    {"player_slot": 200, "hero_id": 2, "kills": 4, "deaths": 5, "assists": 6}
                  ]
                }
                """);

        var normalized = service.normalize("7894561230", rawMatch);

        assertThat(normalized.players()).hasSize(2);
        assertThat(normalized.players().getFirst().dotaAccountId()).isNull();
        assertThat(normalized.players().getFirst().teamSide()).isEqualTo("RADIANT");
        assertThat(normalized.players().getFirst().kills()).isZero();
        assertThat(normalized.players().get(1).teamSide()).isEqualTo("DIRE");
    }

    private OpenDotaRawMatchResponse rawMatch(String json) throws Exception {
        JsonNode rawPayload = objectMapper.readTree(json);
        return objectMapper.treeToValue(rawPayload, OpenDotaRawMatchResponse.class)
                .withRawPayload(rawPayload);
    }
}
