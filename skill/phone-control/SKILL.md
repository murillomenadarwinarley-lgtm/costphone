---
name: phone-control
description: "Controla Android desde Termux con CostPhone/amctl. Prioriza terminar la tarea con el mínimo de pasos; usa esta skill para WhatsApp y otras apps."
---

# phone-control: ejecutar, no enredarse

## Regla principal
Resuelve la petición con el menor número de comandos. No hagas diagnóstico preventivo, no expliques cada paso y no repitas verificaciones que no aportan nada. Usa **IDs/texto y lotes**, no capturas.

## Flujo normal
1. Abre la app una vez: `amctl launch com.paquete.app`.
2. Espera una sola señal de que cargó: `amctl waittext "texto visible" 10`.
3. Si el control tiene ID conocido, úsalo directamente con `clickid`; si no, ejecuta **un solo** `amctl snap` para elegirlo. Usa `snap -a` solo si el snapshot normal no muestra el control.
4. Agrupa acciones consecutivas en un `amctl batch`. Comprueba el resultado al final, no después de cada toque.
5. Termina cuando el objetivo esté confirmado. No ejecutes `status`, `doctor`, `logs`, `tree` ni PNG por rutina.

## Ejemplo: enviar un mensaje
Si el chat ya está abierto y los IDs de WhatsApp coinciden:

```bash
amctl batch <<'EOF'
clickid com.whatsapp:id/entry
type Hola
clickid com.whatsapp:id/send
waittext Hola 8
EOF
```

`type` reemplaza el contenido del campo enfocado. Si no escribió, no repitas la secuencia completa: toma **un** snapshot, comprueba el campo/teclado y corrige el selector.

## Si un paso falla
- **"No pude determinar la app en primer plano"**: reintenta una sola vez el comando fallido. Si falla otra vez, ejecuta `amctl doctor` una vez y detente según su resultado; no encadenes árboles, capturas y varios diagnósticos.
- **"Termux está en primer plano" al abrir una app**: `amctl launch` debe poder abrirla desde Termux. No lo sustituyas por comandos arbitrarios ni desactives la guardia.
- **"Sin elementos interactivos"**: usa `amctl snap -a` una sola vez; luego actúa por el ID o la referencia mostrada.
- Si un selector conocido falla, cambia a un único snapshot textual. Usa PNG solo si la interfaz no expone el control.
- No repitas toques, escritura o envíos a ciegas. No sigas intentando más de una alternativa si la misma pantalla no cambia.

## Comandos suficientes para la mayoría de tareas

| Necesidad | Comando |
|---|---|
| Abrir app | `amctl launch com.paquete.app` |
| Esperar texto | `amctl waittext "texto" 10` |
| Ver controles | `amctl snap` |
| Ver nodos con texto | `amctl snap -a` |
| Tocar por ID | `amctl clickid paquete:id/control` |
| Tocar por texto | `amctl click "texto"` |
| Escribir | `amctl type "texto"` |
| Agrupar acciones | `amctl batch` |
| Volver | `amctl back` |

## Conexión
Shizuku/rish es opcional, no requisito para cada tarea. Usa `amctl status` o `amctl doctor` solo cuando una acción falle como problema de conexión. Si el servicio de CostPhone está desactivado, pide reactivarlo en Ajustes → Accesibilidad; no intentes habilitarlo por código.