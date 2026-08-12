# Decisiones de interfaz — v4.2

> Este documento es el registro del cuarto intento de arreglar la interfaz. Los
> tres anteriores cambiaron colores y formas, y el usuario siguió diciendo lo
> mismo: *"no queda fea, queda GENÉRICA"*. Así que aquí se escribe también lo que
> se hizo mal antes, porque es la mitad del diagnóstico.

---

## 1. Verificar el diagnóstico antes de creérselo

`docs/UI-WARCRAFT.md` sostiene que la causa del aspecto genérico son las
librerías de componentes con estética propia. El encargo era explícito: *"es una
hipótesis, no un hecho: quien escribió esos documentos no ha visto este repo.
VERIFÍCALA."*

Lo primero que hubo que resolver es que el detector de §11 está escrito para web
—busca `border-radius` en CSS, clases `rounded-lg` y dependencias `@mui/material`—
y esto es Android con Compose. Copiarlo tal cual habría dado cero hallazgos y una
falsa tranquilidad. Se tradujo regla por regla:

| web | Compose |
|---|---|
| `@mui/material`, shadcn/ui | `androidx.compose.material3` (`Card`, `Button`, `NavigationBar`, `TopAppBar`, `OutlinedTextField`…) |
| `border-radius: 8px` | `RoundedCornerShape(8.dp)` |
| `rounded-md` / `rounded-lg` | los tokens de `object Radius` |
| `box-shadow` difusa | `Modifier.shadow(...)`, `elevation = N.dp` |
| `ease-in-out`, `cubic-bezier(.4,0,.2,1)` | `FastOutSlowInEasing`, `CubicBezierEasing(.4f,0f,.2f,1f)` |
| `tabular-nums` | `fontFeatureSettings = "tnum"` |
| `outline: none` | `indication = null` sin sustituto |
| fuente por CDN | `fonts.googleapis.com` (igual) |

**Resultado de la primera pasada: 61 hallazgos.** La hipótesis era correcta, y en
esta plataforma toma una forma que ninguno de los tres rediseños anteriores había
tocado.

### Plantar fallos a propósito

*"Una prueba que nunca se ha visto fallar no es una prueba."* Se plantaron fallos
tres veces, y las tres primeras encontraron un agujero **en el propio detector**:

1. Un `RoundedCornerShape(10.dp)` plantado en un token con nombre distinto de
   `sm`/`md`/`lg` no se detectaba: la expresión regular solo miraba esos tres
   nombres. Arreglado leyendo el bloque `object Radius { }` entero.
2. La entrada de `ALLOW` que tenía puesta silenciaba **todos** los hallazgos de
   radio de ese archivo, incluido el plantado. Es exactamente el fallo del que
   avisa el documento. Se añadió un campo `match` para que un silenciado tape un
   hallazgo y no una categoría, y se vació `ALLOW` — la entrada además sobraba.
3. El detector marcaba `WowButton(` como si fuera el `Button(` de Material,
   porque comparaba con `includes()`. Un detector que no distingue mi botón del
   de Google no sirve para decidir nada. Con límite de palabra, 17 de los 82
   hallazgos de aquel momento resultaron ser falsos positivos de mis propios
   envoltorios.

**`ALLOW` está vacío. Ningún hallazgo se ha silenciado.**

---

## 2. Autocrítica: los cinco defectos, uno por uno

El documento lista cinco aspectos por defecto. Juzgando el diseño que había
(v4.1, el tercer intento):

**① Oscuro con acento morado, tarjetas redondeadas, sombra difusa.**
Culpable en v3.0.0, y ya lo dejé escrito entonces. En v4.1 el morado había
desaparecido, pero **las tarjetas de 12dp y la sombra seguían ahí**. Medio
culpable.

**② "Limpio y minimalista con mucho aire".**
Culpable, y este es el que más daño hizo. En v4.1 escribí en `Theme.kt` que "el
chrome se calla y el arte del juego pone la identidad". Suena sensato y es una
excusa: un chrome que se calla del todo es un chrome anónimo. Corregí en exceso
tras el fracaso de la filigrana dorada y aterricé justo en el defecto que el
documento pone en segundo lugar.

**③ Componentes de librería sin vestir.**
Culpable sin atenuantes. `Card`, `Button`, `OutlinedTextField`, `Switch`,
`NavigationBar`, `TabRow`, `FilterChip`, `AlertDialog` en veinte pantallas.

**④ Tipografía de sistema.**
Culpable, y **este es el hallazgo que justifica todo el ejercicio**. La app
escribía enteramente en **Roboto**. Ni una `FontFamily` declarada en el proyecto.
Tres rediseños completos repintando colores encima de la fuente de fábrica de
Android. Es el delator que ninguno de los tres miró, porque no se piensa en la
tipografía como "una decisión" cuando nunca se ha tomado.

**⑤ Movimiento genérico.**
Parcialmente culpable: no había `ease-in-out` explícito, pero todos los
componentes de Material traen el suyo dentro.

### Lo que esta autocrítica cambió del plan

El plan inicial era "reescribir los componentes". Después de ⑤ y sobre todo de
④, el orden se invirtió: **primero la tipografía**, porque es la capa que estaba
por debajo de todas las decisiones anteriores y la que las anulaba.

---

## 3. Decisiones

### Arrancar la librería o reescribir las clases

**Decisión: quedarse con las primitivas sin apariencia y tirar los componentes
vestidos.** No es lo mismo `androidx.compose.material3` que sus componentes.
`Box`, `Row`, `Column` y `Modifier.clickable` no aportan apariencia ninguna; son
el equivalente de un `div`. `Card` y `Button` sí. Se conserva también
`MaterialTheme` como portador de tokens —con mi paleta y mi tipografía— porque
quitarlo obligaría a reescribir cada `Text` de la app sin ganar nada visible.

`Text` e `Icon` se quedan: el primero no tiene aspecto propio más allá del estilo
que se le pasa, y el segundo pinta un vector con un tinte.

### El material: uno solo

**Metal biselado.** El documento pide uno o dos materiales, no diez.

En Compose no existe el `inset` de `box-shadow`, así que se dibuja: degradado
vertical corto, filo claro arriba y a la izquierda, filo oscuro abajo y a la
derecha, y una línea sólida de desenfoque **cero** debajo. Un objeto apoyado en
una mesa no tiene halo; la sombra suave y difusa es el gesto genérico por
excelencia y viene de fábrica en todas las librerías.

Se descartó el **pergamino** —que era la opción obvia— porque esta app es una
consola de datos: tablas, cifras, listas largas. El pergamino a pantalla completa
hace ilegible el texto encima, y ya se intentó en v3.2.0 con ese resultado. El
pergamino aparece solo en los mapas, que son arte del propio juego.

Se descartó la **piedra** por redundante con el metal a este tamaño.

### El elemento firma: el tooltip, exacto

El documento pide un elemento reconocible y pide que sea **exacto, no
aproximado**. La versión anterior de `Tooltip.kt` hacía lo contrario y lo dejaba
escrito en su propio comentario: *"antes era una réplica del tooltip del juego…
se quita la filigrana"*.

Quitarla fue el error. Un jugador ha mirado el tooltip de objeto cientos de miles
de veces; es la pieza de interfaz que mejor conoce del mundo. Cuando la ve **casi
bien** no piensa "qué limpio", piensa "esto no lo ha hecho un jugador".
Aproximarse es peor que no intentarlo.

Ahora lleva, en orden: marco del color de la calidad, fondo negro casi opaco,
nombre en el color de la calidad, nivel de objeto en dorado, **la línea de dos
columnas** —ranura a la izquierda, tipo de armadura a la derecha, a la misma
altura—, estadísticas tabulares y el precio en oro/plata/cobre con sus tres
colores. La línea de dos columnas es el detalle que nadie copia y el que más
identifica: fuera de WoW no existe.

El marco es **una sola capa de dibujo** (`drawBehind` con seis `drawRect`), no
nueve cajas anidadas, que es lo que el documento prohíbe.

### Tipografía

Friz Quadrata y Morpheus son de licencia comercial y no se embeben. Las
sustitutas libres, empaquetadas en el APK (338KB, nunca por CDN):

- **Cinzel** — romana lapidaria. **Solo títulos y nombres propios.**
- **EB Garamond** — glífica legible, el 90% del texto. Es la proporción del juego.
- **IBM Plex Mono** — cifras. Ancho fijo de verdad, no `tnum` sobre una
  proporcional.

El error que más delataría sería usar la display para todo. En el juego la
lapidaria aparece con cuentagotas; aquí igual.

---

## 4. La prueba del algodón

> *Tapa el texto de una pantalla. ¿Podría ser el panel de una fintech?*

Respondiendo con las capturas delante, y por partes, porque la respuesta no es la
misma en toda la pantalla:

**La franja superior — sí, todavía podría serlo.** Una chapa con una cifra grande
("678") y una etiqueta encima es un patrón de panel de datos y punto. Lo único
que la aparta es la textura del metal y la esquina viva; sin el texto, un
diseñador de fintech podría haberla firmado.

**El bloque de filas de datos — sí, casi del todo.** "Etiqueta a la izquierda,
cifra tabular a la derecha, separador fino" es exactamente una tabla financiera.
Y está bien que lo sea: es la forma correcta de presentar cifras comparables, y
cambiarla por algo más "temático" empeoraría la lectura. Aquí no hay nada que
arreglar.

**El tooltip — no, de ninguna manera.** Tapado el texto quedan: un marco de dos
píxeles en morado saturado, un rectángulo negro casi opaco, un icono cuadrado y
una retícula de líneas cortas con dos columnas. Ninguna fintech pone un borde
morado alrededor de una ficha, porque en una fintech el color no codifica nada.
Aquí el marco **es** el dato.

**Las pestañas y la barra inferior — no.** La pestaña activa está *levantada* y
las inactivas *hundidas*, con un filo de acento debajo. El patrón genérico es el
contrario: todas al mismo nivel y un subrayado que se desliza.

**Veredicto: aprueba, pero por el tooltip, no por el conjunto.** La mitad
superior de una pantalla típica sigue siendo neutra. Es una decisión, no un
descuido —una consola de datos tiene que leerse—, pero conviene ser honesto sobre
dónde está el carácter: está concentrado en el elemento firma, en el material y
en la tipografía, no repartido por igual.

**Qué se iteró a raíz de esto:** el `HeroPanel` era el peor caso —un degradado
diagonal de acento a superficie, o sea "el hero con gradiente" del catálogo de
defectos—. Ahora es la misma chapa que el resto, distinguida por una regla de
acento de 2dp arriba, que es como el juego marca el marco activo. Que algo sea
importante se dice con jerarquía, no con degradado.

---

## 5. Qué se quitó

> *Pregunta qué sobra, y quítalo.*

- **La tipografía duplicada.** `Theme.kt` declaraba su propia `Typography` sin
  `fontFamily`. Era la causa de que todo se pintara en Roboto.
- **Los degradados**, salvo el vertical de 3 paradas del metal. El del `HeroPanel`
  era el defecto del catálogo.
- **La píldora.** Radio 999 en etiquetas y barras de progreso: es el chip de
  Material y no existe en el juego. Sobrevive solo para el retrato y los puntos
  de estado, que sí son círculos.
- **Las esquinas redondeadas de los iconos de objeto.** Un icono de objeto es un
  cuadrado con borde de calidad. Redondearlo es el gesto "avatar de app".
- **El interruptor deslizante entero.** No existe en WoW; las opciones del juego
  son casillas de verificación. Además el que había no se entendía.
- **El giro suave del indicador de carga**, sustituido por cuatro casillas que se
  encienden por turnos.
- **`OutlinedTextField` con su etiqueta flotante**, que es una de las señas de
  Material más reconocibles.
- **17 hallazgos del detector** que eran falsos positivos míos.

---

## 6. Verificación

| Comprobación | Antes | Ahora |
|---|---|---|
| `npm run check:ui` | **61 fallos** | **0 fallos**, `ALLOW` vacío |
| Tipografía propia | ninguna (Roboto) | 3 familias, 338KB en el APK |
| Componentes de Material vestidos | 20 pantallas | 0 |
| Desbordamiento lateral a 390/768/1440 | sin medir | 0 px en los tres |
| Contraste de las 13 clases | sin medir | ≥3:1 como marca, ≥4,5:1 como texto |
| Objetivos táctiles | sin medir | ≥44dp, con la excepción del chip escrita |
| Capturas | ninguna | 3 PNG renderados y mirados |

Las capturas se generan con `./gradlew :app:testDebugUnitTest --tests
"*ScreenshotTest*"` y salen en `app/build/capturas/`. Playwright no aplica —esto
es Android, sin navegador ni emulador—; el equivalente es Robolectric en modo
gráfico nativo, que dibuja con el mismo Skia que el dispositivo.

### Lo que no quedó en verde

**Raro (4,21:1) y Épico (4,15:1)** sobre el negro del tooltip se quedan por
debajo del 4,5 que pide WCAG para texto normal. **No se corrige a propósito**: es
exactamente lo que muestra el juego, con el mismo color sobre el mismo fondo, y
ese elemento existe precisamente para ser idéntico. Además el nombre va rodeado
por el marco del mismo color. Queda anotado como excepción consciente en
`ContrastTest.kt`.

**El chip mide 34dp, no 44.** En una fila de filtros caben cinco o seis y a 44dp
se comen media pantalla en un móvil. Lleva separación para que las áreas no se
solapen. Excepción declarada y medida.

---

## 7. Lo que quizá no compartas

Cinco decisiones que tomé solo y con las que es razonable no estar de acuerdo:

1. **La app se ciñe a 560dp y se centra en pantallas anchas.** En una tablet
   quedan dos franjas de fondo vacío a los lados. Lo hice porque a 1440 una fila
   dejaba la etiqueta y su cifra a dos mil píxeles de distancia, y porque los
   marcos del juego tampoco se estiran. Pero es la decisión más discutible del
   lote: se puede preferir aprovechar el ancho con dos columnas, y sería
   defendible. No lo hice porque obligaba a rediseñar las veinte pantallas.

2. **El tooltip vuelve a tener marco de colores.** Es lo contrario de lo que
   decidí en v4.1, donde lo quité por "recargado". Si el rechazo de v3.2.0 era a
   los marcos en general y no solo al oro, esto va en dirección contraria a tu
   crítica. Lo defiendo porque aquí el marco codifica la calidad —es información,
   no adorno— y porque está en **un** elemento, no en toda la pantalla.

3. **Los botones van en versalitas.** Da un aire de placa de interfaz de juego,
   pero encoge el texto y en alemán o en ruso los rótulos largos quedan justos.

4. **Se bajó toda la paleta de grises.** Salió de la medición de contraste, pero
   el efecto secundario es que la app está bastante más oscura que antes. Si te
   parecía bien de brillo, esto la aleja.

5. **El interruptor pasó a ser casilla.** Cambia un gesto que la gente ya conoce
   de otras apps de Android por uno más fiel al juego. Gana en claridad y en
   fidelidad; pierde en familiaridad con el resto del móvil.
