# My Team backend API

Ta dokument opisuje backend pogodbo za My Team UI. Team captain oziroma owner je
team-level koncept prek `teams.captain_profile_id`; uporabnik pri tem ostane
globalni `PLAYER`.

## Aktivna ekipa in capabilities

`GET /api/me/team` zahteva prijavljenega uporabnika. Za playerja vrne aktivno
ekipo ali stabilen empty state. Response vsebuje:

- `isTeamOwner`
- `currentUserTeamRole`: `owner`, `member` ali `null`
- `canCreateTeam`
- `canManageTeam`
- `canManageRoster`
- `canInvitePlayers`
- `canTransferOwnership`
- `canLeaveTeam`
- `canDisbandTeam`
- `canViewAnalytics`

Pravila:

| Stanje | `canCreateTeam` | `canTransferOwnership` | `canLeaveTeam` | `canDisbandTeam` |
| --- | --- | --- | --- | --- |
| Player brez aktivne ekipe | `true` | `false` | `false` | `false` |
| Owner z drugim aktivnim clanom | `false` | `true` | `false` | `true` |
| Solo owner | `false` | `false` | `false` | `true` |
| Navaden aktiven clan | `false` | `false` | `true` | `false` |

## Leave Team

**Method in URL**

```text
POST /api/me/team/leave
```

**Request body**

Brez bodyja.

**Pravice**

Samo `ROLE_PLAYER`. Player mora biti aktiven clan ekipe in ne sme biti trenutni
owner. Owner mora najprej prenesti lastnistvo ali disbandati ekipo.

**Success response**

```json
{
  "data": {
    "team": null,
    "members": [],
    "manualPlayers": [],
    "canCreateTeam": true,
    "canLeaveTeam": false,
    "canDisbandTeam": false
  }
}
```

Clan se ne brise: njegov `team_members` zapis dobi `is_active = false` in
`left_at`. Sprememba je vidna v admin audit logu kot `public.team_members`
`update`.

**Primeri napak**

- `401 UNAUTHORIZED`: manjka veljaven JWT.
- `403 FORBIDDEN`: actor ni `PLAYER`.
- `400 BAD_REQUEST`: owner poskusa zapustiti ekipo brez transferja ali disbanda.
- `404 RESOURCE_NOT_FOUND`: actor nima aktivnega team membershipa.

## Disband Team

**Method in URL**

```text
POST /api/teams/{teamId}/disband
```

**Request body**

Brez bodyja.

**Pravice**

Samo `ROLE_PLAYER`, ki je trenutni owner ekipe. Navaden clan, organizer in admin
nimajo implicitnega bypassa za ta endpoint.

**Success response**

```json
{
  "data": {
    "teamId": "uuid",
    "status": "disbanded",
    "disbandedAt": "2026-06-02T18:00:00Z"
  }
}
```

Disband je soft delete: `teams.disbanded_at` se nastavi, vrstica ekipe in
turnirske zgodovinske povezave ostanejo. Aktivni `team_members` se deaktivirajo,
pending `team_invitations` in `team_join_requests` pa postanejo `cancelled`.
Disbandana ekipa ni vec vrnjena v javnih team list/detail endpointih in ni vec
veljavna za nove roster ali registration write tokove.

**Primeri napak**

- `401 UNAUTHORIZED`: manjka veljaven JWT.
- `403 FORBIDDEN`: actor ni `PLAYER` ali ni owner podane ekipe.
- `404 RESOURCE_NOT_FOUND`: ekipa ne obstaja ali je ze disbandana.

## Roster Profile/Stats

**Method in URL**

```text
GET /api/teams/{teamId}/members/{profileId}/profile
```

**Request body**

Brez bodyja.

**Pravice**

Samo `ROLE_PLAYER`, ki je aktiven clan iste aktivne ekipe. Endpoint ne izpostavi
emaila, Steam identifikatorjev ali drugih zasebnih profilnih podatkov.

**Success response**

```json
{
  "data": {
    "profileId": "uuid",
    "nickname": "MidPulse",
    "displayName": "Mid Pulse",
    "avatarUrl": null,
    "role": "mid",
    "teamOwner": false,
    "joinedAt": "2026-06-02T18:00:00Z",
    "stats": {
      "gamesPlayed": 0,
      "wins": 0,
      "losses": 0,
      "winRate": 0.00,
      "kda": 0.00,
      "avgKills": 0.00,
      "avgDeaths": 0.00,
      "avgAssists": 0.00
    },
    "mostPlayedHeroes": [],
    "recentMatches": []
  }
}
```

`stats` in `mostPlayedHeroes` uporabljajo realne agregate iz
`match_players`. Ce importiranih podatkov ni, backend vrne nicle in prazne
sezname. `recentMatches` je trenutno stabilen prazen seznam, ker protected
analytics sloj se nima queryja za zgodovino tekem.

**Primeri napak**

- `401 UNAUTHORIZED`: manjka veljaven JWT.
- `403 FORBIDDEN`: actor ni `PLAYER` ali ni aktiven clan iste ekipe.
- `404 RESOURCE_NOT_FOUND`: ekipa ali ciljni aktivni roster profil ne obstaja.

## Transfer Ownership

**Method in URL**

```text
POST /api/teams/{teamId}/transfer-ownership
```

**Request body**

```json
{
  "newOwnerProfileId": "uuid"
}
```

**Pravice**

Samo `ROLE_PLAYER`, ki je trenutni owner. Novi owner mora biti obstojec
`PLAYER` in aktiven clan iste ekipe.

**Success response**

Endpoint vrne osvezen `CurrentTeamResponse`. Stari owner ostane aktiven clan,
globalne profilne vloge pa se ne spremenijo.

**Primeri napak**

- `401 UNAUTHORIZED`: manjka veljaven JWT.
- `403 FORBIDDEN`: actor ni `PLAYER` ali ni trenutni owner.
- `400 BAD_REQUEST`: novi owner je isti profil, ni player ali ni aktiven clan.
- `404 RESOURCE_NOT_FOUND`: ekipa ali novi owner profil ne obstaja.

## Baza in audit

Migracija `V31__soft_disband_teams_and_audit_roster_membership.sql` doda:

- `teams.disbanded_at`
- parcialni indeks `teams_active_created_at_idx`
- active-team RLS za javno branje ekip in roster podpornih tabel
- active-team preverjanje v `private.is_team_captain`
- `audit_team_members` trigger

Admin API `GET /api/admin/audit-logs` podpira tudi filter
`tableName=team_members`. Izpostavljena so samo allowlist operativna polja;
raw audit JSON ni vrnjen.

## Testi

```powershell
cd C:\DataOpsProjekt\DotaOps\backend
.\mvnw.cmd test
```

PostgreSQL/Supabase integracijski testi se izvedejo, ko je nastavljen
`SUPABASE_DB_URL`.
