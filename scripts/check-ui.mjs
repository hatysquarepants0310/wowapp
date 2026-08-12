#!/usr/bin/env node
/**
 * Detector de aspecto genérico, adaptado a Jetpack Compose.
 *
 * El detector de `docs/UI-WARCRAFT-HERRAMIENTAS.md` §11 está escrito para un
 * proyecto web: busca `border-radius` en CSS, clases `rounded-lg` de Tailwind y
 * dependencias como `@mui/material`. Este proyecto es Android con Compose, así
 * que **cada regla se traduce a su equivalente exacto en esta plataforma**, que
 * es lo que el encargo pedía. La lista de equivalencias:
 *
 *   web                        →  Compose
 *   ─────────────────────────     ────────────────────────────────────────────
 *   @mui/material, shadcn/ui   →  androidx.compose.material3 (Card, Button,
 *                                 NavigationBar, TopAppBar, OutlinedTextField…)
 *   border-radius: 8px         →  RoundedCornerShape(8.dp)
 *   rounded-md / rounded-lg    →  Radius.sm / Radius.md del tema
 *   box-shadow difusa          →  Modifier.shadow(...) / elevation = N.dp
 *   ease-in-out, cubic-bezier  →  FastOutSlowInEasing, tween() sin easing
 *   colores de calidad         →  los ocho, exactos, en Kotlin
 *   tabular-nums               →  fontFeatureSettings = "tnum"
 *   fonts.googleapis.com       →  igual (no debe aparecer)
 *   outline: none              →  indication = null sin sustituto
 *
 * No comprueba que la interfaz sea bonita: eso no es comprobable. Comprueba que
 * no tenga las huellas MEDIBLES del aspecto de fábrica.
 *
 * Cada hallazgo se silencia añadiéndolo a ALLOW **con el motivo escrito**. Un
 * silenciado sin motivo es deuda; con motivo es una decisión.
 */
import { readdirSync, readFileSync, existsSync } from 'node:fs'
import { join } from 'node:path'

const SRC = 'app/src/main/java'
const RES = 'app/src/main/res'
const GRADLE = 'app/build.gradle.kts'

/**
 * Componentes de Material 3 que traen la silueta de Google puesta: alturas,
 * ripple, forma, elevación y gestos de foco. Son el equivalente en Compose de
 * shadcn/ui o MUI. `MaterialTheme` y `Text` no cuentan: el primero es el
 * portador de tokens (color y tipografía propios) y el segundo no tiene
 * apariencia de fábrica más allá del estilo que se le pasa.
 */
const OPINIONATED_COMPOSABLES = [
  'Card(', 'ElevatedCard(', 'OutlinedCard(',
  'Button(', 'ElevatedButton(', 'FilledTonalButton(', 'OutlinedButton(',
  'TextButton(',
  'NavigationBar(', 'NavigationBarItem(', 'NavigationRail(',
  'TopAppBar(', 'CenterAlignedTopAppBar(', 'MediumTopAppBar(',
  'OutlinedTextField(', 'TextField(',
  'LinearProgressIndicator(', 'CircularProgressIndicator(',
  'Switch(', 'Checkbox(', 'RadioButton(', 'Slider(',
  'FloatingActionButton(', 'ExtendedFloatingActionButton(',
  'AssistChip(', 'FilterChip(', 'SuggestionChip(', 'InputChip(',
  'TabRow(', 'Tab(', 'ScrollableTabRow(',
  'AlertDialog(', 'ModalBottomSheet(', 'Snackbar(',
  'ListItem(', 'Badge(', 'BadgedBox(',
]

/** Los ocho colores de calidad de objeto, exactos (§5). */
const QUALITY = [
  '#9d9d9d', '#ffffff', '#1eff00', '#0070dd',
  '#a335ee', '#ff8000', '#e6cc80', '#00ccff',
]

/**
 * Hallazgos aceptados, CON MOTIVO. Sin motivo escrito no se acepta nada.
 */
const ALLOW = [
  // Vacío a propósito. La primera versión traía una entrada para `Radius.pill`
  // y al plantar un fallo se vio que silenciaba TODOS los hallazgos de radio de
  // ese archivo, incluido el plantado. Aquella entrada además era innecesaria:
  // 999.dp queda fuera del rango 3-14 y nunca se marcaba. Un ALLOW demasiado
  // grueso no es una decisión, es una prueba apagada.
  // Formato: { file, rule, match, why } — `match` exige que el mensaje lo
  // contenga, así que silencia UN hallazgo y no una categoría entera.
]

let fallos = 0
const fallo = (file, rule, msg) => {
  if (ALLOW.some((a) => a.file === file && a.rule === rule && a.why &&
      (!a.match || msg.includes(a.match)))) {
    return
  }
  fallos++
  console.log(` FALLO  [${rule}] ${file}: ${msg}`)
}

function files(dir, exts) {
  if (!existsSync(dir)) return []
  const out = []
  for (const e of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, e.name)
    if (e.isDirectory()) out.push(...files(full, exts))
    else if (exts.test(e.name)) out.push(full)
  }
  return out
}

const kotlin = files(SRC, /\.kt$/)
const xml = files(RES, /\.xml$/)
const all = [...kotlin, ...xml]

// ── 1. Componentes con apariencia de fábrica ───────────────────────────────
// El equivalente en Compose de "dependencias con estética propia": no basta con
// mirar el build.gradle, porque material3 aporta también el portador de tokens.
// Lo que delata es USAR sus componentes ya vestidos.
// El nombre tiene que empezar donde empieza una palabra. La primera versión
// usaba `includes()` y marcaba `WowButton(` como si fuera el `Button(` de
// Material: un detector que no distingue mi botón del de Google no sirve para
// decidir nada. `\b` no salta entre `w` y `B`, así que `WowButton(` ya no cuela,
// y `Button(` o `material3.Button(` sí.
for (const file of kotlin) {
  const src = readFileSync(file, 'utf8')
  for (const composable of OPINIONATED_COMPOSABLES) {
    const name = composable.slice(0, -1)
    if (new RegExp(`\\b${name}\\(`).test(src)) {
      fallo(file, 'component', `${name} de Material 3 trae la silueta de Google (§0)`)
    }
  }
}

// ── 2. Huellas del aspecto por defecto ─────────────────────────────────────
for (const file of all) {
  const src = readFileSync(file, 'utf8')

  // Radio de plantilla: 3-14dp. WoW es de esquina dura o bisel marcado.
  for (const m of src.matchAll(/RoundedCornerShape\(\s*([\d.]+)\s*\.dp/g)) {
    const dp = Number(m[1])
    if (dp >= 3 && dp <= 14) fallo(file, 'radius', `RoundedCornerShape(${m[1]}.dp) (§0)`)
  }
  // Cualquier token DENTRO del objeto de radios, se llame como se llame.
  // Al plantar un fallo salió que la versión anterior solo miraba `sm/md/lg`,
  // así que un token con otro nombre se colaba: por eso se plantan fallos.
  for (const block of src.matchAll(/object\s+Radius\s*\{([\s\S]*?)\n\}/g)) {
    for (const m of block[1].matchAll(/val\s+(\w+)\s*:\s*Dp\s*=\s*([\d.]+)\.dp/g)) {
      const dp = Number(m[2])
      if (dp >= 3 && dp <= 14) fallo(file, 'radius', `token de radio ${m[1]} = ${m[2]}.dp (§0)`)
    }
  }

  // Sombra difusa: en Compose es Modifier.shadow o elevation.
  for (const m of src.matchAll(/\.shadow\(\s*([\d.]+)\s*\.dp/g)) {
    if (Number(m[1]) > 0) fallo(file, 'shadow', `Modifier.shadow(${m[1]}.dp) — usa biseles inset (§3)`)
  }
  for (const m of src.matchAll(/(?:shadowElevation|tonalElevation|defaultElevation)\s*=\s*([\d.]+)\.dp/g)) {
    if (Number(m[1]) > 0) fallo(file, 'shadow', `elevación ${m[1]}.dp — el metal apoyado no flota (§3)`)
  }

  // Easing de Material. En WoW el movimiento es lineal (§6).
  if (/FastOutSlowInEasing|LinearOutSlowInEasing|FastOutLinearInEasing/.test(src)) {
    fallo(file, 'easing', 'easing de Material — en WoW el movimiento es lineal (§6)')
  }
  for (const m of src.matchAll(/CubicBezierEasing\(\s*0?\.4f?,\s*0f?,\s*0?\.2f?,\s*1f?\s*\)/g)) {
    fallo(file, 'easing', 'cubic-bezier de Material (§6)')
  }

  // Foco/indicación desactivada sin sustituto.
  for (const m of src.matchAll(/indication\s*=\s*null/g)) {
    const around = src.slice(Math.max(0, m.index - 400), m.index + 400)
    if (!/focus|Focus|border|outline/.test(around)) {
      fallo(file, 'focus', 'indication = null sin sustituto visible (§8)')
    }
  }

  // Fuentes remotas.
  if (src.includes('fonts.googleapis.com')) {
    fallo(file, 'fonts', 'fuente por CDN — empaquétala local (§2)')
  }
}

// ── 3. Datos canónicos ─────────────────────────────────────────────────────
const joined = all.map((f) => readFileSync(f, 'utf8')).join('\n').toLowerCase()
const missing = QUALITY.filter((hex) => !joined.includes(hex.replace('#', '0xff')))
if (missing.length > 0) {
  fallo('app/src/main', 'quality', `faltan colores de calidad exactos: ${missing.join(' ')} (§5)`)
}

// ── 4. Densidad ────────────────────────────────────────────────────────────
// El equivalente de "hay tablas y ningún tabular-nums": la app está llena de
// columnas de cifras (ilvl, oro, nivel de llave), así que el rasgo tiene que
// estar en la tipografía.
if (!joined.includes('tnum')) {
  fallo('app/src/main', 'density', 'ninguna cifra tabular — las columnas bailan (§4)')
}

// ── 5. Tipografía propia ───────────────────────────────────────────────────
// Sin familia declarada se usa Roboto, la de fábrica de Android: es la huella
// tipográfica del aspecto por defecto.
if (existsSync(GRADLE)) {
  const hasFontRes = files(join(RES, 'font'), /\.(ttf|otf|xml)$/).length > 0
  if (!hasFontRes && !/FontFamily\s*\(/.test(joined)) {
    fallo(GRADLE, 'typeface', 'ninguna tipografía propia: se hereda Roboto (§2)')
  }
}

console.log(`\n${all.length} archivos · ${fallos} fallos`)
if (fallos > 0) process.exit(1)
