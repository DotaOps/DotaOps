package si.um.feri.dotaops.backend.profile.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import si.um.feri.dotaops.backend.auth.domain.ProfileRole;
import si.um.feri.dotaops.backend.profile.domain.Profile;

@Repository
public class ProfileRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProfileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Profile> findProfiles(String search, int size, long offset) {
        String normalizedSearch = normalizeSearch(search);

        return jdbcTemplate.query(
                """
                select
                  id,
                  auth_user_id,
                  nickname,
                  display_name,
                  steam_id,
                  opendota_account_id,
                  role::text as role,
                  avatar_url,
                  avatar_path,
                  bio,
                  country_code,
                  steam_profile_synced_at,
                  opendota_profile_synced_at,
                  created_at,
                  updated_at
                from public.profiles
                where (
                  cast(? as text) is null
                  or nickname ilike '%' || cast(? as text) || '%'
                  or display_name ilike '%' || cast(? as text) || '%'
                )
                order by created_at desc, id desc
                limit ? offset ?
                """,
                this::mapProfile,
                normalizedSearch,
                normalizedSearch,
                normalizedSearch,
                size,
                offset);
    }

    public long countProfiles(String search) {
        String normalizedSearch = normalizeSearch(search);

        Long count = jdbcTemplate.queryForObject(
                """
                select count(*)
                from public.profiles
                where (
                  cast(? as text) is null
                  or nickname ilike '%' || cast(? as text) || '%'
                  or display_name ilike '%' || cast(? as text) || '%'
                )
                """,
                Long.class,
                normalizedSearch,
                normalizedSearch,
                normalizedSearch);

        return count == null ? 0 : count;
    }

    public Optional<Profile> findById(UUID profileId) {
        return jdbcTemplate.query(
                        """
                        select
                          id,
                          auth_user_id,
                          nickname,
                          display_name,
                          steam_id,
                          opendota_account_id,
                          role::text as role,
                          avatar_url,
                          avatar_path,
                          bio,
                          country_code,
                          steam_profile_synced_at,
                          opendota_profile_synced_at,
                          created_at,
                          updated_at
                        from public.profiles
                        where id = ?
                        limit 1
                        """,
                        this::mapProfile,
                        profileId)
                .stream()
                .findFirst();
    }

    public Optional<Profile> findByAuthUserId(UUID authUserId) {
        return jdbcTemplate.query(
                        """
                        select
                          id,
                          auth_user_id,
                          nickname,
                          display_name,
                          steam_id,
                          opendota_account_id,
                          role::text as role,
                          avatar_url,
                          avatar_path,
                          bio,
                          country_code,
                          steam_profile_synced_at,
                          opendota_profile_synced_at,
                          created_at,
                          updated_at
                        from public.profiles
                        where auth_user_id = ?
                        limit 1
                        """,
                        this::mapProfile,
                        authUserId)
                .stream()
                .findFirst();
    }

    public boolean emailMatchesProfileAuthUser(UUID profileId, String normalizedEmail) {
        Boolean matches = jdbcTemplate.queryForObject(
                """
                select exists (
                  select 1
                  from public.profiles p
                  join auth.users u on u.id = p.auth_user_id
                  where p.id = ?
                    and lower(u.email) = ?
                )
                """,
                Boolean.class,
                profileId,
                normalizedEmail);

        return Boolean.TRUE.equals(matches);
    }

    public Optional<Profile> findByNickname(String nickname) {
        return jdbcTemplate.query(
                        """
                        select
                          id,
                          auth_user_id,
                          nickname,
                          display_name,
                          steam_id,
                          opendota_account_id,
                          role::text as role,
                          avatar_url,
                          avatar_path,
                          bio,
                          country_code,
                          steam_profile_synced_at,
                          opendota_profile_synced_at,
                          created_at,
                          updated_at
                        from public.profiles
                        where lower(nickname) = lower(?)
                        limit 1
                        """,
                        this::mapProfile,
                        nickname)
                .stream()
                .findFirst();
    }

    public Profile create(CreateProfileCommand command) {
        return jdbcTemplate.queryForObject(
                """
                insert into public.profiles (
                  auth_user_id,
                  nickname,
                  display_name,
                  avatar_url,
                  bio,
                  country_code,
                  role
                )
                values (?, ?, ?, ?, ?, ?, cast(? as public.dotaops_user_role))
                returning
                  id,
                  auth_user_id,
                  nickname,
                  display_name,
                  steam_id,
                  opendota_account_id,
                  role::text as role,
                  avatar_url,
                  avatar_path,
                  bio,
                  country_code,
                  steam_profile_synced_at,
                  opendota_profile_synced_at,
                  created_at,
                  updated_at
                """,
                this::mapProfile,
                command.authUserId(),
                command.nickname(),
                command.displayName(),
                command.avatarUrl(),
                command.bio(),
                command.countryCode(),
                command.role().databaseValue());
    }

    public Optional<Profile> updateByAuthUserId(UUID authUserId, UpdateProfileCommand command) {
        return updateByColumn("auth_user_id", authUserId, command);
    }

    public Optional<Profile> updateById(UUID profileId, UpdateProfileCommand command) {
        return updateByColumn("id", profileId, command);
    }

    public Optional<Profile> updateRoleById(UUID profileId, ProfileRole role) {
        return jdbcTemplate.query(
                        """
                        update public.profiles
                        set
                          role = cast(? as public.dotaops_user_role),
                          updated_at = now()
                        where id = ?
                        returning
                          id,
                          auth_user_id,
                          nickname,
                          display_name,
                          steam_id,
                          opendota_account_id,
                          role::text as role,
                          avatar_url,
                          avatar_path,
                          bio,
                          country_code,
                          steam_profile_synced_at,
                          opendota_profile_synced_at,
                          created_at,
                          updated_at
                        """,
                        this::mapProfile,
                        role.databaseValue(),
                        profileId)
                .stream()
                .findFirst();
    }

    public Optional<Profile> updateAvatarStorage(UUID profileId, String avatarUrl, String avatarPath) {
        return jdbcTemplate.query(
                        """
                        update public.profiles
                        set
                          avatar_url = ?,
                          avatar_path = ?,
                          updated_at = now()
                        where id = ?
                        returning
                          id,
                          auth_user_id,
                          nickname,
                          display_name,
                          steam_id,
                          opendota_account_id,
                          role::text as role,
                          avatar_url,
                          avatar_path,
                          bio,
                          country_code,
                          steam_profile_synced_at,
                          opendota_profile_synced_at,
                          created_at,
                          updated_at
                        """,
                        this::mapProfile,
                        avatarUrl,
                        avatarPath,
                        profileId)
                .stream()
                .findFirst();
    }

    private Optional<Profile> updateByColumn(String columnName, UUID value, UpdateProfileCommand command) {
        return jdbcTemplate.query(
                        """
                        update public.profiles
                        set
                          nickname = case when ? then ? else nickname end,
                          display_name = case when ? then ? else display_name end,
                          avatar_url = case when ? then ? else avatar_url end,
                          avatar_path = case when ? then null else avatar_path end,
                          bio = case when ? then ? else bio end,
                          country_code = case when ? then ? else country_code end,
                          updated_at = now()
                        where %s = ?
                        returning
                          id,
                          auth_user_id,
                          nickname,
                          display_name,
                          steam_id,
                          opendota_account_id,
                          role::text as role,
                          avatar_url,
                          avatar_path,
                          bio,
                          country_code,
                          steam_profile_synced_at,
                          opendota_profile_synced_at,
                          created_at,
                          updated_at
                        """.formatted(columnName),
                        this::mapProfile,
                        command.nicknamePresent(),
                        command.nickname(),
                        command.displayNamePresent(),
                        command.displayName(),
                        command.avatarUrlPresent(),
                        command.avatarUrl(),
                        command.avatarUrlPresent(),
                        command.bioPresent(),
                        command.bio(),
                        command.countryCodePresent(),
                        command.countryCode(),
                        value)
                .stream()
                .findFirst();
    }

    private Profile mapProfile(ResultSet resultSet, int rowNumber) throws SQLException {
        return new Profile(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("auth_user_id", UUID.class),
                resultSet.getString("nickname"),
                resultSet.getString("display_name"),
                resultSet.getString("steam_id"),
                resultSet.getObject("opendota_account_id", Long.class),
                ProfileRole.fromDatabaseValue(resultSet.getString("role")),
                resultSet.getString("avatar_url"),
                resultSet.getString("avatar_path"),
                resultSet.getString("bio"),
                resultSet.getString("country_code"),
                resultSet.getObject("steam_profile_synced_at", OffsetDateTime.class),
                resultSet.getObject("opendota_profile_synced_at", OffsetDateTime.class),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class));
    }

    private String normalizeSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }

        return search.trim();
    }
}
