# Cuaderno de Hierro — FOKUS

App de entrenamiento (v2.1, versionCode 20) para Android, construida como WebView sobre una app web autocontenida.

**Descargar el APK:** [Releases](https://github.com/alexrodri4/cuaderno-de-hierro/releases/latest).

## Estructura

| Ruta | Contenido |
|---|---|
| `web/index.html` | Código fuente completo de la app (incluye la integración con Claude) |
| `web/img`, `web/fonts` | Imágenes y tipografías (el APK las toma de aquí) |
| `android/proj` | Proyecto Android: Java (`MainActivity`, widget, alarmas, pasos), recursos y manifest |
| `android/tools` | Herramientas de compilación: aapt2 y apksigner (android.jar y d8.jar no están en el repo, ver abajo) |
| `android/build.sh` | Compila `FOKUS.apk` desde `web/` (sale en la raíz, ignorado por git) |

## Compilar

Linux o WSL, con Java 17+ y Python 3:

```bash
export CDH_KS_PASS='...'   # contraseña de la keystore
bash android/build.sh
```

Antes de compilar, copia en `android/tools/` las dos herramientas que no están en el repo por su tamaño:

- `android.jar` (API 34): de [Sable/android-platforms](https://github.com/Sable/android-platforms) (`android-34/android.jar`).
- `d8.jar`: del paquete npm [`@drxiaozhi/minapk`](https://www.npmjs.com/package/@drxiaozhi/minapk), o de las build-tools oficiales del Android SDK (`lib/d8.jar`).

Necesita `cuaderno-hierro.keystore` en la raíz del proyecto. **La keystore y su contraseña no están en este repositorio** (ver `.gitignore`): guárdalas aparte, sin ellas no se pueden publicar actualizaciones de la app instalada.

## IA

La IA solo se activa en el APK, pegando tu clave de API en *Ajustes > Claude*. La clave no se guarda en el repositorio.

## Licencia

MIT, ver [LICENSE](LICENSE).
