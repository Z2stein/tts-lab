param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidateNotNullOrEmpty()]
    [string]$Target,

    [string]$AppSlug = "tts-lab",
    [string]$HostName = "178.105.41.67",
    [string]$SshUser = "root",

    [int]$LocalPort = 15432,
    [int]$RemotePort = 15432,

    [switch]$NoPauseOnError
)

$ErrorActionPreference = "Stop"

function Resolve-Namespace {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Target,

        [Parameter(Mandatory = $true)]
        [string]$AppSlug
    )

    $normalizedTarget = $Target.Trim().ToLowerInvariant()
    $normalizedAppSlug = $AppSlug.Trim().ToLowerInvariant()

    switch ($normalizedTarget) {
        "main"       { return $normalizedAppSlug }
        "prod"       { return $normalizedAppSlug }
        "production" { return $normalizedAppSlug }
        "dev"        { return "$normalizedAppSlug-dev" }
        "develop"    { return "$normalizedAppSlug-dev" }
    }

    if ($normalizedTarget -match '^dev(\d+)$') {
        return "$normalizedAppSlug-dev$($matches[1])"
    }

    if ($normalizedTarget -match '^develop(\d+)$') {
        return "$normalizedAppSlug-dev$($matches[1])"
    }

    # If a full Kubernetes namespace was passed, keep it as-is, e.g. tts-lab-dev2.
    if ($normalizedTarget -eq $normalizedAppSlug -or $normalizedTarget.StartsWith("$normalizedAppSlug-")) {
        return $normalizedTarget
    }

    return $normalizedTarget
}

function Invoke-RemoteBash {
    param(
        [Parameter(Mandatory = $true)]
        [string]$SshTarget,

        [Parameter(Mandatory = $true)]
        [string]$Script
    )

    $output = $Script | ssh $SshTarget "tr -d '\r' | bash -s" 2>&1
    $exitCode = $LASTEXITCODE

    if ($exitCode -ne 0) {
        if ($output) {
            $output | ForEach-Object { Write-Host $_ }
        }
        throw "Remote command failed with exit code $exitCode."
    }

    return ,$output
}

function Stop-LocalPortListener {
    param(
        [Parameter(Mandatory = $true)]
        [int]$Port
    )

    Write-Host "Checking whether local port $Port is already in use..."

    $processIds = @()

    if (Get-Command Get-NetTCPConnection -ErrorAction SilentlyContinue) {
        $processIds = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
            Select-Object -ExpandProperty OwningProcess -Unique
    }

    if (-not $processIds -or $processIds.Count -eq 0) {
        $netstatOutput = netstat -ano -p tcp | Select-String -Pattern ":$Port\s+.*LISTENING\s+(\d+)"
        foreach ($line in $netstatOutput) {
            if ($line.Line -match ':\d+\s+.*LISTENING\s+(\d+)') {
                $processIds += [int]$matches[1]
            }
        }
        $processIds = $processIds | Sort-Object -Unique
    }

    if (-not $processIds -or $processIds.Count -eq 0) {
        Write-Host "Local port $Port is free."
        return
    }

    Write-Host "Local port $Port is already in use by PID(s): $($processIds -join ', ')" -ForegroundColor Yellow

    foreach ($processId in $processIds) {
        try {
            $process = Get-Process -Id $processId -ErrorAction Stop
            Write-Host "Killing local PID $processId ($($process.ProcessName))..."
            Stop-Process -Id $processId -Force -ErrorAction Stop
        }
        catch {
            throw "Could not kill local PID $processId on port $Port. $($_.Exception.Message)"
        }
    }

    Start-Sleep -Milliseconds 500

    $stillUsed = $false
    if (Get-Command Get-NetTCPConnection -ErrorAction SilentlyContinue) {
        $stillUsed = [bool](Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
    }
    else {
        $stillUsed = [bool](netstat -ano -p tcp | Select-String -Pattern ":$Port\s+.*LISTENING")
    }

    if ($stillUsed) {
        throw "Local port $Port is still in use after cleanup."
    }

    Write-Host "Local port $Port is free."
}

try {
    $AppSlug = $AppSlug.Trim().ToLowerInvariant()
    $Namespace = Resolve-Namespace -Target $Target -AppSlug $AppSlug

    if ($AppSlug -notmatch '^[a-z0-9]([-a-z0-9]*[a-z0-9])?$') {
        throw "Invalid app slug: $AppSlug"
    }

    if ($Namespace -notmatch '^[a-z0-9]([-a-z0-9]*[a-z0-9])?$') {
        throw "Invalid Kubernetes namespace: $Namespace"
    }

    if ($Target.Trim().ToLowerInvariant() -ne $Namespace) {
        Write-Host "Resolved target '$Target' to Kubernetes namespace '$Namespace'."
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

    $remoteInfoScript = $remoteInfoScriptTemplate.Replace("__NAMESPACE__", $Namespace).Replace("__APP_SLUG__", $AppSlug)

    Write-Host "Reading PostgreSQL service and credentials from Kubernetes..."
    $info = Invoke-RemoteBash -SshTarget $sshTarget -Script $remoteInfoScript

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
        if ($info) {
            $info | ForEach-Object { Write-Host $_ }
        }
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

    Stop-LocalPortListener -Port $LocalPort

    $remotePortCleanupScriptTemplate = @'
set -euo pipefail

PORT="__REMOTE_PORT__"

PIDS=$(ss -H -ltnp "sport = :$PORT" 2>/dev/null \
  | sed -n 's/.*pid=\([0-9][0-9]*\).*/\1/p' \
  | sort -u)

if [ -z "$PIDS" ]; then
  echo "Remote port $PORT is free."
  exit 0
fi

echo "Remote port $PORT is already in use by PID(s): $PIDS"

for PID in $PIDS; do
  CMD=$(ps -p "$PID" -o comm= 2>/dev/null || true)
  ARGS=$(ps -p "$PID" -o args= 2>/dev/null || true)
  echo "Killing remote PID $PID ($CMD): $ARGS"
  kill "$PID" 2>/dev/null || true
done

for _ in $(seq 1 20); do
  if ! ss -H -ltn "sport = :$PORT" 2>/dev/null | grep -q .; then
    echo "Remote port $PORT is free."
    exit 0
  fi
  sleep 0.25
done

echo "Process did not stop after SIGTERM. Sending SIGKILL."

for PID in $PIDS; do
  kill -9 "$PID" 2>/dev/null || true
done

if ss -H -ltn "sport = :$PORT" 2>/dev/null | grep -q .; then
  echo "ERROR: Remote port $PORT is still in use after cleanup:" >&2
  ss -ltnp "sport = :$PORT" >&2 || true
  exit 30
fi

echo "Remote port $PORT is free."
'@

    $remotePortCleanupScript = $remotePortCleanupScriptTemplate.Replace("__REMOTE_PORT__", ([string]$RemotePort))

    Write-Host "Checking whether remote port $RemotePort is already in use..."
    Invoke-RemoteBash -SshTarget $sshTarget -Script $remotePortCleanupScript | ForEach-Object { Write-Host $_ }

    Write-Host "Starting tunnel. Keep this window open. Stop with Ctrl+C."
    Write-Host ""

    $localForward = "${LocalPort}:127.0.0.1:${RemotePort}"
    $remoteCommand = "kubectl port-forward -n $Namespace svc/$service ${RemotePort}:5432 --address 127.0.0.1"

    ssh -L $localForward $sshTarget $remoteCommand
}
catch {
    Write-Host ""
    Write-Host "ERROR: $($_.Exception.Message)" -ForegroundColor Red

    if (-not $NoPauseOnError) {
        Write-Host ""
        Read-Host "Press Enter to close this window"
    }

    exit 1
}
