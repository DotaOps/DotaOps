package si.um.feri.dotaops.backend.tournament.service;

import java.text.Normalizer;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import si.um.feri.dotaops.backend.auth.domain.AuthenticatedActor;
import si.um.feri.dotaops.backend.auth.domain.ProfileRole;
import si.um.feri.dotaops.backend.auth.service.CurrentUserProvider;
import si.um.feri.dotaops.backend.common.error.BadRequestException;
import si.um.feri.dotaops.backend.common.error.ConflictException;
import si.um.feri.dotaops.backend.common.error.ResourceNotFoundException;
import si.um.feri.dotaops.backend.common.pagination.PageResponse;
import si.um.feri.dotaops.backend.common.security.DatabaseActorContext;
import si.um.feri.dotaops.backend.tournament.domain.Tournament;
import si.um.feri.dotaops.backend.tournament.domain.TournamentFormat;
import si.um.feri.dotaops.backend.tournament.domain.TournamentSettings;
import si.um.feri.dotaops.backend.tournament.domain.TournamentStatus;
import si.um.feri.dotaops.backend.tournament.dto.CreateTournamentRequest;
import si.um.feri.dotaops.backend.tournament.dto.TournamentDetailResponse;
import si.um.feri.dotaops.backend.tournament.dto.TournamentPublicResponse;
import si.um.feri.dotaops.backend.tournament.dto.TournamentResponse;
import si.um.feri.dotaops.backend.tournament.dto.TournamentSettingsDto;
import si.um.feri.dotaops.backend.tournament.dto.UpdateTournamentRequest;
import si.um.feri.dotaops.backend.tournament.mapper.TournamentSettingsMapper;
import si.um.feri.dotaops.backend.tournament.repository.CreateTournamentCommand;
import si.um.feri.dotaops.backend.tournament.repository.TournamentRepository;
import si.um.feri.dotaops.backend.tournament.repository.UpdateTournamentCommand;

@Service
public class TournamentService {

    private static final String SLUG_PATTERN = "^[a-z0-9]+(?:-[a-z0-9]+)*$";
    private static final int MAX_SLUG_LENGTH = 80;
    private static final String DEFAULT_TIMEZONE = "UTC";

    private final TournamentRepository tournamentRepository;
    private final CurrentUserProvider currentUserProvider;
    private final DatabaseActorContext databaseActorContext;
    private final TournamentSettingsMapper settingsMapper;

    public TournamentService(
            TournamentRepository tournamentRepository,
            CurrentUserProvider currentUserProvider,
            DatabaseActorContext databaseActorContext,
            TournamentSettingsMapper settingsMapper
    ) {
        this.tournamentRepository = tournamentRepository;
        this.currentUserProvider = currentUserProvider;
        this.databaseActorContext = databaseActorContext;
        this.settingsMapper = settingsMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<TournamentPublicResponse> listPublicTournaments(String search, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        long offset = (long) safePage * safeSize;

        List<TournamentPublicResponse> tournaments = tournamentRepository
                .findPublicVisible(search, safeSize, offset)
                .stream()
                .map(TournamentPublicResponse::from)
                .toList();
        long total = tournamentRepository.countPublicVisible(search);

        return PageResponse.from(new PageImpl<>(
                tournaments,
                PageRequest.of(safePage, safeSize),
                total));
    }

    @Transactional(readOnly = true)
    public TournamentDetailResponse getPublicTournament(String slug) {
        String normalizedSlug = normalizeSlug(slug);

        return tournamentRepository.findPublicBySlug(normalizedSlug)
                .map(TournamentDetailResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament", "slug", normalizedSlug));
    }

    @Transactional(readOnly = true)
    public PageResponse<TournamentResponse> listOrganizerTournaments(String search, int page, int size) {
        AuthenticatedActor actor = currentUserProvider.requireActor();
        ensureOrganizerOrAdmin(actor);
        UUID profileId = actor.requireProfileId();
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        long offset = (long) safePage * safeSize;

        List<TournamentResponse> tournaments = tournamentRepository
                .findManageable(profileId, actor.isAdmin(), search, safeSize, offset)
                .stream()
                .map(TournamentResponse::from)
                .toList();
        long total = tournamentRepository.countManageable(profileId, actor.isAdmin(), search);

        return PageResponse.from(new PageImpl<>(
                tournaments,
                PageRequest.of(safePage, safeSize),
                total));
    }

    @Transactional(readOnly = true)
    public TournamentResponse getOrganizerTournament(UUID tournamentId) {
        AuthenticatedActor actor = currentUserProvider.requireActor();
        Tournament tournament = findTournament(tournamentId);
        ensureCanManage(actor, tournament.id());

        return TournamentResponse.from(tournament);
    }

    @Transactional
    public TournamentResponse createTournament(CreateTournamentRequest request) {
        AuthenticatedActor actor = currentUserProvider.requireActor();
        ensureCanCreate(actor);
        databaseActorContext.apply(actor);

        TournamentFormat format = request.format() == null
                ? request.settings() != null && request.settings().format() != null
                        ? request.settings().format()
                        : TournamentFormat.SINGLE_ELIMINATION
                : request.format();
        int maxTeams = resolveCreateMaxTeams(request.maxTeams(), request.settings());
        TournamentSettings settings = resolveSettings(
                request.settings(),
                format,
                maxTeams,
                request.maxTeams(),
                true);
        format = settings.format();
        maxTeams = settings.maxTeams();

        DateValues dates = new DateValues(
                requireStartsAt(request.startsAt()),
                request.endsAt(),
                request.registrationOpensAt(),
                request.registrationClosesAt(),
                request.checkInOpensAt(),
                request.checkInClosesAt());
        validateDates(dates, settings);

        try {
            Tournament tournament = tournamentRepository.create(new CreateTournamentCommand(
                    resolveSlug(request.slug(), request.title()),
                    normalizeRequired(request.title(), "Tournament title is required."),
                    format,
                    actor.requireProfileId(),
                    normalizeOptional(request.description()),
                    normalizeOptional(request.rules()),
                    normalizeOptional(request.prizePool()),
                    maxTeams,
                    dates.startsAt(),
                    dates.endsAt(),
                    dates.registrationOpensAt(),
                    dates.registrationClosesAt(),
                    actor.authUserId(),
                    normalizeTimezone(request.timezone()),
                    dates.checkInOpensAt(),
                    dates.checkInClosesAt(),
                    settingsMapper.toJson(settings)));

            return TournamentResponse.from(tournament);
        } catch (DataIntegrityViolationException exception) {
            throw tournamentConstraintException(exception);
        }
    }

    @Transactional
    public TournamentResponse updateTournament(UUID tournamentId, UpdateTournamentRequest request) {
        if (!request.hasChanges()) {
            throw new BadRequestException("At least one tournament field must be provided.");
        }

        AuthenticatedActor actor = currentUserProvider.requireActor();
        Tournament existing = findTournament(tournamentId);
        ensureCanManage(actor, tournamentId);
        ensureNotArchived(existing);
        databaseActorContext.apply(actor);

        if (request.hasSettings() && request.settings() == null) {
            throw new BadRequestException("Tournament settings cannot be cleared.");
        }

        if (request.hasMaxTeams() && request.maxTeams() == null) {
            throw new BadRequestException("Tournament maxTeams cannot be cleared.");
        }

        ResolvedTournamentUpdate resolved = resolveUpdate(existing, request);
        validateDates(resolved.dates(), resolved.settings());

        try {
            return tournamentRepository.update(
                            tournamentId,
                            new UpdateTournamentCommand(
                                    request.hasTitle(),
                                    request.hasTitle() ? resolved.title() : null,
                                    request.hasSlug(),
                                    request.hasSlug() ? resolved.slug() : null,
                                    request.hasFormat() || settingsFormatChanged(existing, resolved.settings()),
                                    resolved.format(),
                                    request.hasDescription(),
                                    request.hasDescription() ? resolved.description() : null,
                                    request.hasRules(),
                                    request.hasRules() ? resolved.rules() : null,
                                    request.hasPrizePool(),
                                    request.hasPrizePool() ? resolved.prizePool() : null,
                                    request.hasMaxTeams() || resolved.settings().maxTeams() != existing.maxTeams(),
                                    resolved.settings().maxTeams(),
                                    request.hasStartsAt(),
                                    request.hasStartsAt() ? resolved.dates().startsAt() : null,
                                    request.hasEndsAt(),
                                    request.hasEndsAt() ? resolved.dates().endsAt() : null,
                                    request.hasRegistrationOpensAt(),
                                    request.hasRegistrationOpensAt() ? resolved.dates().registrationOpensAt() : null,
                                    request.hasRegistrationClosesAt(),
                                    request.hasRegistrationClosesAt() ? resolved.dates().registrationClosesAt() : null,
                                    request.hasTimezone(),
                                    request.hasTimezone() ? resolved.timezone() : null,
                                    request.hasCheckInOpensAt(),
                                    request.hasCheckInOpensAt() ? resolved.dates().checkInOpensAt() : null,
                                    request.hasCheckInClosesAt(),
                                    request.hasCheckInClosesAt() ? resolved.dates().checkInClosesAt() : null,
                                    request.hasSettings()
                                            || request.hasFormat()
                                            || request.hasMaxTeams()
                                            || settingsFormatChanged(existing, resolved.settings()),
                                    settingsMapper.toJson(resolved.settings())))
                    .map(TournamentResponse::from)
                    .orElseThrow(() -> new ResourceNotFoundException("Tournament", "id", tournamentId));
        } catch (DataIntegrityViolationException exception) {
            throw tournamentConstraintException(exception);
        }
    }

    @Transactional
    public TournamentResponse publishTournament(UUID tournamentId) {
        AuthenticatedActor actor = currentUserProvider.requireActor();
        Tournament tournament = findTournament(tournamentId);
        ensureCanManage(actor, tournamentId);
        ensurePublishableStatus(tournament);
        validatePublishable(tournament);
        databaseActorContext.apply(actor);

        return tournamentRepository.publish(tournamentId)
                .map(TournamentResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament", "id", tournamentId));
    }

    @Transactional
    public TournamentResponse archiveTournament(UUID tournamentId) {
        AuthenticatedActor actor = currentUserProvider.requireActor();
        Tournament tournament = findTournament(tournamentId);
        ensureCanManage(actor, tournamentId);
        if (tournament.status() == TournamentStatus.ARCHIVED) {
            throw new ConflictException("Archived tournaments cannot be archived again.");
        }
        databaseActorContext.apply(actor);

        return tournamentRepository.archive(tournamentId)
                .map(TournamentResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament", "id", tournamentId));
    }

    private Tournament findTournament(UUID tournamentId) {
        return tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new ResourceNotFoundException("Tournament", "id", tournamentId));
    }

    private void ensureCanCreate(AuthenticatedActor actor) {
        if (actor.role() == ProfileRole.ORGANIZER || actor.role() == ProfileRole.ADMIN) {
            actor.requireProfileId();
            return;
        }

        throw new AccessDeniedException("Only organizers or admins can create tournaments.");
    }

    private void ensureCanManage(AuthenticatedActor actor, UUID tournamentId) {
        UUID profileId = actor.requireProfileId();
        if (actor.isAdmin()) {
            return;
        }

        if (actor.role() == ProfileRole.ORGANIZER
                && tournamentRepository.canManage(tournamentId, profileId, false)) {
            return;
        }

        throw new AccessDeniedException("Only the tournament owner, tournament organizers, or admins can manage this tournament.");
    }

    private void ensureOrganizerOrAdmin(AuthenticatedActor actor) {
        if (actor.role() == ProfileRole.ORGANIZER || actor.isAdmin()) {
            return;
        }

        throw new AccessDeniedException("Only organizers or admins can access organizer tournaments.");
    }

    private void ensureNotArchived(Tournament tournament) {
        if (tournament.status() == TournamentStatus.ARCHIVED) {
            throw new ConflictException("Archived tournaments cannot be updated.");
        }
    }

    private void ensurePublishableStatus(Tournament tournament) {
        if (!tournament.status().canPublish()) {
            throw new ConflictException("Tournament cannot be published from status '%s'."
                    .formatted(tournament.status().databaseValue()));
        }
    }

    private void validatePublishable(Tournament tournament) {
        normalizeRequired(tournament.title(), "Tournament title is required.");
        normalizeSlug(tournament.slug());
        if (tournament.startsAt() == null) {
            throw new BadRequestException("Tournament start time is required.");
        }

        validateSettings(tournament.settings(), tournament.format(), tournament.maxTeams());
        validateDates(new DateValues(
                tournament.startsAt(),
                tournament.endsAt(),
                tournament.registrationOpensAt(),
                tournament.registrationClosesAt(),
                tournament.checkInOpensAt(),
                tournament.checkInClosesAt()), tournament.settings());
    }

    private ResolvedTournamentUpdate resolveUpdate(Tournament existing, UpdateTournamentRequest request) {
        String title = request.hasTitle()
                ? normalizeRequired(request.title(), "Tournament title is required.")
                : existing.title();
        String slug = request.hasSlug() ? normalizeRequiredSlug(request.slug()) : existing.slug();
        TournamentFormat requestedFormat = request.hasFormat()
                ? requireFormat(request.format())
                : request.hasSettings() && request.settings() != null && request.settings().format() != null
                        ? request.settings().format()
                        : existing.format();
        String description = request.hasDescription() ? normalizeOptional(request.description()) : existing.description();
        String rules = request.hasRules() ? normalizeOptional(request.rules()) : existing.rules();
        String prizePool = request.hasPrizePool() ? normalizeOptional(request.prizePool()) : existing.prizePool();
        String timezone = request.hasTimezone() ? normalizeTimezone(request.timezone()) : existing.timezone();

        DateValues dates = new DateValues(
                request.hasStartsAt() ? requireStartsAt(request.startsAt()) : existing.startsAt(),
                request.hasEndsAt() ? request.endsAt() : existing.endsAt(),
                request.hasRegistrationOpensAt() ? request.registrationOpensAt() : existing.registrationOpensAt(),
                request.hasRegistrationClosesAt() ? request.registrationClosesAt() : existing.registrationClosesAt(),
                request.hasCheckInOpensAt() ? request.checkInOpensAt() : existing.checkInOpensAt(),
                request.hasCheckInClosesAt() ? request.checkInClosesAt() : existing.checkInClosesAt());

        TournamentSettings settings = request.hasSettings()
                ? resolveSettings(
                        request.settings(),
                        requestedFormat,
                        request.hasMaxTeams() ? request.maxTeams() : existing.maxTeams(),
                        request.hasMaxTeams() ? request.maxTeams() : null,
                        false)
                : mergeExistingSettings(existing.settings(), requestedFormat, request.hasMaxTeams() ? request.maxTeams() : null);

        if (request.hasMaxTeams()) {
            settings = new TournamentSettings(
                    request.maxTeams(),
                    settings.minTeams(),
                    settings.teamSize(),
                    settings.bestOf(),
                    settings.format(),
                    settings.checkInEnabled(),
                    settings.allowSubstitutes());
        }

        validateSettings(settings, requestedFormat, settings.maxTeams());

        return new ResolvedTournamentUpdate(
                title,
                slug,
                settings.format(),
                description,
                rules,
                prizePool,
                timezone,
                dates,
                settings);
    }

    private TournamentSettings mergeExistingSettings(
            TournamentSettings existing,
            TournamentFormat format,
            Integer maxTeams
    ) {
        return new TournamentSettings(
                maxTeams == null ? existing.maxTeams() : maxTeams,
                existing.minTeams(),
                existing.teamSize(),
                existing.bestOf(),
                format == null ? existing.format() : format,
                existing.checkInEnabled(),
                existing.allowSubstitutes());
    }

    private TournamentSettings resolveSettings(
            TournamentSettingsDto dto,
            TournamentFormat fallbackFormat,
            int fallbackMaxTeams,
            Integer requestedMaxTeams,
            boolean create
    ) {
        TournamentSettings defaults = TournamentSettings.defaults(fallbackFormat, fallbackMaxTeams);
        if (dto == null) {
            validateSettings(defaults, fallbackFormat, defaults.maxTeams());
            return defaults;
        }

        TournamentFormat format = dto.format() == null ? defaults.format() : dto.format();
        Integer dtoMaxTeams = dto.maxTeams();
        if (requestedMaxTeams != null && dtoMaxTeams != null && !requestedMaxTeams.equals(dtoMaxTeams)) {
            throw new BadRequestException("Tournament maxTeams must match settings.maxTeams.");
        }

        TournamentSettings settings = new TournamentSettings(
                dtoMaxTeams == null ? defaults.maxTeams() : dtoMaxTeams,
                dto.minTeams() == null ? defaults.minTeams() : dto.minTeams(),
                dto.teamSize() == null ? defaults.teamSize() : dto.teamSize(),
                dto.bestOf() == null ? defaults.bestOf() : dto.bestOf(),
                format,
                dto.checkInEnabled() == null ? defaults.checkInEnabled() : dto.checkInEnabled(),
                dto.allowSubstitutes() == null ? defaults.allowSubstitutes() : dto.allowSubstitutes());

        validateSettings(settings, fallbackFormat, settings.maxTeams());
        return settings;
    }

    private void validateSettings(TournamentSettings settings, TournamentFormat columnFormat, int columnMaxTeams) {
        if (settings.maxTeams() <= 0) {
            throw new BadRequestException("Tournament settings maxTeams must be positive.");
        }

        if (settings.maxTeams() < 2 || settings.maxTeams() > 128) {
            throw new BadRequestException("Tournament settings maxTeams must be between 2 and 128.");
        }

        if (settings.minTeams() <= 0) {
            throw new BadRequestException("Tournament settings minTeams must be positive.");
        }

        if (settings.minTeams() > settings.maxTeams()) {
            throw new BadRequestException("Tournament settings minTeams cannot exceed maxTeams.");
        }

        if (!TournamentSettings.isSupportedTeamSize(settings.teamSize())) {
            throw new BadRequestException("Tournament settings teamSize must be 1, 3, or 5.");
        }

        if (settings.bestOf() != 1 && settings.bestOf() != 3 && settings.bestOf() != 5) {
            throw new BadRequestException("Tournament settings bestOf must be 1, 3, or 5.");
        }

        if (settings.format() == null) {
            throw new BadRequestException("Tournament settings format is required.");
        }

        if (columnFormat != null && settings.format() != columnFormat) {
            throw new BadRequestException("Tournament format must match settings.format.");
        }

        if (settings.maxTeams() != columnMaxTeams) {
            throw new BadRequestException("Tournament maxTeams must match settings.maxTeams.");
        }
    }

    private void validateDates(DateValues dates, TournamentSettings settings) {
        if (dates.startsAt() == null) {
            throw new BadRequestException("Tournament start time is required.");
        }

        if (dates.endsAt() != null && dates.endsAt().isBefore(dates.startsAt())) {
            throw new BadRequestException("Tournament end time must be after or equal to start time.");
        }

        if (dates.registrationOpensAt() != null
                && dates.registrationClosesAt() != null
                && dates.registrationClosesAt().isBefore(dates.registrationOpensAt())) {
            throw new BadRequestException("Registration close time must be after or equal to registration open time.");
        }

        if (dates.registrationClosesAt() != null && dates.registrationClosesAt().isAfter(dates.startsAt())) {
            throw new BadRequestException("Registration close time must be before or equal to tournament start time.");
        }

        if (dates.checkInOpensAt() != null
                && dates.checkInClosesAt() != null
                && dates.checkInClosesAt().isBefore(dates.checkInOpensAt())) {
            throw new BadRequestException("Check-in close time must be after or equal to check-in open time.");
        }

        if (settings.checkInEnabled()) {
            if (dates.checkInOpensAt() == null || dates.checkInClosesAt() == null) {
                throw new BadRequestException("Check-in open and close times are required when check-in is enabled.");
            }

            if (dates.registrationClosesAt() != null && dates.checkInOpensAt().isBefore(dates.registrationClosesAt())) {
                throw new BadRequestException("Check-in open time must be after or equal to registration close time.");
            }
        }

        if (dates.checkInClosesAt() != null && dates.checkInClosesAt().isAfter(dates.startsAt())) {
            throw new BadRequestException("Check-in close time must be before or equal to tournament start time.");
        }
    }

    private int resolveCreateMaxTeams(Integer requestMaxTeams, TournamentSettingsDto settings) {
        if (requestMaxTeams != null) {
            return requestMaxTeams;
        }

        if (settings != null && settings.maxTeams() != null) {
            return settings.maxTeams();
        }

        return TournamentSettings.DEFAULT_MAX_TEAMS;
    }

    private TournamentFormat requireFormat(TournamentFormat format) {
        if (format == null) {
            throw new BadRequestException("Tournament format cannot be cleared.");
        }

        return format;
    }

    private OffsetDateTime requireStartsAt(OffsetDateTime startsAt) {
        if (startsAt == null) {
            throw new BadRequestException("Tournament start time is required.");
        }

        return startsAt;
    }

    private String resolveSlug(String requestedSlug, String title) {
        if (requestedSlug != null && !requestedSlug.isBlank()) {
            return normalizeSlug(requestedSlug);
        }

        return generateSlug(title);
    }

    private String generateSlug(String value) {
        String normalized = Normalizer.normalize(normalizeRequired(value, "Tournament title is required."), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");

        if (normalized.length() > MAX_SLUG_LENGTH) {
            normalized = normalized.substring(0, MAX_SLUG_LENGTH).replaceAll("-+$", "");
        }

        return normalizeSlug(normalized);
    }

    private String normalizeRequiredSlug(String value) {
        if (value == null) {
            throw new BadRequestException("Tournament slug cannot be cleared.");
        }

        return normalizeSlug(value);
    }

    private String normalizeSlug(String value) {
        String normalized = normalizeRequired(value, "Tournament slug is required.").toLowerCase(Locale.ROOT);

        if (!normalized.matches(SLUG_PATTERN)) {
            throw new BadRequestException("Tournament slug must contain lowercase letters, numbers and single hyphens only.");
        }

        if (normalized.length() > MAX_SLUG_LENGTH) {
            throw new BadRequestException("Tournament slug must be at most 80 characters.");
        }

        return normalized;
    }

    private String normalizeRequired(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException(message);
        }

        return value.trim();
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private String normalizeTimezone(String value) {
        String normalized = normalizeOptional(value);
        return normalized == null ? DEFAULT_TIMEZONE : normalized;
    }

    private boolean settingsFormatChanged(Tournament existing, TournamentSettings settings) {
        return settings.format() != existing.settings().format();
    }

    private BadRequestException tournamentConstraintException(DataIntegrityViolationException exception) {
        String message = exception.getMostSpecificCause().getMessage();

        if (message != null && message.contains("tournaments_slug_key")) {
            return new BadRequestException("Tournament slug is already in use.");
        }

        if (message != null && message.contains("tournaments_slug_format")) {
            return new BadRequestException("Tournament slug must contain lowercase letters, numbers and single hyphens only.");
        }

        if (message != null && message.contains("tournaments_max_teams")) {
            return new BadRequestException("Tournament maxTeams must be between 2 and 128.");
        }

        if (message != null && message.contains("tournaments_settings_team_size_supported")) {
            return new BadRequestException("Tournament settings teamSize must be 1, 3, or 5.");
        }

        if (message != null && message.contains("tournaments_dates_order")) {
            return new BadRequestException("Tournament end time must be after or equal to start time.");
        }

        if (message != null && message.contains("tournaments_registration_order")) {
            return new BadRequestException("Registration close time must be after or equal to registration open time.");
        }

        if (message != null && message.contains("tournaments_registration_before_start")) {
            return new BadRequestException("Registration close time must be before or equal to tournament start time.");
        }

        if (message != null && message.contains("tournaments_check_in_order")) {
            return new BadRequestException("Check-in close time must be after or equal to check-in open time.");
        }

        return new BadRequestException("Tournament data violates a database constraint.");
    }

    private record DateValues(
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            OffsetDateTime registrationOpensAt,
            OffsetDateTime registrationClosesAt,
            OffsetDateTime checkInOpensAt,
            OffsetDateTime checkInClosesAt
    ) {
    }

    private record ResolvedTournamentUpdate(
            String title,
            String slug,
            TournamentFormat format,
            String description,
            String rules,
            String prizePool,
            String timezone,
            DateValues dates,
            TournamentSettings settings
    ) {
    }
}
