# Dirección de arte para una app de World of Warcraft

> Para el agente que construye la interfaz. Léelo entero antes de escribir CSS.
> No es una guía de estilo con reglas sueltas: es un método, más el contexto
> visual concreto de Warcraft que un modelo no tiene por defecto.

---

## 0. El problema real que resuelve este documento

Cuando a un modelo se le pide "una UI bonita" sin dirección, produce siempre lo
mismo, y la gente lo reconoce al instante. No es que quede feo: es que queda
**genérico**, y en un producto de nicho lo genérico se lee como "esto lo hizo
alguien que no juega".

Los tres defaults que hay que evitar de forma explícita, porque van a salir
solos si no los bloqueas:

1. **Fondo casi negro + un acento morado o azul degradado + tarjetas con
   `border-radius: 12px` y `box-shadow` difusa.** Es el look de plantilla de
   dashboard. Aparece en el 80% de lo que genera un modelo.
2. **Fondo crema + serif de alto contraste + acento terracota.** El look
   "editorial elegante" que no tiene nada que ver con Azeroth.
3. **Glassmorphism**: paneles translúcidos con `backdrop-filter: blur()` y
   bordes de 1px blancos al 10%. Se ve moderno durante seis meses y luego
   fecha el producto entero.

Y dos específicos de este dominio, igual de delatores:

4. **"Fantasía genérica"**: un degradado dorado, una tipografía blackletter
   cualquiera, un dragón de stock, pergamino de textura libre. Eso es *fantasía
   medieval de banco de imágenes*, no Warcraft. Un jugador nota la diferencia
   en medio segundo.
5. **El dashboard de SaaS con skin encima**: la misma tabla de Bootstrap, los
   mismos KPI cards, pero en marrón y con iconos de espadas. La estructura
   sigue siendo de panel de administración.

---

## 1. El método. No lo saltes

Este orden importa más que cualquier regla concreta de abajo. Es lo que
convierte "generar UI" en "diseñar".

### 1.1 Brainstorm
Antes de tocar código, escribe:
- **4 a 6 colores** con hex y con nombre propio (no "primary", "secondary":
  nombres que signifiquen algo en el mundo — `pergamino`, `tinta-forjada`,
  `oro-tabardo`).
- **Dos o tres tipografías**, cada una con un papel claro: una display con
  carácter para nombres y títulos, una de lectura que aguante párrafos largos,
  y opcionalmente una monoespaciada para números y timestamps.
- **Un concepto de layout en una frase**, más un wireframe en ASCII.
- **Un elemento firma**: la única cosa por la que alguien recordaría esta app.
  Ver §3.

### 1.2 Plan
Escríbelo. Un párrafo por decisión, con el porqué.

### 1.3 Autocrítica — el paso que casi todos se saltan
Compara tu propuesta contra los cinco defaults de arriba, uno por uno. Si algo
se parece a lo que habrías generado para *cualquier otro brief*, cámbialo, y
anota qué cambiaste y por qué. Pregúntate también: **¿esto se podría estar
usando para una app de finanzas si le cambio los textos?** Si la respuesta es
sí, no tiene dirección todavía.

### 1.4 Construir
Solo ahora. Sigue el plan revisado al pie de la letra.

### 1.5 Autocrítica final, con capturas reales
Toma capturas a 390px, 768px y 1440px, míralas, y compáralas contra el plan.
No confíes en tu idea de cómo quedó: mira el píxel. Y antes de darlo por bueno,
pregúntate **qué le quitarías**. Casi siempre sobra algo.

---

## 2. El contexto visual de Warcraft, en concreto

Esto es lo que un modelo no sabe por defecto y lo que separa una app que
parece de WoW de una que parece "fantasía".

### 2.1 Los colores de calidad de objeto son sagrados
Un jugador los lee **más rápido que el texto**. Son vocabulario, no decoración,
y cambiarlos rompe algo que la gente lleva veinte años teniendo automatizado:

```
Pobre        #9d9d9d    gris
Común        #ffffff    blanco
Poco común   #1eff00    verde
Raro         #0070dd    azul
Épico        #a335ee    morado
Legendario   #ff8000    naranja
Artefacto    #e6cc80    dorado claro
Reliquia     #00ccff    azul claro
```

**Nunca los reinterpretes.** Si tu paleta choca con ellos, la que cambia es tu
paleta.

Y ojo con el contraste: el morado `#a335ee` sobre negro está en el límite de
legibilidad, y el azul `#0070dd` claramente por debajo de AA para texto
pequeño. La solución no es cambiar el color — es darle tamaño, peso, o un
fondo que lo levante. Nunca uses esos colores para texto de 12px sobre negro
puro.

Lo mismo con los **colores de clase** (Death Knight, Druida, Mago, etc.): son
canónicos, la gente los reconoce, y hay que sacarlos de la fuente oficial en
vez de inventarlos "parecidos". Un Druida que no sea naranja se siente mal
aunque el usuario no sepa explicar por qué.

### 2.2 Tipografía
Blizzard usa **Friz Quadrata** para casi todo el texto de la interfaz, y
**Morpheus** para títulos. Las dos son de licencia comercial: **no las
embebas**. Busca sustitutas con el mismo carácter —una glífica con serifas
angulosas para nombres, una display con peso para títulos— y comprueba que
existan en peso variable y con el juego de caracteres que necesitas.

Lo que NO debe pasar: usar una blackletter genérica para todo. En el juego el
blackletter aparece con cuentagotas; el 90% del texto es una glífica legible.
Copia esa proporción.

### 2.3 El tooltip es el objeto más icónico del juego
Más que cualquier botón. Fondo casi negro, borde fino, título en el color de
la calidad, líneas de estadística alineadas, texto verde para efectos de uso,
precio en oro/plata/cobre al final. **Si tu app muestra objetos, el tooltip es
tu componente más importante y merece que lo hagas exacto**, no aproximado.

### 2.4 Los jugadores de WoW son usuarios avanzados
Este es el punto que más se falla. La cultura de addons —ElvUI, WeakAuras,
Details!— ha entrenado a esta gente para leer interfaces **densas**. No les da
miedo la información: les da miedo que se la escondas.

Un layout "limpio y minimalista" con mucho aire y tres datos por pantalla se
percibe aquí como **una app que no sabe lo que estás haciendo**. Prefiere
tablas densas, números alineados, abreviaturas que la comunidad ya usa (ilvl,
M+, HPS, DPS, BiS) y filtros de verdad. La legibilidad se consigue con
jerarquía, alineación y espaciado consistente — no quitando información.

### 2.5 Facción, con cuidado
Alianza (azul y oro) y Horda (rojo y negro) son identidad fuerte, pero si tu
app sirve a las dos, **no elijas una como color de marca**: la mitad de tus
usuarios va a sentir que la app no es para ellos. Deja la facción como dato del
contenido, no como tema de la interfaz.

### 2.6 Materiales, no adornos
El mundo de Warcraft tiene texturas concretas: metal forjado con biseles duros,
madera oscura, pergamino gastado, cuero, piedra tallada, esmalte de vitral.
Elige **una o dos** y sé consistente. Diez materiales a la vez es un mercadillo.

Y para las texturas: mejor un borde de 2px bien resuelto y una textura sutil
que una imagen de pergamino a pantalla completa que hace ilegible el texto
encima.

---

## 3. El elemento firma

Elige **una sola cosa** memorable y hazla muy bien. Todo lo demás va quieto y
disciplinado. Gastar el atrevimiento en cinco sitios a la vez produce ruido, no
personalidad.

Candidatos honestos para una app de WoW:
- El **tooltip** replicado con precisión obsesiva, usado como tarjeta en toda
  la app.
- El **marco de retrato** de personaje, con su borde metálico, como contenedor
  de avatares en todas partes.
- La **barra de acción** con sus casillas y cooldowns, reinterpretada como
  navegación.
- El **borde de pergamino del libro de misiones** como contenedor de texto
  largo.

Regla: si le quitas el elemento firma y la app sigue viéndose igual de bien, es
que no era el elemento firma.

---

## 4. Piso de calidad. No negociable, da igual la estética

- **Responsive hasta 390px** sin desbordamiento horizontal de la página. Ojo
  con las tablas densas: van dentro de su propio contenedor con scroll, no
  empujando el `body`.
- **Foco de teclado visible** en todo control. Un `outline` de 3px con offset,
  nunca `outline: none` sin sustituto.
- **`prefers-reduced-motion` respetado.** Nada de animaciones decorativas que
  no se puedan apagar.
- **Contraste AA** en texto. Los colores de calidad son la excepción que hay
  que gestionar con tamaño y peso, no ignorar.
- **Objetivos táctiles de 44px** mínimo.
- **Texto que viene de fuera** (nombres de personaje, de guild, títulos de
  objeto) puede ser larguísimo o una sola palabra sin espacios. Usa
  `overflow-wrap: anywhere` en los títulos: una sola palabra más ancha que la
  pantalla empuja su contenedor y desplaza la página entera de lado. Prueba con
  un caso hostil de verdad, no con "Nombre de ejemplo".
- **Números tabulares** (`font-variant-numeric: tabular-nums`) en toda columna
  de cifras. Sin eso, las tablas bailan.

---

## 5. Copy

Escribe desde el lado del jugador, con verbos activos, y usa el vocabulario que
la comunidad ya usa. Un botón que dice "Enviar" no produce un mensaje que dice
"Operación completada".

Y no traduzcas la jerga que nadie traduce: *raid*, *wipe*, *pull*, *tank*,
*BiS*, *parse* se quedan igual en cualquier idioma. Traducirlas suena a manual
de instrucciones.

---

## 6. Lista de verificación antes de decir "listo"

- [ ] Pasé por brainstorm → plan → autocrítica → construir → autocrítica final.
- [ ] Escribí qué cambié en la autocrítica y por qué.
- [ ] Los colores de calidad y de clase son los canónicos, sin reinterpretar.
- [ ] Hay UN elemento firma, y quitarlo se nota.
- [ ] Si le cambio los textos, NO parece una app de finanzas.
- [ ] Tomé capturas reales a 390 / 768 / 1440 y las miré.
- [ ] Probé con un nombre hostil: larguísimo y sin espacios.
- [ ] Foco visible, contraste AA, reduced-motion, 44px.
- [ ] Me pregunté qué quitar, y quité algo.
