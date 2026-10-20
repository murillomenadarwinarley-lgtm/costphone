#requires -Version 5.1
[CmdletBinding()]
param([string]$PhoneHost, [string]$TermuxUser, [ValidateRange(1,65535)][int]$Port=8022)
Set-StrictMode -Version Latest
$ErrorActionPreference="Stop"
function Stop-Setup([string]$Message) { Write-Host ""; Write-Host "CostPhone PC: $Message" -ForegroundColor Red; exit 1 }
$sshCommand=Get-Command ssh.exe -ErrorAction SilentlyContinue
$keygenCommand=Get-Command ssh-keygen.exe -ErrorAction SilentlyContinue
if (-not $sshCommand -or -not $keygenCommand) { Stop-Setup "No encuentro OpenSSH Client. Instálalo en Características opcionales de Windows." }
if ([string]::IsNullOrWhiteSpace($PhoneHost)) { $PhoneHost=Read-Host "IP del teléfono Android (misma red Wi-Fi que el PC)" }
if ($PhoneHost -notmatch '^[A-Za-z0-9][A-Za-z0-9.-]*$') { Stop-Setup "IP o nombre de teléfono inválido." }
if ([string]::IsNullOrWhiteSpace($TermuxUser)) { $TermuxUser=Read-Host "Usuario de Termux (resultado de whoami)" }
if ($TermuxUser -notmatch '^[A-Za-z0-9._-]+$') { Stop-Setup "Usuario de Termux inválido." }
$sshDir=Join-Path $env:USERPROFILE ".ssh"
New-Item -ItemType Directory -Path $sshDir -Force | Out-Null
$keyPath=Join-Path $sshDir "id_ed25519_costphone"
$publicKeyPath="$keyPath.pub"
if (-not (Test-Path -LiteralPath $keyPath)) {
  Write-Host "Creando una clave SSH exclusiva para CostPhone..."
  $keygenArgs=@("-t","ed25519","-a","64","-f",$keyPath,"-N","","-C","costphone-windows")
  & $keygenCommand.Source @keygenArgs
  if ($LASTEXITCODE -ne 0) { Stop-Setup "No se pudo crear la clave SSH." }
}
if (-not (Test-Path -LiteralPath $publicKeyPath)) { Stop-Setup "Falta el archivo de clave pública $publicKeyPath." }
$publicKey=(Get-Content -LiteralPath $publicKeyPath -Raw).Trim()
Write-Host ""
Write-Host "Copia esta línea completa a ~/.ssh/authorized_keys de Termux:" -ForegroundColor Cyan
Write-Host $publicKey -ForegroundColor Yellow
Write-Host ""
Write-Host "En Termux ejecuta: chmod 700 ~/.ssh; chmod 600 ~/.ssh/authorized_keys; sshd -p $Port"
[void](Read-Host "Cuando agregues la clave y arranque sshd en el teléfono, presiona Enter")
$sshArgs=@("-T","-p",[string]$Port,"-o","BatchMode=yes","-o","ConnectTimeout=10","-o","StrictHostKeyChecking=accept-new","$TermuxUser@$PhoneHost","printf COSTPHONE_SSH_OK")
$sshOutput=& $sshCommand.Source @sshArgs 2>&1
$sshExitCode=$LASTEXITCODE
$sshText=($sshOutput | ForEach-Object { [string]$_ }) -join [Environment]::NewLine
if ($sshExitCode -ne 0 -or $sshText -notmatch "COSTPHONE_SSH_OK") {
  Write-Host $sshText
  Stop-Setup "No se pudo conectar por SSH. Comprueba IP, usuario, clave, puerto y misma red Wi-Fi. No se modificó OpenCode."
}
$configDir=Join-Path $env:USERPROFILE ".config\opencode"
$jsonPath=Join-Path $configDir "opencode.json"
$jsoncPath=Join-Path $configDir "opencode.jsonc"
if (Test-Path -LiteralPath $jsoncPath) { Stop-Setup "Encontré opencode.jsonc. Para conservar comentarios, añade el bloque manual de docs/pc-windows.md; no modifiqué la configuración." }
New-Item -ItemType Directory -Path $configDir -Force | Out-Null
$hadConfig=Test-Path -LiteralPath $jsonPath
if ($hadConfig) {
  try { $config=Get-Content -LiteralPath $jsonPath -Raw | ConvertFrom-Json }
  catch { Stop-Setup "opencode.json no contiene JSON válido. No se modificó." }
  if ($null -eq $config -or $config -isnot [System.Management.Automation.PSCustomObject]) { Stop-Setup "La raíz de opencode.json no es un objeto JSON." }
} else { $config=[pscustomobject][ordered]@{ '$schema'="https://opencode.ai/config.json" } }
$mcpProperty=$config.PSObject.Properties["mcp"]
if ($null -eq $mcpProperty -or $null -eq $mcpProperty.Value) {
  $config | Add-Member -MemberType NoteProperty -Name "mcp" -Value ([pscustomobject]@{})
} elseif ($mcpProperty.Value -isnot [System.Management.Automation.PSCustomObject]) { Stop-Setup "La propiedad mcp no es un objeto JSON. No se modificó." }
$mcp=$config.mcp
$entry=[pscustomobject][ordered]@{
  type="local"
  command=@("ssh.exe","-T","-p",[string]$Port,"-o","BatchMode=yes","-o","ServerAliveInterval=30","-o","ServerAliveCountMax=2","$TermuxUser@$PhoneHost",'exec "$HOME/bin/costphone-mcp"')
  enabled=$true
  timeout=120000
}
$existing=$mcp.PSObject.Properties["costphone-engine"]
if ($null -ne $existing) { $mcp.PSObject.Properties.Remove("costphone-engine") }
$mcp | Add-Member -MemberType NoteProperty -Name "costphone-engine" -Value $entry
if ($hadConfig) {
  $backupPath="$jsonPath.costphone-backup-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
  Copy-Item -LiteralPath $jsonPath -Destination $backupPath
  Write-Host "Copia de seguridad: $backupPath"
}
$json=$config | ConvertTo-Json -Depth 100
$utf8NoBom=[System.Text.UTF8Encoding]::new($false)
[System.IO.File]::WriteAllText($jsonPath,$json + [Environment]::NewLine,$utf8NoBom)
Write-Host ""
Write-Host "Configuración lista: $jsonPath" -ForegroundColor Green
Write-Host ("MCP costphone-engine -> " + $TermuxUser + "@" + $PhoneHost + ":" + $Port)
Write-Host "Cierra y vuelve a abrir OpenCode; prueba: opencode mcp list"
Write-Host "No compartas la clave privada ni abras el puerto SSH hacia Internet."
