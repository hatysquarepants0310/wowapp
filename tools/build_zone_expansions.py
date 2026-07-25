#!/usr/bin/env python3
"""
Genera el mapa zona -> expansión usando la expansión DEL CONTENIDO de la zona
(AreaTable.ContentTuningID -> ContentTuning.ExpansionID), no la del continente.

Esto importa: el mapeo por continente clasifica mal todo el contenido que
Blizzard añadió después sobre los continentes antiguos. Verificado:
  Monte Hyjal / Vashj'ir / Tierras Altas Crepusculares -> Cataclysm (no vanilla)
  Zul'Aman / Isla de Quel'Danas                        -> Midnight  (no vanilla)

Uso:
  curl -s https://wago.tools/db2/AreaTable/csv     -o AreaTable.csv
  curl -s https://wago.tools/db2/ContentTuning/csv -o ContentTuning.csv
  # area_index_es.json = /data/wow/quest/area/index de la API de Blizzard
  python3 tools/build_zone_expansions.py AreaTable.csv ContentTuning.csv area_index_es.json
"""
import csv, json, sys

area_csv, tuning_csv, area_index = sys.argv[1], sys.argv[2], sys.argv[3]

areas = json.load(open(area_index, encoding='utf-8'))['areas']
name_to_id = {a['name']: a['id'] for a in areas if a.get('name')}

area_tuning = {}
with open(area_csv, newline='', encoding='utf-8') as f:
    for r in csv.DictReader(f):
        area_tuning[r['ID']] = r.get('ContentTuningID')

tuning_exp = {}
with open(tuning_csv, newline='', encoding='utf-8') as f:
    for r in csv.DictReader(f):
        tuning_exp[r['ID']] = r.get('ExpansionID')

out = {}
for name, aid in name_to_id.items():
    tuning = area_tuning.get(str(aid))
    exp = tuning_exp.get(tuning) if tuning else None
    if exp not in (None, ''):
        out[name] = int(exp)

json.dump(out, open('zone_expansion.json', 'w', encoding='utf-8'), ensure_ascii=False)
print(f"zonas con expansión de contenido: {len(out)} de {len(name_to_id)}")
