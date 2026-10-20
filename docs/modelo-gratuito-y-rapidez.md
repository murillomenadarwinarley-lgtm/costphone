# Modelo de IA económico y ejecución rápida

## Qué controla el modelo y qué controla CostPhone

La app Android no incluye un cliente de IA ni una clave de proveedor. Hoy, el agente se ejecuta en Termux/OpenCode y utiliza la skill `phone-control` para llamar a `amctl`. El modelo interpreta la instrucción y decide qué comandos necesita; CostPhone ejecuta las acciones de accesibilidad en el teléfono.

**No pegues una API key en el código Android, en la skill ni en un commit.** Las claves pertenecen a la configuración privada de OpenCode/Termux. Así se pueden cambiar modelos sin recompilar el APK.

## Opción gratuita para probar razonamiento

OpenRouter mantiene un router gratuito llamado `openrouter/free`. Escoge dinámicamente entre los modelos gratuitos disponibles y filtra según capacidades solicitadas, como herramientas o imagen. La selección cambia; no garantiza el mismo modelo, latencia ni disponibilidad en cada tarea.

### Configuración en OpenCode

1. Abre OpenCode en Termux.
2. Ejecuta `/connect`, selecciona OpenRouter y sigue el proceso oficial para configurar la credencial en el entorno de OpenCode.
3. Abre `/models` y busca un modelo gratuito que admita tool calling. Cuando esté disponible, puedes probar el router `openrouter/free`; también puedes elegir un modelo gratuito concreto para evitar que cambie en cada solicitud.
4. Prueba primero una tarea pequeña e inocua, por ejemplo «abre Ajustes y dime qué opciones aparecen». Comprueba la calidad antes de confiarle secuencias largas.

Las opciones gratuitas suelen tener límites de uso y pueden ser lentas o estar temporalmente saturadas. Un modelo gratuito de razonamiento no será automáticamente el más rápido.

Fuentes oficiales:
- OpenRouter Free Models Router: https://openrouter.ai/openrouter/free
- OpenCode providers: https://docs.opencode.ai/docs/providers/

## Ajustes de velocidad recomendados para CostPhone

- Para una acción simple y clara, utiliza un modelo instruct pequeño/rápido o el modo de razonamiento bajo que ofrezca tu proveedor.
- Reserva el razonamiento más profundo para instrucciones ambiguas, formularios largos o errores repetidos.
- Prefiere `amctl snap` para encontrar elementos interactivos; usa `amctl tree` solo si falta texto o el snapshot no basta.
- No hagas a la vez `snap`, `tree` y `see` por rutina. Elige uno y añade otro solo si hay una razón.
- Reutiliza un snapshot únicamente mientras no cambie la pantalla; después de navegar, abrir un diálogo, desplegar el teclado o desplazarte, vuelve a inspeccionar porque las referencias anteriores pueden caducar.
- Evita esperas fijas largas. Usa una espera breve solo cuando una aplicación todavía está cargando; verifica el estado en lugar de repetir la acción a ciegas.
- Antes de enviar mensajes, publicar, eliminar datos o confirmar una acción externa, revisa el objetivo y pide confirmación cuando corresponda.

## Privacidad

Los modelos en la nube reciben el texto o las imágenes que el agente les envía. No envíes capturas con códigos, notificaciones privadas, datos bancarios u otra información sensible a un proveedor externo. Para tareas privadas, considera un modelo local; su velocidad depende del teléfono, la memoria disponible y el tamaño del modelo.
