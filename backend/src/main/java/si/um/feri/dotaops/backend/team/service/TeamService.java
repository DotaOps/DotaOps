package si.um.feri.dotaops.backend.team.service;

import java.text.Normalizer;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import si.um.feri.dotaops.backend.auth.domain.AuthenticatedProfile;
import si.um.feri.dotaops.backend.auth.domain.ProfileRole;
import si.um.feri.dotaops.backend.auth.service.CurrentUserProvider;
import si.um.feri.dotaops.backend.common.error.BadRequestException;
import si.um.feri.dotaops.backend.common.error.ConflictException;
import si.um.feri.dotaops.backend.common.error.ResourceNotFoundException;
import si.um.feri.dotaops.backend.common.pagination.PageResponse;
import si.um.feri.dotaops.backend.storage.service.StoredImage;
import si.um.feri.dotaops.backend.storage.service.SupabaseImageStorageService;
import si.um.feri.dotaops.backend.team.domain.Team;
import si.um.feri.dotaops.backend.team.domain.TeamManualPlayer;
import si.um.feri.dotaops.backend.team.domain.TeamMemberRole;
import si.um.feri.dotaops.backend.team.repository.CreateTeamCommand;
import si.um.feri.dotaops.backend.team.repository.CreateTeamMemberCommand;
import si.um.feri.dotaops.backend.team.repository.TeamManualPlayerRepository;
import si.um.feri.dotaops.backend.team.repository.TeamMemberRepository;
import si.um.feri.dotaops.backend.team.repository.TeamRepository;
import si.um.feri.dotaops.backend.team.repository.UpdateTeamCommand;
import si.um.feri.dotaops.backend.team.web.CreateTeamRequest;
import si.um.feri.dotaops.backend.team.web.TeamManualPlayerResponse;
import si.um.feri.dotaops.backend.team.web.TeamResponse;
import si.um.feri.dotaops.backend.team.web.UpdateTeamRequest;

@Service
public class TeamService {

    private static final String SLUG_PATTERN = "^[a-z0-9]+(?:-[a-z0-9]+)*$";
    private static final int MAX_SLUG_LENGTH = 80;

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamManualPlayerRepository teamManualPlayerRepository;
    private final SupabaseImageStorageService imageStorageService;
    private final CurrentUserProvider currentUserProvider;

    public TeamService(
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            TeamManualPlayerRepository teamManualPlayerRepository,
            SupabaseImageStorageService imageStorageService,
            CurrentUserProvider currentUserProvider
    ) {
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.teamManualPlayerRepository = teamManualPlayerRepository;
        this.imageStorageService = imageStorageService;
        this.currentUserProvider = currentUserProvider;
    }

    @Transactional(readOnly = true)
    public PageResponse<TeamResponse> listTeams(String search, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        long offset = (long) safePage * safeSize;

        List<TeamResponse> teams = teamRepository.findTeams(search, safeSize, offset)
                .stream()
                .map(TeamResponse::from)
                .toList();
        long total = teamRepository.countTeams(search);

        return PageResponse.from(new PageImpl<>(
                teams,
                PageRequest.of(safePage, safeSize),
                total));
    }

    @Transactional(readOnly = true)
    public TeamResponse getTeam(UUID teamId) {
        return teamRepository.findById(teamId)
                .map(this::toDetailResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Team", "id", teamId));
    }

    @Transactional(readOnly = true)
    public TeamResponse getTeamBySlug(String slug) {
        String normalizedSlug = normalizeSlug(slug);

        return teamRepository.findBySlug(normalizedSlug)
                .map(this::toDetailResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Team", "slug", normalizedSlug));
    }

    @Transactional
    public TeamResponse createTeam(CreateTeamRequest request) {
        AuthenticatedProfile profile = currentUserProvider.requireProfile();
        if (profile.role() != ProfileRole.PLAYER) {
            throw new AccessDeniedException("Only players can create teams.");
        }
        if (teamRepository.existsCurrentTeamForProfileExcluding(profile.profileId(), null)) {
            throw new ConflictException("Profile already belongs to an active team.");
        }
        UUID authUserId = profile.authUserId();

        try {
            Team team = teamRepository.create(new CreateTeamCommand(
                    normalizeRequired(request.name()),
                    normalizeOptional(request.tag()),
                    resolveSlug(request.slug(), request.name()),
                    profile.profileId(),
                    normalizeOptional(request.region()),
                    normalizeOptional(request.logoUrl()),
                    normalizeOptional(request.description()),
                    authUserId));
            teamMemberRepository.create(new CreateTeamMemberCommand(
                    team.id(),
                    profile.profileId(),
                    TeamMemberRole.SUPPORT));

            return TeamResponse.from(team);
        } catch (DataIntegrityViolationException exception) {
            throw duplicateTeamException(exception);
        }
    }

    @Transactional
    public TeamResponse updateTeam(UUID teamId, UpdateTeamRequest request) {
        if (!request.hasChanges()) {
            throw new BadRequestException("At least one team field must be provided.");
        }

        AuthenticatedProfile profile = currentUserProvider.requireProfile();
        Team existing = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team", "id", teamId));

        if (!canUpdate(profile, existing)) {
            throw new AccessDeniedException("Only the team captain or an admin can update this team.");
        }

        try {
            return teamRepository.update(
                            teamId,
                            new UpdateTeamCommand(
                                    request.hasName(),
                                    request.hasName() ? normalizeRequired(request.name()) : null,
                                    request.hasTag(),
                                    request.hasTag() ? normalizeOptional(request.tag()) : null,
                                    request.hasSlug(),
                                    request.hasSlug() ? normalizeRequiredSlug(request.slug()) : null,
                                    request.hasRegion(),
                                    request.hasRegion() ? normalizeOptional(request.region()) : null,
                                    request.hasLogoUrl(),
                                    request.hasLogoUrl() ? normalizeOptional(request.logoUrl()) : null,
                                    request.hasDescription(),
                                    request.hasDescription() ? normalizeOptional(request.description()) : null))
                    .map(TeamResponse::from)
                    .orElseThrow(() -> new ResourceNotFoundException("Team", "id", teamId));
        } catch (DataIntegrityViolationException exception) {
            throw duplicateTeamException(exception);
        }
    }

    @Transactional
    public TeamResponse uploadTeamLogo(UUID teamId, MultipartFile logo) {
        AuthenticatedProfile profile = currentUserProvider.requireProfile();
        Team existing = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team", "id", teamId));
        if (!canUpdate(profile, existing)) {
            throw new AccessDeniedException("Only the team captain or an admin can update this team.");
        }

        StoredImage storedLogo = imageStorageService.storeTeamLogo(teamId, logo);
        return teamRepository.updateLogoUrl(teamId, storedLogo.publicUrl())
                .map(this::toDetailResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Team", "id", teamId));
    }

    @Transactional
    public TeamResponse uploadTeamBanner(UUID teamId, MultipartFile banner) {
        AuthenticatedProfile profile = currentUserProvider.requireProfile();
        Team existing = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team", "id", teamId));
        if (!canUpdate(profile, existing)) {
            throw new AccessDeniedException("Only the team captain or an admin can update this team.");
        }

        StoredImage storedBanner = imageStorageService.storeTeamBanner(teamId, banner);
        return teamRepository.updateBannerUrl(teamId, storedBanner.publicUrl())
                .map(this::toDetailResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Team", "id", teamId));
    }

    @Transactional
    public TeamResponse deleteTeamLogo(UUID teamId) {
        AuthenticatedProfile profile = currentUserProvider.requireProfile();
        Team existing = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team", "id", teamId));
        if (!canUpdate(profile, existing)) {
            throw new AccessDeniedException("Only the team captain or an admin can update this team.");
        }

        return teamRepository.updateLogoUrl(teamId, null)
                .map(this::toDetailResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Team", "id", teamId));
    }

    @Transactional
    public TeamResponse deleteTeamBanner(UUID teamId) {
        AuthenticatedProfile profile = currentUserProvider.requireProfile();
        Team existing = teamRepository.findById(teamId)
                .orElseThrow(() -> new ResourceNotFoundException("Team", "id", teamId));
        if (!canUpdate(profile, existing)) {
            throw new AccessDeniedException("Only the team captain or an admin can update this team.");
        }

        return teamRepository.updateBannerUrl(teamId, null)
                .map(this::toDetailResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Team", "id", teamId));
    }

    private boolean canUpdate(AuthenticatedProfile profile, Team team) {
        return profile.role() == ProfileRole.ADMIN
                || profile.profileId().equals(team.captainProfileId());
    }

    private TeamResponse toDetailResponse(Team team) {
        var manualPlayers = teamManualPlayerRepository.findByTeamId(team.id());
        return TeamResponse.from(
                team,
                (manualPlayers == null ? Collections.<TeamManualPlayer>emptyList() : manualPlayers)
                        .stream()
                        .map(TeamManualPlayerResponse::from)
                        .toList());
    }

    private BadRequestException duplicateTeamException(DataIntegrityViolationException exception) {
        String message = exception.getMostSpecificCause().getMessage();

        if (message != null && message.contains("teams_name_key")) {
            return new BadRequestException("Team name is already in use.");
        }

        if (message != null && message.contains("teams_slug_key")) {
            return new BadRequestException("Team slug is already in use.");
        }

        return new BadRequestException("Team data violates a database constraint.");
    }

    private String resolveSlug(String requestedSlug, String name) {
        if (requestedSlug != null && !requestedSlug.isBlank()) {
            return normalizeSlug(requestedSlug);
        }

        return generateSlug(name);
    }

    private String generateSlug(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");

        if (normalized.length() > MAX_SLUG_LENGTH) {
            normalized = normalized.substring(0, MAX_SLUG_LENGTH).replaceAll("-+$", "");
        }

        return normalizeSlug(normalized);
    }

    private String normalizeSlug(String value) {
        String normalized = normalizeRequired(value).toLowerCase(Locale.ROOT);

        if (!normalized.matches(SLUG_PATTERN)) {
            throw new BadRequestException("Team slug must contain lowercase letters, numbers and single hyphens only.");
        }

        if (normalized.length() > MAX_SLUG_LENGTH) {
            throw new BadRequestException("Team slug must be at most 80 characters.");
        }

        return normalized;
    }

    private String normalizeRequiredSlug(String value) {
        if (value == null) {
            throw new BadRequestException("Team slug cannot be cleared.");
        }

        return normalizeSlug(value);
    }

    private String normalizeRequired(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("Required team field is blank.");
        }

        return value.trim();
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}
