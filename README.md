# ⚔️ Azeroth Companion

> La companion app de World of Warcraft Retail para Android que la comunidad merece.
> **Local-first. Sin backend obligatorio. Nunca más llegar 30 segundos tarde a un evento.**

[![CI](https://github.com/hatysquarepants0310/wowapp/actions/workflows/ci.yml/badge.svg)](https://github.com/hatysquarepants0310/wowapp/actions/workflows/ci.yml)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![minSdk](https://img.shields.io/badge/minSdk-26-3DDC84?logo=android&logoColor=white)
![License](https://img.shields.io/badge/license-Apache%202.0-blue)

---

## 💜 Apoya el proyecto

> **Azeroth Companion es gratis, sin anuncios y sin telemetría.** Si te salvó un cofre
> semanal y quieres invitar un café, cualquier aporte ayuda a mantenerlo vivo para toda
> la comunidad. ¡Gracias! 🙌

| Cripto | Dirección |
|---|---|
| ₿ **Bitcoin** | `bc1qa2r0gufynr7g05mjxlnp4hc9e7r3nkyc7w9u68jjrlzjnllc6n9se064zm` |
| Ξ **Ethereum** | `0x6A0cb583AcE01561D9d12d4625Ee4c1DcAF0f275` |

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
| **Contenido** | Afijos de Mythic+ de la semana + mazmorras y bandas de **cualquier expansión** con sus jefes (actual destacada, anteriores aparte) |
| **Misiones por zona** | Cada zona con todas sus misiones marcadas ✓/○ según tu cuenta, automáticamente |
| **Personaje** | Elige cualquier alt: nivel, ilvl, equipo por slot con iconos y colección de monturas |
| **Temporadas M+** | Tu historial por temporada: rating, mejor llave y en cuáles participaste |
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

📦 **APK listo para instalar:** descarga el `.apk` del **último release** en
[Releases](https://github.com/hatysquarepants0310/wowapp/releases) e instálalo en tu
Android (8.0+). El login de Battle.net viene configurado de fábrica: conecta tu cuenta
y todo se llena solo — sin checklists manuales, sin escribir nada.

> Verifica qué versión tienes en **Ajustes → Diagnóstico**. Si vienes de una versión
> anterior a v1.1.x, desinstala una vez (cambió la firma); desde v1.1.x en adelante las
> actualizaciones instalan encima.

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

Requisitos: JDK 17+, Android SDK 35. El CI compila, testea y publica el APK en cada
release. Los APK se firman con el keystore comunitario del repo para que las
actualizaciones siempre instalen encima.

**OAuth:** Blizzard no admite clientes públicos (su endpoint de token exige
client_secret), así que las credenciales van en la compilación (`gradle.properties`) y
el redirect pasa por una página puente estática en GitHub Pages (`web/oauth.html`) —
cero backend. Para un fork propio: registra tu cliente en el
[Blizzard Developer Portal](https://develop.battle.net/) con redirect a tu propia
página puente y reemplaza `blizzardClientId`/`blizzardClientSecret`.

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

**v1.3.0** — nueva sección **Contenido**: afijos de Mythic+ de la semana (Raider.IO) y el
journal de mazmorras/bandas con sus jefes (API oficial de Datos de Juego de Blizzard). Sin
scraping de Wowhead —que no tiene API pública— y funciona sin iniciar sesión.

**v1.4.0** — Contenido de **todas las expansiones** (las pasadas separadas para no confundir);
nueva pantalla **Personaje** con selector de alt, equipo y monturas; **historial de temporadas
de Mythic+**; **botón de actualización dentro de la app** (descarga e instala el último release
sin abrir GitHub); y un rediseño visual **oscuro amaderado con acentos morados** de Midnight.

**v1.5.0** — **Botín de cada jefe** (toca un jefe y ve qué suelta), **iconos reales de tu equipo**,
eventos con **cadencia legible** ("cada 30 min") y **recompensas** ("qué te da"), y corrección del
bug por el que Inicio mostraba un personaje distinto al seleccionado. La checklist manual de
Voidstorm se sustituyó por requisitos informativos.

**v1.6.0** — **Rastreador de misiones por zona**: las 432 zonas del juego, y al abrir una ves todas
sus misiones marcadas ✓/○ cruzando el catálogo oficial con las misiones completadas de tu cuenta
(igual que Wowhead, con API oficial). Además, **aviso de actualización al abrir la app** con opción
de instalar en el momento o después.

**v1.7.0** — **Historias (storylines)**: 1.639 cadenas de misiones de todo el juego con tu progreso
✓/○ real, estilo Wowhead — datos del cliente del juego (tablas DB2 `QuestLine` vía wago.tools,
horneadas en la app; cero scraping, cero dependencia en runtime). Al abrir una historia ves cada
misión con su zona y recompensa. Además: **barra inferior simplificada a 3 botones** (Inicio ·
Personaje · Más) y un **menú "Más" rediseñado con iconos** para que se sienta a funciones, no a
ajustes.

**v1.8.0** — **Monturas con imagen** en Personaje (cuadrícula con el render real de cada montura,
sin coste de API); **Historias ordenadas por temporada actual → campañas → resto**, con su zona
y filtros; **Mythic+ con las mazmorras de la temporada y el botín de cada jefe**; **feedback
visible al sincronizar** (éxito o motivo del error); y **actualizador robusto**: progreso de
descarga, verificación de integridad del APK y ruta guiada si Android pide permiso para instalar
(la causa del "error de paquete").

**v1.9.0** — **Historias reorganizadas en jerarquía** estilo BtWQuests: temporada (con emblema y
color propios) → categoría (campaña principal / historias de zona / otras) → historias **numeradas
en orden** → misiones con ✓/○ y detalle al tocar (zona, nivel mínimo, recompensa, descripción).
**Gran Bóveda como en el juego**: rejilla 3×3 con casillas bloqueadas/desbloqueadas e ilvl previsto.
**Selector de idioma español/inglés** con autodetección en el primer arranque. **Botón atrás** en
todas las subpantallas de "Más". Corregido el bug por el que los niveles e ilvl volvían a 0 (el
refresco del roster los sobrescribía con ceros), y quitado el bloque de pendientes de Inicio.

**v1.9.1** — Corregida la clasificación por expansión de las historias: ahora usa la expansión
**del contenido** de la zona (`AreaTable.ContentTuningID` → `ContentTuning.ExpansionID`) en lugar del
continente, que clasificaba mal todo lo añadido después sobre los continentes antiguos (Monte Hyjal,
Vashj'ir y las Tierras Altas Crepusculares salían como vanilla en vez de Cataclysm; Zul'Aman y la
Isla de Quel'Danas, en vez de Midnight). Además, el grupo de expansión 0 se llama ahora
**"Azeroth original"** para no confundirlo con el producto *WoW Classic*: son las zonas del juego
base, que siguen existiendo en el WoW actual.

**v2.0.0** — Reconstrucción de las historias y de la semana sobre datos verificables.

*Historias.* La jerarquía de cada temporada ya no son tres cajones inventados, sino las **campañas
reales del juego** (`Campaign` + `CampaignXQuestLine`), con sus capítulos en el orden exacto en que
se juegan y numerados **por campaña**, no de corrido por temporada. Antes la categoría "Campaña
principal" salía de buscar la palabra *campaign* en el nombre y quedaba vacía: The War Within tenía
0 campañas detectadas, ahora tiene su campaña de 14 capítulos y Midnight la suya de 17.

*Misiones opcionales.* `QuestLineXQuest.Flags = 1` marca las misiones **opcionales** de cada cadena.
Exigirlas hacía que casi ninguna historia apareciera completa: sobre un personaje real con las
campañas de las tres últimas expansiones terminadas, The War Within daba 14/16, 8/10, 19/20… y ni un
capítulo completo. Contando solo las obligatorias da 14/14 capítulos, Midnight 17/17 y Minahonda 6/6.
Las opcionales se siguen mostrando, marcadas como tales.

*Misiones instantáneas y sin conexión.* Nombre, zona y nivel de las **17 916 misiones** de todas las
cadenas van horneados en los assets, en español e inglés. Abrir una historia ya no dispara una
petición por misión.

*Clasificación por expansión.* Las campañas mandan sobre sus capítulos (un prólogo en Ventormenta ya
no manda la historia a "Azeroth original") y las cadenas cuyas misiones no declaran zona
—profesiones, salas de clase, guarniciones— se clasifican por rango de ID de misión, calibrado
contra las que sí tienen zona conocida (89 % de acierto medido). Historias sin expansión: de 716 a 10.

*Semanales.* Corregido el fallo por el que las misiones hechas no se registraban nunca. Se comparaba
contra la línea base de la semana, así que quien sincronizaba **después** de hacer la misión veía
siempre 0. Blizzard borra la marca de las misiones repetibles en cada reset —verificado: un personaje
que terminó toda Dragonflight no tiene ninguna de las *Aiding the Accord* en `/quests/completed`—,
así que basta con que aparezca para contarla.

*Gran Bóveda exacta.* Banda y Mythic+ ya no se estiman: `last_kill_timestamp` por jefe y dificultad y
la fecha de cada mazmorra dicen exactamente qué cayó tras el reset, y la recompensa prevista usa la
dificultad o el nivel de llave **de la N-ésima mejor actividad**, como en el juego. La fila de Mundo
se deduce de la estadística de Delves del perfil (40734) por diferencia; mientras no haya una lectura
anterior al reset la app lo dice en vez de enseñar un 0 que parece real.

*"Esta semana".* La antigua checklist —cuyas tareas eran casi todas `ManualOnly` y, sin entrada
manual, nunca se marcaban— es ahora un panel de actividad real: cada M+ con su nivel de llave, cada
jefe con su dificultad, las Delves y las misiones completadas desde el reset.

**v2.1.0** — La sesión de Battle.net ya no se cae cada día, vuelven las semanales y todo el botín
lleva imagen y probabilidad.

*Sesión.* Los tokens de usuario de Blizzard caducan a las 24 h y el grant `refresh_token` **no está
habilitado** para los clientes de desarrollador (el endpoint responde
`invalid_client: Unauthorized grant type`): de ahí el "reconecta la cuenta" diario. El grant
`token_extension` sí está habilitado y devuelve el token con **~90 días** de vida, así que la app lo
alarga en cada arranque. Además, los endpoints de personaje (`/profile/wow/character/**`) funcionan
con el token de aplicación, que la app se emite a sí misma —verificado contra la API—, así que ahora
solo re-importar la lista de personajes de la cuenta necesita sesión: aunque caduque, tu progreso
sigue sincronizándose y la app lo dice en lugar de mostrar un error.

*Semanales.* Vuelven, y esta vez se marcan solas. Blizzard nombra sus misiones semanales de
seguimiento `"Midnight: <actividad>"`; de ahí salen las **16 semanales reales** de la expansión con
su ID de misión, en lugar de las tareas inventadas con regla `ManualOnly` que nunca podían marcarse.

*Botín con imagen y probabilidad.* Misiones, mazmorras, bandas y las recompensas exclusivas de la
temporada muestran el icono real del objeto, su color de calidad, la ranura, el jefe y la
dificultad. Blizzard **no publica tasas de caída**, así que la app estima con lo que sí es público
—la tabla de botín de cada jefe— y **explica de dónde sale cada número** en la propia fila: "la tabla
del jefe tiene 16 objetos y caen ~4 por intento". Las monturas se calculan aparte, porque son una
tirada independiente de la tabla; el catálogo acepta tasas medidas por la comunidad y esas tienen
prioridad sobre cualquier estimación.

*Exclusivo de la temporada, en Inicio.* Nueva tarjeta con las monturas que solo caen esta temporada
(salen del journal oficial: se detectan por `item_class` 15 / subclase 5), marcando las que **ya
tienes** al cruzar tu colección, y una pantalla con todo el equipo destacado de los jefes finales.
Son 385 objetos con imagen y origen, horneados en assets: abren al instante y sin conexión.

**v2.2.0** — Las semanales se marcan de verdad, y el botín te lleva a dónde conseguirlo.

*Bug de las semanales.* No se marcaban aunque la misma misión sí saliera completada en el resto de
la app: la pantalla resolvía el personaje como `activeCharacterId ?: 0L` mientras el sync guardaba el
estado con el ID **real** del personaje (el elegido o, si no hay ninguno, el primero del roster). Se
escribía bajo un ID y se leía bajo otro. La regla vivía copiada en seis sitios; ahora existe una sola
vez, en `ActiveCharacter`, para que no vuelva a divergir.

*Botín navegable.* Cada objeto del botín de temporada lleva al jefe y la instancia de los que cae:
abre la pestaña correcta, despliega la instancia y resalta al jefe. Se acabó ver una montura y no
saber de dónde sale.

*Botín por semanal.* Cada semanal se despliega y enseña lo que se persigue en el contenido que pide,
con imagen y probabilidad. La misión de seguimiento en sí solo da oro y experiencia —comprobado
contra la API—, así que lo útil es el botín de la mazmorra, la banda o el jefe de mundo que hay
detrás; el catálogo mapea cada semanal a sus instancias.

*Tus intentos reales.* Las monturas de temporada muestran cuántas veces has matado a su jefe (sale
de tu perfil, no de una estimación) y qué porcentaje de gente ya la tendría con esos intentos:
1 - (1 - p)^n. Con un 1 %, 100 intentos son un 63 %, no una garantía.

*Sobre las probabilidades.* Confirmado que Blizzard no las publica: el juego tampoco las muestra. Las
webs que sí las tienen las calculan con telemetría de sus propios usuarios — Wowhead lo explica en su
web ("It maintains a WoW addon called the Wowhead Looter, which collects data as you play the
game"). Copiar esos números sería usar datos ajenos sin permiso, así que la app estima con lo que sí
es público y explica cada número; el campo `knownDropRates` del catálogo está para que la comunidad
aporte tasas medidas, y esas ganan sobre cualquier estimación.

**v2.3.0** — Las semanales por fin se marcan, porque dejan de depender de adivinar IDs de misión.

*Qué estaba mal de verdad.* La v2.1.0 sacó las semanales de las misiones que Blizzard nombra
`"Midnight: <actividad>"`, dando por hecho que eran las semanales del jugador. No lo son: son
marcadores internos que **la API no expone**. Comprobado sobre 75 personajes activos sacados de las
clasificaciones de PvP — 15 de los 16 IDs no aparecen en `/quests/completed` de *nadie*, y en la
tabla DB2 los 15 comparten el mismo `UniqueBitFlag` (67601), señal de que no se almacenan
individualmente. Por eso la casilla nunca se marcaba aunque la misión sí saliera completada en el
resto de la app.

*Cómo se arregla sin adivinar.* Cada semanal se apoya ahora en una señal que la API demuestra:

- **Mazmorras Mythic+** y **jefes de banda**: la API fecha cada mazmorra y cada muerte, así que son
  exactos desde el primer sync.
- **Profundidades**: por diferencia de la estadística de Delves del perfil.
- **Misiones semanales**: la app **aprende cuáles de tus misiones son repetibles** observando tus
  propias sincronizaciones. Una misión que estaba completada y deja de estarlo solo puede haber sido
  reiniciada por Blizzard, así que es semanal; y una repetible que figura completada hoy se hizo
  necesariamente en este periodo. Sin listas curadas y sin nada que se pueda quedar obsoleto por
  parche. Al actualizar se recorre el historial de snapshots ya guardado, así que si llevas semanas
  con la app las semanales aparecen con nombre desde el primer sync.

Cuando una fila no se puede medir todavía, la app dice por qué en lugar de mostrar un cero que
parece un dato real.

### Pipeline de datos
`tools/build_storylines.py` regenera `storylines.json`, `quests_es/en.json`, `quest_meta.json`,
`areas_es/en.json` y `mounts.json` a partir de las tablas DB2 de wago.tools (datos del propio cliente
del juego, la misma fuente que usa Wowhead: `QuestLine`, `QuestLineXQuest`, `Campaign`,
`CampaignXQuestLine`, `AreaTable`, `ContentTuning`) más la API oficial de Blizzard para el nombre y
la zona de cada misión. `tools/build_season_loot.py` genera `season_loot.json` recorriendo el journal
oficial de la expansión actual (instancias → jefes → objetos → imagen), que es lo que alimenta el
botín con imagen y probabilidad. Se ejecuta por parche, offline; la app solo
consume los JSON horneados — cero dependencia de esas fuentes en tiempo de ejecución.

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
