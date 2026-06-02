package si.um.feri.dotaops.backend.team.domain;

public record TeamRosterCapacity(
        int participantsCount,
        int capacity
) {

    public int slotsFilled() {
        return participantsCount;
    }

    public int slotsRemaining() {
        return Math.max(capacity - participantsCount, 0);
    }

    public boolean isFull() {
        return participantsCount >= capacity;
    }

    public boolean canAdd(int additionalPlayers) {
        return additionalPlayers >= 0 && participantsCount + additionalPlayers <= capacity;
    }
}
