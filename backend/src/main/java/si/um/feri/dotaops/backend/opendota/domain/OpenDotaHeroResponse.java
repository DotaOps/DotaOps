package si.um.feri.dotaops.backend.opendota.domain;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenDotaHeroResponse(
        @JsonProperty("id")
        Integer id,
        @JsonProperty("name")
        String name,
        @JsonProperty("localized_name")
        String localizedName,
        @JsonProperty("roles")
        List<String> roles
) {

    public OpenDotaHeroResponse {
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}
