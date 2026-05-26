package si.um.feri.dotaops.backend.auth.steam.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import si.um.feri.dotaops.backend.auth.steam.domain.SteamProfileUpsertResult;

@Repository
public class SteamProfileRepository {

    private final JdbcTemplate jdbcTemplate;

    public SteamProfileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public SteamProfileUpsertResult upsertSteamProfile(
            String steamId,
            UUID authUserId,
            String nickname,
            String displayName,
            String avatarUrl,
            String profileUrl,
            String claimedId
    ) {
        return jdbcTemplate.queryForObject(
                """
                select
                  out_profile_id,
                  out_external_account_id,
                  out_is_new_profile,
                  out_is_new_external_account
                from private.upsert_steam_profile(
                  ?,
                  ?,
                  ?,
                  ?,
                  ?,
                  ?,
                  jsonb_build_object(
                    'source', 'steam_openid',
                    'claimed_id', ?,
                    'verified_by_backend', true
                  ),
                  true
                )
                """,
                this::mapUpsertResult,
                steamId,
                authUserId,
                nickname,
                displayName,
                avatarUrl,
                profileUrl,
                claimedId);
    }

    public UUID linkSteamAccountToProfile(
            UUID profileId,
            String steamId,
            String displayName,
            String avatarUrl,
            String profileUrl,
            String claimedId
    ) {
        return jdbcTemplate.queryForObject(
                """
                select private.link_steam_account_to_profile(
                  ?,
                  ?,
                  ?,
                  ?,
                  ?,
                  jsonb_build_object(
                    'source', 'steam_openid',
                    'claimed_id', ?,
                    'verified_by_backend', true
                  ),
                  true
                )
                """,
                UUID.class,
                profileId,
                steamId,
                displayName,
                avatarUrl,
                profileUrl,
                claimedId);
    }

    private SteamProfileUpsertResult mapUpsertResult(ResultSet resultSet, int rowNumber) throws SQLException {
        return new SteamProfileUpsertResult(
                resultSet.getObject("out_profile_id", UUID.class),
                resultSet.getObject("out_external_account_id", UUID.class),
                resultSet.getBoolean("out_is_new_profile"),
                resultSet.getBoolean("out_is_new_external_account"));
    }
}
