# Exit 0 when the .lnk target is the expected file, 1 when it points elsewhere,
# 3 when the link is missing, 2 when the target cannot be read.
# Callers must not delete or replace a link unless the exit code is 0 (or 3 to create).
$ErrorActionPreference = 'Stop'

function Canon([string]$path) {
    if ([string]::IsNullOrWhiteSpace($path)) { return '' }
    try {
        return (Get-Item -LiteralPath $path -ErrorAction Stop).FullName.TrimEnd('\').ToLowerInvariant()
    } catch {
        try { return ([System.IO.Path]::GetFullPath($path)).TrimEnd('\').ToLowerInvariant() }
        catch { return $path.TrimEnd('\').ToLowerInvariant() }
    }
}

$lnk = $env:LNK_PATH
$expect = $env:LNK_EXPECT
if ([string]::IsNullOrWhiteSpace($lnk)) { exit 2 }
if (-not (Test-Path -LiteralPath $lnk -PathType Leaf)) { exit 3 }
try {
    $shell = New-Object -ComObject WScript.Shell
    $target = [string]$shell.CreateShortcut($lnk).TargetPath
} catch {
    exit 2
}
if ([string]::IsNullOrWhiteSpace($target)) { exit 2 }
if ((Canon $target) -eq (Canon $expect)) { exit 0 }
exit 1
