package si.um.feri.dotaops.backend.opendota.domain;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenDotaRawMatchResponse(
        @JsonProperty("match_id")
        Long matchId,
        Integer duration,
        @JsonProperty("start_time")
        Long startTime,
        @JsonProperty("radiant_win")
        Boolean radiantWin,
        @JsonProperty("game_mode")
        Integer gameMode,
        @JsonProperty("lobby_type")
        Integer lobbyType,
        @JsonProperty("radiant_score")
        Integer radiantScore,
        @JsonProperty("dire_score")
        Integer direScore,
        List<OpenDotaRawPlayerResponse> players,
        @JsonIgnore
        JsonNode rawPayload
) {

    public OpenDotaRawMatchResponse {
        players = players == null ? List.of() : List.copyOf(players);
    }

    public OpenDotaRawMatchResponse withRawPayload(JsonNode rawPayload) {
        return new OpenDotaRawMatchResponse(
                matchId,
                duration,
                startTime,
                radiantWin,
                gameMode,
                lobbyType,
                radiantScore,
                direScore,
                players,
                rawPayload);
    }
}
