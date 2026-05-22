package si.um.feri.dotaops.backend.opendota.repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import si.um.feri.dotaops.backend.opendota.domain.Hero;

@Repository
public class HeroRepository {

    private final JdbcTemplate jdbcTemplate;

    public HeroRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean existsByDotaHeroId(int dotaHeroId) {
        Boolean exists = jdbcTemplate.queryForObject(
                """
                select exists (
                  select 1
                  from public.heroes
                  where dota_hero_id = ?
                )
                """,
                Boolean.class,
                dotaHeroId);

        return Boolean.TRUE.equals(exists);
    }

    public Optional<Hero> findByDotaHeroId(int dotaHeroId) {
        return jdbcTemplate.query(
                        selectSql() + """
                        where dota_hero_id = ?
                        limit 1
                        """,
                        this::mapHero,
                        dotaHeroId)
                .stream()
                .findFirst();
    }

    public Hero upsert(HeroUpsertCommand command) {
        return jdbcTemplate.queryForObject(
                """
                insert into public.heroes (
                  dota_hero_id,
                  name,
                  localized_name,
                  slug,
                  roles,
                  image_url,
                  icon_url
                )
                values (?, ?, ?, ?, cast(? as text[]), ?, ?)
                on conflict (dota_hero_id)
                do update set
                  name = excluded.name,
                  localized_name = excluded.localized_name,
                  slug = excluded.slug,
                  roles = excluded.roles,
                  image_url = excluded.image_url,
                  icon_url = excluded.icon_url,
                  updated_at = now()
                returning
                  id,
                  dota_hero_id,
                  name,
                  localized_name,
                  slug,
                  roles,
                  image_url,
                  icon_url,
                  created_at,
                  updated_at
                """,
                this::mapHero,
                command.dotaHeroId(),
                command.name(),
                command.localizedName(),
                command.slug(),
                command.roles().toArray(String[]::new),
                command.imageUrl(),
                command.iconUrl());
    }

    private String selectSql() {
        return """
                select
                  id,
                  dota_hero_id,
                  name,
                  localized_name,
                  slug,
                  roles,
                  image_url,
                  icon_url,
                  created_at,
                  updated_at
                from public.heroes
                """;
    }

    private Hero mapHero(ResultSet resultSet, int rowNumber) throws SQLException {
        return new Hero(
                resultSet.getObject("id", UUID.class),
                resultSet.getInt("dota_hero_id"),
                resultSet.getString("name"),
                resultSet.getString("localized_name"),
                resultSet.getString("slug"),
                roles(resultSet.getArray("roles")),
                resultSet.getString("image_url"),
                resultSet.getString("icon_url"),
                resultSet.getObject("created_at", OffsetDateTime.class),
                resultSet.getObject("updated_at", OffsetDateTime.class));
    }

    private List<String> roles(Array roles) throws SQLException {
        if (roles == null) {
            return List.of();
        }

        Object array = roles.getArray();
        if (array instanceof String[] values) {
            return List.copyOf(Arrays.asList(values));
        }

        return List.of();
    }
}
