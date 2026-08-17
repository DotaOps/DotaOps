package si.um.feri.dotaops.backend.auth.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import si.um.feri.dotaops.backend.auth.domain.AuthenticatedProfile;
import si.um.feri.dotaops.backend.auth.domain.ProfileRole;

public final class SupabaseAuthorities {

    static final String AUTHENTICATED_AUTHORITY = "ROLE_AUTHENTICATED";

    private SupabaseAuthorities() {
    }

    public static Collection<GrantedAuthority> from(Optional<AuthenticatedProfile> profile) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(AUTHENTICATED_AUTHORITY));

        ProfileRole role = profile.map(AuthenticatedProfile::role).orElse(ProfileRole.VISITOR);
        addRole(authorities, role);

        return List.copyOf(authorities);
    }

    private static void addRole(List<GrantedAuthority> authorities, ProfileRole role) {
        authorities.add(new SimpleGrantedAuthority(role.authority()));
    }
}
