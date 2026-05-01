param(
  [Parameter(Mandatory = $true)]
  [string]$Message,

  [string]$Branch = 'main',

  [switch]$SkipBackend,
  [switch]$SkipFrontend,
  [switch]$SkipDocs
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

Push-Location $repoRoot
try {
  $qualityGateArgs = @()
  if ($SkipBackend) {
    $qualityGateArgs += '-SkipBackend'
  }
  if ($SkipFrontend) {
    $qualityGateArgs += '-SkipFrontend'
  }
  if ($SkipDocs) {
    $qualityGateArgs += '-SkipDocs'
  }

  & powershell -ExecutionPolicy Bypass -File (Join-Path $repoRoot 'scripts/dev/run-quality-gates.ps1') @qualityGateArgs

  & git status --short
  & git add .
  & git commit -m $Message
  & git push origin $Branch
}
finally {
  Pop-Location
}
