#!/usr/bin/env python3
"""
Genera app/src/main/assets/catalog/storylines.json a partir de las tablas DB2
QuestLine y QuestLineXQuest exportadas de wago.tools (datos del cliente del juego,
no scraping de Wowhead). Uso:

  curl -s https://wago.tools/db2/QuestLine/csv -o ql.csv
  curl -s https://wago.tools/db2/QuestLineXQuest/csv -o qlxq.csv
  python3 tools/build_storylines.py ql.csv qlxq.csv

Se re-ejecuta por parche; la app lo consume horneado (cero dependencia en runtime).
"""
import csv, json, os, sys
from collections import defaultdict

ql_csv, qlxq_csv = sys.argv[1], sys.argv[2]
names = {}
with open(ql_csv, newline='', encoding='utf-8') as f:
    for r in csv.DictReader(f):
        nm = (r.get('Name_lang') or '').strip()
        # Excluir marcadores internos de Blizzard (Do Not Translate / placeholders).
        if nm and not nm.startswith('(DNT)') and not nm.startswith('[DNT]'):
            names[r['ID']] = nm

lines = defaultdict(list)
with open(qlxq_csv, newline='', encoding='utf-8') as f:
    for r in csv.DictReader(f):
        qid = r['QuestID']
        if qid and qid != '0':
            try: order = int(r.get('OrderIndex') or 0)
            except ValueError: order = 0
            lines[r['QuestLineID']].append((order, int(qid)))

out = []
for qlid, name in names.items():
    quests = [q for _, q in sorted(lines.get(qlid, []))]
    if quests:
        out.append({"id": int(qlid), "name": name, "questIds": quests})
out.sort(key=lambda s: s["name"].lower())

dest = os.path.join(os.path.dirname(__file__), '..',
                    'app/src/main/assets/catalog/storylines.json')
with open(dest, 'w', encoding='utf-8') as f:
    json.dump({"source": "wago.tools (QuestLine/QuestLineXQuest)",
               "count": len(out), "storylines": out},
              f, ensure_ascii=False, separators=(',', ':'))
print(f"{len(out)} storylines -> {os.path.getsize(dest)} bytes")
