# Rediseño del Inicio (Dashboard) de Parity

> Análisis desde UX/UI y psicología del comportamiento financiero. Objetivo: que abrir Parity se sienta **calmado, rápido y útil**, no como revisar un extracto bancario.
> Estado: propuesta (2026-11-11). Lo marcado ✅ ya está implementado en la v0.5.0.

---

## 1. Diagnóstico del Inicio actual

### Inventario (de arriba abajo)

| # | Bloque | Qué responde | Problema |
|---|--------|--------------|----------|
| 1 | Encabezado: avatar, saludo, tasas | ¿Quién soy? ¿a cuánto está el dólar? | ✅ Antes tenía 5 botones (Sincronizar, Widgets, Actualizar, Ajustes, Tutoriales) compitiendo con el saldo. |
| 2 | Accesos rápidos (8) | ¿A dónde voy? | Duplican la barra inferior (Historial, Análisis, Metas, Billeteras ya están abajo). 8 opciones = parálisis (ley de Hick). |
| 3 | Saldo total + tendencia 30 d | ¿Cuánto tengo? | Bien. Es el ancla emocional; debe ser el protagonista. |
| 4 | Conversor de moneda | ¿Cuántos Bs son X $? | Útil pero esporádico: ocupa una pantalla completa todos los días. |
| 5 | Carrusel de billeteras | ¿Dónde está mi dinero? | Repite el saldo total partido. |
| 6 | Estructura de gastos (dona) | ¿En qué gasto? | Análisis, no acción. Repite la vista Análisis. |
| 7 | Ingresos vs gastos (barras) | ¿Gano más de lo que gasto? | Igual que 6. |
| 8 | Próximos 7 días | ¿Qué tengo que pagar? | **Muy valioso** y está enterrado abajo. |
| 9 | Presupuestos (top 3) | ¿Me estoy pasando? | Valioso; compite con 6 y 7. |
| 10 | Metas (top 3) | ¿Voy bien con mis metas? | Valioso para motivación. |
| 11 | Transacciones recientes | ¿Qué pasó últimamente? | Útil para verificar; puede ser más corto. |

**Resultado:** ~11 bloques y 4–5 pantallas de scroll. El usuario promedio de apps de finanzas abre la app 5–15 s; en ese tiempo solo ve 1–3.

### Problemas desde la psicología del comportamiento

1. **Efecto avestruz (ostrich effect):** la gente evita mirar sus finanzas cuando anticipa malas noticias. Una pared de gráficos y números rojos aumenta la evitación. El Inicio debe dar primero una sensación de control.
2. **Sobrecarga de elección (Hick / choice overload):** 8 accesos + 5 chips + 10 widgets = demasiadas decisiones antes de registrar un gasto, que es la acción núcleo.
3. **Aversión a la pérdida:** los rojos pesan ~2× más que los verdes. Hoy el rojo aparece en dona, barras, presupuestos y transacciones a la vez.
4. **Carga cognitiva por duplicación:** el mismo dato (saldo) aparece en 3 formatos (total, billeteras, conversor).
5. **Fricción en el registro:** cada paso extra antes de "+" reduce la constancia. La constancia (registrar todos los días) es lo que predice éxito en apps de presupuesto, no la cantidad de gráficos.
6. **Falta de "siguiente paso":** el Inicio informa pero no sugiere qué hacer (pagar, aportar a una meta, revisar un presupuesto).

---

## 2. Referentes y qué tomar de cada uno

| App | Qué hace bien | Qué adoptar en Parity |
|-----|---------------|------------------------|
| **YNAB** | "Dale a cada dólar un trabajo"; un número protagonista (*Ready to Assign*). | Un único número de decisión: **Disponible para gastar**. |
| **Copilot Money** | Inicio muy limpio: un número, una tendencia, "lo que requiere tu atención". | Bloque "Para hoy" con máximo 3 tarjetas accionables. |
| **Monarch Money** | Resumen semanal y progreso de metas con tono positivo. | Recaps semanales y celebración de hitos. |
| **PocketGuard** | *In My Pocket*: saldo − facturas próximas − metas. | Fórmula del "Disponible para gastar". |
| **Revolut / Wise** | Tasas en vivo de un toque, conversión instantánea. | ✅ Tasas como botón de actualizar; conversor como hoja bajo demanda. |
| **Wallet (BudgetBakers) / Spendee** | Escaneo de recibos, registro en 2 toques. | ✅ Cámara + OCR en la nota; plantillas de gasto frecuente. |
| **Cleo / Emma** | Tono conversacional, humor, cero culpa. | Microcopy amable (sin red, presupuesto excedido, etc.). |
| **Duolingo (no financiera)** | Rachas y refuerzo variable. | Racha de días registrando (ver `parity_gamification_plan` de la web). |

---

## 3. Principios del rediseño

1. **Una pantalla, una pregunta:** "¿Cuánto puedo gastar con tranquilidad hoy?"
2. **Divulgación progresiva:** el detalle vive a un toque (hojas inferiores, vista Análisis), no en el Inicio.
3. **Acción antes que análisis:** lo accionable (pagos próximos, presupuesto al límite, meta casi lograda) arriba; los gráficos, abajo o fuera.
4. **Color con intención:** rojo solo para lo que requiere acción; el resto en neutros. Verde para logros.
5. **Cero culpa:** frases neutras o positivas ("Te quedan $40 para comida esta semana" en vez de "Excediste 80 %").
6. **100 % offline:** todo se calcula localmente; la red solo para tasas y sincronización, siempre con aviso amable. ✅
7. **Minimalismo funcional:** máx. 5 bloques visibles por defecto; el resto, opcional en *Personalizar inicio*.

---

## 4. Nueva arquitectura del Inicio

```
┌─────────────────────────────────────┐
│ (foto) Hola, Ana          USD 48,20 ⟳│  ← encabezado: avatar + tasas (tocar = actualizar) ✅
│                           EUR 52,10  │
├─────────────────────────────────────┤
│  DISPONIBLE PARA GASTAR             │
│  $ 312,40            ≈ Bs 15.057    │  ← héroe: un solo número (ojo para ocultar)
│  ▁▂▃▅▆▇ +4,2 % en 30 días           │
│  Saldo $ 820 · Reservado $ 508  ⓘ   │  ← desglose a un toque
├─────────────────────────────────────┤
│ [ + Gasto ] [ 📷 Factura ] [ ⇄ ]     │  ← 3 acciones núcleo (no 8)
├─────────────────────────────────────┤
│  PARA HOY                           │  ← máx. 3 tarjetas accionables
│  ● Netflix vence mañana · $ 15  Pagar│
│  ● Comida: te quedan $ 40 esta sem. │
│  ● Meta Laptop al 92 % 🎉  Aportar   │
├─────────────────────────────────────┤
│  RECIENTES (5)            Ver todo › │
│  ☕ Café  hoy             - $ 3,50 🧾│  ← 🧾 = factura asociada ✅
└─────────────────────────────────────┘
```

### 4.1 Héroe: "Disponible para gastar"
- **Fórmula (offline):** `saldo total − pagos planificados de los próximos 30 días − aportes mensuales comprometidos a metas`.
- Debajo, en pequeño: *Saldo* y *Reservado*, tocables (hoja con el desglose).
- Mantiene: ojo para ocultar, moneda de visualización, tendencia de 30 días.
- **Psicología:** reduce ansiedad (un número, no diez) y convierte el saldo en un permiso para gastar en vez de una amenaza.

### 4.2 Acciones núcleo (reemplaza los 8 accesos)
- 3 botones: **Gasto**, **Factura** (abre la cámara directo al OCR) y **Transferir**.
- Los accesos rápidos personalizables pasan a ser opcionales (widget apagado por defecto). Mientras existan, ✅ ya no se pueden desmarcar: cada casilla se *cambia* por otra, así la fila nunca queda con huecos.

### 4.3 "Para hoy" (inteligencia local)
Reglas simples, sin IA ni red, ordenadas por urgencia; máximo 3:
1. Pago planificado que vence hoy/mañana → botón *Registrar pago*.
2. Presupuesto ≥ 80 % → "Te quedan $X para {categoría} hasta el {fecha}".
3. Meta ≥ 90 % o con aporte pendiente del mes → *Aportar*.
4. Ninguno → estado vacío positivo: "Todo en orden por hoy ✨".

### 4.4 Recientes
- 5 por defecto (hoy está en `dashboardTxLimit`). Ícono 🧾 si tiene factura ✅.
- Deslizar a la izquierda = borrar con *Deshacer* (snackbar 5 s); a la derecha = duplicar (gasto recurrente rápido).

### 4.5 Lo que sale del Inicio (pasa a opcional o a otra vista)
| Bloque | Destino |
|--------|---------|
| Conversor | Hoja que se abre al tocar las tasas largo o desde *Personalizar inicio* |
| Estructura de gastos / Ingresos vs gastos | Vista Análisis (ya existe) |
| Carrusel de billeteras | Vista Billeteras; en el Inicio, solo dentro del desglose del héroe |
| Presupuestos y metas (listas) | Se resumen en "Para hoy"; listas completas en sus vistas |
| Ajustes / Tutoriales / Widgets | ✅ Perfil → grupo **App** |
| Sincronizar | ✅ Perfil → Respaldo y sincronización (y automática en segundo plano) |

---

## 5. Registro de transacciones (la acción núcleo)

- ✅ **Dictado por voz** en la nota (micrófono; usa el paquete de voz del teléfono, sin red).
- ✅ **Cámara / galería** para la factura: OCR en el teléfono lee comercio, fecha, ítems, subtotal, IVA y total; "Usar factura" rellena monto, moneda, nota y fecha.
- Próximo: **interpretar el dictado** ("veinte dólares de comida ayer") → monto, categoría y fecha.
- Próximo: **plantillas** a partir de los 5 gastos más repetidos (un toque = transacción lista para confirmar).
- Próximo: categoría sugerida por comercio de la factura (aprende de las últimas asignaciones; local).

---

## 6. Micro-interacciones

| Momento | Interacción | Estado |
|---------|-------------|--------|
| Tocar cualquier tarjeta/botón | Escala 0,96 con resorte | ✅ (existente) |
| Actualizar tasas | Ícono ⟳ gira; el valor nuevo sube con fundido; vibración suave al terminar | ✅ |
| Guardar transacción | Vibración háptica de confirmación | ✅ |
| Leer factura | Ticket con "láser" que barre mientras el OCR trabaja | ✅ |
| Usar factura | Vibración y el monto aparece en el teclado | ✅ |
| Cambiar foto de perfil | Fundido cruzado entre iniciales y foto | ✅ |
| "Agregar más información" | Flecha que rota 180° + despliegue animado | ✅ |
| Cambiar acceso rápido | Vibración al intercambiar | ✅ |
| Saldo | Conteo animado al abrir y trazo progresivo de la línea | ✅ (existente) |
| Meta lograda | Confeti breve + vibración "éxito" | Pendiente |
| Presupuesto superado | Sacudida sutil de la barra (sin rojo intenso) | Pendiente |
| Deslizar transacción | Revela acción con ícono que crece al acercarse al umbral | Pendiente |
| Sin red | Snackbar amable 🌙 en vez de error | ✅ |

**Reglas:** duración 150–300 ms, resortes con amortiguación 0,6–0,8, nunca bloquear la interacción, respetar *Reducir animaciones* del sistema.

---

## 7. Microcopy (tono)

| Antes | Después |
|-------|---------|
| "Sin conexión" | "Sin conexión por ahora 🌙 Tus datos siguen a salvo en el teléfono." ✅ |
| "Error de Drive" | "Estás sin red 📡 Parity funciona sin internet; para sincronizar conéctate y vuelve a intentarlo." ✅ |
| "Presupuesto excedido" | "Comida pasó su límite por $12. ¿Ajustamos el de la próxima semana?" |
| "No hay transacciones" | "Tu primer registro tarda 5 segundos. ¿Empezamos?" |

---

## 8. Hábito y motivación (sin presión)

- **Racha de registro** (días seguidos con al menos un movimiento o un "hoy no gasté"). Recompensa la constancia, no el gasto bajo.
- **Recap semanal** el domingo (notificación local): 3 líneas, tono positivo, un consejo accionable.
- **Hitos** de metas al 25/50/75/100 % con celebración.
- Evitar comparaciones sociales y rankings: generan ansiedad en finanzas personales.

---

## 9. Plan por fases

| Fase | Alcance | Esfuerzo |
|------|---------|----------|
| **0 (hecho, v0.5.0)** | Encabezado limpio, tasas = actualizar, App en Perfil, foto y datos opcionales, accesos sin huecos, offline amable, facturas con OCR + dictado, micro-interacciones base, logo nuevo | — |
| **1** | Héroe "Disponible para gastar" + desglose; acciones núcleo (Gasto / Factura / Transferir); conversor a hoja | 1–2 días |
| **2** | "Para hoy" con reglas locales; recientes = 5 con deslizar | 2 días |
| **3** | Widgets de análisis fuera del Inicio por defecto; *Personalizar inicio* con presets ("Minimal", "Completo") | 1 día |
| **4** | Dictado inteligente, plantillas, categoría sugerida por comercio | 2–3 días |
| **5** | Racha, recap semanal, celebraciones | 2 días |

---

## 10. Cómo medir (local, sin analítica externa)

- **Tiempo a registrar** (abrir app → guardar): objetivo < 8 s.
- **Días con registro / semana:** objetivo ≥ 5.
- **Profundidad de scroll del Inicio:** objetivo ≤ 1,5 pantallas.
- **% de transacciones con factura** (adopción del OCR).

Contadores guardados en `SharedPreferences`, visibles en modo Dev.
