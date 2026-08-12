# Herramientas de UI para que no parezca hecha por una IA

> Complemento de `UI-WARCRAFT.md`. Ese documento explica **cómo decidir**: el
> método brainstorm → plan → autocrítica → construir → autocrítica final, y el
> contexto visual concreto de Warcraft.
>
> Este es sobre **con qué construir** y, sobre todo, sobre **cómo comprobar sin
> preguntarle a nadie** que funcionó. La sección §11 es la más importante del
> documento: convierte "esto se ve de IA" —que es una opinión— en una prueba que
> se corre y se pone roja.

---

## 0. El diagnóstico, dicho sin rodeos

Si una interfaz se sigue viendo "de IA" teniendo buena dirección de arte, casi
siempre es por una razón muy concreta:

**Está hecha con componentes que ya vienen con opinión estética.**

shadcn/ui, MUI, Chakra, Ant Design, Mantine — todos traen un aspecto de fábrica.
Puedes cambiarles los colores, el radio de las esquinas y la tipografía, y aun
así la **silueta** se queda: las mismas alturas de control, las mismas sombras
difusas, el mismo ritmo vertical, los mismos gestos de foco. Y esa silueta es lo
que la gente reconoce sin saber que la reconoce.

Cambiar tokens sobre un componente pre-estilizado es maquillaje. La estructura
sigue siendo la de otra persona.

**La regla que resuelve esto:**

> Usa primitivas **sin estilo** (headless) y pon tú cada píxel.

Cuesta más al principio. Es la única forma de que el resultado no se parezca a
nada más.

### Esto es una hipótesis, no un hecho — verifícala

Lo de arriba es lo más probable, pero quien escribió este documento no ha visto
tu repo. **Antes de reescribir nada, mide** (§11 tiene el script). Si el
diagnóstico automático sale limpio y la interfaz sigue viéndose genérica, el
problema está en otro sitio y hay que buscarlo, no aplicar esta receta por fe.

---

## 1. Primitivas sin estilo

Todas resuelven accesibilidad, navegación por teclado, foco y ARIA — y aportan
**cero apariencia**. Eso es exactamente lo que se quiere: lo difícil hecho, lo
visible en tus manos.

| Herramienta | Para qué | Nota |
|---|---|---|
| **Radix Primitives** | Diálogos, menús, tabs, tooltips, popovers, selects | El estándar de facto. `@radix-ui/react-*` |
| **Base UI** | Lo mismo, del equipo de MUI | Más nuevo; API muy limpia |
| **Ark UI** | Lo mismo + más componentes de datos | React, Vue y Svelte |
| **TanStack Table** | Tablas densas y ordenables | **Imprescindible aquí** — ver §4 |
| **Floating UI** | Posicionar tooltips y popovers | La base del elemento firma — ver §7 |
| **TanStack Virtual** | Listas de miles de filas | Inventarios, logs largos |
| **cmdk** | Paleta de comandos (⌘K) | Solo si la app la pide de verdad |

**Importante:** shadcn/ui *es* Radix por debajo, con estilos encima. Si ya está
instalado no hace falta arrancarlo de raíz: puedes quedarte con los componentes y
**reescribir sus clases desde cero**, tratándolos como el Radix que son. Lo que
no funciona es dejar los estilos que vinieron y ajustar tokens.

### Lo que NO conviene instalar

Cualquier librería que se venda por cómo se ve. Si su documentación te enseña el
resultado en vez de la API, ese resultado es el que vas a tener.

---

## 2. Tipografía: alrededor del 60% del problema

Blizzard usa **Friz Quadrata** para la interfaz y **Morpheus** para títulos. Las
dos son de licencia comercial: **no las embebas**.

### Sustitutas libres, todas en Fontsource

**Display y títulos** (glífica, serifas angulosas, presencia):
- `Cinzel` — romana lapidaria, mayúsculas talladas. La apuesta más segura.
- `Marcellus` — más estrecha, elegante sin ser fría.
- `Grenze` — gótica sin llegar a blackletter. Buen punto medio.
- `Uncial Antiqua` — muy marcada. Con cuentagotas, para un título y nada más.

**Texto largo** (que aguante párrafos de verdad):
- `EB Garamond` — la más legible del grupo.
- `Vollkorn` — más robusta, buena sobre fondo oscuro.
- `Spectral` — diseñada para pantalla, más peso óptico.

**Números y datos:**
- `IBM Plex Mono` o `Roboto Mono`, siempre con `tabular-nums`.

### Instalación

```bash
npm i @fontsource-variable/cinzel @fontsource-variable/eb-garamond
```
```ts
import '@fontsource-variable/cinzel'
```

**No** por `<link>` a fonts.googleapis.com: petición a otro dominio, latencia en
el primer pintado, y dependencia de un servicio externo. §11 lo comprueba.

### El error que más delata

Usar la fuente display para todo. En el juego el **90% del texto es una glífica
legible** y el blackletter aparece contadas veces. Copia esa proporción.

---

## 3. Materiales sin imágenes de stock

Metal biselado, pergamino, piedra y esmalte **se hacen en CSS**, sin un solo PNG.

### Bisel metálico

`box-shadow` con varios `inset` que simulan luz arriba y sombra abajo. Nada de
sombra difusa exterior:

```css
.metal {
  background: linear-gradient(180deg, #6b6357 0%, #40382e 100%);
  box-shadow:
    inset 0  1px 0 rgba(255,255,255,.35),   /* filo iluminado arriba */
    inset 0 -1px 0 rgba(0,0,0,.55),         /* filo en sombra abajo */
    inset  1px 0 0 rgba(255,255,255,.12),
    inset -1px 0 0 rgba(0,0,0,.35),
    0 2px 0 rgba(0,0,0,.4);                 /* asiento, no flotación */
}
```

La sombra exterior tiene **desenfoque cero**. Un objeto de metal apoyado no
flota.

### Marcos ornamentados: `border-image`

La propiedad más olvidada de CSS, hecha exactamente para esto: un marco que se
estira sin deformar las esquinas.

```css
.frame {
  border: 18px solid transparent;
  border-image: url('/frame.svg') 18 fill round;
}
```

Mejor que nueve divs y que un `background-size: 100% 100%` que estruja los
remates. Con un SVG de 2 KB tienes un marco que funciona a cualquier tamaño.

### Grano y textura: filtro SVG, no imagen

```html
<svg width="0" height="0" style="position:absolute">
  <filter id="grain">
    <feTurbulence type="fractalNoise" baseFrequency=".8" numOctaves="3"/>
    <feColorMatrix type="saturate" values="0"/>
    <feComponentTransfer><feFuncA type="linear" slope=".08"/></feComponentTransfer>
  </filter>
</svg>
```
```css
.parchment::after {
  content: ''; position: absolute; inset: 0;
  filter: url(#grain);
  mix-blend-mode: multiply;
  pointer-events: none;
}
```

Pesa bytes, no kilobytes, y escala a cualquier resolución.

### Vitral y esmalte

`conic-gradient` con `mix-blend-mode: color-dodge` da luz atravesando cristal sin
ninguna imagen.

### La advertencia que va con todo esto

**Mejor un borde de 2px bien resuelto que un pergamino a pantalla completa** que
hace ilegible el texto encima. Elige **uno o dos** materiales y sé consistente.
Diez materiales a la vez es un mercadillo, no un mundo.

---

## 4. Densidad: la parte que se falla siempre

Los jugadores de WoW vienen de ElvUI, WeakAuras y Details!. Están entrenados para
leer interfaces **densas**. No les da miedo la información: les da miedo que se
la escondas.

Un layout "limpio y minimalista" con mucho aire y tres datos por pantalla se
percibe aquí como **una app que no sabe lo que estás haciendo**.

Este es además el error por defecto de cualquier modelo cuando le piden "que se
vea mejor": más aire, menos datos, todo más limpio. Aquí eso es exactamente el
fallo.

### Herramientas

- **TanStack Table** para todo lo tabular. Headless: ordenación, filtrado,
  agrupado y paginación resueltos; el marcado lo escribes tú.
- **TanStack Virtual** si hay miles de filas.

### Reglas no negociables

- `font-variant-numeric: tabular-nums` en **toda** columna de cifras. Sin eso los
  números cambian de ancho al cambiar de valor, la tabla baila y comparar se
  vuelve imposible — que es lo único que se hace en una tabla.
- Números **alineados a la derecha**. Siempre.
- Filtros de verdad, no solo un buscador.
- Las abreviaturas que la comunidad ya usa: ilvl, M+, HPS, DPS, BiS, parse,
  pull, wipe. Traducirlas suena a manual de instrucciones.
- Toda tabla dentro de un contenedor con `overflow-x: auto`. **Si empuja el
  `body`, la app entera se desplaza de lado en móvil.**

La legibilidad en una tabla densa se consigue con jerarquía, alineación y
espaciado consistente. No quitando información.

---

## 5. Datos e iconos canónicos, nunca inventados

Esto separa "parece WoW" de "parece fantasía genérica".

| Fuente | Qué da | Auth |
|---|---|---|
| **Blizzard API** (`develop.battle.net`) | Colores de clase, calidades, iconos de hechizo y objeto **oficiales**, datos de reinos | OAuth, gratis |
| **Raider.IO API** | Puntuación y rankings de M+ | Ninguna |
| **WarcraftLogs API v2** | Parses, rankings, logs de raid | OAuth (GraphQL) |
| **Wowhead** | Consultar | **No hotlinkear iconos** — revisa sus términos |

### Los colores son vocabulario, no decoración

Un jugador los lee **más rápido que el texto**. Calidades de objeto, exactas:

```
Pobre        #9d9d9d      Épico        #a335ee
Común        #ffffff      Legendario   #ff8000
Poco común   #1eff00      Artefacto    #e6cc80
Raro         #0070dd      Reliquia     #00ccff
```

**Nunca los reinterpretes.** Si tu paleta choca con ellos, la que cambia es tu
paleta. §11 comprueba que estén exactos.

Sobre contraste: el morado `#a335ee` sobre negro está al límite de legibilidad y
el azul `#0070dd` claramente por debajo de AA para texto pequeño. La solución no
es cambiar el color: es darle tamaño, peso o un fondo que lo levante. Nunca esos
colores en 12px sobre negro puro.

---

## 6. Movimiento

**Motion** (antes Framer Motion) es la herramienta. Lo que importa es el criterio.

En WoW **nada** hace `ease-in-out` de 400ms:

- Los cooldowns son **lineales**. Siempre.
- Las barras de casteo son **lineales**.
- Los tooltips aparecen **sin transición**.
- Los números de daño flotan rápido y se van.

Copia esa sequedad: es parte del carácter. Una animación suave y elástica se lee
como un componente de otra app pegado con cinta.

`prefers-reduced-motion` respetado siempre, sin excepciones decorativas.

---

## 7. El elemento firma, con herramientas

Elige **una sola cosa** memorable y hazla muy bien. La candidata más fuerte:

### El tooltip

Es el objeto más icónico del juego, más que cualquier botón. Fondo casi negro,
borde fino, título en el color de la calidad, líneas de estadística alineadas,
texto verde para efectos de uso, precio en oro/plata/cobre al final.

- **Floating UI** (`@floating-ui/react`) para posicionarlo: colisiones con los
  bordes, `flip`, `shift`, seguimiento del cursor.
- Radix Tooltip **solo** si necesitas su comportamiento de accesibilidad; para un
  tooltip de datos al hover, Floating UI directo da más control.
- Iconos de la Blizzard API. Números con `tabular-nums`.

**Si la app muestra objetos, el tooltip merece hacerse exacto, no aproximado.**

### Regla de comprobación

Si le quitas el elemento firma y la app sigue viéndose igual de bien, es que no
era el elemento firma.

---

## 8. Piso de calidad, da igual la estética

- Responsive hasta **390px** sin desbordamiento horizontal.
- **Foco de teclado visible** en todo control: `outline` de 3px con `offset`.
  Nunca `outline: none` sin sustituto.
- **`prefers-reduced-motion`** respetado.
- **Contraste AA** en texto. Los colores de calidad son la excepción que se
  gestiona con tamaño y peso, no ignorándola.
- **Objetivos táctiles de 44px** mínimo.
- **Números tabulares** en toda columna de cifras.
- Texto que viene de fuera (nombres de personaje, de guild, títulos de objeto)
  puede ser larguísimo o una sola palabra sin espacios: `overflow-wrap: anywhere`
  en los títulos.

---

## 9. Sobre la autocrítica visual, sin humano al otro lado

El paso "mira la captura y juzga si se ve genérica" no se puede automatizar del
todo. Lo que sí se puede, y es casi todo el valor:

1. **Tomar las capturas y mirarlas de verdad** (390 / 768 / 1440). Si tienes
   capacidad de visión, evalúa la imagen contra el plan escrito, no contra tu
   recuerdo de lo que programaste. Escribe qué ves, en frases concretas: "los
   botones tienen esquinas redondeadas de 8px y sombra difusa" es útil; "se ve
   bien" no.
2. **Comparar contra los cinco defaults** de `UI-WARCRAFT.md` §0, uno por uno, y
   anotar el veredicto de cada uno.
3. **Convertir en prueba todo lo que sea convertible** — §11.

Lo que queda de juicio irreductible es poco, y para eso está la pregunta final:
si le quitas los textos a una pantalla y podría ser un dashboard de finanzas, no
tiene dirección todavía. Contéstala tú mismo, por escrito, y si la respuesta es
"sí podría", vuelve al plan. No hace falta que nadie te lo confirme.

---

## 10. Verificar mirando el píxel

- **Playwright** con capturas a **390 / 768 / 1440**.
- Comprobar que la página **no se pueda desplazar a lo ancho**:
  ```js
  document.documentElement.scrollWidth - document.documentElement.clientWidth
  ```
  Debe ser 0. Las tablas densas son el sospechoso habitual.
- Probar con un **nombre hostil de verdad**: un nombre de guild larguísimo y sin
  espacios, no "Nombre de ejemplo".
- **axe-core** (`@axe-core/playwright`) para contraste y foco.

---

## 11. El detector de aspecto genérico

Esta es la pieza que hace que todo lo anterior no dependa de que alguien opine.
Escribe `scripts/check-ui.mjs`, cablea `npm run check:ui`, y **déjalo en verde**.

### Qué busca, y por qué esos números

Cada regla es una huella medible del aspecto por defecto:

| Regla | Umbral | Por qué |
|---|---|---|
| Dependencias con estética propia | ninguna, o justificada | §0 |
| `border-radius` | nada entre 3px y 14px | El rango exacto del look de plantilla. WoW es de esquina dura o bisel marcado |
| `box-shadow` difusa | ningún blur ≥ 4px con alpha ≤ .25 | La sombra suave genérica. Los biseles van con `inset` y blur 0 |
| `ease-in-out` y `cubic-bezier(.4,0,.2,1)` | ninguno | El easing de Material. En WoW el movimiento es lineal |
| Colores de calidad | los 8, exactos | Son vocabulario. Aproximarlos se nota |
| `tabular-nums` | presente si hay tablas | Sin él las cifras bailan |
| `fonts.googleapis.com` | ninguna referencia | Fuentes locales |
| `outline: none` | ninguno sin sustituto | Foco visible |

### El script

```js
#!/usr/bin/env node
/**
 * Detector de aspecto genérico.
 *
 * No comprueba que la interfaz sea bonita — eso no es comprobable. Comprueba
 * que no tenga las huellas MEDIBLES del aspecto por defecto de las librerías
 * de componentes, que es de lo que se queja la gente cuando dice que algo
 * "parece hecho por una IA".
 *
 * Cada hallazgo se puede silenciar añadiéndolo a ALLOW con un motivo escrito.
 * Un silenciado sin motivo es deuda; con motivo es una decisión.
 */
import { readdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'

const SRC = 'src'

/** Librerías que traen apariencia de fábrica (§0). */
const OPINIONATED = [
  '@mui/material', '@chakra-ui/react', 'antd', '@mantine/core',
  'react-bootstrap', 'semantic-ui-react', '@nextui-org/react', 'primereact',
]

/** Los ocho colores de calidad, exactos (§5). */
const QUALITY = [
  '#9d9d9d', '#ffffff', '#1eff00', '#0070dd',
  '#a335ee', '#ff8000', '#e6cc80', '#00ccff',
]

/**
 * Hallazgos aceptados, CON MOTIVO. Sin motivo escrito no se acepta nada: la
 * lista existe para registrar decisiones, no para callar la prueba.
 */
const ALLOW = [
  // { file: 'src/x.css', rule: 'radius', why: '…' },
]

let fallos = 0
const fallo = (file, rule, msg) => {
  if (ALLOW.some((a) => a.file === file && a.rule === rule && a.why)) return
  fallos++
  console.log(` FALLO  [${rule}] ${file}: ${msg}`)
}

function files(dir) {
  const out = []
  for (const e of readdirSync(dir, { withFileTypes: true })) {
    const full = join(dir, e.name)
    if (e.isDirectory()) out.push(...files(full))
    else if (/\.(tsx?|jsx?|css)$/.test(e.name)) out.push(full)
  }
  return out
}

// ── 1. Dependencias ────────────────────────────────────────────────────────
const pkg = JSON.parse(readFileSync('package.json', 'utf8'))
const deps = { ...pkg.dependencies, ...pkg.devDependencies }
for (const dep of OPINIONATED) {
  if (dep in deps) {
    fallo('package.json', 'dep', `${dep} trae apariencia de fábrica (§0)`)
  }
}

// ── 2. Huellas del aspecto por defecto ─────────────────────────────────────
const all = files(SRC)
for (const file of all) {
  const src = readFileSync(file, 'utf8')

  // border-radius de plantilla: 3–14px, o su equivalente en rem.
  for (const m of src.matchAll(/border-radius:\s*([\d.]+)(px|rem)/g)) {
    const px = m[2] === 'rem' ? Number(m[1]) * 16 : Number(m[1])
    if (px >= 3 && px <= 14) fallo(file, 'radius', `border-radius ${m[1]}${m[2]} (§0)`)
  }
  // Tailwind: rounded-md/lg/xl son ese mismo rango.
  for (const m of src.matchAll(/\brounded-(sm|md|lg|xl)\b/g)) {
    fallo(file, 'radius', `clase ${m[0]} (§0)`)
  }

  // Sombra difusa y suave: el gesto genérico por excelencia.
  for (const m of src.matchAll(
    /box-shadow:[^;]*?(\d+)px\s+(\d+)px\s+rgba\(0,\s*0,\s*0,\s*(0?\.\d+)\)/g,
  )) {
    if (Number(m[2]) >= 4 && Number(m[3]) <= 0.25) {
      fallo(file, 'shadow', `sombra difusa blur ${m[2]}px alpha ${m[3]} (§3)`)
    }
  }
  for (const m of src.matchAll(/\bshadow-(sm|md|lg|xl|2xl)\b/g)) {
    fallo(file, 'shadow', `clase ${m[0]} — usa inset con blur 0 (§3)`)
  }

  // Easing de Material. En WoW el movimiento es lineal (§6).
  if (/ease-in-out|cubic-bezier\(\s*\.?0?\.4,\s*0,\s*0?\.2,\s*1\s*\)/.test(src)) {
    fallo(file, 'easing', 'easing genérico — en WoW es lineal (§6)')
  }

  // Foco sin sustituto.
  for (const m of src.matchAll(/outline:\s*none/g)) {
    if (!/outline(-offset)?:/.test(src.slice(m.index, m.index + 400))) {
      fallo(file, 'focus', 'outline: none sin sustituto (§8)')
    }
  }

  // Fuentes remotas.
  if (src.includes('fonts.googleapis.com')) {
    fallo(file, 'fonts', 'fuente por CDN — usa Fontsource (§2)')
  }
}

// ── 3. Datos canónicos ─────────────────────────────────────────────────────
const joined = all.map((f) => readFileSync(f, 'utf8')).join('\n').toLowerCase()
const missing = QUALITY.filter((hex) => !joined.includes(hex))
if (missing.length > 0) {
  fallo('src', 'quality', `faltan colores de calidad exactos: ${missing.join(' ')} (§5)`)
}

// ── 4. Densidad ────────────────────────────────────────────────────────────
if (/<t(able|body)\b|<thead\b/.test(joined) && !joined.includes('tabular-nums')) {
  fallo('src', 'density', 'hay tablas y ningún tabular-nums (§4)')
}

console.log(`\n${all.length} archivos · ${fallos} fallos`)
if (fallos > 0) process.exit(1)
```

### La disciplina que hace que esto sirva

Dos cosas, aprendidas de detectores que acabaron ignorados:

1. **Verifica que la prueba detecta.** Antes de confiar en ella, planta el fallo
   a propósito: pon un `rounded-lg` en un botón y comprueba que se pone roja.
   Una prueba que nunca se ha visto fallar no es una prueba, es decoración.
2. **Cuida los falsos positivos.** Si grita en cada archivo, se acaba ignorando y
   entonces no protege de nada. Cuando algo salte y sea legítimo, va a `ALLOW`
   **con el motivo escrito**. Un silenciado sin motivo es deuda.

---

## 12. La prueba del algodón

Quítale los textos a una pantalla y mírala.

**Si podría ser un dashboard de finanzas, no tiene dirección todavía — tiene
tema.**

Y antes de darlo por bueno: pregúntate qué le quitarías. Casi siempre sobra algo.

---

## Lista de verificación

Todo lo marcado con ⚙ lo comprueba `npm run check:ui`.

- [ ] ⚙ Ninguna dependencia de UI trae apariencia de fábrica.
- [ ] ⚙ Ningún `border-radius` entre 3 y 14px, ninguna clase `rounded-md/lg/xl`.
- [ ] ⚙ Ninguna sombra difusa suave; los biseles son `inset` con blur 0.
- [ ] ⚙ Ningún `ease-in-out`.
- [ ] ⚙ Los ocho colores de calidad, exactos.
- [ ] ⚙ `tabular-nums` presente si hay tablas.
- [ ] ⚙ Ninguna fuente por CDN.
- [ ] ⚙ Ningún `outline: none` sin sustituto.
- [ ] ⚙ El propio detector se probó plantando un fallo y se puso rojo.
- [ ] La display se usa en títulos, no en todo.
- [ ] Hay uno o dos materiales, no diez.
- [ ] Los números van alineados a la derecha.
- [ ] Las tablas tienen su propio scroll y no empujan el `body` (Playwright).
- [ ] Capturas a 390 / 768 / 1440, tomadas y descritas por escrito.
- [ ] Probado con un nombre hostil larguísimo y sin espacios.
- [ ] axe-core sin violaciones de contraste ni de foco.
- [ ] Hay UN elemento firma, y quitarlo se nota.
- [ ] Contestada por escrito: sin textos, ¿parecería una app de finanzas?
