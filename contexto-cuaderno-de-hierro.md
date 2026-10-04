# Contexto: Cuaderno de Hierro / FOKUS

Actualizado: 04-oct-2026.

## Repositorio
- GitHub: https://github.com/alexrodri4/cuaderno-de-hierro (público, creado por Alex).
- Subido por la web de GitHub desde Chrome (en segundo plano), un commit por carpeta, verificado por hash contra el repo local. Completo.
- `android/tools/android.jar` (27 MB) y `android/tools/d8.jar` (16 MB) están **fuera del repo a propósito** (.gitignore), igual que en Mi Día 2; siguen en la carpeta del PC. El README dice de dónde descargarlos.
- Repo local (esta carpeta): rama `main`, commit `940d24e`, con historial; remote `origin` = el repo de arriba. Tiene el mismo contenido que GitHub (más los dos jars) pero otra historia.

## Historial
- GitHub tiene los commits de la web, no el historial local. Opcional en Git Bash: `git fetch origin && git reset --hard origin/main` (alinear la copia local) o `git push -f origin main` (subir el historial local).

## Secretos (fuera de git, a propósito)
- `cuaderno-hierro.keystore` y `signing-key-info.txt` están en `.gitignore`.
- `android/build.sh` ya no lleva la contraseña escrita: la lee de la variable `CDH_KS_PASS` o, si no está, de `signing-key-info.txt`.

## Artefacto
- **FOKUS**: https://claude.ai/artifact/KrV5dfoKwGkBnyAM3ff5cv — `web/index.html` con imágenes y fuentes. Capacidades: `db` + `user` (sincronización privada por usuario en `data/users/<id>`; sin ellas usa localStorage). Privado hasta que se comparta.
- El artefacto antiguo de referencia es https://claude.ai/artifact/VzMLxjBPonxBkQAGTebiM8.
