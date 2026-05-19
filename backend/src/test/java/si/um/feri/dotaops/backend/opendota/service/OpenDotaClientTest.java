package si.um.feri.dotaops.backend.opendota.service;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import si.um.feri.dotaops.backend.config.properties.IntegrationHttpProperties;
import si.um.feri.dotaops.backend.config.properties.OpenDotaProperties;
import si.um.feri.dotaops.backend.opendota.domain.OpenDotaErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenDotaClientTest {

    private static final long ACCOUNT_ID = 39734273L;
    private static final long MATCH_ID = 7894561230L;

    @Test
    void fetchPlayerParsesOpenDotaProfileResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenDotaClient client = client(builder);

        server.expect(requestTo("https://api.opendota.com/api/players/" + ACCOUNT_ID))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {
                          "profile": {
                            "account_id": 39734273,
                            "personaname": "Dota Player",
                            "avatarfull": "https://cdn.example.test/avatar.png",
                            "profileurl": "https://steamcommunity.com/profiles/76561198000000001/"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        var profile = client.fetchPlayer(ACCOUNT_ID);

        assertThat(profile).isPresent();
        assertThat(profile.orElseThrow().accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(profile.orElseThrow().personaName()).isEqualTo("Dota Player");
        assertThat(profile.orElseThrow().avatarUrl()).isEqualTo("https://cdn.example.test/avatar.png");
        server.verify();
    }

    @Test
    void fetchPlayerReturnsEmptyOnRateLimit() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenDotaClient client = client(builder);

        server.expect(requestTo("https://api.opendota.com/api/players/" + ACCOUNT_ID))
                .andExpect(method(GET))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThat(client.fetchPlayer(ACCOUNT_ID)).isEmpty();
        server.verify();
    }

    @Test
    void fetchRecentMatchesParsesBasicMatchFields() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenDotaClient client = client(builder);

        server.expect(requestTo("https://api.opendota.com/api/players/" + ACCOUNT_ID + "/matches?limit=2"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        [
                          {
                            "match_id": 7740000001,
                            "start_time": 1778544000,
                            "hero_id": 1,
                            "player_slot": 0,
                            "radiant_win": true
                          }
                        ]
                        """, MediaType.APPLICATION_JSON));

        var matches = client.fetchRecentMatches(ACCOUNT_ID, 2);

        assertThat(matches).hasSize(1);
        assertThat(matches.getFirst().matchId()).isEqualTo(7740000001L);
        assertThat(matches.getFirst().heroId()).isEqualTo(1);
        assertThat(matches.getFirst().radiantWin()).isTrue();
        server.verify();
    }

    @Test
    void fetchMatchReturnsRawMatchPayload() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenDotaClient client = client(builder);

        server.expect(requestTo("https://api.opendota.com/api/matches/" + MATCH_ID))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {
                          "match_id": 7894561230,
                          "duration": 1900,
                          "start_time": 1778544000,
                          "radiant_win": true,
                          "players": [
                            {
                              "account_id": 39734273,
                              "player_slot": 0,
                              "hero_id": 1,
                              "kills": 8,
                              "last_hits": 120,
                              "gold_per_min": 500,
                              "xp_per_min": 600
                            }
                          ],
                          "unknown_field": "ignored"
                        }
                        """, MediaType.APPLICATION_JSON));

        var match = client.fetchMatch(MATCH_ID);

        assertThat(match.matchId()).isEqualTo(MATCH_ID);
        assertThat(match.duration()).isEqualTo(1900);
        assertThat(match.radiantWin()).isTrue();
        assertThat(match.players()).hasSize(1);
        assertThat(match.players().getFirst().accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(match.players().getFirst().goldPerMin()).isEqualTo(500);
        assertThat(match.rawPayload().path("match_id").asLong()).isEqualTo(MATCH_ID);
        server.verify();
    }

    @Test
    void fetchMatchAddsApiKeyWhenConfigured() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenDotaClient client = client(
                new OpenDotaProperties("https://opendota.example.test/api", "server-only-key"),
                builder,
                1);

        server.expect(requestTo("https://opendota.example.test/api/matches/" + MATCH_ID + "?api_key=server-only-key"))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {
                          "match_id": 7894561230,
                          "players": []
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.fetchMatch(MATCH_ID).matchId()).isEqualTo(MATCH_ID);
        server.verify();
    }

    @Test
    void fetchMatchMapsNotFoundToClientExceptionWithoutRetry() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenDotaClient client = client(builder);

        server.expect(requestTo("https://api.opendota.com/api/matches/" + MATCH_ID))
                .andExpect(method(GET))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.fetchMatch(MATCH_ID))
                .isInstanceOfSatisfying(OpenDotaClientException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(OpenDotaErrorCode.MATCH_NOT_FOUND));
        server.verify();
    }

    @Test
    void fetchMatchMapsErrorPayloadToNotFound() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenDotaClient client = client(builder);

        server.expect(requestTo("https://api.opendota.com/api/matches/" + MATCH_ID))
                .andExpect(method(GET))
                .andRespond(withSuccess("""
                        {
                          "error": "match not found"
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchMatch(MATCH_ID))
                .isInstanceOfSatisfying(OpenDotaClientException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(OpenDotaErrorCode.MATCH_NOT_FOUND));
        server.verify();
    }

    @Test
    void fetchMatchRetriesRateLimitWithRetryAfterThenReturnsRateLimited() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        List<Duration> sleeps = new ArrayList<>();
        OpenDotaClient client = client(properties(), builder, 2, sleeps);

        for (int attempt = 0; attempt < 2; attempt++) {
            server.expect(requestTo("https://api.opendota.com/api/matches/" + MATCH_ID))
                    .andExpect(method(GET))
                    .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", "2"));
        }

        assertThatThrownBy(() -> client.fetchMatch(MATCH_ID))
                .isInstanceOfSatisfying(OpenDotaClientException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(OpenDotaErrorCode.RATE_LIMITED));
        assertThat(sleeps).containsExactly(Duration.ofSeconds(2));
        server.verify();
    }

    @Test
    void fetchMatchRetriesServerErrorsThenReturnsProviderUnavailable() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenDotaClient client = client(builder);

        for (int attempt = 0; attempt < 3; attempt++) {
            server.expect(requestTo("https://api.opendota.com/api/matches/" + MATCH_ID))
                    .andExpect(method(GET))
                    .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        }

        assertThatThrownBy(() -> client.fetchMatch(MATCH_ID))
                .isInstanceOfSatisfying(OpenDotaClientException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(OpenDotaErrorCode.PROVIDER_UNAVAILABLE));
        server.verify();
    }

    @Test
    void fetchMatchMapsTimeoutToProviderTimeout() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenDotaClient client = client(properties(), builder, 1);

        server.expect(requestTo("https://api.opendota.com/api/matches/" + MATCH_ID))
                .andExpect(method(GET))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> client.fetchMatch(MATCH_ID))
                .isInstanceOfSatisfying(OpenDotaClientException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(OpenDotaErrorCode.PROVIDER_TIMEOUT));
        server.verify();
    }

    @Test
    void fetchMatchMapsInvalidJsonToInvalidProviderResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OpenDotaClient client = client(builder);

        server.expect(requestTo("https://api.opendota.com/api/matches/" + MATCH_ID))
                .andExpect(method(GET))
                .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.fetchMatch(MATCH_ID))
                .isInstanceOfSatisfying(OpenDotaClientException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(OpenDotaErrorCode.INVALID_PROVIDER_RESPONSE));
        server.verify();
    }

    private static OpenDotaClient client(RestClient.Builder builder) {
        return client(properties(), builder, 3);
    }

    private static OpenDotaClient client(OpenDotaProperties properties, RestClient.Builder builder, int maxAttempts) {
        return client(properties, builder, maxAttempts, new ArrayList<>());
    }

    private static OpenDotaClient client(
            OpenDotaProperties properties,
            RestClient.Builder builder,
            int maxAttempts,
            List<Duration> sleeps
    ) {
        return new OpenDotaClient(
                properties,
                integrationProperties(maxAttempts),
                builder,
                duration -> sleeps.add(duration));
    }

    private static OpenDotaProperties properties() {
        return new OpenDotaProperties("https://api.opendota.com/api", "");
    }

    private static IntegrationHttpProperties integrationProperties(int maxAttempts) {
        return new IntegrationHttpProperties(
                null,
                new IntegrationHttpProperties.Client(
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(5),
                        new IntegrationHttpProperties.Retry(maxAttempts, Duration.ZERO)));
    }
}
