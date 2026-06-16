package si.um.feri.dotaops.backend.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import si.um.feri.dotaops.backend.auth.web.SupabaseJwtAuthenticationFilter;
import si.um.feri.dotaops.backend.common.security.JsonAccessDeniedHandler;
import si.um.feri.dotaops.backend.common.security.JsonAuthenticationEntryPoint;
import si.um.feri.dotaops.backend.config.properties.CorsProperties;
import si.um.feri.dotaops.backend.config.properties.SteamAuthProperties;
import si.um.feri.dotaops.backend.config.properties.SteamSessionProperties;
import si.um.feri.dotaops.backend.config.properties.SupabaseAuthProperties;
import si.um.feri.dotaops.backend.config.properties.SupabaseStorageProperties;

@Configuration
@EnableConfigurationProperties({
        SupabaseAuthProperties.class,
        SupabaseStorageProperties.class,
        SteamAuthProperties.class,
        SteamSessionProperties.class,
        CorsProperties.class
})
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SupabaseJwtAuthenticationFilter supabaseJwtAuthenticationFilter,
            JsonAuthenticationEntryPoint authenticationEntryPoint,
            JsonAccessDeniedHandler accessDeniedHandler
    ) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> { })
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(supabaseJwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info", "/api/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/steam/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/steam/logout").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/steam/link").authenticated()
                        .requestMatchers(HttpMethod.GET,
                                "/api/analytics/compare/**",
                                "/api/teams/*/lookups/players").authenticated()
                        .requestMatchers(HttpMethod.GET,
                                "/api/teams/*/invitations",
                                "/api/teams/*/join-requests",
                                "/api/teams/*/tournament-registrations",
                                "/api/teams/*/invitations/**").authenticated()
                        .requestMatchers(HttpMethod.GET,
                                "/api/lookups/heroes",
                                "/api/public/analytics/**",
                                "/api/public/tournaments/**",
                                "/api/tournament-groups/*/teams",
                                "/api/tournament-groups/*/standings",
                                "/api/public/tournament-groups/*/standings").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/teams").hasRole("PLAYER")
                        .requestMatchers(HttpMethod.POST,
                                "/api/me/team/leave",
                                "/api/teams/*/disband",
                                "/api/teams/*/transfer-ownership",
                                "/api/teams/*/logo/upload-url",
                                "/api/teams/*/logo/confirm",
                                "/api/teams/*/banner/upload-url",
                                "/api/teams/*/banner/confirm").hasRole("PLAYER")
                        .requestMatchers(HttpMethod.GET, "/api/teams/*/members/*/profile").hasRole("PLAYER")
                        .requestMatchers(HttpMethod.POST,
                                "/api/teams/*/join-requests",
                                "/api/team-invitations/*/accept",
                                "/api/team-invitations/*/decline").hasRole("PLAYER")
                        .requestMatchers(HttpMethod.GET,
                                "/api/me/analytics",
                                "/api/me/analytics/heroes",
                                "/api/me/analytics/heroes/*/mastery",
                                "/api/me/analytics/insights",
                                "/api/me/analytics/progress",
                                "/api/me/team/analytics").hasRole("PLAYER")
                        .requestMatchers(HttpMethod.GET,
                                "/api/organizer/analytics",
                                "/api/organizer/tournaments/*/analytics").hasAnyRole("ORGANIZER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/match-imports").hasAnyRole("ORGANIZER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/match-imports/*/retry")
                                .hasAnyRole("ORGANIZER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/match-imports/**").authenticated()
                        .requestMatchers(HttpMethod.GET,
                                "/api/profiles/**",
                                "/api/tournaments/**",
                                "/api/teams/**",
                                "/api/matches/**",
                                "/api/analytics/**",
                                "/api/roadmap").permitAll()
                        .requestMatchers(
                                "/api/organizer/tournaments",
                                "/api/organizer/tournaments/**").authenticated()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/organizer/**").hasAnyRole("ORGANIZER", "ADMIN")
                        .requestMatchers("/api/me/**").authenticated()
                        .anyRequest().authenticated())
                .build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(corsProperties.allowedOriginPatterns());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
