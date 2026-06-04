# Backend Storage Upload API

## Namen

BE/DB-26 dodaja backend in Supabase Storage podporo za varne uploade javnih slik:

- avatarji profilov,
- logotipi ekip,
- bannerji ekip.

Stari multipart endpointi ostajajo zaradi kompatibilnosti, novi flow pa uporablja backend validacijo, Supabase signed upload URL in confirm endpoint, ki sele nato posodobi `profiles.avatar_url`, `teams.logo_url` ali `teams.banner_url`.

## Buckets

| Bucket | Public read | Write pravilo |
| --- | --- | --- |
| `avatars` | Da | Authenticated uporabnik samo za svoj `profiles/{profileId}/avatar.*` path |
| `team-assets` | Da | Samo captain/owner ekipe za `teams/{teamId}/logo.*` in `teams/{teamId}/banner.*` |

Obstojeci `dotaops-images` bucket ostaja za legacy server-side multipart upload.

## Path convention

Backend generira stabilne poti. `fileName` iz requesta se uporablja samo za validacijo koncnice; ime datoteke ni nikoli del koncnega object path-a.

| Asset | Bucket | Path |
| --- | --- | --- |
| Avatar | `avatars` | `profiles/{profileId}/avatar.{png|jpg|webp}` |
| Team logo | `team-assets` | `teams/{teamId}/logo.{png|jpg|webp}` |
| Team banner | `team-assets` | `teams/{teamId}/banner.{png|jpg|webp}` |

## Velikosti in tipi

| Asset | Max size | MIME tipi |
| --- | --- | --- |
| Avatar | 2 MB | `image/png`, `image/jpeg`, `image/webp` |
| Team logo | 2 MB | `image/png`, `image/jpeg`, `image/webp` |
| Team banner | 5 MB | `image/png`, `image/jpeg`, `image/webp` |

SVG ni dovoljen.

## Frontend flow

### Avatar upload

1. Frontend poklice:

```http
POST /api/me/avatar/upload-url
Authorization: Bearer <user-jwt>
Content-Type: application/json
```

```json
{
  "fileName": "avatar.png",
  "contentType": "image/png",
  "fileSizeBytes": 123456
}
```

2. Backend vrne signed upload pogodbo:

```json
{
  "data": {
    "bucket": "avatars",
    "path": "profiles/PROFILE_ID/avatar.png",
    "uploadUrl": "https://PROJECT.supabase.co/storage/v1/object/upload/sign/avatars/profiles/PROFILE_ID/avatar.png?token=...",
    "uploadToken": "...",
    "uploadMethod": "PUT",
    "requiredHeaders": {
      "cache-control": "max-age=3600",
      "content-type": "image/png",
      "x-upsert": "true"
    },
    "publicUrl": "https://PROJECT.supabase.co/storage/v1/object/public/avatars/profiles/PROFILE_ID/avatar.png",
    "expiresInSeconds": 7200,
    "maxFileSizeBytes": 2097152,
    "contentType": "image/png",
    "upsert": true
  }
}
```

3. Frontend upload-a datoteko na `uploadUrl` z metodo `PUT` in zahtevanimi headerji, ali uporabi Supabase SDK `uploadToSignedUrl(path, uploadToken, file)`.

4. Po uspesnem uploadu frontend potrdi:

```http
POST /api/me/avatar/confirm
Authorization: Bearer <user-jwt>
Content-Type: application/json
```

```json
{
  "bucket": "avatars",
  "path": "profiles/PROFILE_ID/avatar.png",
  "publicUrl": "https://PROJECT.supabase.co/storage/v1/object/public/avatars/profiles/PROFILE_ID/avatar.png"
}
```

Confirm response:

```json
{
  "data": {
    "avatarUrl": "https://PROJECT.supabase.co/storage/v1/object/public/avatars/profiles/PROFILE_ID/avatar.png",
    "message": "Avatar upload confirmed.",
    "persisted": true
  }
}
```

### Team logo upload

```http
POST /api/teams/{teamId}/logo/upload-url
POST /api/teams/{teamId}/logo/confirm
```

Dostop: `ROLE_PLAYER` in service-level preverjanje, da je trenutni profil `teams.captain_profile_id` za `teamId`.

Path: `teams/{teamId}/logo.{png|jpg|webp}`.

Confirm posodobi:

- `teams.logo_url`,
- `teams.logo_path`,
- `teams.updated_at`.

### Team banner upload

```http
POST /api/teams/{teamId}/banner/upload-url
POST /api/teams/{teamId}/banner/confirm
```

Dostop: `ROLE_PLAYER` in service-level captain preverjanje.

Path: `teams/{teamId}/banner.{png|jpg|webp}`.

Confirm posodobi:

- `teams.banner_url`,
- `teams.banner_path`,
- `teams.updated_at`.

## Varnostna pravila

- Backend ne vraca Supabase `service_role` kljuca.
- `uploadToken` je Supabase signed upload token, vezan na bucket/path in veljaven 7200 sekund.
- Backend generira path iz trenutnega uporabnika oziroma team id-ja.
- `confirm` ne zaupa frontend `publicUrl`; ce je podan, ga primerja z backend-generiranim public URL-jem.
- Team upload route-i zahtevajo `ROLE_PLAYER`; organizer ne more uploadati team asseta samo zato, ker je organizer.
- Captain je team-level pravilo prek `teams.captain_profile_id`; globalna `TEAM_CAPTAIN` rola se ne uporablja.
- Public read je dovoljen samo zato, ker so avatarji/logotipi/bannerji javni asseti.

## DB spremembe

Migracija `V32__supabase_storage_signed_upload_policies.sql` doda:

- `profiles.avatar_path`,
- `teams.logo_path`,
- `teams.banner_path`,
- format check constrainte za nove path stolpce,
- `avatars` in `team-assets` bucket setup,
- Storage RLS policyje za public read in owner/captain write/update/delete,
- `private.storage_profile_avatar_owner(text)`,
- `private.storage_team_asset_owner(text)`,
- `audit_profiles` trigger.

## Audit

`public.profiles` je dodan v admin audit projekcijo, vendar se iz `previous_row` in `new_row` vracajo samo varna profile polja:

- `nickname`,
- `display_name`,
- `role`,
- `avatar_url`,
- `avatar_path`,
- `updated_at`.

`public.teams` audit projekcija zdaj vkljucuje tudi:

- `logo_url`,
- `logo_path`,
- `banner_url`,
- `banner_path`.

## Znane omejitve

- Confirm endpoint trenutno ne preveri z dodatnim Storage HEAD klicem, ali objekt ze obstaja; potrdi samo validen bucket/path/ownership in backend-generiran public URL.
- Stari multipart endpointi se vedno uporabljajo `dotaops-images`; novi signed flow uporablja `avatars` in `team-assets`.
- CDN lahko po overwrite-u stabilnega path-a kratek cas vraca staro sliko.

## Testi

Relevantni testi:

- `SupabaseImageStorageServiceTest`
- `StorageUploadServiceTest`
- `StorageUploadControllerTest`
- `SecurityConfigTest`
- `AdminAuditLogServiceTest`
- `MigrationIntegrationTest`
- `DatabasePolicyIntegrationTest`

Lokalni unit testi:

```powershell
cd backend
.\mvnw.cmd test "-Dspring.profiles.active=test"
```

Integracijski testi za migracije/RLS se zazenejo samo, ce so nastavljene varne Supabase testne spremenljivke, predvsem `SUPABASE_DB_URL`.
