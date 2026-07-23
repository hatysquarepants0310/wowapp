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
| **Ajustes** | Región, legacy, diagnóstico completo de la app |

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

## Compilar

```bash
./gradlew :app:assembleDebug        # APK debug
./gradlew :app:testDebugUnitTest    # tests del motor de tiempo y detección
```

Requisitos: JDK 17+, Android SDK 35. El CI compila y publica el APK como artifact en cada push.

Para habilitar el login de Battle.net registra un cliente OAuth (público, PKCE) en el
[Blizzard Developer Portal](https://develop.battle.net/) con redirect
`azerothcompanion://oauth` y configura el `client_id` en la app.

## Estado y roadmap

- ✅ **Fase 1 — Núcleo offline:** motor de tiempo, catálogo, notificaciones, checklists, dashboard. *La app ya resuelve el problema de llegar tarde sin depender de nadie.*
- 🚧 **Fase 2 — Integración Blizzard:** OAuth PKCE y capa de red listas; falta cablear el sync de roster y el motor de snapshots end-to-end.
- 🚧 **Fase 3 — Progresión:** Bóveda con datos reales, Folio, Presas, calculadora de mejora.
- 🔜 **Fase 4 — Temporada y alts:** viabilidad con datos del personaje, intentos de montura.
- 🔜 **Fase 5 — Pulido:** widgets Glance, exportación de datos, localización completa.

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
