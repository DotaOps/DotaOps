package si.um.feri.dotaops.backend.notification.service;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import si.um.feri.dotaops.backend.notification.domain.NotificationChannel;
import si.um.feri.dotaops.backend.notification.domain.NotificationType;
import si.um.feri.dotaops.backend.notification.repository.CreateNotificationCommand;
import si.um.feri.dotaops.backend.tournament.domain.TournamentFormat;
import si.um.feri.dotaops.backend.tournament.domain.TournamentRegistration;
import si.um.feri.dotaops.backend.tournament.domain.TournamentRegistrationStatus;
import si.um.feri.dotaops.backend.tournament.domain.TournamentSettings;
import si.um.feri.dotaops.backend.tournament.domain.TournamentStatus;
import si.um.feri.dotaops.backend.tournament.domain.Tournament;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NotificationOutboxServiceTest {

    private static final UUID AUTH_USER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID ORGANIZER_PROFILE_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID CAPTAIN_PROFILE_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");
    private static final UUID TOURNAMENT_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID TEAM_ID = UUID.fromString("55555555-5555-4555-8555-555555555555");
    private static final UUID REGISTRATION_ID = UUID.fromString("66666666-6666-4666-8666-666666666666");
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-05-29T12:00:00Z");

    private final NotificationOutboxWriter notificationOutboxWriter = mock(NotificationOutboxWriter.class);
    private final NotificationOutboxService service = new NotificationOutboxService(notificationOutboxWriter);

    @Test
    void teamApplicationSubmittedNotificationTargetsOrganizer() {
        service.createTeamApplicationSubmittedNotification(tournament(), registration());

        ArgumentCaptor<CreateNotificationCommand> captor = ArgumentCaptor.forClass(CreateNotificationCommand.class);
        verify(notificationOutboxWriter).enqueue(captor.capture());
        assertThat(captor.getValue().recipientProfileId()).isEqualTo(ORGANIZER_PROFILE_ID);
        assertThat(captor.getValue().type()).isEqualTo(NotificationType.TEAM_APPLICATION_SUBMITTED);
        assertThat(captor.getValue().channel()).isEqualTo(NotificationChannel.IN_APP);
        assertThat(captor.getValue().title()).isEqualTo("Nova prijava ekipe");
        assertThat(captor.getValue().payload()).containsEntry("registrationId", REGISTRATION_ID.toString());
    }

    @Test
    void enqueueFailureIsSwallowed() {
        doThrow(new IllegalStateException("database unavailable"))
                .when(notificationOutboxWriter)
                .enqueue(org.mockito.ArgumentMatchers.any(CreateNotificationCommand.class));

        assertThatCode(() -> service.createTeamApplicationApprovedNotification(registration()))
                .doesNotThrowAnyException();
    }

    private static Tournament tournament() {
        TournamentSettings settings = TournamentSettings.defaults(TournamentFormat.SINGLE_ELIMINATION, 8);
        return new Tournament(
                TOURNAMENT_ID,
                "mid-wars-open",
                "Mid Wars Open",
                TournamentStatus.PUBLISHED,
                TournamentFormat.SINGLE_ELIMINATION,
                ORGANIZER_PROFILE_ID,
                "Organizer",
                "Qualifier",
                "Rules",
                "TBD",
                8,
                NOW.plusDays(2),
                NOW.plusDays(3),
                NOW.minusDays(2),
                NOW.plusDays(1),
                true,
                AUTH_USER_ID,
                "UTC",
                null,
                null,
                NOW.minusDays(5),
                settings,
                0,
                NOW.minusDays(10),
                NOW.minusDays(1));
    }

    private static TournamentRegistration registration() {
        return new TournamentRegistration(
                REGISTRATION_ID,
                TOURNAMENT_ID,
                "mid-wars-open",
                "Mid Wars Open",
                TEAM_ID,
                "Radiant Five",
                "R5",
                "radiant-five",
                CAPTAIN_PROFILE_ID,
                "Captain",
                TournamentRegistrationStatus.PENDING,
                "Ready",
                null,
                null,
                null,
                null,
                null,
                "captain@example.test",
                NOW.minusHours(2),
                NOW.minusHours(1));
    }
}
