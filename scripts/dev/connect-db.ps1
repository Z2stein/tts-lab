param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$Target,

    [string]$AppSlug = "tts-lab",
    [string]$HostName = "178.105.41.67",
    [string]$SshUser = "root",

    [int]$LocalPort = 15432,
    [int]$RemotePort = 15432
)

$ErrorActionPreference = "Stop"

function Resolve-Namespace {
    param(
        [string]$Target,
        [string]$AppSlug
    )

    switch ($Target.ToLowerInvariant()) {
        "main"    { return $AppSlug }
        "dev"     { return "$AppSlug-dev" }
        "develop" { return "$AppSlug-dev" }
        default   { return $Target }
    }
}

$Namespace = Resolve-Namespace -Target $Target -AppSlug $AppSlug

if ($AppSlug -notmatch '^[a-z0-9]([-a-z0-9]*[a-z0-9])?$') {
    throw "Invalid app slug: $AppSlug"
}

if ($Namespace -notmatch '^[a-z0-9]([-a-z0-9]*[a-z0-9])?$') {
    throw "Invalid Kubernetes namespace: $Namespace"
}

if ($Target.ToLowerInvariant() -eq "main" -or $Namespace -eq $AppSlug) {
    Write-Host ""
    Write-Host "WARNING: You are connecting to MAIN / permanent database." -ForegroundColor Yellow
    Write-Host "Be careful with manual data changes." -ForegroundColor Yellow
    Write-Host ""
}

if ($Target.ToLowerInvariant() -eq "dev" -or $Target.ToLowerInvariant() -eq "develop" -or $Namespace -eq "$AppSlug-dev") {
    Write-Host ""
    Write-Host "INFO: You are connecting to DEVELOP / permanent dev database." -ForegroundColor Yellow
    Write-Host "Data survives deployments." -ForegroundColor Yellow
    Write-Host ""
}

$sshTarget = "$SshUser@$HostName"

Write-Host "Using SSH target: $sshTarget"
Write-Host "Target:           $Target"
Write-Host "Resolved NS:      $Namespace"
Write-Host ""

$remoteInfoScriptTemplate = @'
set -euo pipefail

NS="__NAMESPACE__"
APP_SLUG="__APP_SLUG__"

if ! kubectl get namespace "$NS" >/dev/null 2>&1; then
  echo "ERROR: Namespace does not exist: $NS" >&2
  echo "Available app namespaces:" >&2
  kubectl get ns -o jsonpath='{range .items[*]}{.metadata.name}{"\n"}{end}' | grep -E "^${APP_SLUG}($|-)" >&2 || true
  exit 20
fi

SVC=$(kubectl get svc -n "$NS" \
  -l app.kubernetes.io/component=postgresql \
  -o jsonpath='{range .items[*]}{.metadata.name}{"\n"}{end}' \
  | head -n 1 || true)

if [ -z "$SVC" ]; then
  SVC=$(kubectl get svc -n "$NS" -o jsonpath='{range .items[*]}{.metadata.name}{"\n"}{end}' \
    | grep -Ei 'postgres|postgresql' \
    | head -n 1 || true)
fi

if [ -z "$SVC" ]; then
  echo "ERROR: No PostgreSQL service found in namespace $NS" >&2
  echo "Services in namespace:" >&2
  kubectl get svc -n "$NS" >&2
  exit 21
fi

if kubectl get secret "$SVC" -n "$NS" >/dev/null 2>&1; then
  SECRET="$SVC"
else
  SECRET=$(kubectl get secret -n "$NS" \
    -o jsonpath='{range .items[*]}{.metadata.name}{"|"}{.data.database}{"|"}{.data.username}{"|"}{.data.password}{"\n"}{end}' \
    | awk -F'|' '$2 != "" && $3 != "" && $4 != "" { print $1; exit }')
fi

if [ -z "${SECRET:-}" ]; then
  echo "ERROR: No DB credential secret with keys database/username/password found in namespace $NS" >&2
  echo "Secrets in namespace:" >&2
  kubectl get secrets -n "$NS" >&2
  exit 22
fi

DB=$(kubectl get secret "$SECRET" -n "$NS" -o jsonpath='{.data.database}' | base64 -d)
DB_USER=$(kubectl get secret "$SECRET" -n "$NS" -o jsonpath='{.data.username}' | base64 -d)
DB_PASS=$(kubectl get secret "$SECRET" -n "$NS" -o jsonpath='{.data.password}' | base64 -d)

printf 'SERVICE=%s\n' "$SVC"
printf 'SECRET=%s\n' "$SECRET"
printf 'DATABASE=%s\n' "$DB"
printf 'USERNAME=%s\n' "$DB_USER"
printf 'PASSWORD=%s\n' "$DB_PASS"
'@

$remoteInfoScript = $remoteInfoScriptTemplate `
    -replace "__NAMESPACE__", $Namespace `
    -replace "__APP_SLUG__", $AppSlug

Write-Host "Reading PostgreSQL service and credentials from Kubernetes..."
$info = $remoteInfoScript | ssh $sshTarget "tr -d '\r' | bash -s"

$values = @{}

foreach ($line in $info) {
    if ($line -match '^([^=]+)=(.*)$') {
        $values[$matches[1]] = $matches[2]
    }
}

$service = $values["SERVICE"]
$secret = $values["SECRET"]
$database = $values["DATABASE"]
$username = $values["USERNAME"]
$password = $values["PASSWORD"]

if (-not $service -or -not $secret -or -not $database -or -not $username -or -not $password) {
    Write-Host $info
    throw "Could not read all required connection values."
}

Write-Host ""
Write-Host "PostgreSQL service found:"
Write-Host "  Namespace: $Namespace"
Write-Host "  Service:   $service"
Write-Host "  Secret:    $secret"
Write-Host ""
Write-Host "Use these values in IntelliJ/DataGrip:"
Write-Host "  Host:      localhost"
Write-Host "  Port:      $LocalPort"
Write-Host "  Database:  $database"
Write-Host "  User:      $username"
Write-Host "  Password:  $password"
Write-Host "  JDBC URL:  jdbc:postgresql://localhost:$LocalPort/$database"
Write-Host ""
Write-Host "Important: In IntelliJ, disable 'Use SSH tunnel'. This script already creates the tunnel."
Write-Host ""
Write-Host "Starting tunnel. Keep this window open. Stop with Ctrl+C."
Write-Host ""

$localForward = "${LocalPort}:127.0.0.1:${RemotePort}"
$remoteCommand = "kubectl port-forward -n $Namespace svc/$service ${RemotePort}:5432 --address 127.0.0.1"

ssh -L $localForward $sshTarget $remoteCommand