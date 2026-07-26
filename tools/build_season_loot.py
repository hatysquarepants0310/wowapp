#!/usr/bin/env python3
"""
Genera app/src/main/assets/catalog/season_loot.json: el botín de la expansión
actual con imagen, calidad, origen (instancia + jefe + dificultades) y el tamaño
de la tabla de botín de cada jefe, que es lo que permite estimar la probabilidad.

Todo sale del journal oficial de Blizzard (Game Data API). Blizzard NO publica
probabilidades de botín, así que la app las estima con el tamaño de la tabla y lo
etiqueta como estimado; las monturas y mascotas se marcan aparte porque su
probabilidad es fija y baja, independiente de la tabla.

Uso:
  echo "<access_token>" > /tmp/tok
  python3 tools/build_season_loot.py <journalExpansionId>   # 516 = Midnight
"""
import json, os, sys, threading, queue
import urllib.request, urllib.error

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
ASSETS = os.path.join(ROOT, 'app/src/main/assets/catalog')
BASE = 'https://us.api.blizzard.com'
TOK = open('/tmp/tok').read().strip()

# item_class 15 = Miscelánea; subclase 5 = montura, 2 = mascota de compañía.
MOUNT = (15, 5)
PET = (15, 2)


def get(path, locale='es_MX'):
    url = f'{BASE}{path}{"&" if "?" in path else "?"}namespace=static-us&locale={locale}'
    req = urllib.request.Request(url, headers={'Authorization': f'Bearer {TOK}'})
    for attempt in range(4):
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                return json.loads(r.read())
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return None
            if attempt == 3:
                return None
        except Exception:
            if attempt == 3:
                return None
    return None


def parallel(fn, items, workers=16):
    q, out, lock = queue.Queue(), [], threading.Lock()
    for i in items:
        q.put(i)

    def work():
        while True:
            try:
                item = q.get_nowait()
            except queue.Empty:
                return
            r = fn(item)
            if r is not None:
                with lock:
                    out.append(r)

    ts = [threading.Thread(target=work, daemon=True) for _ in range(workers)]
    for t in ts:
        t.start()
    for t in ts:
        t.join()
    return out


def main(expansion_id):
    exp = get(f'/data/wow/journal-expansion/{expansion_id}')
    if not exp:
        sys.exit(f'expansión {expansion_id} no encontrada')
    print(f"expansión: {exp['name']}")

    instances = [('RAID', i['id'], i['name']) for i in exp.get('raids', [])] + \
                [('DUNGEON', i['id'], i['name']) for i in exp.get('dungeons', [])]

    def load_instance(spec):
        kind, iid, name = spec
        d = get(f'/data/wow/journal-instance/{iid}')
        if not d:
            return None
        return (kind, iid, name, [e['id'] for e in d.get('encounters', [])])

    loaded = parallel(load_instance, instances)
    encounters = [(k, iid, iname, eid) for k, iid, iname, eids in loaded for eid in eids]
    print(f'instancias: {len(loaded)} | jefes: {len(encounters)}')

    def load_encounter(spec):
        kind, iid, iname, eid = spec
        es = get(f'/data/wow/journal-encounter/{eid}', 'es_MX')
        en = get(f'/data/wow/journal-encounter/{eid}', 'en_US')
        if not es:
            return None
        items = [i['item']['id'] for i in es.get('items', []) if i.get('item')]
        return {
            'kind': kind, 'instanceId': iid, 'instance': iname,
            'bossId': eid, 'boss': es.get('name', ''),
            'bossEn': (en or {}).get('name', ''),
            'modes': [m['type'] for m in es.get('modes', [])],
            'items': items,
        }

    bosses = parallel(load_encounter, encounters)
    bosses.sort(key=lambda b: (b['instanceId'], b['bossId']))

    item_ids = sorted({i for b in bosses for i in b['items']})
    print(f'objetos distintos: {len(item_ids)}')

    def load_item(iid):
        es = get(f'/data/wow/item/{iid}', 'es_MX')
        if not es:
            return None
        en = get(f'/data/wow/item/{iid}', 'en_US')
        media = get(f'/data/wow/media/item/{iid}')
        icon = next((a['value'] for a in (media or {}).get('assets', [])
                     if a.get('key') == 'icon'), None)
        return {
            'id': iid,
            'name': es.get('name', ''),
            'nameEn': (en or {}).get('name', ''),
            'quality': (es.get('quality') or {}).get('type', ''),
            'cls': (es.get('item_class') or {}).get('id', 0),
            'sub': (es.get('item_subclass') or {}).get('id', 0),
            'slot': (es.get('inventory_type') or {}).get('type', ''),
            'icon': icon,
        }

    items = {i['id']: i for i in parallel(load_item, item_ids, workers=24)}
    print(f'objetos resueltos: {len(items)}')

    mounts = [i for i in items.values() if (i['cls'], i['sub']) == MOUNT]
    pets = [i for i in items.values() if (i['cls'], i['sub']) == PET]
    print(f'monturas: {len(mounts)} | mascotas: {len(pets)}')
    for m in mounts:
        print('  montura:', m['name'])

    payload = {
        'source': 'Blizzard Game Data API (journal-expansion / journal-instance / '
                  'journal-encounter / item / media-item)',
        'expansionId': expansion_id,
        'expansion': exp['name'],
        'bosses': bosses,
        'items': {str(k): v for k, v in items.items()},
        'mountItemIds': sorted(m['id'] for m in mounts),
        'petItemIds': sorted(p['id'] for p in pets),
    }
    dest = os.path.join(ASSETS, 'season_loot.json')
    with open(dest, 'w', encoding='utf-8') as f:
        json.dump(payload, f, ensure_ascii=False, separators=(',', ':'))
    print(f'season_loot.json: {os.path.getsize(dest)} bytes')


if __name__ == '__main__':
    main(int(sys.argv[1]) if len(sys.argv) > 1 else 516)
