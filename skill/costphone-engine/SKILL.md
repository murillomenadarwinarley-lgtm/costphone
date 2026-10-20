---
name: costphone-engine
description: "Motor local y verificable para automatizar tareas Android mediante CLI o MCP, con comprobaciones de estado y acciones sensibles protegidas."
---

# CostPhone Engine

Usa el motor solo si las herramientas MCP phone_inspect, phone_launch, phone_validate_plan y phone_run_plan están disponibles, o si el usuario pide explícitamente usar la CLI instalada. Esta skill no reemplaza la skill existente phone-control ni los comandos amctl.

## Reglas operativas

## Objetivo de velocidad y alcance general

- El motor es para tareas generales de Android, no para una app concreta. Funciona en la medida en que la app exponga controles accesibles y los permisos del dispositivo permitan la operación.
- Para tareas cortas y de bajo riesgo, apunta a unos 10–20 segundos cuando sea viable. Es un objetivo de rendimiento, nunca una garantía.
- Reduce latencia evitando llamadas MCP redundantes: observa el estado una vez, construye un plan breve con transiciones claras y envíalo en una llamada a `phone_run_plan`. No repitas inspecciones o lanzamientos si el estado recién verificado sigue siendo válido dentro de la misma etapa.
- No elimines las comprobaciones obligatorias, no uses datos de pantalla obsoletos para un nuevo estado, y no recortes la verificación para alcanzar el tiempo objetivo. Si la app, la red o el servicio tardan, informa el tiempo real y la causa observada.
- Revisa `timings` del resultado para optimizar según mediciones reales. No prometas 10–20 segundos para tareas largas, apps lentas ni operaciones que dependan de red o confirmación externa.

1. Traduce la petición en una secuencia pequeña de estados esperados y acciones permitidas. No inventes paquetes, botones o texto de pantalla.
2. Si se conoce el paquete objetivo, usa phone_launch para abrirlo y confirmar el primer plano; después usa phone_inspect para descubrir los controles actuales. Si el motor informa que Termux está delante o que no puede leer la pantalla, no desactives la guardia ni repitas comandos en bucle.
3. Cada etapa de un plan debe tener:
   - expect.package: paquete exacto de Android;
   - expect.contains: texto real que distinga el estado correcto;
   - actions: una o pocas acciones allow-listed; si hay más de una, cada acción posterior debe declarar action.requires con el texto que debe seguir visible antes de ese toque;
   - after.package y after.contains: prueba visible del resultado esperado.
4. Usa IDs accesibles cuando sean fiables; de lo contrario, usa el texto visible exacto. No reutilices coordenadas ni referencias de snapshots anteriores.
5. Para acciones que puedan enviar, publicar, borrar, pagar, llamar, compartir o confirmar, incluye action.requires con el destino y el contenido/contexto visible. El motor vuelve a leer la pantalla antes de esa acción. Si los efectos externos están bloqueados, no intentes eludirlo; comunica el bloqueo y deja que el usuario decida si modifica la configuración local.
6. No declares que una tarea tuvo éxito solo porque el comando devolvió código cero. La etapa debe comprobar su condición after y el informe debe decir qué se verificó exactamente. Una coincidencia de texto accesible no demuestra por sí sola que un efecto externo (por ejemplo, enviar un mensaje) se completara. Si no hay evidencia específica del resultado en el contexto correcto, informa "resultado no verificado".
7. Nunca repitas automáticamente una acción fallida ni vuelvas a ejecutar el plan completo si una etapa anterior pudo haber tenido efecto. Inspecciona el estado de forma segura y continúa solo si el estado lo permite.
8. No supongas que WhatsApp u otra aplicación representa todas las tareas. El motor sirve para apps y flujos Android en general, dentro de los controles accesibles y los permisos concedidos.
9. No uses acciones arbitrarias de shell, no desactives Accesibilidad/guardias, no borres datos, y no instales o modifiques software por iniciativa propia.
10. Si el MCP no está disponible, no afirmes que el motor se encuentra activo. Usa la skill phone-control existente únicamente como alternativa explícita.

## Plan JSON versión 1

Antes de ejecutar un plan complejo, valida el JSON con phone_validate_plan. Para previsualizarlo sin interacción con el teléfono, usa phone_run_plan con dry_run=true; esa opción está disponible por MCP y CLI, no debe confundirse con una ejecución real. Para ejecutar una secuencia que incluya transiciones de pantalla, usa phone_run_plan una sola vez, con una verificación after por etapa. Para una operación de bajo riesgo, mantén las etapas cortas; para un efecto externo, verifica el destino y el contenido justo antes de actuar. El resultado exitoso informa que coincidieron las condiciones visibles configuradas, no que un resultado externo quede demostrado automáticamente.

Ejemplo de abrir la sección visible Network desde Ajustes:

~~~json
{
  "version": 1,
  "name": "Abrir Network",
  "steps": [
    {
      "launch": "com.android.settings",
      "expect": {
        "package": "com.android.settings",
        "contains": ["Settings"]
      },
      "actions": [{"kind": "click_text", "value": "Network"}],
      "after": {
        "package": "com.android.settings",
        "contains": ["Network"]
      }
    }
  ]
}
~~~

El motor no interpreta lenguaje natural: tú construyes el plan a partir de la petición y del estado observado. Si el texto accesible no permite diferenciar el destino, no adivines; pide una aclaración o detente.

## Clientes compatibles

OpenCode es una forma de usar el motor, no la única. Cualquier cliente de IA que soporte un servidor MCP local por stdio puede configurarse con el ejecutable `~/bin/costphone-mcp`. También puede invocarse desde scripts o agentes locales mediante la CLI `costphone-engine`; estos deben construir planes JSON válidos y respetar los mismos controles. Una IA remota sin acceso al dispositivo, a Termux o a un puente MCP local no puede manejar el teléfono directamente solo con el nombre del repositorio.