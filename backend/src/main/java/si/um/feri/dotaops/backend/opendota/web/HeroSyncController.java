package si.um.feri.dotaops.backend.opendota.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import si.um.feri.dotaops.backend.common.api.ApiResponse;
import si.um.feri.dotaops.backend.opendota.service.HeroSyncService;

@RestController
@RequestMapping("/api/admin/heroes")
public class HeroSyncController {

    private final HeroSyncService heroSyncService;

    public HeroSyncController(HeroSyncService heroSyncService) {
        this.heroSyncService = heroSyncService;
    }

    @PostMapping("/sync")
    ResponseEntity<ApiResponse<HeroSyncResponse>> syncHeroes() {
        return ResponseEntity.ok(ApiResponse.of(heroSyncService.syncHeroes()));
    }
}
