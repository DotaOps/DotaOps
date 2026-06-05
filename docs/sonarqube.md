# SonarQube

This repository is configured as a single SonarQube project for the Java backend, Next.js frontend, Supabase/config files, scripts, and GitHub workflows.

## GitHub setup

Create these repository settings before enabling the workflow:

- Secret `SONAR_TOKEN`: a SonarQube project analysis token.
- Variable `SONAR_HOST_URL`: the SonarQube Server URL, for example `https://sonarqube.example.com`.
- Optional secret `SONAR_ROOT_CERT`: PEM certificate content if the SonarQube Server uses a private CA.

The default project key is `dotaops`. If your SonarQube project uses another key, update `sonar.projectKey` in `sonar-project.properties`.

## CI behavior

The `.github/workflows/sonarqube.yml` workflow runs on pushes and pull requests targeting `main` or `development`, plus manual `workflow_dispatch` runs.

If `SONAR_TOKEN` or `SONAR_HOST_URL` is missing, the workflow exits successfully after printing a skip message. When configured, it:

1. Checks out the full Git history for better blame and new-code analysis.
2. Compiles backend Java bytecode required by the SonarQube Java analyzer.
3. Installs frontend dependencies for more accurate TypeScript analysis.
4. Runs `SonarSource/sonarqube-scan-action@v7` from the repository root.

## Local scan

Compile backend bytecode before running a local scan:

```powershell
Set-Location backend
.\mvnw.cmd -B -DskipTests clean test-compile
Set-Location ..
sonar-scanner.bat -D"sonar.host.url=https://sonarqube.example.com" -D"sonar.token=$env:SONAR_TOKEN"
```
