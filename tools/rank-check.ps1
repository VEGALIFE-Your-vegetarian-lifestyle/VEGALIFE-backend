#!/usr/bin/env pwsh
# ---------------------------------------------------------------------------
# Rank check for ADR-018 (clean-architecture package dependencies).
#
# Scans every import in src/main/java for com.vegalife types, maps both the
# importing package and the imported type onto the ADR's layer ranks, and
# reports every edge that points from a lower rank to a higher one. Same-rank
# edges are tolerated (ADR-018 documents the remaining ones).
#
# Run from the repository root:
#   pwsh tools/rank-check.ps1
#
# Exit code 0 = no upward edges, 1 = at least one upward edge (listed).
# ---------------------------------------------------------------------------
$ErrorActionPreference = 'Stop'

$root = Join-Path $PSScriptRoot '..\src\main\java\com\vegalife'
$root = (Resolve-Path $root).Path

function Get-Rank([string]$p) {
  if ($p -eq 'com.vegalife') { return 0 }
  if ($p -eq 'com.vegalife.shared' -or $p -like 'com.vegalife.shared.*' -or $p -like 'com.vegalife.enums*') { return 1 }
  if ($p -like 'com.vegalife.model*') { return 2 }
  if ($p -like 'com.vegalife.repository*') { return 3 }
  if ($p -like 'com.vegalife.dto*') { return 4 }
  if ($p -like 'com.vegalife.service*') { return 5 }
  if ($p -like 'com.vegalife.infrastructure*') { return 6 }
  if ($p -like 'com.vegalife.controller*' -or $p -like 'com.vegalife.scheduled*') { return 7 }
  if ($p -like 'com.vegalife.config*') { return 8 }
  return -1
}

# Imported targets may be classes or packages: strip trailing segments until the
# remainder resolves to a layer package.
function Get-ImportRank([string]$imp) {
  $segs = $imp -split '\.'
  for ($i = $segs.Count - 1; $i -ge 0; $i--) {
    $cand = ($segs[0..$i]) -join '.'
    $r = Get-Rank $cand
    if ($r -ne -1) { return $r }
  }
  return -1
}

$violations = @()
foreach ($f in (Get-ChildItem -Path $root -Recurse -Filter *.java)) {
  $rel = $f.FullName.Substring($root.Length + 1).Replace('\', '/')
  $fromPkg = if ($rel -match '/') {
    'com.vegalife.' + ($rel.Substring(0, $rel.LastIndexOf('/')) -replace '/', '.')
  } else {
    'com.vegalife'
  }
  $rf = Get-Rank $fromPkg
  foreach ($line in (Get-Content $f.FullName)) {
    if ($line -notmatch '^\s*import\s+(static\s+)?(com\.vegalife\.[\w\.]+);') { continue }
    $imp = $Matches[2]
    $rt = Get-ImportRank $imp
    if ($rt -eq -1 -or $rf -eq -1 -or $rt -eq 0) { continue }
    if ($rt -gt $rf) {
      $violations += ("UPWARD rank $rf -> rank " + $rt + " : " + $fromPkg + " imports " + $imp)
    }
  }
}

$violations | Sort-Object -Unique | ForEach-Object { Write-Output $_ }
Write-Output ('upward edges: ' + ($violations | Sort-Object -Unique).Count)
if (($violations | Sort-Object -Unique).Count -gt 0) { exit 1 }
exit 0
