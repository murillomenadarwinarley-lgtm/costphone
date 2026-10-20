# CostPhone PC nativo para Windows

Esta variante ejecuta **el motor y el servidor MCP directamente en Windows**. No utiliza Termux, SSH, el APK ni el teléfono Android. La versión Android y su servidor **costphone-engine** se conservan sin cambios; esta instancia nueva se registra como **costphone-pc**, por lo que ambas pueden coexistir en OpenCode.

## Funciones incluidas

- **desktop_inspect:** título de la ventana activa, nombre/ruta del proceso y resolución de pantalla.
- **desktop_screenshot:** captura real de la pantalla enviada al agente como imagen MCP.
- **desktop_launch_app:** abre un ejecutable .exe concreto, sin shell ni argumentos.
- **desktop_open_url:** abre una URL HTTP/HTTPS en el navegador predeterminado.
- **desktop_inspect_controls:** lee los controles visibles que Windows UI Automation expone, con nombre, Automation ID, tipo, estado, rectángulo e identidad de la ventana/proceso. Los selectores están ligados a la ventana que se inspeccionó para rechazar acciones obsoletas.
- **desktop_click_control:** hace clic en un control seleccionado de forma exacta por los metadatos de Windows.
- **desktop_scroll:** desplaza una página o panel en una ubicación y cantidad limitadas.
- **desktop_set_control_text:** establece texto en controles de tipo Edit; exige permiso de entrada y bloquea campos identificados como contraseña.
- **desktop_click, desktop_type_text, desktop_press_key:** control de ratón/teclado tras autorización explícita durante la instalación.

La versión usa Windows UI Automation cuando la aplicación expone controles accesibles y recurre a capturas visuales/coordenadas cuando no están disponibles. No todos los programas exponen el árbol completo; juegos, escritorios remotos y superficies dibujadas a medida pueden requerir interacción visual. Tampoco intenta imitar funciones exclusivas de Android que dependen de amctl. La automatización requiere una sesión de Windows iniciada, desbloqueada y con escritorio visible; no es un servicio para ejecutar desde una sesión cerrada ni desde Windows Server sin escritorio.

## Instalación

1. Instala Python 3.10 o posterior y OpenCode para Windows.
2. Abre PowerShell normal (no hace falta ejecutarlo como administrador).
3. Descarga y ejecuta el instalador de esta rama:

    $script = "$env:TEMP\install-costphone-pc.ps1"
    Invoke-WebRequest "https://raw.githubusercontent.com/murillomenadarwinarley-lgtm/costphone/engine/costphone-engine-v0/pc/windows/install-costphone-pc.ps1" -OutFile $script
    Set-ExecutionPolicy -Scope Process Bypass -Force
    & $script

El instalador descarga el motor, sus dependencias (incluido Windows UI Automation) y el skill de uso a %LOCALAPPDATA%\CostPhonePC, crea o actualiza un entorno virtual aislado, instala sus dependencias, instala el skill en %USERPROFILE%\.config\opencode\skills\costphone-pc y añade **costphone-pc** a %USERPROFILE%\.config\opencode\opencode.json. Si ya existe ese archivo, primero crea una copia de seguridad. Si encuentra opencode.jsonc, se detiene para no destruir comentarios ni editarlo de forma insegura.

Durante la instalación pregunta si autorizas a OpenCode a escribir y hacer clic en el PC. La respuesta predeterminada es **No**. Si escribes **SI**, la configuración añade el argumento --allow-input; puedes reinstalar para cambiar la decisión. Sin ese permiso, inspeccionar capturas, abrir aplicaciones y abrir URL sigue disponible, pero las herramientas de teclado/ratón responden con input_disabled.

## Comprobación

Cierra y vuelve a abrir OpenCode. Ejecuta en una terminal:

    opencode mcp list

Debe aparecer **costphone-pc**. Prueba primero desktop_inspect y desktop_inspect_controls; luego desktop_screenshot. Para páginas largas, prueba desktop_scroll con una captura reciente. Después, pide abrir el Bloc de notas y escribir un texto de prueba si autorizaste el control de entrada. Al hacer clic, toma una captura reciente y usa solo coordenadas dentro de esa imagen; nunca repitas a ciegas una acción que parezca fallar.

## Configuración manual

Si usas opencode.jsonc, no lo sobrescribas. Agrega dentro de mcp este bloque y sustituye RUTA_LOCAL por la ruta de instalación en tu PC. No incluyas --allow-input a menos que quieras autorizar explícitamente teclado y ratón:

    "costphone-pc": {
      "type": "local",
      "command": [
        "RUTA_LOCAL\\.venv\\Scripts\\python.exe",
        "RUTA_LOCAL\\engine\\windows_mcp_server.py"
      ],
      "enabled": true,
      "timeout": 120000
    }

## Límites y seguridad

- El motor local no necesita que el celular esté encendido ni conectado.
- El control de entrada empieza deshabilitado y solo se habilita con consentimiento explícito. Aun habilitado, verifica siempre la ventana y la captura actual antes de actuar; un clic o Enter puede confirmar un formulario o enviar un mensaje.
- desktop_type_text usa temporalmente el portapapeles y lo restaura cuando es posible.
- Las capturas pueden mostrar datos privados. No pidas que el agente capture ni procese información que no necesita.
- OpenCode puede seguir necesitando Internet y un proveedor de modelo de IA; que el motor sea local no hace gratis el servicio del modelo.
- Para volver a usar el teléfono, conserva el MCP Android costphone-engine. Esta variante no reemplaza archivos de Android ni sus credenciales de firma.
