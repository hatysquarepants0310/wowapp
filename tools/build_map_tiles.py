#!/usr/bin/env python3
"""Genera el índice de tiles del mapa de cada zona.

La app pinta el mapa REAL del juego. Aquí NO se hornea ninguna imagen: solo
números —qué archivo compone cada casilla de la cuadrícula de cada zona—, que
es información, no arte. Las texturas las descarga el dispositivo del usuario
en el momento de mirar el mapa, igual que haría un navegador, y quedan en su
caché local. El APK no redistribuye arte de Blizzard.

Fuentes (wago.tools, tablas del propio cliente):
  UiMapXMapArt         uiMapId -> uiMapArtId
  UiMapArt             uiMapArtId -> estilo
  UiMapArtStyleLayer   estilo -> tamaño REAL del mapa
  UiMapArtTile         uiMapArtId -> (fila, columna, fileDataId)

El tamaño real importa: la cuadrícula de tiles redondea hacia arriba (un mapa
de 1002x668 se sirve en 4x3 casillas de 256, o sea 1024x768), así que sin
recortar sobra un borde negro y, peor, los puntos de misión se colocan
desplazados porque sus coordenadas 0-100 van sobre el mapa real, no sobre la
cuadrícula.

Uso:  python3 tools/build_map_tiles.py <dir con los CSV>
"""
import csv
import json
import os
import sys
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'app/src/main/assets/catalog')

csv.field_size_limit(10 * 1024 * 1024)


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 1
    src = sys.argv[1]

    # Solo las zonas para las que la app tiene misiones situadas: el resto
    # serían kilobytes de índice que nadie va a abrir.
    with open(os.path.join(OUT, 'quest_coords.json'), encoding='utf-8') as fh:
        wanted = {int(v[0]) for v in json.load(fh).values() if v}

    # Una zona puede tener varias versiones de arte (fases de la historia). Se
    # queda la de menor PhaseID, que es la que ve un jugador normal.
    art_for_map = {}
    with open(os.path.join(src, 'UiMapXMapArt.csv'), encoding='utf-8') as fh:
        for row in csv.DictReader(fh):
            try:
                ui_map = int(row['UiMapID'])
                art = int(row['UiMapArtID'])
                phase = int(row['PhaseID'] or 0)
            except (KeyError, ValueError):
                continue
            if ui_map not in wanted:
                continue
            current = art_for_map.get(ui_map)
            if current is None or phase < current[0]:
                art_for_map[ui_map] = (phase, art)

    # estilo -> (ancho, alto) reales del mapa
    size_for_style = {}
    with open(os.path.join(src, 'UiMapArtStyleLayer.csv'), encoding='utf-8') as fh:
        for row in csv.DictReader(fh):
            try:
                if int(row['LayerIndex'] or 0) != 0:
                    continue
                size_for_style[int(row['UiMapArtStyleID'])] = (
                    int(row['LayerWidth']), int(row['LayerHeight']),
                )
            except (KeyError, ValueError):
                continue

    style_for_art = {}
    with open(os.path.join(src, 'UiMapArt.csv'), encoding='utf-8') as fh:
        for row in csv.DictReader(fh):
            try:
                style_for_art[int(row['ID'])] = int(row['UiMapArtStyleID'])
            except (KeyError, ValueError):
                continue

    tiles_by_art = defaultdict(list)
    with open(os.path.join(src, 'UiMapArtTile.csv'), encoding='utf-8') as fh:
        for row in csv.DictReader(fh):
            try:
                art = int(row['UiMapArtID'])
                layer = int(row['LayerIndex'] or 0)
                if layer != 0:
                    continue  # la capa 0 es el mapa; las demás son overlays
                tiles_by_art[art].append(
                    (int(row['RowIndex']), int(row['ColIndex']), int(row['FileDataID'])),
                )
            except (KeyError, ValueError):
                continue

    out = {}
    for ui_map, (_, art) in sorted(art_for_map.items()):
        tiles = tiles_by_art.get(art)
        if not tiles:
            continue
        rows = max(t[0] for t in tiles) + 1
        cols = max(t[1] for t in tiles) + 1
        if rows * cols != len(tiles):
            # Cuadrícula incompleta: mejor no ofrecer un mapa con agujeros.
            continue
        size = size_for_style.get(style_for_art.get(art, -1))
        if not size:
            continue
        grid = [0] * (rows * cols)
        for r, c, fid in tiles:
            grid[r * cols + c] = fid
        out[str(ui_map)] = {
            'r': rows, 'c': cols,
            'w': size[0], 'h': size[1],
            't': grid,
        }

    path = os.path.join(OUT, 'map_tiles.json')
    with open(path, 'w', encoding='utf-8') as fh:
        json.dump(out, fh, separators=(',', ':'))
    total = sum(len(v['t']) for v in out.values())
    print(f'map_tiles.json  {len(out)} zonas  {total} tiles  '
          f'{os.path.getsize(path) // 1024} KB')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
