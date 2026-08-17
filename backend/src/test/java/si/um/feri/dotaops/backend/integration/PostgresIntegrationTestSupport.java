package si.um.feri.dotaops.backend.integration;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

abstract class PostgresIntegrationTestSupport {

    private static final List<String> SUPABASE_ROLES = List.of("anon", "authenticated", "service_role");

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    protected <T> T asAuthenticated(UUID authUserId, Supplier<T> operation) {
        return asRole("authenticated", authUserId, operation);
    }

    protected <T> T asServiceRole(Supplier<T> operation) {
        return asRole("service_role", null, operation);
    }

    protected <T> T asBackend(Supplier<T> operation) {
        return new TransactionTemplate(transactionManager).execute(status -> operation.get());
    }

    protected <T> T asRole(String role, UUID authUserId, Supplier<T> operation) {
        if (!SUPABASE_ROLES.contains(role)) {
            throw new IllegalArgumentException("Unsupported test role: " + role);
        }

        return asBackend(() -> {
            jdbcTemplate.execute("set local role " + role);
            if (authUserId == null) {
                jdbcTemplate.queryForObject(
                        "select set_config('request.jwt.claim.sub', '', true)",
                        String.class);
            } else {
                jdbcTemplate.queryForObject(
                        "select set_config('request.jwt.claim.sub', ?, true)",
                        String.class,
                        authUserId.toString());
            }

            return operation.get();
        });
    }

    protected void seedAuthUser(UUID authUserId) {
        jdbcTemplate.update(
                """
                insert into auth.users (id, email)
                values (?, ?)
                on conflict (id) do nothing
                """,
                authUserId,
                authUserId + "@integration.test");
    }

    protected UUID upsertProfile(UUID authUserId, String role) {
        seedAuthUser(authUserId);
        String nickname = "it_" + uniqueSuffix();

        return jdbcTemplate.queryForObject(
                """
                insert into public.profiles (auth_user_id, nickname, display_name, role)
                values (?, ?, ?, ?::public.dotaops_user_role)
                on conflict (auth_user_id) do update
                set nickname = excluded.nickname,
                    display_name = excluded.display_name,
                    role = excluded.role,
                    updated_at = now()
                returning id
                """,
                UUID.class,
                authUserId,
                nickname,
                nickname,
                role);
    }

    protected static String uniqueSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    protected static String uniqueSteamId64() {
        long value = Math.abs(UUID.randomUUID().getLeastSignificantBits() % 10_000_000_000L);
        return "7656119" + String.format("%010d", value);
    }
}
