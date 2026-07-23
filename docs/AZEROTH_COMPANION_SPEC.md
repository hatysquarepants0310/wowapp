# Azeroth Companion — Especificación técnica para implementación

> **Documento de trabajo para Claude Code.**
> Objetivo: construir una aplicación Android nativa que funcione como companion app de World of Warcraft Retail (expansión *Midnight*, 12.x), cubriendo el hueco dejado por la descontinuación de la WoW Companion App oficial (julio 2024) y por la muerte de reemplazos de terceros como VaultAlts (backend caído, OAuth de Blizzard roto, endpoint de reportes fuera de servicio).

---

## 0. Contexto y principios de diseño

### 0.1 El problema real que resuelve

No existe hoy una app de Android confiable para WoW Retail. Lo disponible es:

- **Nada oficial.** Blizzard retiró la Companion App con el parche 11.0 y nunca la reemplazó.
- **Terceros frágiles.** VaultAlts (iOS/Android, mayo 2026) es el caso ejemplar: dependía 100% de un backend propio + OAuth de Blizzard, y al caer el servidor la app quedó inservible, aunque su sitio web siga publicando contenido de marketing.
- **Webs adaptadas.** Raider.io, Wowhead, la Armería oficial y todayinwow.com funcionan en navegador móvil, pero no dan notificaciones nativas, ni estado consolidado, ni funcionan offline.

### 0.2 Principios no negociables

Estos principios existen precisamente por las fallas observadas en la competencia y deben respetarse en cada decisión de arquitectura:

1. **Local-first.** La app debe seguir siendo útil sin red y sin backend propio. Toda la lógica de calendario, temporizadores de eventos y checklist semanal se calcula localmente a partir de reglas y catálogos embebidos.
2. **Cero backend propietario obligatorio.** La app habla directo con la API pública de Blizzard. Si mañana se cae cualquier servidor intermedio, la app sigue funcionando en modo degradado, no muere.
3. **Degradación explícita, nunca silenciosa.** Si el OAuth falla o la API no responde, la UI dice exactamente qué falló y qué funciones siguen vivas. Prohibido mostrar datos vacíos como si fueran datos reales.
4. **El reloj es la feature estrella.** El dolor #1 documentado del usuario objetivo es perder recompensas semanales por llegar tarde a un evento con ventana fija. Las notificaciones anticipadas son la razón de existir de la app.
5. **Catálogo versionado y actualizable sin publicar APK.** El contenido de la expansión cambia por parche. El catálogo de eventos/actividades vive en un JSON firmado, descargable, con copia embebida como fallback.

---

## 1. Stack técnico

| Capa | Tecnología | Notas |
|---|---|---|
| Lenguaje | Kotlin 2.x | |
| UI | Jetpack Compose + Material 3 | Tema oscuro por defecto |
| Arquitectura | MVVM + Clean, módulos por feature | |
| DI | Hilt | |
| Persistencia | Room | Fuente de verdad local |
| Preferencias | DataStore (Proto) | Config y sesión |
| Red | Retrofit + OkHttp + kotlinx.serialization | |
| Trabajos en background | WorkManager | Sync periódico |
| Alarmas exactas | AlarmManager (`setExactAndAllowWhileIdle`) | Ver §5.3 |
| Navegación | Navigation Compose | |
| Widgets | Glance | |
| minSdk / targetSdk | 26 / 35 | |
| Testing | JUnit5, Turbine, Room testing, MockWebServer | |

**Módulos Gradle sugeridos:**

```
:app
:core:model
:core:database
:core:network
:core:datastore
:core:designsystem
:core:time            // reglas de reset, husos, cálculo de ventanas
:core:catalog         // catálogo de contenido versionado
:feature:dashboard
:feature:events
:feature:weekly
:feature:vault
:feature:progression  // Omnium Folio, Prey, campaña
:feature:currencies
:feature:seasonal     // contenido con fecha límite (FOMO)
:feature:roster       // alts
:feature:settings
```

---

## 2. Integración con Blizzard API

### 2.1 Autenticación

- **OAuth 2.0 Authorization Code + PKCE** contra `https://oauth.battle.net`.
- Scope requerido: `wow.profile`.
- El `client_id`/`client_secret` se registran en el Blizzard Developer Portal. **El secret nunca va en el APK**; si se requiere flujo confidencial, usar PKCE puro (público) para evitar backend.
- Redirect URI vía App Links / Custom Tab (`androidx.browser`), nunca WebView embebido.
- Tokens en `EncryptedSharedPreferences` o DataStore cifrado.
- Refresh automático; ante fallo, estado `AuthState.Broken` con mensaje accionable en UI.

### 2.2 Regiones y namespaces

Soportar `us`, `eu`, `kr`, `tw`. Host: `https://{region}.api.blizzard.com`.

Namespaces: `profile-{region}`, `static-{region}`, `dynamic-{region}`. Locale por defecto `es_MX` con fallback `en_US`.

### 2.3 Endpoints requeridos

**Perfil (requieren OAuth de usuario):**

| Endpoint | Uso |
|---|---|
| `/profile/user/wow` | Lista de cuentas y personajes |
| `/profile/wow/character/{realm}/{name}` | Datos base, nivel, ilvl |
| `/profile/wow/character/{realm}/{name}/equipment` | Equipo por slot |
| `/profile/wow/character/{realm}/{name}/mythic-keystone-profile` | M+ temporada actual |
| `/profile/wow/character/{realm}/{name}/mythic-keystone-profile/season/{id}` | Runs de la temporada |
| `/profile/wow/character/{realm}/{name}/encounters/raids` | Progreso de raid |
| `/profile/wow/character/{realm}/{name}/quests/completed` | **Crítico** — resolución de weeklies |
| `/profile/wow/character/{realm}/{name}/reputations` | Renombre por facción |
| `/profile/wow/character/{realm}/{name}/achievements` | Logros, incl. de temporada |
| `/profile/wow/character/{realm}/{name}/collections/mounts` | Farmeo de monturas |
| `/profile/wow/character/{realm}/{name}/collections/pets` | |
| `/profile/wow/character/{realm}/{name}/professions` | Profesiones y conocimiento |
| `/profile/wow/character/{realm}/{name}/statistics` | |

**Datos estáticos/dinámicos:**

| Endpoint | Uso |
|---|---|
| `/data/wow/token/index` | Precio del WoW Token |
| `/data/wow/connected-realm/index` + detalle | Estado de reinos, colas |
| `/data/wow/quest/{id}` | Metadatos de misiones |
| `/data/wow/achievement/{id}` | Metadatos de logros |
| `/data/wow/item/{id}` | Metadatos de ítems |
| `/data/wow/mythic-keystone/season/index` | Temporada activa |

### 2.4 Limitaciones conocidas de la API (tratar explícitamente)

La API de Blizzard **no expone** varias cosas que la app necesita. Estas se resuelven por inferencia local, y la UI debe marcarlas con nivel de confianza:

- **No expone la Gran Bóveda directamente.** Se infiere de `mythic-keystone-profile` (runs de la semana), `encounters/raids` (kills desde el último reset) y quests completadas de contenido de mundo. Marcar como **estimado**.
- **No expone world quests activas.** Se resuelven por catálogo local + reglas de rotación. Marcar como **predicho**.
- **No expone temporizadores de eventos de mundo.** Se calculan localmente (§4).
- **No expone monedas/currencies de forma completa.** Usar lo disponible en `reputations` y `quests/completed`; el resto se estima o se ingresa manual.
- **Latencia de propagación.** Los datos de perfil pueden tardar minutos en reflejar acciones in-game. Mostrar siempre `lastSyncedAt` y advertir si supera 30 min.

**Regla de UI:** todo dato inferido lleva un badge visual: `CONFIRMADO` (vino de la API), `ESTIMADO` (derivado), `PREDICHO` (solo calendario/catálogo).

---

## 3. Modelo de dominio

```kotlin
// ---------- Cuenta y personajes ----------
data class WowAccount(val id: Long, val region: Region)

data class Character(
    val id: Long,
    val name: String,
    val realmSlug: String,
    val realmName: String,
    val region: Region,
    val faction: Faction,
    val playableClass: PlayableClass,
    val activeSpec: String?,
    val level: Int,
    val averageItemLevel: Int,
    val equippedItemLevel: Int,
    val isMain: Boolean,
    val lastLogin: Instant?,
    val lastSyncedAt: Instant?,
)

// ---------- Confianza del dato ----------
enum class Confidence { CONFIRMED, ESTIMATED, PREDICTED }

// ---------- Eventos de mundo con ventana fija ----------
data class WorldEventDefinition(
    val id: String,                  // "stormarion_assault"
    val name: String,                // localizable
    val zone: String,
    val coordinates: Coordinates?,   // 26.4, 67.6
    val cadence: EventCadence,       // ver §4.1
    val phases: List<EventPhase>,
    val requiresLevel: Int,
    val associatedQuestIds: List<Int>,
    val weeklyRewardItemIds: List<Int>,
    val maxWeeklyCompletions: Int,
    val requiresPresenceFromStart: Boolean,  // true en Stormarion
    val forbidsRaidGroup: Boolean,           // true en Stormarion
    val mountDropItemIds: List<Int>,
)

data class EventPhase(
    val order: Int,
    val name: String,
    val durationSeconds: Int,
    val playerActionHint: String,
)

data class EventOccurrence(
    val definitionId: String,
    val startsAt: Instant,
    val endsAt: Instant,
    val confidence: Confidence,
)

// ---------- Tareas periódicas ----------
enum class ResetPeriod { DAILY, WEEKLY, BIWEEKLY_WQ, HALF_WEEKLY, SEASONAL, ONE_TIME }

data class TrackedTask(
    val id: String,
    val category: TaskCategory,
    val title: String,
    val description: String?,
    val resetPeriod: ResetPeriod,
    val maxCompletions: Int,
    val detectionRule: DetectionRule,   // ver §6
    val rewards: List<RewardHint>,
    val zone: String?,
    val minLevel: Int,
    val isRemovedAtSeasonEnd: Boolean,
    val priorityWeight: Int,            // para ordenar el plan semanal
)

enum class TaskCategory {
    WORLD_EVENT, WEEKLY_QUEST, GREAT_VAULT, DELVE, PREY_HUNT,
    OMNIUM_FOLIO, CURRENCY, PROFESSION, WORLD_BOSS, PVP,
    CAMPAIGN, SEASONAL_REWARD, LEGACY, CUSTOM
}

data class TaskState(
    val taskId: String,
    val characterId: Long,
    val completions: Int,
    val confidence: Confidence,
    val manualOverride: Boolean,
    val updatedAt: Instant,
)

// ---------- Gran Bóveda ----------
data class GreatVaultProgress(
    val characterId: Long,
    val raidSlots: SlotProgress,     // 3 slots
    val mythicPlusSlots: SlotProgress,
    val worldSlots: SlotProgress,
    val confidence: Confidence,
)

data class SlotProgress(
    val current: Int,
    val thresholds: List<Int>,       // p.ej. [2, 4, 8]
    val predictedRewardIlvl: List<Int?>,
)

// ---------- Progresión de expansión ----------
data class OmniumFolioState(
    val characterId: Long,
    val unlockedRows: Int,           // 0..5
    val totalRows: Int = 5,
    val selectedRunes: Map<Int, String>,
    val catchUpAvailable: Boolean,
    val nextStepQuestId: Int?,
)

data class PreyHunt(
    val characterId: Long,
    val zone: String,                // Eversong, Zul'Aman, Harandar, Voidstorm
    val progressPercent: Int,
    val revealed: Boolean,
    val difficultyCompleted: Set<PreyDifficulty>,
)

// ---------- Contenido con fecha límite ----------
data class SeasonalReward(
    val id: String,
    val name: String,
    val type: SeasonalRewardType,    // MOUNT, TITLE, ACHIEVEMENT, TRANSMOG, FEAT
    val source: String,
    val estimatedDifficulty: Difficulty,
    val removedAt: SeasonBoundary,   // fecha estimada o "fin de temporada N"
    val obtainedByCharacter: Map<Long, Boolean>,
    val realisticForItemLevel: Int?, // filtro de viabilidad
)
```

---

## 4. Motor de tiempo — la feature central

### 4.1 Cadencias soportadas

```kotlin
sealed interface EventCadence {
    // Cada N minutos desde una época conocida, alineado a hora del reino
    data class FixedInterval(
        val intervalMinutes: Int,      // 30 en Stormarion Assault
        val anchorUtc: Instant,        // referencia calibrada
        val offsetMinutes: Int = 0,
    ) : EventCadence

    // Días concretos de la semana a horas concretas
    data class WeeklySchedule(
        val entries: List<DayTime>,
    ) : EventCadence

    // Ventanas de rotación de world quests (miércoles y sábado)
    data class RefreshWindows(
        val daysOfWeek: List<DayOfWeek>,
        val timeOfDay: LocalTime,
    ) : EventCadence

    data class Continuous(val durationHours: IntRange) : EventCadence
}
```

### 4.2 Resets

- **Reset diario:** refresca world quests de rotación corta y Delves con bonificación.
- **Reset semanal:** martes para Américas (US/LATAM), miércoles para EU. Corea/Taiwán según su huso. Reinicia lockouts de raid, M+, quests semanales, world boss y Gran Bóveda.
- **Refresco de world quests:** según catálogo por facción. Para *La Singularidad* (Voidstorm) las fuentes documentan refrescos **miércoles y sábado**.

> ⚠️ **Nota de implementación obligatoria.** Los días exactos de refresco de world quests y los anclajes de eventos son datos observados, no publicados por Blizzard, y Blizzard los ha ajustado entre parches. Por eso:
> - Todos estos valores viven en el **catálogo JSON**, nunca hardcodeados.
> - La app incluye **auto-calibración** (§4.4).
> - La UI marca estas predicciones con confianza `PREDICTED` y permite al usuario corregirlas.

### 4.3 Cálculo de ocurrencias

```kotlin
interface EventScheduler {
    fun nextOccurrence(def: WorldEventDefinition, from: Instant): EventOccurrence?
    fun occurrencesInRange(def: WorldEventDefinition, range: ClosedRange<Instant>): List<EventOccurrence>
    fun currentPhase(def: WorldEventDefinition, at: Instant): EventPhase?
    fun timeUntilNextStart(def: WorldEventDefinition, from: Instant): Duration?
}
```

Todo cálculo se hace en la **zona horaria del reino**, no la del dispositivo. Mostrar ambas cuando difieran. Manejar cambios de horario de verano (los reinos de EU y US no cambian el mismo día).

### 4.4 Auto-calibración del anclaje

Para que la app no dependa de valores fijos que se rompen con los parches:

1. El usuario puede pulsar **"El evento acaba de empezar"** dentro de la app.
2. Ese timestamp se guarda como observación.
3. Con ≥3 observaciones consistentes, el scheduler recalcula `anchorUtc` por reino y sube la confianza a `CONFIRMED`.
4. Las observaciones se guardan localmente; opcionalmente exportables, nunca obligatorio subirlas.

Esto convierte al usuario en la fuente de verdad y elimina la dependencia de que alguien mantenga un catálogo remoto.

---

## 5. Notificaciones — requisitos detallados

### 5.1 Por qué son la feature más importante

El caso de uso que justifica la app: el usuario llegó **30 segundos tarde** a un evento con ventana fija y perdió la recompensa semanal completa, porque el crédito de la world quest asociada solo se otorga si estás presente **desde el inicio** del evento. Unirse a la mitad no da progreso alguno.

### 5.2 Tipos de notificación

| ID | Disparador | Default | Configurable |
|---|---|---|---|
| `event_prewarn_long` | 15 min antes del inicio del evento | On | 5–60 min |
| `event_prewarn_short` | 3 min antes | On | 1–10 min |
| `event_start` | Al iniciar | On | — |
| `event_phase_change` | Cambio de fase | Off | — |
| `weekly_reset_soon` | 12 h antes del reset | On | 1–48 h |
| `weekly_reset_now` | Al reset | On | — |
| `vault_unclaimed` | Bóveda de la semana anterior sin reclamar | On | — |
| `wq_refresh` | Día de refresco de world quests | On | — |
| `omnium_step_available` | Nueva fila del Folio disponible | On | — |
| `prey_ready` | Presa revelada, lista para matar | On | — |
| `seasonal_deadline` | 30/14/7/3/1 días antes del fin de temporada | On | — |
| `season_reward_at_risk` | Recompensa marcada como objetivo, aún no obtenida, cerca del deadline | On | — |

### 5.3 Implementación

- **Alarmas exactas** vía `AlarmManager.setExactAndAllowWhileIdle`. Solicitar `SCHEDULE_EXACT_ALARM` (API 31+) con pantalla explicativa; degradar a `setWindow` si se deniega y avisarlo en UI.
- Reprogramar en `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED` y `TIMEZONE_CHANGED`.
- Canales de notificación separados por tipo, para que el usuario silencie categorías sin perder las críticas.
- Las notificaciones de evento incluyen **acción rápida**: "Ver checklist previa" (§5.4).
- Modo **No molestar por horario**: rango configurable, con excepción opcional para `event_prewarn_*`.

### 5.4 Checklist previa al evento

Al tocar la notificación de aviso, abrir una pantalla con la lista de verificación específica del evento. Para Stormarion Assault:

- [ ] Estás en el Voidstorm, Ciudadela de Stormarion (~26.4, 67.6)
- [ ] Nivel 90 alcanzado
- [ ] Campaña de leveo completada (requisito para world quests)
- [ ] **No estás en grupo de banda (raid)** — el crédito no se otorga en raid
- [ ] Contrato de facción activo (si aplica a la facción de la zona)
- [ ] La world quest aparece en tu mapa
- [ ] Núcleos suficientes para colocar defensas permanentes
- [ ] Te faltan N de 2 completaciones esta semana

Este contenido viene del catálogo, por evento, como lista de `PreconditionHint`.

---

## 6. Detección automática de progreso

La API no dice "completaste el evento X". Hay que inferirlo. Cada `TrackedTask` declara una `DetectionRule`:

```kotlin
sealed interface DetectionRule {
    // El quest ID aparece en /quests/completed y no aparecía antes del último reset
    data class QuestCompleted(val questIds: List<Int>, val countsAs: Int = 1) : DetectionRule

    // Comparación de snapshots entre syncs
    data class QuestDelta(val questIds: List<Int>) : DetectionRule

    data class ReputationGain(val factionId: Int, val minDelta: Int) : DetectionRule

    data class AchievementCriteria(val achievementId: Int, val criteriaIndex: Int) : DetectionRule

    data class MythicPlusRuns(val minRuns: Int) : DetectionRule

    data class RaidBossKills(val instanceId: Int, val minKills: Int) : DetectionRule

    data class CurrencyThreshold(val currencyId: Int, val amount: Int) : DetectionRule

    // Sin fuente API — el usuario marca a mano
    data object ManualOnly : DetectionRule

    data class AnyOf(val rules: List<DetectionRule>) : DetectionRule
    data class AllOf(val rules: List<DetectionRule>) : DetectionRule
}
```

**Motor de snapshots.** En cada sync se guarda un snapshot inmutable de `quests/completed`, reputaciones y logros por personaje. La detección compara el snapshot actual contra el primero posterior al último reset. Esto permite detectar "hecho esta semana" aunque la API solo dé estado acumulado.

**Override manual siempre disponible.** Cualquier tarea se puede marcar/desmarcar a mano. El override gana sobre la inferencia y persiste hasta el próximo reset.

---

## 7. Catálogo de contenido (Midnight 12.x)

El catálogo es un JSON versionado. La app trae una copia embebida y opcionalmente descarga actualizaciones. Estructura:

```json
{
  "catalogVersion": 12,
  "gameVersion": "12.1",
  "updatedAt": "2026-07-23T00:00:00Z",
  "resets": { "...": "..." },
  "worldEvents": [ "..." ],
  "weeklyTasks": [ "..." ],
  "seasonalRewards": [ "..." ],
  "zones": [ "..." ]
}
```

### 7.1 Eventos de mundo (uno por zona)

| Evento | Zona | Cadencia | Recompensa semanal |
|---|---|---|---|
| Stormarion Assault (Asalto a Stormarion) | Voidstorm | cada 30 min | Victorious Stormarion Pinnacle Cache |
| Saltheril's Soiree (Velada de Saltheril) | Silvermoon / Eversong | ver catálogo | Mayor variedad de monedas |
| Abundance (Abundancia) | Eversong | ver catálogo | — |
| Ritual Sites (Lugares rituales) | rota por zona | semanal | — |

**Ficha detallada: Stormarion Assault** (referencia de cómo se modela todo evento)

```json
{
  "id": "stormarion_assault",
  "name": { "es_MX": "Asalto a Stormarion", "en_US": "Stormarion Assault" },
  "zone": "voidstorm",
  "coordinates": { "x": 26.4, "y": 67.6 },
  "location": "Ciudadela de Stormarion",
  "requiresLevel": 90,
  "requiresCampaignComplete": true,
  "cadence": { "type": "FixedInterval", "intervalMinutes": 30 },
  "requiresPresenceFromStart": true,
  "forbidsRaidGroup": true,
  "maxWeeklyCompletions": 2,
  "worldQuestRefresh": { "daysOfWeek": ["WEDNESDAY", "SATURDAY"] },
  "phases": [
    { "order": 1, "name": "Preparación", "durationSeconds": 300,
      "hint": "Junta Núcleos de tesoros y quests. Coloca defensas PERMANENTES antes que consumibles." },
    { "order": 2, "name": "Oleada 1", "durationSeconds": null, "hint": "Defiende el Ancla de Singularidad." },
    { "order": 3, "name": "Descanso", "durationSeconds": 60, "hint": "Refuerza defensas." },
    { "order": 4, "name": "Oleada 2", "durationSeconds": null, "hint": "" },
    { "order": 5, "name": "Descanso", "durationSeconds": 60, "hint": "" },
    { "order": 6, "name": "Oleada 3", "durationSeconds": null, "hint": "" },
    { "order": 7, "name": "Victoria", "durationSeconds": null, "hint": "Entrega la world quest." }
  ],
  "mechanics": {
    "anchorHealth": 100,
    "defenseCostCores": 10,
    "vendor": "Xy'dax",
    "note": "Los enemigos ignoran a los jugadores salvo que los ataques directamente. El posicionamiento de defensas en su ruta es lo decisivo."
  },
  "rewards": {
    "firstWeeklyCompletion": "Victorious Stormarion Pinnacle Cache",
    "secondWeeklyCompletion": "Victorious Stormarion Cache",
    "beyondCap": "Solo Núcleos, oro y Voidlight Marl. Sin cofre.",
    "reputation": { "faction": "the_singularity", "amount": 2000 },
    "mountChance": "Contained Stormarion Defender (ambos cofres)",
    "gearTrack": "Veteran"
  },
  "knownIssues": [
    "Existen reportes de que el evento no otorga crédito pese a completarse correctamente. Si ocurre, sugerir /reload o salir y volver a entrar a la zona."
  ]
}
```

### 7.2 Tareas semanales de Midnight

El catálogo debe incluir, como mínimo, estas categorías observadas en el juego:

- Semanal de Arcantina
- Semanal de Halduron
- Semanal: Abundancia
- Leyendas perdidas (con coordenadas variables)
- Velada de Saltheril
- Fortificar las piedras rúnicas
- Unidad contra el Vacío
- Asignación especial (múltiples activas simultáneas)
- Asaltos del Vacío
- Lugares rituales (zona rotativa)
- Pescadores del abismo
- Enfrentamiento de la Invasión
- Jefe de mundo (1 por semana)
- Semanal de PvP

### 7.3 Sistemas de progresión de expansión

**Folio Omnium** (sistema de poder de personaje, parche 12.0.7)
- Árbol de 5 filas; se desbloquea 1 fila por reset semanal.
- Cada fila ofrece 2–4 runas; se elige una; se puede recambiar fuera de combate sin costo.
- No ocupa slot de equipo.
- Questline de desbloqueo inicia en Ciudad Solaz ("La Llamada del Magister", Aviso del Magister) y continúa en la Isla de Quel'Danas.
- Cada paso otorga un Vestigio de Indagación Omnial.
- **Catch-up:** un jugador atrasado puede hacer las semanas pendientes de forma secuencial sin esperar el reset, una a la vez, hasta emparejarse. No existe moneda para saltar pasos.

La app debe modelar esto como una **cadena secuencial con estado**, mostrando: fila actual, filas pendientes, si hay catch-up disponible, y un enlace/recordatorio para consultar la runa recomendada por clase antes de elegir (la elección de la Runa Central condiciona el resto de la build).

**Sistema de Presas (Prey Hunts)**
- 4 contratos simultáneos, uno por zona: Eversong Woods, Zul'Aman, Harandar, Voidstorm.
- La barra de progreso se llena haciendo contenido de mundo abierto en la zona: world quests, rares, tesoros, materiales de profesión, desarmar trampas, activar emboscadas.
- La presa puede emboscar al jugador; seguir el rastro de niebla de sangre y atacarla **aumenta** el progreso.
- Al completar, Astalor revela la ubicación; se invoca, se mata, se reclama.
- Límite: una cacería **por dificultad, por semana, por zona**.
- Recompensas: cofre de equipo (hasta track Champion), Dawncrests, fragmentos de Restored Coffer Key, progreso al slot de mundo de la Bóveda.

**Gran Bóveda**
- 3 fuentes: raid, Mythic+, mundo. 9 slots totales.
- Se reclama **una sola** recompensa por semana.
- Mostrar umbrales, progreso e ilvl predicho por slot.

**Delves (Profundidades)**
- Bountiful Delves consumen Restored Coffer Keys y dan cofre con equipo.
- Trackear llaves disponibles y Delves hechas.

**Monedas**
- Crests / Dawncrests con tope semanal.
- Voidlight Marl, Stormarion Cores, Brimming Arcana, Latent Arcana.
- Monedas de PvP: Conquista, Honor.
- Incluir **calculadora de mejora**: costo en crests e ilvl ganado por pieza.

**Campaña (progresión única, no se resetea)**
- "La guerra de la Luz y la Sombra" (campaña base, 6 capítulos) → hacer **primero**. Desbloquea sistemas de endgame. Recompensas: set Atavíos del Pacto de Solestrella, montura Dracohalcón Peridoto.
- "La maldición de Ula'tek" (contenido 12.1, Isla Enroscada) → hacer **después**.

### 7.4 Contenido legacy

La app debe poder **filtrar y ocultar** contenido de expansiones anteriores que sigue siendo repetible (ej. Bóvedas de Zskera de Dragonflight). Por defecto: oculto. Toggle en ajustes: "Mostrar contenido legacy". Cuando se muestre, etiquetarlo claramente con la expansión de origen y una nota de relevancia ("solo coleccionables, sin valor de progresión actual").

---

## 8. Módulo de contenido con fecha límite (Seasonal / FOMO)

Este módulo no existe en ninguna herramienta actual y es un diferenciador claro.

### 8.1 Qué rastrea

Recompensas que se eliminan permanentemente al cambiar de temporada:

- Monturas, títulos, logros y Feats of Strength de temporada
- Cutting Edge y Ahead of the Curve (pasan a legacy al cerrar el tier)
- Montura de Keystone de la temporada actual
- Apariencias élite y títulos de PvP de la temporada
- Efectos visuales de los tier sets de temporada

**Qué NO se pierde** (y la app debe decirlo explícitamente para reducir ansiedad innecesaria):
equipo del personaje, drops normales de mazmorra no ligados a logros de temporada, apariencias de raid recuperables por legacy, juguetes/mascotas/reputación no estacionales.

### 8.2 Funcionalidad

- **Countdown de fin de temporada**, con la fecha marcada como estimada mientras Blizzard no la confirme.
- **Filtro de viabilidad.** El usuario ingresa (o se detecta) su ilvl, rating de M+ y experiencia de raid; la app clasifica cada recompensa en: `Alcanzable`, `Ajustado`, `No realista en el tiempo restante`. Un jugador recién llegado a nivel máximo no debe recibir como "pendiente" el Cutting Edge de Mythic.
- **Lista de objetivos.** El usuario marca qué quiere conseguir; genera notificaciones escaladas conforme se acerca el deadline.
- **Cross-check con colecciones.** Consultar `/collections/mounts` y `/achievements` para marcar automáticamente lo ya obtenido.

### 8.3 Advertencia obligatoria en UI

> Las fechas de fin de temporada son estimaciones basadas en cronogramas de parches anunciados. Blizzard rara vez confirma la fecha exacta con antelación. Trata los deadlines como aproximados y con margen.

---

## 9. Pantallas

### 9.1 Dashboard (inicio)

De arriba a abajo:

1. **Próximo evento** — tarjeta grande con cuenta regresiva al minuto/segundo, nombre, zona, coordenadas, y botón "Prepararme" (abre checklist previa).
2. **Reset semanal** — tiempo restante.
3. **Resumen del personaje activo** — nombre, ilvl, clase/spec.
4. **Gran Bóveda** — 3 barras (raid/M+/mundo) con umbrales.
5. **Pendientes de alta prioridad** — top 5 tareas ordenadas por `priorityWeight` y por lo que aún es alcanzable esta semana.
6. **Deadline de temporada** — si faltan <30 días.

### 9.2 Eventos

- Línea de tiempo de las próximas 24 h con todas las ocurrencias.
- Filtro por zona y por "solo los que me faltan esta semana".
- Detalle por evento: fases, mecánicas, recompensas, límite semanal, problemas conocidos, botón "El evento acaba de empezar" (calibración).

### 9.3 Semanal

Checklist agrupada por categoría, replicando y mejorando la organización de un tracker in-game:

```
Actividades            1/1
Semanales              3/12
Folio Omnium           2/5
Monedas de PvP         0/3
Semanales de PvP       0/1
Gran Bóveda            1/3
Monedas                4/15
Profesión              0/6
Profundidades          2/5
Sistema de presas      1/4
Jefe de mundo          0/1
Tareas personalizadas  0/0
Historia (una vez)     —
```

Cada línea expandible a sub-tareas, con badge de confianza y opción de override manual.

### 9.4 Progresión

Pestañas: Folio Omnium · Presas · Campaña · Delves.

### 9.5 Temporada (FOMO)

Lista filtrable de recompensas con countdown, estado obtenido/pendiente y clasificación de viabilidad.

### 9.6 Roster (alts)

Grid de personajes con: ilvl, Bóveda, tareas clave pendientes, y **intentos de montura restantes esta semana** (relevante porque los límites son por personaje).

### 9.7 Ajustes

Región · Reino y huso · Notificaciones por tipo y anticipación · Mostrar/ocultar legacy · Idioma · Estado de la API y último sync · Exportar/importar datos · Cerrar sesión.

### 9.8 Widgets (Glance)

- **Widget pequeño:** cuenta regresiva al próximo evento.
- **Widget mediano:** próximo evento + reset semanal + Bóveda.
- **Widget grande:** checklist de pendientes del día.

---

## 10. Sincronización

| Trabajo | Frecuencia | Condiciones |
|---|---|---|
| `SyncActiveCharacterWorker` | cada 30 min | Red disponible |
| `SyncRosterWorker` | cada 6 h | Red + no batería baja |
| `SyncStaticDataWorker` | cada 24 h | Red sin medición |
| `CatalogUpdateWorker` | cada 24 h | Red sin medición |
| `RescheduleAlarmsWorker` | tras cada sync y en boot | — |

- Respetar `Cache-Control` y ETags de la API.
- Backoff exponencial ante 429/5xx.
- Un sync manual con pull-to-refresh, con rate limit local de 1/min.
- **Nunca bloquear la UI por el sync.** Los datos locales se muestran de inmediato con su `lastSyncedAt`.

---

## 11. Manejo de errores — requisitos explícitos

Aprendizaje directo de la app que murió: los fallos deben ser visibles y no deben tumbar la aplicación entera.

| Fallo | Comportamiento requerido |
|---|---|
| OAuth roto / token irrecuperable | Banner persistente: "La sesión de Battle.net no responde." La app **sigue funcionando** con temporizadores, catálogo, checklist manual y notificaciones. |
| API 5xx / timeout | Mostrar datos en caché con `lastSyncedAt` y aviso de antigüedad si >30 min. |
| API 429 | Backoff silencioso; solo informar si persiste >1 h. |
| Personaje no encontrado | Marcar como inactivo, no borrar datos históricos. |
| Catálogo remoto inaccesible | Usar el embebido; avisar la versión en uso. |
| Datos inconsistentes con lo que el usuario ve in-game | Botón "Corregir a mano" siempre presente, en cada tarjeta. |

**Pantalla de diagnóstico** (en Ajustes): estado de cada endpoint, último código HTTP, versión del catálogo, hora del reino calculada vs. hora del dispositivo, y **exportación de log a archivo local** (nunca a un servidor que pueda desaparecer).

---

## 12. Privacidad y seguridad

- Solo scope `wow.profile`, solo lectura. Documentarlo en pantalla antes del login.
- Cero telemetría por defecto. Si se añade analítica, debe ser opt-in explícito.
- Sin anuncios que dependan de un SDK que pueda romper la app.
- Todos los datos del usuario residen en el dispositivo, exportables a JSON.
- Marcas registradas: incluir el aviso estándar de que World of Warcraft® y Battle.net® son marcas de Blizzard Entertainment, Inc., y que la app no está afiliada ni respaldada por Blizzard.

---

## 13. Criterios de aceptación (funcionales)

1. Con la app instalada y sin sesión iniciada, el usuario recibe una notificación 15 y 3 minutos antes del próximo Stormarion Assault, con la hora correcta según su reino.
2. Al tocar esa notificación se abre la checklist previa con los 8 puntos de verificación del evento.
3. Tras completar el evento in-game y hacer sync, la tarea aparece marcada con badge `ESTIMADO` y contador `1/2`.
4. Si la API no lo detecta, el usuario puede marcarla a mano y el override persiste hasta el reset.
5. La Gran Bóveda muestra progreso de las 3 fuentes con umbrales y badge `ESTIMADO`.
6. Tras el reset semanal, todas las tareas semanales vuelven a 0 automáticamente y las de tipo `ONE_TIME` permanecen completadas.
7. Con el OAuth deliberadamente roto (simulable en debug), la app arranca, muestra temporizadores correctos y permite checklist manual completa.
8. Cambiar la región en Ajustes recalcula todos los resets y ocurrencias sin reinstalar.
9. El módulo de temporada oculta las recompensas clasificadas como no realistas cuando el usuario activa el filtro de viabilidad.
10. El widget de pantalla de inicio actualiza la cuenta regresiva al menos cada minuto.

---

## 14. Plan de implementación por fases

**Fase 1 — Núcleo offline (sin API)**
`:core:time`, `:core:catalog`, scheduler de eventos, notificaciones, checklist manual, dashboard básico.
*Entregable: una app útil que ya resuelve el problema de llegar tarde, sin depender de nadie.*

**Fase 2 — Integración Blizzard**
OAuth PKCE, sync de personaje, roster, motor de snapshots, detección automática de tareas.

**Fase 3 — Progresión**
Gran Bóveda, Folio Omnium, Presas, Delves, monedas, calculadora de mejora.

**Fase 4 — Temporada y alts**
Módulo FOMO con filtro de viabilidad, roster multi-personaje, tracking de intentos de montura.

**Fase 5 — Pulido**
Widgets Glance, calibración comunitaria de anclajes, exportación de datos, localización completa es_MX / en_US.

---

## 15. Notas de exactitud para quien implemente

Este documento mezcla dos tipos de información y conviene distinguirlos:

- **Estructural y verificable:** endpoints de la API de Blizzard, comportamiento del OAuth, días de reset semanal, arquitectura Android. Esto es estable.
- **Contenido específico de la expansión:** nombres de eventos, IDs de quest, cadencias exactas, días de refresco de world quests, fechas de fin de temporada, valores de recompensa. Esto proviene de guías de terceros y observación de jugadores, cambia entre parches, y **algunas fuentes se contradicen entre sí** (por ejemplo, sobre la cadencia exacta del Stormarion Assault y sobre si existe catch-up en el Folio Omnium).

Por eso la arquitectura exige que **todo el contenido específico viva en el catálogo JSON, sea corregible por el usuario, y se muestre con nivel de confianza explícito**. Ninguna cadencia, ID ni fecha debe quedar hardcodeada en el código de la app. Antes de dar por buenos los valores del §7, verificarlos contra la API (`/data/wow/quest/{id}`) y contra observación directa in-game.
