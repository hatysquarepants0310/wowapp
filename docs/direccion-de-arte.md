# Dirección de arte

## Por qué esta es la tercera dirección

Las dos anteriores fallaron por el **mismo error de fondo**, aunque parecieran
opuestas:

- **v3.0.0** — fondo casi negro, acento morado, tarjetas redondeadas con sombra.
  Genérico: quitándole los textos era un dashboard de finanzas.
- **v3.2.0** — negro azulado, oro, pergamino, bordes metálicos, réplica del
  tooltip. Menos genérico, pero recargado y viejo.

Las dos intentaban que la app **pareciera** World of Warcraft **imitando su
interfaz**. Y ahí está el error: la interfaz del juego está diseñada para un
monitor de 27 pulgadas y un ratón. A 390px de ancho, la filigrana dorada no se
lee como épica; se lee como ruido. Las propias apps móviles de Blizzard no hacen
eso — son limpias y modernas, y lo que las hace de WoW es **el contenido**.

## La dirección

> **El chrome se calla. El arte del juego pone la identidad.**

La app ya no tiene que *señalar* que es de WoW con adornos, porque está llena de
WoW de verdad. Ahora tengo las herramientas para traerlo:

| Qué | De dónde |
|---|---|
| Render de tu personaje, cuerpo entero | `character-media` → `main-raw` |
| Tu cara, para listas | `character-media` → `avatar` |
| Mapas reales de zona | tiles del cliente, decodificados en el móvil |
| Iconos de objeto | `media/item` |
| Colores de clase y calidad | canónicos |

### Los tres pilares

**1. Tu personaje preside la app.** Blizzard publica un render de cuerpo entero
de cada personaje, con fondo transparente. La pantalla de Inicio no la encabeza
un icono ni un degradado: la encabeza *tu* gnomo guerrero con su equipo puesto.
Ninguna plantilla tiene eso.

**2. El color de tu clase es el acento.** Los colores de clase son canónicos y
llevan veinte años sin cambiar. La app entera se tiñe del de tu personaje
activo: un druida la ve naranja, un brujo morada, y cambia sola al cambiar de
personaje. Personalización real sin una pantalla de ajustes.

**3. El chrome es neutro y moderno.** Grises fríos, tipografía clara, radios
suaves, espaciado cómodo. Sin metal falso, sin pergamino, sin degradados de
marca. Su trabajo es no estorbar al arte.

### Paleta

Neutra a propósito: el arte del juego es cálido y saturado, así que debe ser lo
único con color en pantalla.

| Nombre | Hex | Para qué |
|---|---|---|
| `Base` | `#0E0F13` | Fondo |
| `Surface` | `#171920` | Panel |
| `SurfaceHigh` | `#20232C` | Panel elevado |
| `Line` | `#2A2E38` | Separadores |
| `TextHigh` | `#ECEDF0` | Texto principal |
| `TextMid` | `#A3A8B4` | Secundario |
| `Gold` | `#E3B341` | **Solo** recompensas y dinero |
| acento | color de clase | Todo lo interactivo |

Los colores de **calidad** y de **clase** son los canónicos y no se
reinterpretan. Si algo choca con ellos, lo que cambia es la paleta.

### Prueba del algodón

*Si le cambio los textos, ¿parece una app de finanzas?* No: hay un elfo de
sangre a tamaño completo en la cabecera y la interfaz está teñida del color de
su clase.

*Si quito el elemento firma, ¿se nota?* Sí: sin el retrato y sin el color de
clase, la app pierde de golpe lo único que la hace personal.

## Arquitectura de la app

El rediseño no era solo de colores: la estructura estaba mal. Había tres
pestañas —Inicio, Personaje y **Más**— y esa tercera era un cajón de sastre con
doce entradas planas. Todo lo que no cabía en las otras dos acababa ahí, así
que para llegar a cualquier cosa había que leerse la lista entera. Un cajón
llamado "Más" es la señal de que nadie decidió dónde van las cosas.

Ahora hay cinco pestañas, cada una con un tema que se puede decir en una frase:

| Pestaña | Qué contiene | Por qué existe |
|---|---|---|
| **Hoy** | Eventos, reset, misiones de la semana | Lo que caduca. Es la pantalla del día a día |
| **Personaje** | Equipo, puntuación, roster, progresión | Todo lo tuyo, que antes estaba repartido |
| **Mundo** | Mapa en vivo, eventos, noticias | Lo que pasa fuera de ti |
| **Contenido** | Mazmorras, bandas, botín, historias | La enciclopedia: lo que existe |
| **Mercado** | Casa de subastas | Una tarea distinta a todas las demás |

Ajustes sale de la navegación y pasa al engranaje de la barra superior, que es
donde se busca en cualquier app. La barra inferior queda solo para contenido.

La regla que sigue: **si algo no encaja claramente en una de las cinco, el
problema es la arquitectura, no la cosa.** No vuelve a haber un "Más".

## Lo que no está verificado

El paso de mirar capturas reales a 390 / 768 / 1440 **sigue pendiente**: este
entorno no tiene emulador ni dispositivo. Lo comprobado es que compila, que
pasan las pruebas y que los contrastes están calculados. Quien tenga el móvil
delante debería mirarlo antes de darlo por bueno.

## Evolución reciente (v6.0.0)

v6 deja de usar **hubs-índice**: ya no hay pestañas que sean listas de enlaces
que abren otra pantalla. Cada pestaña ahora es el contenido directamente:

- **Mundo = mapa.** La fila de WorldMap no es un índice; es el mapa activo con
  sus pestañas de eventos y noticias.
- **Contenido = enciclopedia.** La fila de WowChip no es un índice; filtra y
  muestra directamente mazmorras, bandas, botín e historias.
- **Hoy = héroe + reloj + tareas.** El countdown del próximo evento vive en el
  héroe; la semana es un ticker; las misiones de la semana se renderizan como
  tareas concretas.
