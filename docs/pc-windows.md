# CostPhone desde un PC Windows, sin pagar por el puente

## Qué queda ejecutándose en cada dispositivo

- **PC Windows:** OpenCode y la interfaz desde la que trabajas.
- **Teléfono Android:** APK de CostPhone, servicio de Accesibilidad, Termux, amctl y el motor Python/MCP que ya tienes.
- **Conexión:** SSH por la red local. No hace falta publicar ningún puerto en Internet ni instalar una nube de pago para conectar ambos dispositivos.

Esta adaptación no convierte amctl en un programa nativo de Windows: el motor sigue ejecutándose en Termux, porque su transporte depende de Android. El PC se conecta a ese motor por SSH.

## 1. Preparar el teléfono (Termux)

Abre Termux y ejecuta:

    pkg update
    pkg install -y python openssh curl

Instala o actualiza el motor desde la rama de trabajo:

    curl -fL --connect-timeout 10 --max-time 60 "https://raw.githubusercontent.com/murillomenadarwinarley-lgtm/costphone/engine/costphone-engine-v0/termux/install-costphone-engine.sh" -o "$HOME/install-costphone-engine.sh" &&
    bash "$HOME/install-costphone-engine.sh"

Comprueba el usuario y la IP local:

    whoami
    ip -4 addr show wlan0

Usa la dirección local de la interfaz conectada al mismo Wi-Fi que el PC (normalmente una dirección 192.168.x.x o 10.x.x.x). No uses 127.0.0.1.

Prepara el archivo que autoriza la clave del PC:

    mkdir -p ~/.ssh
    chmod 700 ~/.ssh
    touch ~/.ssh/authorized_keys
    chmod 600 ~/.ssh/authorized_keys

Inicia el servidor SSH en Termux:

    sshd -p 8022

El servidor suele quedar en segundo plano. No configures reenvío de puertos en el router ni expongas el puerto 8022 a Internet.

## 2. Preparar Windows

Necesitas Windows 10/11 y OpenSSH Client. Comprueba en PowerShell:

    ssh -V
    ssh-keygen -?

Si no reconoce esos comandos, instala OpenSSH Client en Configuración → Sistema → Características opcionales → Agregar una característica.

Descarga el instalador desde esta rama del repositorio y ejecútalo en PowerShell:

    $script = "$env:TEMP\setup-costphone.ps1"
    Invoke-WebRequest "https://raw.githubusercontent.com/murillomenadarwinarley-lgtm/costphone/engine/costphone-engine-v0/pc/windows/setup-costphone.ps1" -OutFile $script
    Set-ExecutionPolicy -Scope Process Bypass -Force
    & $script

El instalador solicita la IP del teléfono y el usuario que imprimió whoami en Termux. Genera una clave SSH exclusiva para CostPhone y muestra la clave pública que debes copiar.

En el teléfono, pega la línea pública completa en ~/.ssh/authorized_keys en una sola línea. Guarda el archivo. Después ejecuta:

    chmod 700 ~/.ssh
    chmod 600 ~/.ssh/authorized_keys
    sshd -p 8022

Vuelve a PowerShell y pulsa Enter. El instalador comprobará que la conexión por clave funciona y añadirá costphone-engine a la configuración global de OpenCode. Si ya existe opencode.json, crea una copia de seguridad y conserva las demás propiedades. Si detecta opencode.jsonc, se detiene en lugar de sobrescribir comentarios.

## 3. Comprobar la conexión

Cierra y vuelve a abrir OpenCode en Windows. En una terminal de PowerShell ejecuta:

    opencode mcp list

Comprueba que costphone-engine aparece conectado. Desde OpenCode, primero pide una inspección no destructiva de la pantalla del teléfono. Para cualquier acción que envíe un mensaje, publique, borre, confirme una compra o cambie información, revisa el plan antes de ejecutarlo.

## Configuración manual si usas JSONC

Si tu configuración existente es %USERPROFILE%\.config\opencode\opencode.jsonc, no la reemplaces por un archivo nuevo ni borres sus comentarios. Agrega este objeto dentro de la propiedad raíz mcp (si mcp ya existe, añade solo la entrada costphone-engine):

    "costphone-engine": {
      "type": "local",
      "command": [
        "ssh.exe",
        "-T",
        "-p",
        "8022",
        "-o",
        "BatchMode=yes",
        "-o",
        "ServerAliveInterval=30",
        "-o",
        "ServerAliveCountMax=2",
        "USUARIO_TERMUX@IP_DEL_TELEFONO",
        "exec \"$HOME/bin/costphone-mcp\""
      ],
      "enabled": true,
      "timeout": 120000
    }

Sustituye USUARIO_TERMUX@IP_DEL_TELEFONO por tus valores; conserva comillas y comas del formato JSONC donde correspondan.

## Si no conecta

1. En Termux, vuelve a ejecutar sshd -p 8022.
2. Verifica que PC y teléfono sigan en la misma red y que la IP no haya cambiado.
3. Prueba manualmente desde PowerShell: ssh -T -p 8022 USUARIO_TERMUX@IP_DEL_TELEFONO "printf COSTPHONE_SSH_OK".
4. Si el teléfono duerme o Android pausa Termux, activa el bloqueo de suspensión de Termux y excluye Termux del ahorro de batería. La disponibilidad del puente depende de que el teléfono siga encendido y accesible.
5. No compartas la clave privada que queda en %USERPROFILE%\.ssh\id_ed25519_costphone.

## Coste

El APK, el motor, OpenSSH y el script de conexión no requieren una licencia de pago. Eso no significa que la inferencia de IA de OpenCode sea siempre gratuita: el proveedor/modelo que elijas puede tener límites o cobrar por uso. Esta configuración no añade ni cambia proveedor, modelo ni credenciales de IA.
