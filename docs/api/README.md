# DotaOps API Documentation Index

Ta dokument je krovni kazalnik za obstojece API zapiske. Namenjen je hitremu iskanju pravega modula, ne podvaja celotnih endpoint specifikacij.

## Auth In Profile

- Primarni tokovi so opisani v `../project-current-state-overview.md` in v frontend auth integraciji.
- Register UI uporablja samo globalni vlogi `player` in `organizer`.
- Team captain je team-specific stanje prek ekipe, ne globalna auth rola.

## Tournaments

- Public tournament list/detail, registration, groups, standings, bracket, schedule and result flows are covered across `../project-current-state-overview.md` and implementation notes in the frontend/backend source.
- Organizer tournament management uses protected `/api/organizer/**` routes.

## My Team

- Team profile, roster, invitations, join requests, manual players, and image/banner API notes:
  - `../backend-my-team-api.md`
  - `../backend-storage-upload-api.md`

## Analytics

- Public and role-based analytics endpoints, filters, lookups, comparison endpoints, and admin refresh:
  - `../backend-analytics-api.md`

## Admin And Audit

- Admin-only actions are protected under `/api/admin/**`.
- Notification outbox and analytics refresh are summarized in `../project-current-state-overview.md` and `../backend-guide.md`.

## Storage And Upload

- Team logo/banner upload and storage details:
  - `../backend-storage-upload-api.md`

## Demo Data

- Local/demo seed setup, demo users, tournament fixtures, match imports, and analytics data:
  - `../backend-demo-seed.md`
