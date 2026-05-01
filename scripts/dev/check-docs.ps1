param()

$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)

$requiredDocs = @(
  'README.md',
  'CHANGELOG.md',
  'AGENTS.md',
  'docs/PRD.md',
  'docs/REQUIREMENTS-ANALYSIS.md',
  'docs/DESIGN.md',
  'docs/TEST-STRATEGY.md',
  'docs/ITERATION-PLAN.md',
  'docs/WORKFLOW.md',
  'docs/CODING-STANDARD.md'
)

foreach ($relativePath in $requiredDocs) {
  $fullPath = Join-Path $repoRoot $relativePath
  if (-not (Test-Path $fullPath)) {
    throw "Missing required document: $relativePath"
  }
}

$readme = Get-Content (Join-Path $repoRoot 'README.md') -Raw
$agents = Get-Content (Join-Path $repoRoot 'AGENTS.md') -Raw
$workflow = Get-Content (Join-Path $repoRoot 'docs/WORKFLOW.md') -Raw

$requiredReadmeRefs = @(
  'docs/PRD.md',
  'docs/REQUIREMENTS-ANALYSIS.md',
  'docs/DESIGN.md',
  'docs/TEST-STRATEGY.md',
  'docs/ITERATION-PLAN.md',
  'docs/WORKFLOW.md'
)

foreach ($ref in $requiredReadmeRefs) {
  if (-not $readme.Contains($ref)) {
    throw "README.md is missing doc reference: $ref"
  }
}

$requiredAgentRefs = @(
  'docs/PRD.md',
  'docs/REQUIREMENTS-ANALYSIS.md',
  'docs/DESIGN.md',
  'docs/TEST-STRATEGY.md',
  'docs/ITERATION-PLAN.md'
)

foreach ($ref in $requiredAgentRefs) {
  if (-not $agents.Contains($ref)) {
    throw "AGENTS.md is missing doc reference: $ref"
  }
}

if (-not $workflow.Contains('实现 -> 测试 -> 更新 `README` / `CHANGELOG` / 相关设计文档 -> 中文提交信息 -> push')) {
  throw 'docs/WORKFLOW.md is missing the delivery-order rule'
}

Write-Host 'Docs consistency check passed.'
