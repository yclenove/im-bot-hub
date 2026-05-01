param(
  [switch]$SkipBackend,
  [switch]$SkipFrontend,
  [switch]$SkipDocs
)

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

function Assert-LastExitCode {
  param(
    [string]$CommandName
  )

  if ($LASTEXITCODE -ne 0) {
    throw "$CommandName failed with exit code $LASTEXITCODE."
  }
}

function Invoke-Step {
  param(
    [string]$Name,
    [scriptblock]$Action
  )

  Write-Host "==> $Name"
  & $Action
}

if (-not $SkipDocs) {
  Invoke-Step 'Check docs consistency' {
    & powershell -ExecutionPolicy Bypass -File (Join-Path $repoRoot 'scripts/dev/check-docs.ps1')
    Assert-LastExitCode 'check-docs.ps1'
  }
}

if (-not $SkipBackend) {
  Invoke-Step 'Run backend tests' {
    $backendWrapper = Join-Path $repoRoot 'backend/mvnw.cmd'

    if (Test-Path $backendWrapper) {
      Push-Location (Join-Path $repoRoot 'backend')
      try {
        & .\mvnw.cmd -q test
        Assert-LastExitCode 'backend/mvnw.cmd test'
        return
      }
      finally {
        Pop-Location
      }
    }

    if (Get-Command mvn -ErrorAction SilentlyContinue) {
      & mvn -q -f (Join-Path $repoRoot 'backend/pom.xml') test
      Assert-LastExitCode 'mvn test'
      return
    }

    throw 'Cannot run backend tests because neither backend/mvnw.cmd nor mvn is available.'
  }
}

if (-not $SkipFrontend) {
  Invoke-Step 'Run frontend build' {
    Push-Location (Join-Path $repoRoot 'admin-ui')
    try {
      & npm run build
      Assert-LastExitCode 'npm run build'
    }
    finally {
      Pop-Location
    }
  }
}

Write-Host 'Quality gates completed.'
