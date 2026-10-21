---
name: phone-control
description: "Control del teléfono Android desde Termux con CostPhone/amctl. Entra a la app objetivo, actúa con selectores accesibles y valida el resultado con pocos pasos."
---

# Phone control — modo rápido

**Objetivo:** terminar la tarea, no narrar ni diagnosticar. Usa el mínimo de llamadas al terminal. No generes capturas ni vuelques árboles por rutina. Carga esta skill antes del primer comando de teléfono; no empieces comprobando la pantalla de Termux.

## Tareas de varios pasos: usar puntos de control del motor si están disponibles

- Antes de una tarea Android con varias pantallas o una secuencia que podría quedar interrumpida, comprueba si el cliente expone `phone_run_plan`, `phone_get_run` y `phone_resume_plan`. Si están disponibles, prefiere el motor para esa tarea: crea un plan por etapas con precondiciones y resultado visible esperado.
- Conserva el `run_id` de `phone_run_plan`. Si la ejecución falla, lee `phone_get_run` y usa `phone_resume_plan` con el mismo ID y exactamente el mismo plan; no repitas todo el lote de `amctl`.
- Si aparece `external_outcome_uncertain`, `needs_attention` o `resume_ambiguous`, no repitas envíos, eliminaciones, pagos, publicaciones ni otra acción sensible. Verifica el estado actual y decide cómo continuar desde evidencia nueva.
- Para una única acción conocida y de bajo riesgo, conserva la ruta rápida de `amctl` descrita abajo. No ejecutes simultáneamente un mismo flujo mediante el motor y mediante `amctl`.
## Preparación rápida de la ruta Shizuku

Al comenzar una sesión nueva del agente, si `rish` está instalado y aún no hay sesión viva, intenta una sola vez:

```bash
if command -v rish >/dev/null 2>&1; then SESSION_START_WAIT=4 amctl session-start; fi
```

Si responde `sesión rish activa`, reutilízala para las siguientes tareas; no repitas el arranque ni ejecutes `amctl status` en cada tarea. Si falla, no insistas en bucle: usa la ruta `am` clásica y avisa del problema solo si afecta la tarea. El propietario debe haber iniciado el servidor de Shizuku y concedido permiso a CostPhone; no se puede saltar esa autorización.


## Flujo obligatorio de mínima latencia
1. Reutiliza la sesión rish si ya está activa. No hagas `status`, `doctor` ni `snap` de diagnóstico en cada tarea.
2. **Si la app objetivo es conocida, empieza con `amctl launch <paquete>`**. No ejecutes antes `waittext`, `snap`, `home` ni `sleep`. La CLI confirma el primer plano y devuelve error rápido si Android deja la app en el launcher.
3. Si `launch` confirma el primer plano, actúa directamente usando un selector fiable. Si devuelve error, haz solo un `amctl snap` nuevo para ver la pantalla real; no reutilices referencias antiguas.
4. Para WhatsApp, la pantalla de chats muestra `com.whatsapp:id/search_bar_inner_layout` con la etiqueta `Preguntar a Meta AI o buscar`. No uses `com.whatsapp:id/menuitem_search` a menos que esté en la pantalla actual. Si el chat deseado aparece ya en la lista actual, toca ese resultado; busca por texto solo si no aparece.
5. Agrupa acciones conocidas en un único `amctl batch`. No hagas una llamada al modelo entre cada toque.
6. Usa espera de 3–5 s para transiciones locales; evita `sleep` fijo y espera 8–10 s por defecto. Deja que `waittext` haga la espera.
7. Si un paso falla, haz una sola lectura textual, decide con ese estado y no repitas el mismo click. Verifica el resultado una vez al final.

### Ejemplo: encontrar un chat de WhatsApp
Abre primero WhatsApp con `amctl launch com.whatsapp`; solo cuando confirme primer plano usa el selector actual. Cuando el snapshot muestre `Preguntar a Meta AI o buscar` con `com.whatsapp:id/search_bar_inner_layout`, ese es el selector correcto. No uses `com.whatsapp:id/menuitem_search` a menos que aparezca en la pantalla actual.

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
No uses Python, timestamps ni hagas scripts de medición durante la tarea. Si el usuario pide medir duración, una llamada breve al inicio puede guardar `time.monotonic()` en un archivo existente y una llamada al final calcula el total; no corrijas errores de formato en cadena. No uses `status`, `doctor`, `logs`, `tree` o PNG para una tarea normal. No abras Termux al final para mostrar un informe si el resultado ya quedó verificado; basta con responder una línea.

El tiempo de razonamiento del modelo no se arregla con permisos del teléfono. Para tareas rutinarias, elige desde `/models` un modelo rápido que soporte bien herramientas y mantén un plan corto: una lectura cuando sea necesaria, un lote de acciones y una confirmación final. No narres cada paso ni generes informes largos.

## Recuperación limitada
- Si un comando falla por primera vez, prueba una única alternativa basada en el estado observado.
- Si falla la alternativa, detente y explica el bloqueo. Usa `amctl doctor` solo si parece problema de conexión y `amctl logs` solo para investigar una reproducción.
- Nunca afirmes que un mensaje se envió sin confirmación visible. Nunca repitas un envío por falta de confirmación; verifica primero para evitar duplicados.
- No desactives la guardia ni uses comandos de shell arbitrarios para saltártela.

## Diagnóstico solo bajo demanda
`amctl status` muestra el transporte; `amctl doctor` comprueba respuesta de CostPhone; `amctl logs` exporta el registro. Si Android desactivó Accesibilidad, se debe volver a habilitar manualmente en Ajustes → Accesibilidad → CostPhone.