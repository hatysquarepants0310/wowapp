# ⚔️ Azeroth Companion

> La companion app de World of Warcraft Retail para Android que la comunidad merece.
> **Local-first. Sin backend obligatorio. Nunca más llegar 30 segundos tarde a un evento.**

[![CI](https://github.com/hatysquarepants0310/wowapp/actions/workflows/ci.yml/badge.svg)](https://github.com/hatysquarepants0310/wowapp/actions/workflows/ci.yml)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![minSdk](https://img.shields.io/badge/minSdk-26-3DDC84?logo=android&logoColor=white)
![License](https://img.shields.io/badge/license-Apache%202.0-blue)

---

## ¿Por qué existe?

Blizzard retiró la WoW Companion App oficial con el parche 11.0 y nunca la reemplazó. Los
reemplazos de terceros que dependían de backends propios murieron cuando esos servidores
cayeron. Esta app está diseñada para que eso **no pueda pasar**:

- 🕐 **El reloj es la feature estrella.** Notificaciones exactas 15 y 3 minutos antes de cada
  evento de mundo con ventana fija (Stormarion Assault y compañía). El crédito semanal solo se
  otorga si estás presente desde el inicio — la app existe para que llegues.
- 📴 **Local-first.** Temporizadores, calendario, checklist semanal y catálogo funcionan sin
  red y sin sesión. La API de Blizzard es un extra, no una dependencia.
- 🚫 **Cero backend propietario.** La app habla directo con la API pública de Blizzard vía
  OAuth PKCE. No hay servidor intermedio que pueda morir y llevarse la app.
- 🏷️ **Confianza explícita.** Todo dato lleva badge: `CONFIRMADO` (API), `ESTIMADO`
  (inferido), `PREDICHO` (catálogo). Nunca datos vacíos disfrazados de datos reales.
- 🔧 **Auto-calibración comunitaria.** ¿Blizzard movió el horario de un evento en un parche?
  Pulsa "El evento acaba de empezar" 3 veces y el scheduler se recalibra solo, sin esperar
  a que nadie actualice nada.

## Features

| Pantalla | Qué hace |
|---|---|
| **Inicio** | Cuenta regresiva al próximo evento, reset semanal, Gran Bóveda, top pendientes |
| **Eventos** | Timeline de ocurrencias con fases, mecánicas y botón de calibración |
| **Checklist previa** | Al tocar la notificación: los 8 puntos de verificación del evento (¿estás fuera de raid? ¿tienes Núcleos?) |
| **Semanal** | Checklist agrupada por categoría con detección automática de progreso y override manual siempre disponible |
| **Progresión** | Folio Omnium (con reglas de catch-up), Sistema de Presas, Campaña, Delves |
| **Temporada** | Tracker de recompensas con fecha límite (FOMO) con filtro de viabilidad honesto |
| **Roster** | Alts con Bóveda, intentos de montura restantes y cambio de personaje activo |
| **Widget** | Próximo evento en tu pantalla de inicio, sin abrir la app |
| **Ajustes** | Cuenta Battle.net, región, legacy, exportar/importar datos, diagnóstico |

## Arquitectura

```
app/src/main/java/com/azeroth/companion/
├── core/
│   ├── model/          # Dominio: eventos, cadencias, tareas, reglas de detección
│   ├── time/           # ⭐ Motor de tiempo: scheduler, resets por región, auto-calibración
│   ├── catalog/        # Catálogo JSON versionado (embebido + actualizable sin APK)
│   ├── detection/      # Inferencia de progreso por snapshots (la API no dice "completaste X")
│   ├── database/       # Room: estados, snapshots, observaciones de calibración
│   ├── datastore/      # Preferencias
│   ├── network/        # Blizzard API + OAuth PKCE (sin client_secret en el APK)
│   └── notifications/  # Alarmas exactas + canales + reprogramación en boot/timezone
├── data/               # Repositorios
├── feature/            # Pantallas Compose por feature
└── sync/               # WorkManager: alarmas, catálogo
```

**Stack:** Kotlin 2.x · Jetpack Compose + Material 3 (tema oscuro) · Hilt · Room · DataStore ·
Retrofit + kotlinx.serialization · WorkManager · AlarmManager exacto · JUnit.

**Principio rector:** ninguna cadencia, ID de quest ni fecha vive en el código. Todo el
contenido de la expansión está en [`catalog.json`](app/src/main/assets/catalog/catalog.json),
corregible por el usuario y marcado con nivel de confianza. Ver la
[especificación completa](docs/AZEROTH_COMPANION_SPEC.md).

## Descargar

📦 **APK listo para instalar:** en la sección [Releases](https://github.com/hatysquarepants0310/wowapp/releases)
del repositorio. Descarga el `.apk` del último release e instálalo en tu Android (8.0+).
Cada push también genera un APK como artifact del [CI](https://github.com/hatysquarepants0310/wowapp/actions).

> ⚠️ **Si vienes de v1.0.0 o anterior:** desinstala la app una única vez antes de instalar
> v1.1.0+. Desde v1.1.0 todos los APKs comparten una firma comunitaria estable, así que las
> actualizaciones futuras instalan directamente sin desinstalar.

**El login de Battle.net funciona de fábrica** desde v1.1.0 (client ID público PKCE incluido
en la compilación). Conecta tu cuenta en Ajustes y el roster, ilvl, Bóveda, semanales
detectables, logros y monturas se sincronizan solos — la checklist manual queda únicamente
como corrección opcional para lo que la API de Blizzard no expone.

## Compilar

```bash
./gradlew :app:assembleDebug        # APK debug
./gradlew :app:testDebugUnitTest    # tests del motor de tiempo y detección
```

Requisitos: JDK 17+, Android SDK 35. El CI compila y publica el APK como artifact en cada push.

El `client_id` público de Battle.net (flujo PKCE, redirect `azerothcompanion://oauth`) vive en
`gradle.properties`; para usar el tuyo propio, cámbialo ahí o pasa `-PblizzardClientId=...`.
**El client secret nunca se usa ni se incluye** — un cliente público PKCE no lo necesita.

La firma de los APKs usa `signing/community.keystore`, un keystore comunitario versionado a
propósito: garantiza que las actualizaciones instalen sin desinstalar. No acredita autoría y
no debe usarse para Play Store; para distribución en tienda genera una clave privada propia.

## Estado y roadmap

- ✅ **Fase 1 — Núcleo offline:** motor de tiempo, catálogo, notificaciones, checklists, dashboard. *La app ya resuelve el problema de llegar tarde sin depender de nadie.*
- ✅ **Fase 2 — Integración Blizzard:** login OAuth PKCE, sync de roster y personaje, motor de snapshots con detección automática de progreso.
- ✅ **Fase 3 — Progresión:** Bóveda estimada desde datos reales, Folio Omnium con catch-up, Presas por zona, Delves y calculadora de mejora con crests.
- ✅ **Fase 4 — Temporada y alts:** viabilidad con el ilvl real del personaje, lista de objetivos con alarmas escaladas de deadline, roster con intentos de montura por personaje.
- ✅ **Fase 5 — Pulido:** widget de pantalla de inicio (próximo evento), exportación/importación de datos a JSON, catálogo bilingüe es_MX/en_US.

**v1.0.0** — las 5 fases del plan completadas. Cada release lleva su APK adjunto.

## Contribuir

El catálogo (`catalog.json`) es el corazón comunitario del proyecto: cadencias, IDs de quest
y fechas provienen de observación de jugadores y cambian entre parches. Los PRs que corrijan
o amplíen el catálogo son los más valiosos que puedes enviar.

## Aviso legal

World of Warcraft® y Battle.net® son marcas registradas de Blizzard Entertainment, Inc.
Esta aplicación es un proyecto comunitario y **no está afiliada ni respaldada por Blizzard
Entertainment**. Solo usa la API pública oficial con scope de lectura `wow.profile`.
Cero telemetría. Tus datos viven en tu dispositivo.

---

*Hecho con ❤️ para todos los jugadores que alguna vez llegaron 30 segundos tarde.*
