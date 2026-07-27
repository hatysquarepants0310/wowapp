#!/usr/bin/env python3
"""
Genera app/src/main/assets/catalog/quest_coords.json: la coordenada del punto de
la misión en su mapa, lista para el comando /way de TomTom.

Fuente: tablas DB2 del cliente (wago.tools).
  QuestPOIBlob   qué mapa (UiMapID) tiene puntos de cada misión
  QuestPOIPoint  los puntos, en coordenadas de MUNDO
  UiMapAssignment  los límites de cada mapa, para pasar mundo -> 0..100

La conversión es la del propio cliente: los ejes del mundo están girados
respecto al mapa (el eje Y del mundo es el horizontal del mapa y crece hacia el
oeste), de ahí que X e Y se crucen e inviertan.

Uso:
  python3 tools/build_quest_coords.py <dir-con-los-csv>
"""
import csv, json, os, sys
from collections import defaultdict

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
ASSETS = os.path.join(ROOT, 'app/src/main/assets/catalog')


def read(path):
    with open(path, newline='', encoding='utf-8') as f:
        return list(csv.DictReader(f))


def num(row, key, default=0.0):
    try:
        return float(row.get(key) or default)
    except ValueError:
        return default


def main(d):
    j = lambda n: os.path.join(d, n)

    # Un UiMapID puede tener varias asignaciones (una por MapID). Se indexan las
    # dos claves para poder afinar con el MapID del propio POI.
    by_pair, by_map = {}, {}
    for r in read(j('UiMapAssignment.csv')):
        ui, mp = int(r['UiMapID']), int(r['MapID'])
        box = (
            num(r, 'Region_0'), num(r, 'Region_1'),
            num(r, 'Region_3'), num(r, 'Region_4'),
            num(r, 'UiMin_0'), num(r, 'UiMin_1'),
            num(r, 'UiMax_0', 1.0), num(r, 'UiMax_1', 1.0),
        )
        order = int(r.get('OrderIndex') or 0)
        for table, key in ((by_pair, (ui, mp)), (by_map, ui)):
            prev = table.get(key)
            if prev is None or order < prev[0]:
                table[key] = (order, box)

    def to_map(ui, mp, wx, wy):
        entry = by_pair.get((ui, mp)) or by_map.get(ui)
        if entry is None:
            return None
        _, (min_x, min_y, max_x, max_y, ui_min_x, ui_min_y, ui_max_x, ui_max_y) = entry
        if max_x == min_x or max_y == min_y:
            return None
        # Ejes girados: la Y del mundo da la horizontal del mapa y la X, la vertical.
        nx = (max_y - wy) / (max_y - min_y)
        ny = (max_x - wx) / (max_x - min_x)
        x = ui_min_x + (ui_max_x - ui_min_x) * nx
        y = ui_min_y + (ui_max_y - ui_min_y) * ny
        if not (0.0 <= x <= 1.0 and 0.0 <= y <= 1.0):
            return None
        return round(x * 100, 1), round(y * 100, 1)

    points = defaultdict(list)
    for r in read(j('QuestPOIPoint.csv')):
        points[int(r['QuestPOIBlobID'])].append((num(r, 'X'), num(r, 'Y')))

    # Se prefiere el POI del objetivo -1 (el punto de inicio/entrega); si no lo
    # hay, el del primer objetivo, que es a donde el jugador tiene que ir primero.
    best = {}
    for r in read(j('QuestPOIBlob.csv')):
        quest = int(r['QuestID'])
        pts = points.get(int(r['ID']))
        if not pts:
            continue
        objective = int(r.get('ObjectiveIndex') or 0)
        rank = 0 if objective < 0 else objective + 1
        current = best.get(quest)
        if current is not None and current[0] <= rank:
            continue
        coord = to_map(int(r['UiMapID']), int(r.get('MapID') or 0), pts[0][0], pts[0][1])
        if coord is None:
            continue
        best[quest] = (rank, int(r['UiMapID']), coord[0], coord[1])

    out = {str(q): [ui, x, y] for q, (_, ui, x, y) in sorted(best.items())}
    dest = os.path.join(ASSETS, 'quest_coords.json')
    with open(dest, 'w', encoding='utf-8') as f:
        json.dump(out, f, separators=(',', ':'))
    print(f'misiones con coordenada: {len(out)} -> {os.path.getsize(dest)} bytes')
    for q in list(out)[:5]:
        print(f'  ejemplo: /way #{out[q][0]} {out[q][1]} {out[q][2]}   (misión {q})')


if __name__ == '__main__':
    main(sys.argv[1] if len(sys.argv) > 1 else '.')
