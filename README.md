# PARITY Android 💸 📱

<p align="center">
  <strong>Master the Flux. Two Currencies. One Wallet.</strong><br>
  <em>Cliente nativo de Parity en Kotlin y Jetpack Compose para la gestión financiera en economías bimonetarias y de alta volatilidad.</em>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android%208.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Platform: Android" />
  <img src="https://img.shields.io/badge/Language-Kotlin%201.9.22-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose%20%7C%20Material%203-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/Architecture-Local--First%20%7C%20Zero--Knowledge-00C853?style=flat-square" alt="Local First" />
  <img src="https://img.shields.io/badge/License-MIT-blue.svg?style=flat-square" alt="License: MIT" />
</p>

---

## 📋 Acerca del Proyecto

**PARITY Android** es la implementación nativa para dispositivos móviles de **[PARITY](https://github.com/MetalSyntax/PARITY)**. Diseñada específicamente para personas y familias que operan en economías con múltiples monedas e inflación constante (especialmente optimizada para la dinámica **USD & VES / Bolívares** en Venezuela).

A diferencia de las herramientas de finanzas tradicionales, Parity concibe la dualidad monetaria como un elemento de primer nivel: permite alternar los saldos globales con un solo toque, calcular transferencias y comisiones entre cuentas con tasas dinámicas y consultar la tasa oficial en tiempo real.

Construida bajo una rigurosa filosofía **Local-First & Zero-Knowledge**, la aplicación ejecuta todas las operaciones y cálculos directamente en el teléfono sin intermediación de servidores externos o bases de datos propietarias.

---

## ✨ Características Principales

### 💱 Núcleo Bimonetario Inteligente (USD / VES)
- **Alternador Global:** Cambia de forma instantánea entre **USD** y **VES** en toda la interfaz (inicio, saldos, gráficos y transacciones).
- **Tasa Oficial BCV en Vivo:** Obtención de la tasa de cambio oficial del Banco Central de Venezuela desde `ve.dolarapi.com` (la misma fuente de Parity Web), con opción de ajuste manual.
- **Transferencias entre Divisas:** Mueve dinero entre cuentas en distintas monedas (ej. Zelle en USD $\rightarrow$ Banco local en VES) con cálculo automático de equivalencia y gestión de comisiones.

### 📊 Gestión Financiera Completa
- **Inicio (Dashboard):** Visión panorámica del patrimonio neto consolidado, flujo neto de ingresos frente a egresos y quema diaria de dinero (*burn rate*).
- **Movimientos (Transacciones CRUD):** Registro exhaustivo de ingresos, gastos y transferencias con soporte para categorías, notas, comprobantes/recibos y etiquetas fiscales (`NEUTRAL`, etc.).
- **Planificados (Compromisos Recurrentes):** Control de pagos periódicos (alquiler, suscripciones, nómina), avance automático de fechas y notificaciones de vencimiento con el sistema de recordatorios (`Reminders`).
- **Presupuestos y Metas de Ahorro:** Definición de límites mensuales de gasto por categoría con control visual del ritmo de consumo y seguimiento de metas de ahorro con hitos y aportes.
- **Billeteras y Cuentas:** Monitoreo de balances en múltiples cuentas (efectivo, bancos nacionales, Zelle, Binance, PayPal, etc.) acompañadas de iconos vectoriales de marca.
- **Lista de Compras Interactiva:** Planificador de compras con reordenamiento intuitivo mediante arrastrar y soltar (*drag & drop*), marcado de artículos y edición rápida.

### 🔄 Sincronización e Interoperabilidad Total con Parity Web
Parity Android comparte el 100% de la especificación de datos de Parity Web (`packages/core/src/types.ts`). Los archivos de respaldo y sincronización son compatibles de forma bidireccional:
1. **Importación / Exportación SAF (Storage Access Framework):** Exporta e importa copias en formato `.json` a través del selector nativo de Android (compatible con Google Drive, tarjetas SD, descargas o almacenamiento local).
2. **Copia de Seguridad Automática Local:** Generación reactiva de un archivo de respaldo (`parity_backup_auto.json`) en una carpeta externa configurada (Drive, Dropbox, Syncthing) tras detectar cambios.
3. **Google Drive Sync (OAuth 2.0 Directo):**
   - Utiliza el alcance restringido `https://www.googleapis.com/auth/drive.file`, garantizando que la aplicación solo pueda leer y escribir los archivos que ella misma genera (`parity_sync_*.json`).
   - Algoritmo de mezcla determinista: sincronización basada en marcas de tiempo (`updatedAt`) y lápidas (*tombstones*) para evitar la reaparición de elementos eliminados.
   - **AutoDrive:** Sincronización automática al iniciar la app, subida con retardo (*debounce* de 30 segundos) ante modificaciones y respaldo inmediato al salir a segundo plano.
   - **Conservación de Metadatos:** Campos adicionales generados por la versión web que el cliente móvil no utilice directamente se conservan intactos en cada ciclo de guardado/mezcla (*round-trip*).

### 🛡️ Privacidad, Seguridad y Rendimiento
- **Almacenamiento Local sin Sobrecostes:** Persistencia directa y ultrarrápida en JSON estructurado, sin pesadas capas ORM como Room ni recolección de datos en segundo plano.
- **Biometría Nativa:** Desbloqueo mediante huella dactilar o reconocimiento facial mediante `androidx.biometric`.
- **Protección por PIN:** Bloqueo de 4 dígitos con opción de bloqueo automático inmediato al cerrar o enviar la app a segundo plano.
- **Modo Privacidad:** Ocultamiento instantáneo de cifras y balances sensibles (`••••••`) para operar con discreción en espacios públicos.
- **Internacionalización (i18n):** Interfaz disponible en Español, Inglés y Portugués.

---

## 🛠️ Stack Tecnológico

| Componente | Tecnología |
| :--- | :--- |
| **Lenguaje** | [Kotlin](https://kotlinlang.org/) `1.9.22` (JVM Target 17) |
| **Framework UI** | [Jetpack Compose](https://developer.android.com/jetpack/compose) con Compose BOM `2024.06.00` |
| **Sistema de Diseño** | [Material Design 3](https://m3.material.io/) |
| **Tipografía** | Inter (`res/font/inter.ttf`) |
| **Seguridad / Biometría** | `androidx.biometric:biometric:1.1.0` |
| **Autenticación Cloud** | `com.google.android.gms:play-services-auth:21.0.0` (OAuth Drive) |
| **Compatibilidad** | Min SDK 26 (Android 8.0 Oreo) · Target SDK 34 (Android 14) |
| **Testing** | JUnit 4 + org.json |
| **Build System** | Gradle 8.2 (Kotlin DSL `.kts`) |

---

## 📂 Estructura del Código

```text
app/src/main/java/com/metalsyntax/paritykt/
├── MainActivity.kt          # Actividad principal, enrutamiento, ciclo de vida y Edge-to-Edge
├── Reminders.kt             # Alarmas y programación de recordatorios de pagos planificados
├── data/
│   ├── Calc.kt              # Motores de conversión de divisas, redondeos y cálculos
│   ├── I18n.kt              # Carga y resolución de cadenas multilingües
│   ├── Models.kt            # Modelos de dominio (Account, Transaction, Goal, Budget, etc.)
│   ├── Store.kt             # Fuente única de verdad (Single Source of Truth), codificación y merge
│   └── Vault.kt             # Control de accesos y utilidades de almacenamiento
├── sync/
│   ├── AutoDrive.kt         # Orquestador reactivo de sincronización en segundo plano con Drive
│   └── Sync.kt              # Operaciones de red REST de Drive, SAF y copias automáticas
└── ui/
    ├── Analysis.kt          # Pantalla de análisis y reportes estadísticos
    ├── Components.kt        # Componentes UI reutilizables (tarjetas, botones, selectores)
    ├── Home.kt              # Pantalla principal (Dashboard y balances globales)
    ├── More.kt              # Menú y opciones adicionales
    ├── Onboarding.kt        # Flujo de bienvenida y configuración inicial
    ├── Planning.kt          # Presupuestos, Metas y Pagos planificados
    ├── Security.kt          # Pantalla de bloqueo biométrico y teclado PIN
    ├── Theme.kt             # Paleta de colores, tipografías y tema oscuro/claro
    ├── Transactions.kt      # Listado y filtros de movimientos
    ├── Tutorials.kt         # Guías y recorridos paso a paso
    └── TxEditor.kt          # Modal y formulario de creación/edición de transacciones
```

---

## 🚀 Compilación e Instalación

### Requisitos Previos
- **JDK:** Java 17 instalado y configurado en el entorno.
- **Android SDK:** API Level 34.
- **Herramientas de compilación:** Gradle Wrapper incluido (`./gradlew`).

### Comandos de Compilación
```bash
# Compilar el APK de desarrollo (Debug)
./gradlew assembleDebug

# El archivo generado se ubicará en:
# app/build/outputs/apk/debug/app-debug.apk

# Ejecutar la suite completa de pruebas unitarias
./gradlew testDebugUnitTest
```

---

## ☁️ Configuración de Google Drive Sync

Para habilitar la sincronización directa con Google Drive utilizando el mismo ecosistema que la versión web de Parity:

1. Ve a la consola de **[Google Cloud Console](https://console.cloud.google.com/)** en el proyecto correspondiente.
2. Asegúrate de tener habilitada la **Google Drive API**.
3. En **Credenciales**, crea un nuevo **ID de cliente de OAuth**:
   - **Tipo de aplicación:** Android.
   - **Nombre del paquete:** `com.metalsyntax.paritykt`
   - **Huella digital del certificado SHA-1:** Obtén el hash ejecutando:
     ```bash
     ./gradlew signingReport
     ```
4. Con el alcance `drive.file`, la aplicación únicamente accederá a los archivos que ella misma o el mismo Client ID hayan creado, manteniendo protegidos todos los demás archivos del usuario en Google Drive.

---

## 🧪 Pruebas Unitarias

La suite de pruebas locales (`StoreTest.kt`) valida la integridad de los datos financieros sin necesidad de emulador:
- Normalización automática de importes entre bolívares (VES) y dólares (USD).
- Edición, eliminación e impacto balanceado en cuentas de origen y destino.
- Manejo de comisiones en transferencias multidivisa.
- Integridad de lápidas (*tombstones*) y resolución de conflictos en mezcla remota.
- Importación completa y preservación de campos desconocidos provenientes de respaldos de Parity Web.

---

## 📄 Licencia

Este proyecto está distribuido bajo la Licencia **MIT**. Consulta el archivo [LICENSE](./LICENSE) para más detalles.

---

<p align="center">
  <strong>PARITY</strong> — <em>La economía es volátil. Tu tranquilidad financiera no debería serlo.</em>
</p>
