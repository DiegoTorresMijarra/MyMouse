# MyMouse

Convierte un móvil Android en el **trackpad y el teclado de un PC con Windows**.

- **Trackpad** con gestos como los de un portátil: clic, clic derecho, arrastrar, scroll con inercia, zoom y gestos de Windows con 3 dedos.
- **Modo gestos:** dibuja una forma para lanzar un atajo, abrir una app o escribir un texto.
- **Panel de widgets:** música, volumen y botones de un toque junto al trackpad.
- **Modo teclado:** un teclado de ordenador de verdad, con modificadores, bloques (Fn, flechas, numpad) y luces.
- **Temas** en el móvil y en el PC.

Proyecto personal y open source. Estado: funcional y probado en dispositivos reales. Siguiente paso: publicarlo en Play Store ([hoja de ruta](docs/00-roadmap.md)).

## Dos formas de conectar

```
Bluetooth:  Móvil ──Bluetooth HID──────────────────► Windows (sin instalar nada)
Cable:      Móvil ──USB / adb reverse──► MyMouse para Windows ──SendInput──► Windows
```

- **Bluetooth:** el móvil se empareja como ratón y teclado. El PC no necesita nada instalado; las formas y los botones se configuran en el móvil.
- **Cable + MyMouse para Windows:** además, la **toolbox del PC** (panel flotante, gesto en vivo, editor de formas y widgets), música y volumen en el panel, y el tema sincronizado. Necesita la depuración USB del móvil.

En los dos casos el móvil interpreta los toques y envía órdenes simples; el PC solo las ejecuta.

## Instalar

Descarga el APK y el instalador de Windows (`MyMouse-Setup-<versión>.exe`, con adb incluido) de la [última release](https://github.com/DiegoTorresMijarra/MyMouse/releases) y sigue [docs/probar.md](docs/probar.md). El instalador no está firmado: SmartScreen avisará ("Más información" → "Ejecutar de todas formas").

## Documentación

| Documento | Contenido |
|---|---|
| [docs/00-roadmap.md](docs/00-roadmap.md) | Versiones hechas y siguientes |
| [docs/probar.md](docs/probar.md) | Cómo instalar y probar cada función |
| [docs/01-concepto.md](docs/01-concepto.md) | La idea original y su alcance |
| [docs/02-tecnico.md](docs/02-tecnico.md) | Limitaciones técnicas y decisiones |
| `docs/spec-*.md` | La especificación de cada versión (enlazadas desde la hoja de ruta) |
| [protocol/PROTOCOL.md](protocol/PROTOCOL.md) | Protocolo móvil ↔ PC (fuente de verdad) |

## Compilar

### Requisitos
- **PC:** Windows 10/11 y el [.NET 10 SDK](https://dotnet.microsoft.com/download). Para el cable, [Android platform-tools](https://developer.android.com/tools/releases/platform-tools) (`adb`) si no usas el instalador.
- **Móvil:** Android 8 o superior (el Bluetooth necesita Android 9).
- **Para la app:** Android Studio, o el SDK de Android con `sdk.dir` en `android/local.properties`.

### Windows
```powershell
cd windows
dotnet test                                   # tests del protocolo y del host
dotnet run --project src/MyMouse.Host         # app de bandeja
dotnet run --project src/MyMouse.TestClient   # (otra terminal) simula el móvil: mueve el cursor en círculo
```
El host busca `adb.exe` en la ruta de `%APPDATA%\MyMouse\config.json` (si se indica), **junto a su propio exe** (el instalador lo trae), en el `PATH` y en `%LOCALAPPDATA%\Android\Sdk\platform-tools`.

### Android
```bash
cd android
./gradlew :core:test           # tests de la lógica pura, sin emulador
./gradlew :app:assembleDebug   # APK
```

### Releases
Al hacer push, si existe `docs/releases/<etiqueta>.md` (por ejemplo `v7.0.md`) y aún no hay release con ese nombre, la CI compila y publica la release con ese texto como notas.

## Licencia
[MIT](LICENSE)
