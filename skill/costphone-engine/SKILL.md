---
name: costphone-engine
description: "Motor local y verificable para automatizar tareas Android mediante CLI o MCP, con comprobaciones de estado y acciones sensibles protegidas."
---

# CostPhone Engine

Usa el motor solo si las herramientas MCP phone_inspect, phone_launch, phone_validate_plan y phone_run_plan están disponibles, o si el usuario pide explícitamente usar la CLI instalada. Esta skill no reemplaza la skill existente phone-control ni los comandos amctl.

## Reglas operativas

1. Traduce la petición en una secuencia pequeña de estados esperados y acciones permitidas. No inventes paquetes, botones o texto de pantalla.
2. Si se conoce el paquete objetivo, usa phone_launch para abrirlo y confirmar el primer plano; después usa phone_inspect para descubrir los controles actuales. Si el motor informa que Termux está delante o que no puede leer la pantalla, no desactives la guardia ni repitas comandos en bucle.
3. Cada etapa de un plan debe tener:
   - expect.package: paquete exacto de Android;
   - expect.contains: texto real que distinga el estado correcto;
   - actions: una o pocas acciones allow-listed; si hay más de una, cada acción posterior debe declarar action.requires con el texto que debe seguir visible antes de ese toque;
   - after.package y after.contains: prueba visible del resultado esperado.
4. Usa IDs accesibles cuando sean fiables; de lo contrario, usa el texto visible exacto. No reutilices coordenadas ni referencias de snapshots anteriores.
5. Para acciones que puedan enviar, publicar, borrar, pagar, llamar, compartir o confirmar, incluye action.requires con el destino y el contenido/contexto visible. El motor vuelve a leer la pantalla antes de esa acción. Si los efectos externos están bloqueados, no intentes eludirlo; comunica el bloqueo y deja que el usuario decida si modifica la configuración local.
6. No declares que una tarea tuvo éxito solo porque el comando devolvió código cero. La etapa debe verificar su condición after.
7. Nunca repitas automáticamente una acción fallida ni vuelvas a ejecutar el plan completo si una etapa anterior pudo haber tenido efecto. Inspecciona el estado de forma segura y continúa solo si el estado lo permite.
8. No supongas que WhatsApp u otra aplicación representa todas las tareas. El motor sirve para apps y flujos Android en general, dentro de los controles accesibles y los permisos concedidos.
9. No uses acciones arbitrarias de shell, no desactives Accesibilidad/guardias, no borres datos, y no instales o modifiques software por iniciativa propia.
10. Si el MCP no está disponible, no afirmes que el motor se encuentra activo. Usa la skill phone-control existente únicamente como alternativa explícita.

## Plan JSON versión 1

Antes de ejecutar un plan complejo, valida el JSON con phone_validate_plan. Para ejecutar una secuencia que incluya transiciones de pantalla, usa phone_run_plan una sola vez, con una verificación after por etapa. Para una operación de bajo riesgo, mantén las etapas cortas; para un efecto externo, verifica el destino y el contenido justo antes de actuar.

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