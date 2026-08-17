param(
    [Parameter(Mandatory = $false)]
    [string] $DatabaseUrl = $env:DATABASE_URL,

    [Parameter(Mandatory = $false)]
    [switch] $ConfirmDemoSeed,

    [Parameter(Mandatory = $false)]
    [switch] $ResetFirst,

    [Parameter(Mandatory = $false)]
    [switch] $CleanGeneratedTestData,

    [Parameter(Mandatory = $false)]
    [switch] $SkipVerify,

    [Parameter(Mandatory = $false)]
    [switch] $AllowProductionTarget
)

$ErrorActionPreference = "Stop"

if (-not $ConfirmDemoSeed) {
    throw "Demo seed requires explicit confirmation. Re-run with -ConfirmDemoSeed."
}

$productionSignals = @(
    $env:DOTAOPS_ENV,
    $env:APP_ENV,
    $env:SPRING_PROFILES_ACTIVE,
    $env:NODE_ENV
) | Where-Object { $_ -and $_.ToLowerInvariant().Contains("prod") }

if ($productionSignals.Count -gt 0 -and -not $AllowProductionTarget) {
    throw "Production-like environment detected ($($productionSignals -join ', ')). Refusing demo seed without -AllowProductionTarget."
}

if (-not $DatabaseUrl) {
    throw "Database connection string is missing. Set DATABASE_URL or pass -DatabaseUrl."
}

$psql = Get-Command psql -ErrorAction SilentlyContinue
if (-not $psql) {
    throw "psql was not found on PATH. Install PostgreSQL client tools or run the SQL files manually."
}

$repoRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$demoDir = Join-Path $repoRoot "backend/src/main/resources/db/demo"
$seedSql = Join-Path $demoDir "demo-seed.sql"
$resetSql = Join-Path $demoDir "reset-demo-seed.sql"
$resetGeneratedSql = Join-Path $demoDir "reset-generated-test-data.sql"
$verifySql = Join-Path $demoDir "verify-demo-seed.sql"

if (-not (Test-Path -LiteralPath $seedSql)) {
    throw "Demo seed SQL not found: $seedSql"
}

if ($CleanGeneratedTestData) {
    if (-not (Test-Path -LiteralPath $resetGeneratedSql)) {
        throw "Generated test data reset SQL not found: $resetGeneratedSql"
    }

    & $psql.Source $DatabaseUrl -v ON_ERROR_STOP=1 -f $resetGeneratedSql
} elseif ($ResetFirst) {
    if (-not (Test-Path -LiteralPath $resetSql)) {
        throw "Demo reset SQL not found: $resetSql"
    }

    & $psql.Source $DatabaseUrl -v ON_ERROR_STOP=1 -f $resetSql
}

& $psql.Source $DatabaseUrl -v ON_ERROR_STOP=1 -f $seedSql

if (-not $SkipVerify) {
    if (-not (Test-Path -LiteralPath $verifySql)) {
        throw "Demo verify SQL not found: $verifySql"
    }

    & $psql.Source $DatabaseUrl -v ON_ERROR_STOP=1 -f $verifySql
}
