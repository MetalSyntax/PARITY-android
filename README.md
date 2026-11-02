# Parity MVP (Kotlin · Jetpack Compose · Material 3)

Versión nativa mínima de Parity: Inicio, Movimientos (CRUD), Planificado, Presupuesto y Metas, Billeteras,
Lista de compras y Perfil. Datos locales en un JSON con el **mismo formato que Parity web**
(`packages/core/src/types.ts`): los respaldos `parity_backup_*.json` y `parity_sync_*.json` sirven en las dos.

- Tasa: **solo la oficial del BCV** (ve.dolarapi.com, misma fuente que la web) o manual.
- Sincronización, de más simple a más automática:
  1. Exportar / importar archivo (el selector del sistema permite elegir Google Drive sin configurar nada).
  2. Copia automática en una carpeta (Drive, Dropbox, Syncthing…): `parity_backup_auto.json` en cada cambio.
  3. Google Drive directo (`drive.file`, formato mínimo de la web, mezcla por `updatedAt` + lápidas).

## Google Drive directo: configuración
Crear en el **mismo proyecto de Google Cloud que usa la web** un cliente OAuth *Android* con el paquete
`com.metalsyntax.paritykt` y el SHA-1 del certificado (`./gradlew signingReport`). Con `drive.file`, la app solo ve
los archivos creados por ese proyecto, así que comparte los `parity_sync_*` con la web.

## Compilar y probar
```
./gradlew assembleDebug           # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest       # saldos, transferencias, edición, borrado, mezcla, respaldo, planificados
```
Gradle 8.2, AGP 8.2.1, Kotlin 1.9.22, Compose BOM 2024.02. Sin Room ni red social: solo AndroidX + play-services-auth.
