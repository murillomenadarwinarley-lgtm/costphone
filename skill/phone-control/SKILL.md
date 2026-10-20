---
name: phone-control
description: "Control del teléfono Android desde Termux con CostPhone/amctl. Entra a la app objetivo, actúa con selectores accesibles y valida el resultado con pocos pasos."
---

# Phone control — modo rápido

**Objetivo:** terminar la tarea, no narrar ni diagnosticar. Usa el mínimo de llamadas al terminal. No generes capturas ni vuelques árboles por rutina.

## Preparación rápida de la ruta Shizuku

Al comenzar una sesión nueva del agente, si `rish` está instalado y aún no hay sesión viva, intenta una sola vez:

```bash
if command -v rish >/dev/null 2>&1; then SESSION_START_WAIT=4 amctl session-start; fi
```

Si responde `sesión rish activa`, reutilízala para las siguientes tareas; no repitas el arranque ni ejecutes `amctl status` en cada tarea. Si falla, no insistas en bucle: usa la ruta `am` clásica y avisa del problema solo si afecta la tarea. El propietario debe haber iniciado el servidor de Shizuku y concedido permiso a CostPhone; no se puede saltar esa autorización.


## Flujo de mínima latencia
1. Reutiliza la sesión rish si ya está activa. No hagas comprobaciones de estado por rutina.
2. Abre la app solo si aún no está en primer plano.
3. Usa el selector que esté documentado **en la pantalla actual**. No des por válido un ID recordado si no coincide con el snapshot actual.
4. Agrupa acciones conocidas en un único `amctl batch`. No hagas una llamada al modelo entre cada toque.
5. Usa una espera corta y relevante (3–5 s para una transición local rápida); no pongas 8–10 s por defecto.
6. Haz una sola lectura textual si el primer selector no produce el cambio esperado. No repitas el mismo click, no uses referencias de pantallas anteriores y no abras capturas PNG para una tarea normal.
7. Comprueba el resultado una sola vez al final; no vuelvas a Termux solo para redactar un informe técnico.

### Ejemplo: encontrar un chat de WhatsApp
Cuando el snapshot de WhatsApp muestre `Preguntar a Meta AI o buscar` con el ID `com.whatsapp:id/search_bar_inner_layout`, ese es el selector actual. No uses `com.whatsapp:id/menuitem_search` a menos que aparezca en el snapshot.

```bash
amctl batch <<'EOF'
clickid com.whatsapp:id/search_bar_inner_layout
type Trion Systems
waittext "Trion Systems" 5
EOF
```

Si ya hay resultados y no está claro cuál es el chat correcto, haz **un solo** `amctl snap`, toca la referencia de la fila correcta y confirma que se abrió el chat. Respeta la ortografía del nombre visible: `Trion Systems` no es lo mismo que `Tryon Systems`.


## Ejemplo de acciones juntas

```bash
amctl batch <<'EOF'
clickid com.whatsapp:id/entry
type Hola
clickid com.whatsapp:id/send
waittext "Hola" 8
EOF
```

En `batch`, usa `waittext "texto con espacios" 8` con comillas normales. CostPhone comprueba la app objetivo al inicio del lote y valida en el servicio que no haya cambiado durante su ejecución. Si la validación falla, no sigas enviando acciones.

## Selectores y esperas
- ID conocido: `amctl clickid paquete:id/control`.
- Texto visible: `amctl click "Buscar"`.
- Referencia reciente: `amctl tapref @pN`.
- Campo enfocado: `amctl type "texto"` (reemplaza el contenido).
- Esperar texto: `amctl waittext "texto" 8`; los árboles se consultan hasta que aparezca.
- No busques un chat por coordenadas si el texto o la fila son accesibles.
- Tras un timeout, la pantalla puede ser distinta. No vuelvas a usar referencias antiguas: mira una sola vez el estado y elige el siguiente paso.

## Regla para no perder tiempo
No uses Python, timestamps, `amctl status`, `doctor`, `logs`, `tree` o PNG para una tarea normal. No abras Termux al final solo para reportar tiempo. Si hay que medir, crea la marca de tiempo antes del flujo y léela una vez al final con una ruta absoluta y válida.

El tiempo de razonamiento del modelo no se arregla con permisos del teléfono. Para tareas rutinarias, elige desde `/models` un modelo rápido que soporte bien herramientas y mantén un plan corto: una lectura cuando sea necesaria, un lote de acciones y una confirmación final. No narres cada paso ni generes informes largos.

## Recuperación limitada
- Si un comando falla por primera vez, prueba una única alternativa basada en el estado observado.
- Si falla la alternativa, detente y explica el bloqueo. Usa `amctl doctor` solo si parece problema de conexión y `amctl logs` solo para investigar una reproducción.
- Nunca afirmes que un mensaje se envió sin confirmación visible. Nunca repitas un envío por falta de confirmación; verifica primero para evitar duplicados.
- No desactives la guardia ni uses comandos de shell arbitrarios para saltártela.

## Diagnóstico solo bajo demanda
`amctl status` muestra el transporte; `amctl doctor` comprueba respuesta de CostPhone; `amctl logs` exporta el registro. Si Android desactivó Accesibilidad, se debe volver a habilitar manualmente en Ajustes → Accesibilidad → CostPhone.