#requires -Version 5.1
[CmdletBinding()]
param([switch]$AllowInput)
Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Stop-Setup([string]$Message) {
  Write-Host ""
  Write-Host "CostPhone PC: $Message" -ForegroundColor Red
  exit 1
}

$pythonLauncher = Get-Command py.exe -ErrorAction SilentlyContinue
if (-not $pythonLauncher) { Stop-Setup "No encuentro el lanzador py.exe. Instala Python 3.10+ desde python.org y marca 'Add Python to PATH'." }
$pythonVersion = & $pythonLauncher.Source -3 -c "import sys; print(f'{sys.version_info.major}.{sys.version_info.minor}')" 2>$null
if ($LASTEXITCODE -ne 0 -or $pythonVersion -notmatch '^([0-9]+)\.([0-9]+)$') { Stop-Setup "No puedo ejecutar Python 3. Instala Python 3.10 o posterior." }
if ([int]$Matches[1] -lt 3 -or ([int]$Matches[1] -eq 3 -and [int]$Matches[2] -lt 10)) { Stop-Setup "CostPhone PC requiere Python 3.10 o posterior; detecté $pythonVersion." }

$configDir = Join-Path $env:USERPROFILE ".config\opencode"
$jsonPath = Join-Path $configDir "opencode.json"
$jsoncPath = Join-Path $configDir "opencode.jsonc"
if (Test-Path -LiteralPath $jsoncPath) {
  Stop-Setup "Encontré opencode.jsonc. No se modificó nada de OpenCode; consulta docs/pc-native-windows.md para añadir el bloque manual sin perder comentarios."
}

$installRoot = Join-Path $env:LOCALAPPDATA "CostPhonePC"
$engineDir = Join-Path $installRoot "engine"
New-Item -ItemType Directory -Path $engineDir -Force | Out-Null
$skillSourceDir = Join-Path $installRoot "skill"
New-Item -ItemType Directory -Path $skillSourceDir -Force | Out-Null
$baseUrl = "https://raw.githubusercontent.com/murillomenadarwinarley-lgtm/costphone/engine/costphone-engine-v0"
$downloadFiles = @(
  @{ Relative = "engine/windows_engine.py"; Destination = (Join-Path $engineDir "windows_engine.py") },
  @{ Relative = "engine/windows_mcp_server.py"; Destination = (Join-Path $engineDir "windows_mcp_server.py") },
  @{ Relative = "engine/requirements-windows.txt"; Destination = (Join-Path $engineDir "requirements-windows.txt") },
  @{ Relative = "skill/costphone-pc/SKILL.md"; Destination = (Join-Path $skillSourceDir "SKILL.md") }
)
foreach ($file in $downloadFiles) {
  $url = "$baseUrl/$($file.Relative)"
  try { Invoke-WebRequest -Uri $url -OutFile $file.Destination -UseBasicParsing } catch { Stop-Setup "No pude descargar $($file.Relative). Comprueba tu conexión y vuelve a intentarlo." }
}

$venvDir = Join-Path $installRoot ".venv"
$venvPython = Join-Path $venvDir "Scripts\python.exe"
if (-not (Test-Path -LiteralPath $venvPython)) {
  & $pythonLauncher.Source -3 -m venv $venvDir
  if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $venvPython)) { Stop-Setup "No pude crear el entorno virtual de Python." }
}
Write-Host "Instalando dependencias locales de CostPhone PC..."
& $venvPython -m pip install --disable-pip-version-check -r (Join-Path $engineDir "requirements-windows.txt")
if ($LASTEXITCODE -ne 0) { Stop-Setup "Falló la instalación de dependencias. Revisa Python/Internet y vuelve a ejecutar el instalador." }
& $venvPython (Join-Path $engineDir "windows_mcp_server.py") --help | Out-Null
if ($LASTEXITCODE -ne 0) { Stop-Setup "El servidor MCP local no pasó la comprobación inicial." }

$inputEnabled = $AllowInput.IsPresent
if (-not $inputEnabled) {
  Write-Host ""
  Write-Host "El control de teclado/ratón puede hacer clic o escribir en la ventana activa." -ForegroundColor Yellow
  Write-Host "Por seguridad queda deshabilitado de forma predeterminada."
  $answer = Read-Host "¿Autorizas que OpenCode controle teclado y ratón en este PC? Escribe SI para habilitarlo"
  $inputEnabled = ($answer -ceq "SI")
}

New-Item -ItemType Directory -Path $configDir -Force | Out-Null
$hadConfig = Test-Path -LiteralPath $jsonPath
if ($hadConfig) {
  try { $config = Get-Content -LiteralPath $jsonPath -Raw -Encoding UTF8 | ConvertFrom-Json }
  catch { Stop-Setup "opencode.json no contiene JSON válido. No lo modifiqué." }
  if ($null -eq $config -or $config -isnot [System.Management.Automation.PSCustomObject]) { Stop-Setup "La raíz de opencode.json no es un objeto JSON. No lo modifiqué." }
} else {
  $config = [pscustomobject][ordered]@{ '$schema' = "https://opencode.ai/config.json" }
}
$mcpProperty = $config.PSObject.Properties["mcp"]
if ($null -eq $mcpProperty -or $null -eq $mcpProperty.Value) {
  $config | Add-Member -MemberType NoteProperty -Name "mcp" -Value ([pscustomobject]@{})
} elseif ($mcpProperty.Value -isnot [System.Management.Automation.PSCustomObject]) {
  Stop-Setup "La propiedad mcp no es un objeto JSON. No modifiqué la configuración."
}
$mcp = $config.mcp
$command = @($venvPython, (Join-Path $engineDir "windows_mcp_server.py"))
if ($inputEnabled) { $command += "--allow-input" }
$entry = [pscustomobject][ordered]@{
  type = "local"
  command = $command
  enabled = $true
  timeout = 120000
}
$existing = $mcp.PSObject.Properties["costphone-pc"]
if ($null -ne $existing) { $mcp.PSObject.Properties.Remove("costphone-pc") }
$mcp | Add-Member -MemberType NoteProperty -Name "costphone-pc" -Value $entry

if ($hadConfig) {
  $backupPath = "$jsonPath.costphone-pc-backup-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
  Copy-Item -LiteralPath $jsonPath -Destination $backupPath
  Write-Host "Copia de seguridad de OpenCode: $backupPath"
}
$json = $config | ConvertTo-Json -Depth 100
$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
[System.IO.File]::WriteAllText($jsonPath, $json + [Environment]::NewLine, $utf8NoBom)

$skillTargetDir = Join-Path $configDir "skills\costphone-pc"
$skillTarget = Join-Path $skillTargetDir "SKILL.md"
New-Item -ItemType Directory -Path $skillTargetDir -Force | Out-Null
if (Test-Path -LiteralPath $skillTarget) {
  $skillBackup = "$skillTarget.costphone-backup-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
  Copy-Item -LiteralPath $skillTarget -Destination $skillBackup
  Write-Host "Copia de seguridad del skill anterior: $skillBackup"
}
Copy-Item -LiteralPath (Join-Path $skillSourceDir "SKILL.md") -Destination $skillTarget -Force

Write-Host ""
Write-Host "CostPhone PC nativo quedó instalado." -ForegroundColor Green
Write-Host "Motor: $engineDir"
Write-Host "Configuración OpenCode: $jsonPath"
Write-Host "MCP: costphone-pc"
if ($inputEnabled) {
  Write-Host "Control teclado/ratón: AUTORIZADO (usa escritorio activo; revisa cada acción)." -ForegroundColor Yellow
} else {
  Write-Host "Control teclado/ratón: desactivado; inspección, capturas, abrir .exe y URL siguen habilitados."
}
Write-Host "Cierra y vuelve a abrir OpenCode; después ejecuta: opencode mcp list"
Write-Host "Esta versión funciona en Windows y no necesita el teléfono ni Termux."
