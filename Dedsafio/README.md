# Dedsafío · Paper 1.21.1

Plugin inspirado en Dedsafío: armaduras, espadas, tótems, deditas, bloques, retos, portales, emotes y gulags.

## Compilar
Requiere Java 21 y Maven.

    mvn clean package

El .jar queda en `target/Dedsafio-1.0.0.jar`. Cópialo a `plugins/` de tu servidor Paper 1.21.1.

## Comandos
| Comando | Qué hace |
|---|---|
| `/tienda` | Tienda paginada, se paga en deditas |
| `/deditas [saldo\|ver\|pagar\|retirar\|depositar\|top]` | Economía (admin: `dar`, `quitar`, `fijar`) |
| `/reto iniciar [facil\|normal\|dificil\|extremo]` | Oleadas con dificultad dinámica y jefe final |
| `/gulag <boxeo\|espacio\|carreras> unirse\|salir\|estado` | Minijuegos |
| `/emote <saludo\|baile\|victoria\|reverencia>` | Animaciones |
| `/portal crear\|enlazar\|borrar\|tp\|lista` | Portales (admin) |
| `/dedsafio give <jugador> <id> [n]`, `list`, `reload` | Administración |

## Configurar los gulags (admin)
Colócate en el sitio y ejecuta:

    /gulag boxeo lobby
    /gulag boxeo spawn        (repite una vez por jugador)
    /gulag carreras lobby
    /gulag carreras spawn     (uno por kart)
    /gulag carreras checkpoint (opcional, en orden)
    /gulag carreras meta

Con 2 jugadores empieza la cuenta atrás. Los admins pueden forzar con `/gulag <tipo> iniciar`.

## Objetos (ids para /dedsafio give)
- Armaduras: `dungeon_*`, `earth_*`, `dimensional_*`, `boxing_red_*`, `boxing_blue_*` (helmet, chestplate, leggings, boots)
- Espadas: `ancestral_sword`, `belic_sword`, `celestial_sword`, `space_stellar_sword`, `space_void_sword`
- Boxeo: `boxing_gloves`
- Pociones: `potion_vitality`, `potion_fury`, `potion_dimensional`, `potion_ancestral`
- Otros: `soul_flower`, `resurrection_spoon`, `dedita_common`, `dedita_rare`, `dedita_legendary`
- Tótems: `totem_eonic`, `totem_iron`, `totem_otter`, `totem_flashbang`
- Bloques/entidades: `rune_table`, `lighthouse`, `ancestral_block`, `statue`, `kart`

## Resource pack (texturas)
`resourcepack/` (se genera, no se sube al repo) contiene una textura pixel-art de 16×16 por cada objeto (44 en total), ligada a su `CustomModelData`
(2001+ objetos, 3000+ armaduras). Se genera con `python3 tools/generate_pack.py` (requiere Pillow) y puedes retocar
colores y formas en ese script.

Para usarlo:
1. Descarga `Dedsafio-pack.zip` de la release (o súbelo a cualquier enlace directo).
2. En `config.yml` rellena `resource-pack.url` y `resource-pack.sha1` y ejecuta `/dedsafio reload`.
3. Los jugadores lo reciben al entrar; `/dedsafio pack` lo reenvía.

Limitaciones: las texturas son de **icono** (inventario y mano). Lo que se ve puesto sobre el cuerpo (armadura) y los
bloques ya colocados siguen siendo los vanilla; el modelo del pack no incluye las vistas previas de trims.

## GitHub Actions
`.github/workflows/build.yml` compila el plugin, genera y valida el pack, calcula su SHA-1 y, al subir un tag `v*`
(`git tag v1.0.0 && git push --tags`), publica una release con el `.jar`, el `.zip` del pack y el SHA-1.
