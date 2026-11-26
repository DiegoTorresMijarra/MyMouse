# Cómo probar una release

Cada release (`v0.0`, `v0.1`, …) añade una etapa encima de la anterior. Si algo falla, baja a la release anterior hasta encontrar la primera en la que falla y apunta cuál es.

## Una sola vez

1. **Móvil (P10):**
   - Ajustes → Sistema → Información del teléfono → toca 7 veces "Número de compilación".
   - Ajustes → Sistema → Opciones de desarrollador → activa **Depuración USB** y **Permitir depuración ADB en modo solo carga**.
2. **PC (Windows):**
   - Instala [Android platform-tools](https://developer.android.com/tools/releases/platform-tools) (descomprimir y añadir la carpeta al `PATH`, o dejarla en `%LOCALAPPDATA%\Android\Sdk\platform-tools`).
   - Si `adb devices` no ve el móvil, instala el driver de Huawei (viene con **HiSuite**).
   - Conecta el cable, acepta en el móvil el aviso "¿Permitir depuración USB?" (marca "Permitir siempre") y comprueba que `adb devices` muestra el móvil como `device`.
   - Recomendado: desactiva "Mejorar la precisión del puntero" (Configuración del mouse → Opciones adicionales → Opciones de puntero).

## Para cada release

De la página de la release descarga:
- `MyMouse-<versión>.apk`: la app del móvil.
- `MyMouse-Windows-<versión>.zip`: `MyMouse.exe` (el host de bandeja) y `MyMouse.TestClient.exe` (simula el móvil). No necesitan instalar .NET.

1. **Cerrar el host anterior:** icono de MyMouse en la bandeja → Salir.
2. **Instalar la app** (sustituye a la anterior y conserva los ajustes, también al bajar de versión):
   ```powershell
   adb install -r -d MyMouse-<versión>.apk
   ```
3. **Arrancar el host:** descomprime el zip y ejecuta `MyMouse.exe`. Windows SmartScreen puede avisar porque no está firmado: "Más información" → "Ejecutar de todas formas".
4. Abre **MyMouse** en el móvil. El punto de la esquina se pone verde al conectar.

Si algo no conecta:
- Pasa el ratón por el icono de la bandeja: el tooltip dice si encuentra adb y el móvil.
- Logs del host: icono de la bandeja → "Abrir carpeta de logs".
- Prueba solo el PC con `MyMouse.TestClient.exe` (con el host abierto): el cursor debe dibujar un círculo.
- Logs del móvil: `adb logcat -s MyMouse`.

## Qué anotar si algo falla
- Release en la que falla y la última en la que funcionaba.
- Qué hiciste y qué pasó (y qué esperabas).
- El log del host del día (`%APPDATA%\MyMouse\logs`) y, si puedes, `adb logcat -s MyMouse -d > movil.txt`.
