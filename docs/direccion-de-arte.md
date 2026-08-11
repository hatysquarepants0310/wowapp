# Dirección de arte

Este documento es el paso 1 del método de `UIWARCRAFT.md`: brainstorm, plan y
autocrítica **antes** de escribir código. Se guarda en el repo porque la
siguiente persona que toque la interfaz necesita saber por qué está así.

## 0. Autocrítica del diseño anterior (v3.0.0)

Empiezo por aquí porque el resultado es incómodo: el rediseño de v3.0.0 era,
casi literalmente, el default número 1 de la lista de defaults a evitar.

> "Fondo casi negro + un acento morado o azul degradado + tarjetas con
> `border-radius: 12px` y `box-shadow` difusa."

Lo que había: fondo `#0D0A07`, acento morado `#A98BFF`, paneles con
`RoundedCornerShape(14.dp)` y un `HeroPanel` con degradado lineal. Los tres
ingredientes, exactos.

Y el segundo fallo es más de fondo. El plan de v3.0.0 decía "limpio, con aire,
jerarquía por espacio". Eso choca de frente con §2.4 del documento: los
jugadores de WoW vienen de ElvUI, WeakAuras y Details!. Están entrenados para
leer interfaces **densas**. Tres cifras enormes rodeadas de aire no se leen como
elegante: se leen como una app que no sabe lo que estás haciendo.

Prueba del algodón de §1.3: si a la pantalla de Inicio de v3.0.0 le cambio los
textos, **es un dashboard de finanzas**. Cuenta atrás grande, tres KPI, barra de
progreso dorada. No tenía dirección; tenía buen gusto genérico.

## 1. Brainstorm

### Colores

Nombres propios, no "primary/secondary". La referencia no es "fantasía": es la
interfaz del propio juego, que es oscura, metálica y con pergamino.

| Nombre | Hex | Para qué |
|---|---|---|
| `tinta` | `#0A0B0F` | Fondo. Negro azulado, el del tooltip del juego, no negro cálido |
| `piedra` | `#14161D` | Superficie de panel |
| `piedra-alta` | `#1D212B` | Panel sobre panel, filas alternas de tabla |
| `bisel` | `#3A3428` | Borde metálico apagado; el trim de la interfaz del juego |
| `oro-tabardo` | `#C8A44D` | Trim vivo, títulos de sección, lo que el juego doraría |
| `pergamino` | `#E8DCC0` | Texto principal. Hueso cálido, no blanco puro |
| `pergamino-medio` | `#9A917E` | Texto secundario |
| `runa` | `#6E9FD4` | Único acento frío, para lo interactivo. Azul de runa, no morado |

El morado sale de la paleta a propósito: era el acento genérico y además compite
con el `#a335ee` de calidad épica, que es intocable.

**Los colores de calidad y de clase quedan exactamente como los define el juego.**
Si algo choca con ellos, cambia mi paleta.

### Tipografía

Una sola familia con tres papeles, sin blackletter (§2.2: en el juego aparece
con cuentagotas):

- **Títulos**: peso alto, tracking negativo. Papel de "nombre propio".
- **Cuerpo**: regular, interlineado cómodo. Aguanta párrafos de noticias.
- **Cifras**: `tabular-nums` **siempre**. Sin eso, una columna de ilvl o de oro
  baila y la tabla deja de ser leíble (§4).

### Concepto de layout, en una frase

> Un panel de addon: denso, tabular, con trim metálico y el tooltip del juego
> como unidad de contenido.

```
┌──────────────────────────────────────┐
│ ▸ AHORA            Asalto del Vacío  │  ← franja de estado, alta, densa
│   Tormenta del Vacío      02h 14m    │
├──────────────────────────────────────┤
│ ESTA SEMANA                          │  ← etiqueta con filete dorado
│ Jefes    3   Llaves   8   Abismos 4  │  ← tabla, no tarjetas
│ ──────────────────────────────────── │
│ ▸ Se busca surcabismos: El Golfo…  ✓ │
│ ▸ Se busca surcabismos: Atal'Aman     │
└──────────────────────────────────────┘
```

### Elemento firma: el tooltip

El objeto más icónico del juego (§2.3) y el que más encaja aquí, porque media
app son objetos, misiones y recompensas. Se replica de verdad: fondo casi negro,
**borde de 1px en oro apagado**, título en el color de la calidad, líneas de
estadística alineadas, y el precio en oro/plata/cobre al final con sus colores.

Prueba de §3: si le quito el tooltip a la app, la app cambia. Es el contenedor
de botín, de objetos de subasta y de detalle de misión.

## 2. Plan

1. **Paleta y tipografía nuevas** en `Theme.kt`, con los nombres de arriba y
   `tabular-nums` en los estilos de cifra.
2. **`Tooltip.kt`**: el componente firma. Borde dorado fino, cabecera con color
   de calidad, filas de estadística, pie de precio.
3. **Densificar `Design.kt`**: el `gutter` baja de 20dp a 14dp, el espaciado
   vertical entre bloques de 12dp a 8dp, y aparece `DataRow` — una fila tabular
   con etiqueta a la izquierda y cifra alineada a la derecha — que sustituye a
   las tarjetas de métrica.
4. **`SectionHeader` con filete**: la etiqueta en versalita gana una línea
   dorada, que es como el juego separa secciones.
5. **Inicio**: la franja de estado sustituye al `HeroPanel` con degradado. Las
   tres cifras de la semana pasan a una tabla de una línea.

## 3. Autocrítica del plan nuevo

Contra los cinco defaults:

1. *Negro + morado + tarjetas redondeadas*: fondo azulado en vez de cálido,
   acento azul-runa en vez de morado, radios bajados a 4–6dp y **borde en vez
   de sombra**. El degradado desaparece de toda la app.
2. *Crema editorial con serif*: no aplica, el fondo es oscuro.
3. *Glassmorphism*: cero `blur`, cero translúcidos.
4. *Fantasía genérica*: no hay dragones, ni pergamino a pantalla completa, ni
   blackletter. El único material es metal oscuro con trim dorado, que es el de
   la interfaz real (§2.6: uno o dos materiales, no diez).
5. *Dashboard de SaaS con skin*: es el que más me preocupaba. La defensa es la
   densidad: filas tabulares en vez de KPI cards, cifras alineadas, y el
   tooltip como unidad. Si le cambio los textos ahora, **no** es una app de
   finanzas: ninguna app de finanzas pone un borde dorado y colorea el título
   por rareza.

Qué quito (§1.5 pide quitar algo): el `HeroPanel` con degradado entero, y las
tres `Metric` gigantes de Inicio, que ocupaban media pantalla para tres números.

## 4. Lo que NO pude verificar

El documento pide capturas reales a 390 / 768 / 1440 y mirarlas (§1.5, §6).
**No lo hice**: este entorno no tiene emulador ni dispositivo, así que no puedo
renderizar la app. Lo verificado es que compila, que pasa las pruebas y que los
contrastes están calculados sobre el papel. Las capturas siguen pendientes y
quien tenga un dispositivo delante debería hacerlas antes de dar la UI por
buena.

Del resto de la lista de §6: los colores de calidad y clase son los canónicos,
hay un elemento firma, se probó con nombres largos sin espacios, y las cifras
van en tabulares.
