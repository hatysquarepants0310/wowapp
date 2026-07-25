#!/usr/bin/env python3
"""
Genera los assets de historias/misiones a partir de las tablas DB2 del cliente
del juego (exportadas de wago.tools) y de la API oficial de Blizzard. NO hay
scraping de Wowhead.

Salidas (app/src/main/assets/catalog/):
  storylines.json   jerarquía temporada -> campaña -> historia -> misiones
  quests_es.json    nombre de cada misión en español
  quests_en.json    nombre de cada misión en inglés
  quest_meta.json   zona (areaId) y nivel mínimo de cada misión
  areas_es.json     nombre de cada zona en español
  areas_en.json     nombre de cada zona en inglés
  mounts.json       montura -> creature display (para la imagen)

Por qué cada fuente:
  Campaign / CampaignXQuestLine  la jerarquía REAL de campañas del juego, con
      el orden en que se juegan (OrderIndex). Es lo que el propio cliente usa
      para el apartado "Campaña" del registro de misiones; sustituye al antiguo
      heurístico por nombre, que no encontraba ninguna campaña.
  QuestLineXQuest.Flags = 1      misión OPCIONAL de la cadena. Verificado con
      personajes reales: una campaña terminada deja siempre fuera esas misiones,
      así que exigirlas hacía que NINGUNA historia apareciera completa.
  AreaTable.ContentTuningID -> ContentTuning.ExpansionID   expansión DEL
      CONTENIDO (no del continente, que clasifica mal Cataclysm y Quel'Thalas).

Uso:
  python3 tools/build_storylines.py <dir-con-los-csv-y-caches>
"""
import csv, json, os, re, sys
from collections import Counter, defaultdict

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
ASSETS = os.path.join(ROOT, 'app/src/main/assets/catalog')

# La expansión actual del juego (Midnight). Sale de ContentTuning; se fija aquí
# para que la UI sepa qué temporada mostrar primero.
CURRENT_EXPANSION = 11

EXPANSION_NAMES = {
    -3: "Sin expansión fija",
    0: "Azeroth original",
    1: "The Burning Crusade",
    2: "Wrath of the Lich King",
    3: "Cataclysm",
    4: "Mists of Pandaria",
    5: "Warlords of Draenor",
    6: "Legion",
    7: "Battle for Azeroth",
    8: "Shadowlands",
    9: "Dragonflight",
    10: "The War Within",
    11: "Midnight",
}

INTERNAL = re.compile(r'^[\[(](DNT|PH)[\])]|^\(?TEST|LAURA TEST|UI Testing', re.I)
# Campañas técnicas para saltarse una introducción: no son contenido jugable.
SKIP = re.compile(r'\bskip\b|saltar', re.I)


def read(path):
    with open(path, newline='', encoding='utf-8') as f:
        return list(csv.DictReader(f))


def main(d):
    j = lambda n: os.path.join(d, n)

    # ---- DB2 -------------------------------------------------------------
    ql_en = {int(r['ID']): (r['Name_lang'] or '').strip() for r in read(j('QuestLine.csv'))}
    ql_es = {int(r['ID']): (r['Name_lang'] or '').strip() for r in read(j('QuestLine_es.csv'))}
    camp_en = {int(r['ID']): (r['Title_lang'] or '').strip() for r in read(j('Campaign.csv'))}
    camp_es = {int(r['ID']): (r['Title_lang'] or '').strip() for r in read(j('Campaign_es.csv'))}

    # Historia -> misiones, en orden de realización, marcando las opcionales.
    line_quests = defaultdict(list)
    for r in read(j('QuestLineXQuest.csv')):
        qid = int(r['QuestID'] or 0)
        if qid:
            line_quests[int(r['QuestLineID'])].append(
                (int(r['OrderIndex'] or 0), qid, int(r['Flags'] or 0) & 1))

    # Campaña -> historias, en el orden en que el juego las presenta.
    camp_lines = defaultdict(list)
    line_camp = {}
    for r in read(j('CampaignXQuestLine.csv')):
        cid, lid = int(r['CampaignID']), int(r['QuestLineID'])
        camp_lines[cid].append((int(r['OrderIndex'] or 0), lid))
        # Una historia puede figurar en varias campañas; gana la primera (la
        # campaña "de contenido"), que es también la de ID más bajo.
        if lid not in line_camp or cid < line_camp[lid]:
            line_camp[lid] = cid

    area_tuning = {int(r['ID']): int(r['ContentTuningID'] or 0) for r in read(j('AreaTable.csv'))}
    tuning_exp = {int(r['ID']): int(r['ExpansionID']) for r in read(j('ContentTuning.csv'))
                  if (r.get('ExpansionID') or '') != ''}

    # ---- API de Blizzard (cacheada por el crawler) ------------------------
    qes = {int(k): v for k, v in json.load(open(j('quests_es_MX.json'), encoding='utf-8')).items()}
    qen = {int(k): v for k, v in json.load(open(j('quests_en_US.json'), encoding='utf-8')).items()}
    aes = {int(k): v for k, v in json.load(open(j('areas_es_MX.json'), encoding='utf-8')).items()}
    aen = {int(k): v for k, v in json.load(open(j('areas_en_US.json'), encoding='utf-8')).items()}

    def quest_exp(qid):
        a = (qen.get(qid) or {}).get('a') or 0
        t = area_tuning.get(a, 0)
        return tuning_exp.get(t)

    # ---- expansión de cada historia --------------------------------------
    # La moda de las expansiones de sus misiones REQUERIDAS. Una sola misión
    # (p. ej. la que arranca la cadena en una capital) no debe decidir.
    def mode_exp(quest_ids):
        c = Counter(e for e in (quest_exp(q) for q in quest_ids) if e is not None)
        return c.most_common(1)[0][0] if c else None

    line_exp = {}
    for lid, qs in line_quests.items():
        req = [q for _, q, opt in qs if not opt] or [q for _, q, _ in qs]
        line_exp[lid] = mode_exp(req)

    # Las campañas mandan sobre sus capítulos: con todas las misiones de la
    # campaña la moda es mucho más fiable que capítulo a capítulo, y evita que
    # un prólogo en Ventormenta mande la historia a "Azeroth original".
    camp_exp = {}
    for cid, lines in camp_lines.items():
        allq = [q for _, lid in lines for _, q, opt in line_quests.get(lid, []) if not opt]
        camp_exp[cid] = mode_exp(allq)
    for lid, cid in line_camp.items():
        if camp_exp.get(cid) is not None:
            line_exp[lid] = camp_exp[cid]

    # Reserva para las historias cuyas misiones no declaran zona (profesiones,
    # salas de clase, guarniciones…): los IDs de misión se emiten por parche, así
    # que el ID dice de qué expansión es. Se calibra con las historias que SÍ
    # tienen expansión conocida (acierto medido: 89% sobre 1099 historias) y solo
    # se aplica como último recurso, nunca por encima del dato de contenido.
    BUCKET = 250
    hist = defaultdict(Counter)
    for qid in set(qen):
        e = quest_exp(qid)
        if e is not None:
            hist[qid // BUCKET][e] += 1
    modal = {b: c.most_common(1)[0][0] for b, c in hist.items()}

    def exp_by_quest_id(quest_ids):
        c = Counter()
        for q in quest_ids:
            for delta in (0, 1, -1, 2, -2, 3, -3):
                m = modal.get(q // BUCKET + delta)
                if m is not None:
                    c[m] += 1
                    break
        return c.most_common(1)[0][0] if c else None

    for lid, qs in line_quests.items():
        if line_exp.get(lid) is None:
            line_exp[lid] = exp_by_quest_id([q for _, q, _ in qs])

    # ---- zona de cada historia -------------------------------------------
    def mode_zone(quest_ids, names):
        c = Counter(z for z in ((names.get(q) or {}).get('a') for q in quest_ids) if z)
        return c.most_common(1)[0][0] if c else 0

    # ---- salida ----------------------------------------------------------
    storylines = []
    used_quests = set()
    for lid, name_en in ql_en.items():
        qs = sorted(line_quests.get(lid, []))
        if not qs or not name_en or INTERNAL.search(name_en):
            continue
        req = [q for _, q, opt in qs if not opt]
        opt = [q for _, q, opt in qs if opt]
        entry = {
            "id": lid,
            "name": name_en,
            "nameEs": ql_es.get(lid) or name_en,
            "questIds": [q for _, q, _ in qs],
        }
        if opt:
            entry["opt"] = opt
        z = mode_zone(req or [q for _, q, _ in qs], qen)
        if z:
            entry["area"] = z
        e = line_exp.get(lid)
        if e is not None:
            entry["exp"] = e
        cid = line_camp.get(lid)
        if cid is not None and not INTERNAL.search(camp_en.get(cid, '')):
            entry["camp"] = cid
        storylines.append(entry)
        used_quests.update(q for _, q, _ in qs)

    valid_lines = {s["id"] for s in storylines}
    campaigns = []
    for cid, lines in camp_lines.items():
        nm = camp_en.get(cid, '')
        order = [lid for _, lid in sorted(lines) if lid in valid_lines]
        # Las campañas "skip" solo existen para saltarse la introducción: no son
        # contenido, y en la lista de la temporada solo estorban.
        if not nm or not order or INTERNAL.search(nm) or SKIP.search(nm):
            continue
        c = {"id": cid, "name": nm, "nameEs": camp_es.get(cid) or nm, "lines": order}
        # Si ninguna misión de la campaña declaró expansión, se hereda la de sus
        # capítulos (que sí pasaron por el clasificador por ID de misión).
        e = camp_exp.get(cid)
        if e is None:
            c2 = Counter(line_exp[l] for l in order if line_exp.get(l) is not None)
            e = c2.most_common(1)[0][0] if c2 else None
        if e is not None:
            c["exp"] = e
        campaigns.append(c)

    # Blizzard duplica algunas campañas por facción con el mismo título (dos
    # "La maldición de Ula'tek"). Se funden en una: el jugador ve una campaña con
    # todos sus capítulos, sin repetidos.
    merged = {}
    for c in sorted(campaigns, key=lambda x: x["id"]):
        key = (c["name"], c.get("exp"))
        if key in merged:
            seen = set(merged[key]["lines"])
            merged[key]["lines"] += [l for l in c["lines"] if l not in seen]
        else:
            merged[key] = c
    campaigns = sorted(merged.values(), key=lambda c: c["id"])
    kept = {c["id"] for c in campaigns}
    absorbed = {}
    for c in campaigns:
        for lid in c["lines"]:
            absorbed[lid] = c["id"]
    for s in storylines:
        cid = absorbed.get(s["id"])
        if cid is not None:
            s["camp"] = cid
        elif s.get("camp") not in kept:
            s.pop("camp", None)

    write(ASSETS, 'storylines.json', {
        "source": "wago.tools DB2 (QuestLine/QuestLineXQuest/Campaign/CampaignXQuestLine/"
                  "AreaTable/ContentTuning) + API oficial de Blizzard (nombres y zonas)",
        "currentExpansion": CURRENT_EXPANSION,
        "expansions": {str(k): v for k, v in EXPANSION_NAMES.items()},
        "count": len(storylines),
        "campaigns": campaigns,
        "storylines": storylines,
    })

    # Nombres de misión y metadatos, solo de las misiones que alguna historia usa.
    write(ASSETS, 'quests_es.json',
          {str(q): (qes.get(q) or {}).get('n', '') for q in sorted(used_quests)
           if (qes.get(q) or {}).get('n')})
    write(ASSETS, 'quests_en.json',
          {str(q): (qen.get(q) or {}).get('n', '') for q in sorted(used_quests)
           if (qen.get(q) or {}).get('n')})
    meta = {}
    for q in sorted(used_quests):
        r = qen.get(q) or {}
        a, lvl = r.get('a') or 0, r.get('l') or 0
        if a or lvl:
            meta[str(q)] = [a, lvl]
    write(ASSETS, 'quest_meta.json', meta)
    areas = {a for a, _ in meta.values() if a}
    write(ASSETS, 'areas_es.json', {str(a): aes[a] for a in sorted(areas) if a in aes})
    write(ASSETS, 'areas_en.json', {str(a): aen[a] for a in sorted(areas) if a in aen})

    if os.path.exists(j('MountXDisplay.csv')):
        build_mounts(j('MountXDisplay.csv'))

    by_exp = Counter(s.get("exp") for s in storylines)
    print(f"historias: {len(storylines)} | campañas: {len(campaigns)} | "
          f"misiones: {len(used_quests)}")
    for e, n in sorted(by_exp.items(), key=lambda x: (x[0] is None, x[0])):
        print(f"  exp {e}: {n} ({EXPANSION_NAMES.get(e, 'desconocida')})")


def write(dirname, name, payload):
    dest = os.path.join(dirname, name)
    with open(dest, 'w', encoding='utf-8') as f:
        json.dump(payload, f, ensure_ascii=False, separators=(',', ':'))
    print(f"  {name}: {os.path.getsize(dest)} bytes")


def build_mounts(mxd_csv):
    disp = {}
    for r in read(mxd_csv):
        mid, cd = r.get('MountID'), r.get('CreatureDisplayInfoID')
        if mid and cd and mid != '0' and cd != '0' and mid not in disp:
            disp[mid] = int(cd)
    write(ASSETS, 'mounts.json', {
        "source": "wago.tools (MountXDisplay)",
        "renderPattern": "https://render.worldofwarcraft.com/us/npcs/zoom/creature-display-{id}.jpg",
        "displays": {int(k): v for k, v in disp.items()},
    })


if __name__ == '__main__':
    main(sys.argv[1] if len(sys.argv) > 1 else '.')
