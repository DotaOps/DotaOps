package si.um.feri.dotaops.backend.auth.steam.web;

import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import si.um.feri.dotaops.backend.auth.steam.domain.SteamAuthResult;
import si.um.feri.dotaops.backend.auth.steam.service.SteamAuthService;
import si.um.feri.dotaops.backend.auth.steam.service.SteamSessionCookieService;
import si.um.feri.dotaops.backend.auth.steam.service.SteamSessionTokenService;
import si.um.feri.dotaops.backend.common.api.ApiResponse;

@RestController
@RequestMapping("/api/auth/steam")
public class SteamAuthController {

    private final SteamAuthService steamAuthService;
    private final SteamSessionTokenService steamSessionTokenService;
    private final SteamSessionCookieService steamSessionCookieService;

    public SteamAuthController(
            SteamAuthService steamAuthService,
            SteamSessionTokenService steamSessionTokenService,
            SteamSessionCookieService steamSessionCookieService
    ) {
        this.steamAuthService = steamAuthService;
        this.steamSessionTokenService = steamSessionTokenService;
        this.steamSessionCookieService = steamSessionCookieService;
    }

    @GetMapping("/login")
    ResponseEntity<Void> login(HttpServletRequest request) {
        URI redirectUri = steamAuthService.beginLogin(request);

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(redirectUri)
                .build();
    }

    @PostMapping("/link")
    ResponseEntity<ApiResponse<SteamLinkStartResponse>> link(HttpServletRequest request) {
        URI redirectUri = steamAuthService.beginProfileLink(request);

        return ResponseEntity.ok(ApiResponse.of(new SteamLinkStartResponse(redirectUri.toString())));
    }

    @GetMapping("/callback")
    ResponseEntity<?> callback(@RequestParam MultiValueMap<String, String> params) {
        SteamAuthResult result = steamAuthService.completeCallback(params);
        String sessionToken = steamSessionTokenService.createToken(result);
        String sessionCookie = steamSessionCookieService.createSessionCookie(sessionToken).toString();
        if (result.redirectUri() != null) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .header(HttpHeaders.SET_COOKIE, sessionCookie)
                    .location(result.redirectUri())
                    .build();
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, sessionCookie)
                .body(ApiResponse.of(SteamAuthResponse.from(result)));
    }

    @PostMapping("/logout")
    ResponseEntity<ApiResponse<SteamLogoutResponse>> logout() {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, steamSessionCookieService.clearSessionCookie().toString())
                .body(ApiResponse.of(new SteamLogoutResponse("logged_out")));
    }
}
