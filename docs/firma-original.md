# Recuperar y usar la firma original de CostPhone

## Por qué es necesaria

Android solo permite actualizar una aplicación instalada si la firma del APK nuevo coincide con la que ya está instalada (salvo mecanismos especiales de rotación de certificados que este proyecto no configura). El identificador de paquete por sí solo no basta.

El workflow antiguo compilaba una APK debug en un runner efímero de GitHub Actions. Cada runner puede crear su propio `debug.keystore`; por eso esa APK no garantiza la misma firma que CostPhone v1.2. La firma privada no se puede reconstruir a partir de la APK.

## Qué clave se debe recuperar

Se necesita el archivo keystore privado exacto que firmó la APK original, junto con su contraseña, alias y contraseña de clave. No generes un keystore nuevo esperando que sea equivalente.

Si la versión original se compiló localmente con Gradle, revisa en el equipo de compilación si aún existe `~/.android/debug.keystore`. Solo sirve si se confirma que ese archivo firmó la APK instalada; una clave debug generada en otro equipo será diferente.

La verificación de CI compara el certificado SHA-256 del APK de release con el APK heredado `apk/CostPhone-v1.2.apk` del repositorio. Si no coincide, el workflow falla y no publica el APK. Antes de usarla para actualizar el teléfono, confirma además que el certificado de la aplicación instalada coincide con esa misma referencia.

## Configurar la firma en GitHub Actions

En el repositorio, abre **Settings → Secrets and variables → Actions → New repository secret** y crea estos cuatro secretos con los valores de la clave original:

- `COSTPHONE_KEYSTORE_BASE64`: contenido del archivo keystore codificado en Base64, en una sola línea.
- `COSTPHONE_KEYSTORE_PASSWORD`: contraseña del keystore.
- `COSTPHONE_KEY_ALIAS`: alias de la clave de firma.
- `COSTPHONE_KEY_PASSWORD`: contraseña de esa clave.

Para codificar el archivo localmente sin modificarlo:

```sh
base64 < /ruta/privada/al/keystore.jks | tr -d '\n'
```

Pega el resultado directamente en el campo del secreto de GitHub. **No lo pegues en una conversación, no lo añadas al repositorio y no subas el keystore a un issue o PR.** El archivo y las contraseñas son material privado de firma. Si usas el keystore debug predeterminado de Android, el alias y las contraseñas suelen ser `androiddebugkey` / `android`, pero utiliza esos valores únicamente si verificaste que se trata del archivo original.

Después, ejecuta el workflow desde **Actions → Build CostPhone APK → Run workflow**. Si la clave coincide, el artefacto se llamará `costphone-update-apk`. Si falta la clave o el certificado no coincide, no habrá APK de actualización descargable.

## Compilación local

Define las siguientes variables antes de ejecutar `cd app && bash build.sh`:

- `COSTPHONE_KEYSTORE_PATH`: ruta local del keystore original.
- `COSTPHONE_KEYSTORE_PASSWORD`
- `COSTPHONE_KEY_ALIAS`
- `COSTPHONE_KEY_PASSWORD`

Con esos datos, el script solo copia el resultado a `app/build/CostPhone.apk` si la firma coincide con la APK heredada de referencia. Sin ellos, genera una APK de pruebas llamada `app/build/CostPhone-debug.apk`; **no la instales encima de la app existente ni la distribuyas como actualización**.

## Si no aparece la clave original

No desinstales la aplicación que tienes instalada. Conserva sus datos y busca el archivo original en el equipo o entorno que compiló la versión instalada. Una APK permite comprobar el certificado público, pero no recuperar la clave privada. Hasta encontrarla, no es posible garantizar una actualización con la misma firma.
