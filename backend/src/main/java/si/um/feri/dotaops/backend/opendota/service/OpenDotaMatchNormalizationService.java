package si.um.feri.dotaops.backend.opendota.service;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.springframework.stereotype.Service;

import si.um.feri.dotaops.backend.opendota.domain.MatchGameImport;
import si.um.feri.dotaops.backend.opendota.domain.MatchPlayerImport;
import si.um.feri.dotaops.backend.opendota.domain.NormalizedMatchImport;
import si.um.feri.dotaops.backend.opendota.domain.OpenDotaRawMatchResponse;
import si.um.feri.dotaops.backend.opendota.domain.OpenDotaRawPlayerResponse;

@Service
public class OpenDotaMatchNormalizationService {

    private static final int NORMALIZATION_VERSION = 1;

    private final ObjectMapper objectMapper;

    public OpenDotaMatchNormalizationService() {
        this(new ObjectMapper());
    }

    OpenDotaMatchNormalizationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public NormalizedMatchImport normalize(String requestedDotaMatchId, OpenDotaRawMatchResponse rawMatch)
            throws Exception {
        List<MatchPlayerImport> players = extractPlayers(rawMatch);
        int radiantPlayers = (int) players.stream()
                .filter(player -> "RADIANT".equals(player.teamSide()))
                .count();
        int direPlayers = (int) players.stream()
                .filter(player -> "DIRE".equals(player.teamSide()))
                .count();
        String rawResponse = objectMapper.writeValueAsString(rawPayload(rawMatch));
        String normalizedPayload = objectMapper.writeValueAsString(normalizedPayload(
                rawMatch,
                players.size(),
                radiantPlayers,
                direPlayers));
        OffsetDateTime startedAt = startedAt(rawMatch.startTime());

        return new NormalizedMatchImport(
                new MatchGameImport(
                        rawMatch.matchId() == null ? requestedDotaMatchId : Long.toString(rawMatch.matchId()),
                        rawMatch.duration(),
                        startedAt,
                        finishedAt(startedAt, rawMatch.duration()),
                        rawMatch.radiantWin(),
                        rawMatch.gameMode(),
                        rawMatch.lobbyType(),
                        rawMatch.radiantScore(),
                        rawMatch.direScore(),
                        winnerSide(rawMatch.radiantWin()),
                        rawResponse,
                        normalizedPayload),
                players);
    }

    private List<MatchPlayerImport> extractPlayers(OpenDotaRawMatchResponse rawMatch) throws Exception {
        Integer durationSeconds = rawMatch.duration();
        Boolean radiantWin = rawMatch.radiantWin();
        List<MatchPlayerImport> players = new ArrayList<>();

        for (OpenDotaRawPlayerResponse player : rawMatch.players()) {
            if (player.playerSlot() == null) {
                continue;
            }

            int playerSlot = player.playerSlot();
            String teamSide = teamSide(playerSlot);
            Boolean radiant = "RADIANT".equals(teamSide);
            Boolean winner = radiantWin == null ? null : radiantWin.equals(radiant);

            players.add(new MatchPlayerImport(
                    player.accountId(),
                    null,
                    player.heroId(),
                    playerSlot,
                    teamSide,
                    radiant,
                    winner,
                    nonNegativeInt(player.kills()),
                    nonNegativeInt(player.deaths()),
                    nonNegativeInt(player.assists()),
                    nonNegativeInt(player.lastHits()),
                    nonNegativeInt(player.denies()),
                    player.goldPerMin(),
                    player.xpPerMin(),
                    player.netWorth(),
                    player.heroDamage(),
                    player.towerDamage(),
                    player.heroHealing(),
                    player.level(),
                    durationSeconds,
                    objectMapper.writeValueAsString(items(player)),
                    objectMapper.writeValueAsString(rawPayload(player))));
        }

        return players;
    }

    private ObjectNode normalizedPayload(
            OpenDotaRawMatchResponse rawMatch,
            int playersNormalized,
            int radiantPlayers,
            int direPlayers
    ) {
        ObjectNode normalized = objectMapper.createObjectNode();
        normalized.put("source", "OPENDOTA");
        normalized.put("version", NORMALIZATION_VERSION);
        normalized.put("normalizedAt", OffsetDateTime.now(ZoneOffset.UTC).toString());
        putLong(normalized, "dotaMatchId", rawMatch.matchId());
        putInt(normalized, "durationSeconds", rawMatch.duration());
        putBoolean(normalized, "radiantWin", rawMatch.radiantWin());
        putInt(normalized, "gameMode", rawMatch.gameMode());
        putInt(normalized, "lobbyType", rawMatch.lobbyType());
        putInt(normalized, "radiantScore", rawMatch.radiantScore());
        putInt(normalized, "direScore", rawMatch.direScore());
        normalized.put("playersNormalized", playersNormalized);
        normalized.put("radiantPlayers", radiantPlayers);
        normalized.put("direPlayers", direPlayers);
        return normalized;
    }

    private ObjectNode items(OpenDotaRawPlayerResponse player) {
        ObjectNode items = objectMapper.createObjectNode();
        putNullableInt(items, "item_0", player.item0());
        putNullableInt(items, "item_1", player.item1());
        putNullableInt(items, "item_2", player.item2());
        putNullableInt(items, "item_3", player.item3());
        putNullableInt(items, "item_4", player.item4());
        putNullableInt(items, "item_5", player.item5());
        putNullableInt(items, "backpack_0", player.backpack0());
        putNullableInt(items, "backpack_1", player.backpack1());
        putNullableInt(items, "backpack_2", player.backpack2());
        putNullableInt(items, "item_neutral", player.itemNeutral());
        return items;
    }

    private JsonNode rawPayload(OpenDotaRawMatchResponse rawMatch) {
        return rawMatch.rawPayload() == null ? objectMapper.valueToTree(rawMatch) : rawMatch.rawPayload();
    }

    private JsonNode rawPayload(OpenDotaRawPlayerResponse player) {
        return player.rawPayload() == null ? objectMapper.valueToTree(player) : player.rawPayload();
    }

    private String teamSide(int playerSlot) {
        if (playerSlot >= 0 && playerSlot <= 4) {
            return "RADIANT";
        }

        if (playerSlot >= 128 && playerSlot <= 132) {
            return "DIRE";
        }

        return playerSlot < 128 ? "RADIANT" : "DIRE";
    }

    private String winnerSide(Boolean radiantWin) {
        if (radiantWin == null) {
            return null;
        }

        return radiantWin ? "RADIANT" : "DIRE";
    }

    private OffsetDateTime startedAt(Long startTime) {
        return startTime == null ? null : OffsetDateTime.ofInstant(Instant.ofEpochSecond(startTime), ZoneOffset.UTC);
    }

    private OffsetDateTime finishedAt(OffsetDateTime startedAt, Integer durationSeconds) {
        return startedAt == null || durationSeconds == null ? null : startedAt.plusSeconds(durationSeconds);
    }

    private int nonNegativeInt(Integer value) {
        if (value == null) {
            return 0;
        }

        return Math.max(0, value);
    }

    private void putLong(ObjectNode target, String fieldName, Long value) {
        if (value != null) {
            target.put(fieldName, value);
        }
    }

    private void putInt(ObjectNode target, String fieldName, Integer value) {
        if (value != null) {
            target.put(fieldName, value);
        }
    }

    private void putBoolean(ObjectNode target, String fieldName, Boolean value) {
        if (value != null) {
            target.put(fieldName, value);
        }
    }

    private void putNullableInt(ObjectNode target, String fieldName, Integer value) {
        if (value == null) {
            target.putNull(fieldName);
        } else {
            target.put(fieldName, value);
        }
    }
}
