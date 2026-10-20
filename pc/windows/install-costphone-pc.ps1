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
if (-not $pythonLauncher) {
  Stop-Setup "No encuentro py.exe. Instala Python 3.10 o posterior y activa el lanzador de Python."
}

[object[]]$versionOutput = & $pythonLauncher.Source -3 -c "import sys; print(sys.version_info.major); print(sys.version_info.minor)" 2>$null
if ($LASTEXITCODE -ne 0 -or $versionOutput.Count -lt 2) {
  Stop-Setup "No puedo ejecutar Python 3. Instala Python 3.10 o posterior."
}
try {
  $pythonMajor = [int]$versionOutput[0]
  $pythonMinor = [int]$versionOutput[1]
} catch {
  Stop-Setup "No pude interpretar la versión de Python detectada."
}
if ($pythonMajor -lt 3 -or ($pythonMajor -eq 3 -and $pythonMinor -lt 10)) {
  Stop-Setup "CostPhone PC requiere Python 3.10 o posterior; detecté $pythonMajor.$pythonMinor."
}

$configDir = Join-Path $env:USERPROFILE ".config\opencode"
$jsonPath = Join-Path $configDir "opencode.json"
$jsoncPath = Join-Path $configDir "opencode.jsonc"
if (Test-Path -LiteralPath $jsoncPath) {
  Stop-Setup "Encontré opencode.jsonc. No modifiqué OpenCode. Consulta docs/pc-native-windows.md para añadir el bloque manual sin perder comentarios."
}

$installRoot = Join-Path $env:LOCALAPPDATA "CostPhonePC"
$engineDir = Join-Path $installRoot "engine"
$skillSourceDir = Join-Path $installRoot "skill"
New-Item -ItemType Directory -Path $engineDir -Force | Out-Null
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
  try {
    Invoke-WebRequest -Uri $url -OutFile $file.Destination -UseBasicParsing
  } catch {
    Stop-Setup "No pude descargar $($file.Relative). Comprueba Internet e inténtalo de nuevo."
  }
}

$venvDir = Join-Path $installRoot ".venv"
$venvPython = Join-Path $venvDir "Scripts\python.exe"
if (-not (Test-Path -LiteralPath $venvPython)) {
  & $pythonLauncher.Source -3 -m venv $venvDir
  if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $venvPython)) {
    Stop-Setup "No pude crear el entorno virtual de Python."
  }
}

Write-Host "Instalando o actualizando dependencias locales..."
& $venvPython -m pip install --disable-pip-version-check -r (Join-Path $engineDir "requirements-windows.txt")
if ($LASTEXITCODE -ne 0) {
  Stop-Setup "Falló la instalación de dependencias. Revisa Python/Internet e inténtalo de nuevo."
}
& $venvPython (Join-Path $engineDir "windows_mcp_server.py") --help | Out-Null
if ($LASTEXITCODE -ne 0) {
  Stop-Setup "El servidor MCP local no pasó la comprobación inicial."
}

$inputEnabled = $AllowInput.IsPresent
if (-not $inputEnabled) {
  Write-Host ""
  Write-Host "El control de teclado/ratón permite actuar sobre la ventana activa." -ForegroundColor Yellow
  Write-Host "Por seguridad queda deshabilitado si no das consentimiento."
  $answer = Read-Host "¿Autorizas que OpenCode controle teclado y ratón en este PC? Escribe SI para habilitarlo"
  $inputEnabled = ($answer -ceq "SI")
}

New-Item -ItemType Directory -Path $configDir -Force | Out-Null
$hadConfig = Test-Path -LiteralPath $jsonPath
if ($hadConfig) {
  try {
    $config = Get-Content -LiteralPath $jsonPath -Raw -Encoding UTF8 | ConvertFrom-Json
  } catch {
    Stop-Setup "opencode.json no contiene JSON válido. No modifiqué ese archivo."
  }
  if ($null -eq $config -or $config -isnot [System.Management.Automation.PSCustomObject]) {
    Stop-Setup "La raíz de opencode.json no es un objeto JSON. No modifiqué ese archivo."
  }
} else {
  $config = [pscustomobject][ordered]@{}
}

$mcpProperty = $config.PSObject.Properties["mcp"]
if ($null -eq $mcpProperty -or $null -eq $mcpProperty.Value) {
  $config | Add-Member -MemberType NoteProperty -Name "mcp" -Value ([pscustomobject]@{})
} elseif ($mcpProperty.Value -isnot [System.Management.Automation.PSCustomObject]) {
  Stop-Setup "La propiedad mcp no es un objeto JSON. No modifiqué OpenCode."
}

$mcp = $config.mcp
$command = @($venvPython, (Join-Path $engineDir "windows_mcp_server.py"))
if ($inputEnabled) {
  $command += "--allow-input"
}
$entry = [pscustomobject][ordered]@{
  type = "local"
  command = $command
  enabled = $true
  timeout = 120000
}
$existing = $mcp.PSObject.Properties["costphone-pc"]
if ($null -ne $existing) {
  $mcp.PSObject.Properties.Remove("costphone-pc")
}
$mcp | Add-Member -MemberType NoteProperty -Name "costphone-pc" -Value $entry

if ($hadConfig) {
  $stamp = (Get-Date).ToString("yyyyMMdd-HHmmss")
  $backupPath = "{0}.costphone-pc-backup-{1}" -f $jsonPath, $stamp
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
  $stamp = (Get-Date).ToString("yyyyMMdd-HHmmss")
  $skillBackup = "{0}.costphone-backup-{1}" -f $skillTarget, $stamp
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
  Write-Host "Control de teclado/ratón: autorizado. Revisa la ventana antes de cada acción." -ForegroundColor Yellow
} else {
  Write-Host "Control de teclado/ratón: desactivado; lectura de controles/capturas y apertura de apps/URL siguen disponibles."
}
Write-Host "Cierra y vuelve a abrir OpenCode; después ejecuta: opencode mcp list"
Write-Host "Esta versión funciona en Windows y no necesita el teléfono ni Termux."
