package si.um.feri.dotaops.backend.opendota.web;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import si.um.feri.dotaops.backend.common.api.ApiResponse;
import si.um.feri.dotaops.backend.common.security.ClientIpAddressResolver;
import si.um.feri.dotaops.backend.opendota.service.MatchImportService;

@RestController
@RequestMapping("/api/match-imports")
public class MatchImportController {

    private final MatchImportService matchImportService;
    private final ClientIpAddressResolver clientIpAddressResolver;

    public MatchImportController(
            MatchImportService matchImportService,
            ClientIpAddressResolver clientIpAddressResolver
    ) {
        this.matchImportService = matchImportService;
        this.clientIpAddressResolver = clientIpAddressResolver;
    }

    @PostMapping
    ResponseEntity<ApiResponse<MatchImportResponse>> importMatch(
            @Valid @RequestBody CreateMatchImportRequest request,
            HttpServletRequest servletRequest
    ) {
        MatchImportResponse response = matchImportService.importMatch(
                request,
                clientIpAddressResolver.resolve(servletRequest));

        return ResponseEntity
                .created(URI.create("/api/match-imports/" + response.id()))
                .body(ApiResponse.of(response));
    }

    @GetMapping("/{id}")
    ResponseEntity<ApiResponse<MatchImportResponse>> getImport(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(matchImportService.getImport(id)));
    }

    @GetMapping("/by-match/{dotaMatchId}")
    ResponseEntity<ApiResponse<MatchImportResponse>> getImportByMatch(@PathVariable String dotaMatchId) {
        return ResponseEntity.ok(ApiResponse.of(matchImportService.getImportByDotaMatchId(dotaMatchId)));
    }

    @GetMapping("/{id}/events")
    ResponseEntity<ApiResponse<List<MatchImportEventResponse>>> getImportEvents(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(matchImportService.getImportEvents(id)));
    }

    @PostMapping("/{id}/retry")
    ResponseEntity<ApiResponse<MatchImportResponse>> retryImport(
            @PathVariable UUID id,
            HttpServletRequest servletRequest
    ) {
        MatchImportResponse response = matchImportService.retryImport(
                id,
                clientIpAddressResolver.resolve(servletRequest));

        return ResponseEntity.ok(ApiResponse.of(response));
    }
}
