#!/usr/bin/env python3
"""
Genera app/src/main/assets/catalog/storylines.json y mounts.json a partir de las
tablas DB2 del cliente del juego (exportadas de wago.tools) y, para la zona de
cada historia, la API oficial de Blizzard. NO hay scraping de Wowhead.

Uso:
  curl -s https://wago.tools/db2/QuestLine/csv       -o ql.csv
  curl -s https://wago.tools/db2/QuestLineXQuest/csv -o qlxq.csv
  curl -s https://wago.tools/db2/MountXDisplay/csv   -o mxd.csv
  python3 tools/build_storylines.py ql.csv qlxq.csv [mxd.csv] [zones_by_storyline.json]

Se re-ejecuta por parche; la app consume los JSON horneados (cero dependencia
de estas fuentes en tiempo de ejecución).
"""
import csv, json, os, re, sys
from collections import defaultdict

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
ASSETS = os.path.join(ROOT, 'app/src/main/assets/catalog')

def build_storylines(ql_csv, qlxq_csv, zones_json=None):
    names = {}
    with open(ql_csv, newline='', encoding='utf-8') as f:
        for r in csv.DictReader(f):
            nm = (r.get('Name_lang') or '').strip()
            # Excluir marcadores internos de Blizzard (Do Not Translate).
            # Excluir marcadores internos: (DNT)=Do Not Translate, [PH]=placeholder.
            if nm and not re.match(r'^[\[(](DNT|PH)[\])]', nm):
                names[r['ID']] = nm

    lines = defaultdict(list)
    with open(qlxq_csv, newline='', encoding='utf-8') as f:
        for r in csv.DictReader(f):
            qid = r['QuestID']
            if qid and qid != '0':
                try: order = int(r.get('OrderIndex') or 0)
                except ValueError: order = 0
                lines[r['QuestLineID']].append((order, int(qid)))

    zones = {}
    if zones_json and os.path.exists(zones_json):
        zones = {int(k): v for k, v in json.load(open(zones_json, encoding='utf-8')).items()}
    # La expansión de cada zona sale de tools/build_zone_expansions.py, que usa
    # AreaTable.ContentTuningID -> ContentTuning.ExpansionID (expansión DEL
    # CONTENIDO). No usar el continente: clasifica mal Cataclysm y Quel'Thalas.

    out = []
    for qlid, name in names.items():
        quests = [q for _, q in sorted(lines.get(qlid, []))]
        if not quests:
            continue
        sid = int(qlid)
        entry = {"id": sid, "name": name, "questIds": quests}
        z = zones.get(sid)
        if z:
            entry["zone"] = z
        # Las campañas se identifican por nombre (el DB2 no marca el tipo).
        low = name.lower()
        if 'campaign' in low or 'campaña' in low:
            entry["campaign"] = True
        out.append(entry)
    out.sort(key=lambda s: s["name"].lower())

    dest = os.path.join(ASSETS, 'storylines.json')
    with open(dest, 'w', encoding='utf-8') as f:
        json.dump({"source": "wago.tools (QuestLine/QuestLineXQuest) + Blizzard quest API (zona)",
                   "count": len(out), "storylines": out},
                  f, ensure_ascii=False, separators=(',', ':'))
    withzone = sum(1 for s in out if 'zone' in s)
    print(f"storylines: {len(out)} ({withzone} con zona) -> {os.path.getsize(dest)} bytes")

def build_mounts(mxd_csv):
    disp = {}
    with open(mxd_csv, newline='', encoding='utf-8') as f:
        for r in csv.DictReader(f):
            mid, cd = r.get('MountID'), r.get('CreatureDisplayInfoID')
            if mid and cd and mid != '0' and cd != '0' and mid not in disp:
                disp[mid] = int(cd)
    dest = os.path.join(ASSETS, 'mounts.json')
    with open(dest, 'w', encoding='utf-8') as f:
        json.dump({"source": "wago.tools (MountXDisplay)",
                   "renderPattern": "https://render.worldofwarcraft.com/us/npcs/zoom/creature-display-{id}.jpg",
                   "displays": {int(k): v for k, v in disp.items()}},
                  f, separators=(',', ':'))
    print(f"monturas: {len(disp)} -> {os.path.getsize(dest)} bytes")

if __name__ == '__main__':
    args = sys.argv[1:]
    build_storylines(args[0], args[1], args[3] if len(args) > 3 else None)
    if len(args) > 2:
        build_mounts(args[2])
