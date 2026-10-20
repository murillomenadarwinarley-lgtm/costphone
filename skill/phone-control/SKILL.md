---
name: phone-control
description: "Control del teléfono Android desde Termux con CostPhone/amctl. Entra a la app objetivo, actúa con selectores accesibles y valida el resultado con pocos pasos."
---

# Phone control — modo rápido

**Objetivo:** terminar la tarea, no narrar ni diagnosticar. Usa el mínimo de llamadas al terminal. No generes capturas ni vuelques árboles por rutina.

## Flujo
1. Abre la app una sola vez con `amctl launch <paquete>` si no está abierta.
2. Ve directo a un selector conocido (`clickid`, `click`) y agrupa pasos dependientes con `amctl batch`.
3. Si el selector es desconocido, ejecuta un único `amctl snap`; usa esa referencia enseguida, antes de cambiar de pantalla. Usa `snap -a` solo cuando falte el control.
4. Espera solo una condición que confirme el objetivo final. No hagas `waittext` después de cada toque.
5. Si la pantalla cambia inesperadamente, toma una decisión con una sola lectura textual. No repitas el mismo plan ni hagas capturas en cadena.

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

## Recuperación limitada
- Si un comando falla por primera vez, prueba una única alternativa basada en el estado observado.
- Si falla la alternativa, detente y explica el bloqueo. Usa `amctl doctor` solo si parece problema de conexión y `amctl logs` solo para investigar una reproducción.
- Nunca afirmes que un mensaje se envió sin confirmación visible. Nunca repitas un envío por falta de confirmación; verifica primero para evitar duplicados.
- No desactives la guardia ni uses comandos de shell arbitrarios para saltártela.

## Diagnóstico solo bajo demanda
`amctl status` muestra el transporte; `amctl doctor` comprueba respuesta de CostPhone; `amctl logs` exporta el registro. Si Android desactivó Accesibilidad, se debe volver a habilitar manualmente en Ajustes → Accesibilidad → CostPhone.