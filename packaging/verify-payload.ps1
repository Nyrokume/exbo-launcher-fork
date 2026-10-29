# Verifies SHA-256 of every file listed in the manifest. Fail closed.
# PAYLOAD_ROOT is the directory that should contain the files.
# PAYLOAD_MANIFEST lines are "<64 lowercase hex> *<relative path>".
# File size is never a success signal: a matching length with a different hash fails.
$ErrorActionPreference = 'Stop'

function Fail([int]$code) { exit $code }

$root = $env:PAYLOAD_ROOT
$manifest = $env:PAYLOAD_MANIFEST
if ([string]::IsNullOrWhiteSpace($root) -or [string]::IsNullOrWhiteSpace($manifest)) { Fail 2 }
if (-not (Test-Path -LiteralPath $root -PathType Container)) { Fail 2 }
if (-not (Test-Path -LiteralPath $manifest -PathType Leaf)) { Fail 2 }

$lines = @(Get-Content -LiteralPath $manifest)
if ($lines.Count -lt 1) { Fail 2 }

$failed = $false
$index = 0
foreach ($raw in $lines) {
    $index++
    $line = $raw.TrimEnd("`r").Trim()
    if ($line.Length -eq 0) { Fail 2 }
    if ($line -notmatch '^([0-9a-f]{64}) \*(.+)$') { Fail 2 }
    $want = $Matches[1]
    $rel = $Matches[2]
    # Best-effort live status. A write failure must not change the hash result.
    if (-not [string]::IsNullOrWhiteSpace($env:PAYLOAD_PROGRESS)) {
        try {
            $utf8 = New-Object System.Text.UTF8Encoding $false
            [System.IO.File]::AppendAllText($env:PAYLOAD_PROGRESS, "v $index $($lines.Count) $rel`r`n", $utf8)
        } catch {}
    }
    # Reject absolute paths and parent segments. Size is not consulted.
    if ($rel -match '[:]|(\.\.)|(^\\)|(^/)') { Fail 2 }
    $path = Join-Path -Path $root -ChildPath $rel
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        $failed = $true
        continue
    }
    $got = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash
    if ([string]::IsNullOrWhiteSpace($got) -or $got.ToLowerInvariant() -ne $want) {
        $failed = $true
    }
}
if ($failed) { Fail 1 }
exit 0
