package si.um.feri.dotaops.backend.team.web;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.validation.constraints.Size;

public class UpdateTeamManualPlayerRequest {

    @Size(min = 1, max = 80)
    private String displayName;

    @Size(max = 80)
    private String nickname;

    @Size(max = 500)
    private String note;

    @JsonIgnore
    private boolean displayNamePresent;

    @JsonIgnore
    private boolean nicknamePresent;

    @JsonIgnore
    private boolean notePresent;

    public UpdateTeamManualPlayerRequest() {
    }

    public UpdateTeamManualPlayerRequest(String displayName, String nickname, String note) {
        this.displayName = displayName;
        this.nickname = nickname;
        this.note = note;
        this.displayNamePresent = displayName != null;
        this.nicknamePresent = nickname != null;
        this.notePresent = note != null;
    }

    public String displayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
        this.displayNamePresent = true;
    }

    public String nickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
        this.nicknamePresent = true;
    }

    public String note() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
        this.notePresent = true;
    }

    @JsonIgnore
    public boolean hasDisplayName() {
        return displayNamePresent;
    }

    @JsonIgnore
    public boolean hasNickname() {
        return nicknamePresent;
    }

    @JsonIgnore
    public boolean hasNote() {
        return notePresent;
    }

    @JsonIgnore
    public boolean hasChanges() {
        return displayNamePresent || nicknamePresent || notePresent;
    }
}
