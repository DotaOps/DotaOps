package si.um.feri.dotaops.backend.notification.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import si.um.feri.dotaops.backend.notification.domain.NotificationChannel;
import si.um.feri.dotaops.backend.notification.domain.NotificationType;
import si.um.feri.dotaops.backend.notification.repository.CreateNotificationCommand;
import si.um.feri.dotaops.backend.tournament.domain.MatchTeamCaptain;
import si.um.feri.dotaops.backend.tournament.domain.Tournament;
import si.um.feri.dotaops.backend.tournament.domain.TournamentMatch;
import si.um.feri.dotaops.backend.tournament.domain.TournamentRegistration;

@Service
public class NotificationOutboxService {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificationOutboxService.class);

    private final NotificationOutboxWriter notificationOutboxWriter;

    public NotificationOutboxService(NotificationOutboxWriter notificationOutboxWriter) {
        this.notificationOutboxWriter = notificationOutboxWriter;
    }

    public void createTeamApplicationSubmittedNotification(
            Tournament tournament,
            TournamentRegistration registration
    ) {
        if (tournament.organizerProfileId() == null) {
            return;
        }

        enqueueSafely(new CreateNotificationCommand(
                tournament.organizerProfileId(),
                NotificationType.TEAM_APPLICATION_SUBMITTED,
                NotificationChannel.IN_APP,
                "Nova prijava ekipe",
                "Ekipa %s se je prijavila na turnir %s.".formatted(
                        registration.teamName(),
                        registration.tournamentTitle()),
                payload(
                        "tournamentId", registration.tournamentId().toString(),
                        "tournamentSlug", registration.tournamentSlug(),
                        "tournamentName", registration.tournamentTitle(),
                        "teamId", registration.teamId().toString(),
                        "teamSlug", registration.teamSlug(),
                        "teamName", registration.teamName(),
                        "registrationId", registration.id().toString()),
                now()));
    }

    public void createTeamApplicationApprovedNotification(TournamentRegistration registration) {
        enqueueTeamApplicationReviewNotification(
                NotificationType.TEAM_APPLICATION_APPROVED,
                "Ekipa odobrena",
                "Tvoja ekipa %s je bila odobrena za turnir %s.".formatted(
                        registration.teamName(),
                        registration.tournamentTitle()),
                registration);
    }

    public void createTeamApplicationRejectedNotification(TournamentRegistration registration) {
        enqueueTeamApplicationReviewNotification(
                NotificationType.TEAM_APPLICATION_REJECTED,
                "Ekipa zavrnjena",
                "Tvoja ekipa %s je bila zavrnjena za turnir %s.".formatted(
                        registration.teamName(),
                        registration.tournamentTitle()),
                registration);
    }

    public void createMatchScheduledNotification(
            TournamentMatch match,
            String tournamentTitle,
            MatchTeamCaptain recipient
    ) {
        if (recipient.captainProfileId() == null) {
            return;
        }

        enqueueSafely(new CreateNotificationCommand(
                recipient.captainProfileId(),
                NotificationType.MATCH_SCHEDULED,
                NotificationChannel.IN_APP,
                "Tekma razporejena",
                "Tvoja ekipa %s ima razporejeno tekmo v turnirju %s.".formatted(
                        recipient.teamName(),
                        tournamentTitle),
                payload(
                        "tournamentId", match.tournamentId().toString(),
                        "tournamentName", tournamentTitle,
                        "matchId", match.id().toString(),
                        "teamId", recipient.teamId().toString(),
                        "teamName", recipient.teamName(),
                        "scheduledAt", match.scheduledAt() == null ? null : match.scheduledAt().toString(),
                        "stageName", match.stageName(),
                        "roundName", match.roundName()),
                now()));
    }

    private void enqueueTeamApplicationReviewNotification(
            NotificationType type,
            String title,
            String message,
            TournamentRegistration registration
    ) {
        if (registration.captainProfileId() == null) {
            return;
        }

        enqueueSafely(new CreateNotificationCommand(
                registration.captainProfileId(),
                type,
                NotificationChannel.IN_APP,
                title,
                message,
                payload(
                        "tournamentId", registration.tournamentId().toString(),
                        "tournamentSlug", registration.tournamentSlug(),
                        "tournamentName", registration.tournamentTitle(),
                        "teamId", registration.teamId().toString(),
                        "teamSlug", registration.teamSlug(),
                        "teamName", registration.teamName(),
                        "registrationId", registration.id().toString()),
                now()));
    }

    private void enqueueSafely(CreateNotificationCommand command) {
        try {
            notificationOutboxWriter.enqueue(command);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Notification outbox enqueue failed for recipient {} and type {}.",
                    command.recipientProfileId(),
                    command.type(),
                    exception);
        }
    }

    private Map<String, Object> payload(Object... keyValues) {
        Map<String, Object> payload = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            Object value = keyValues[index + 1];
            if (value != null) {
                payload.put((String) keyValues[index], value);
            }
        }
        return payload;
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
