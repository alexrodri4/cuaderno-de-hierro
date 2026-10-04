# Cuaderno de Hierro — FOKUS

App de entrenamiento (v2.1, versionCode 20) para Android, construida como WebView sobre una app web autocontenida.

## Estructura

| Ruta | Contenido |
|---|---|
| `web/index.html` | Código fuente completo de la app (incluye la integración con Claude) |
| `web/img`, `web/fonts` | Imágenes y tipografías (el APK las toma de aquí) |
| `android/proj` | Proyecto Android: Java (`MainActivity`, widget, alarmas, pasos), recursos y manifest |
| `android/tools` | Herramientas de compilación: aapt2, d8, android.jar, apksigner |
| `android/build.sh` | Compila `FOKUS.apk` desde `web/` |
| `FOKUS.apk` | Último APK compilado y firmado |

## Compilar

Linux o WSL, con Java 17+ y Python 3:

```bash
export CDH_KS_PASS='...'   # contraseña de la keystore
bash android/build.sh
```

Necesita `cuaderno-hierro.keystore` en la raíz del proyecto. **La keystore y su contraseña no están en este repositorio** (ver `.gitignore`): guárdalas aparte, sin ellas no se pueden publicar actualizaciones de la app instalada.

## IA

La IA solo se activa en el APK, pegando tu clave de API en *Ajustes > Claude*. La clave no se guarda en el repositorio.
