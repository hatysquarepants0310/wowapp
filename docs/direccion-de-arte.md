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

## Lo que no está verificado

El paso de mirar capturas reales a 390 / 768 / 1440 **sigue pendiente**: este
entorno no tiene emulador ni dispositivo. Lo comprobado es que compila, que
pasan las pruebas y que los contrastes están calculados. Quien tenga el móvil
delante debería mirarlo antes de darlo por bueno.
