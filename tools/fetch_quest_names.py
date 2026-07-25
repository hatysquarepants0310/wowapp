#!/usr/bin/env python3
"""
Descarga nombre, zona y nivel de cada misión desde la API oficial de Blizzard y
los cachea en quests_<locale>.json, que consume tools/build_storylines.py.

Uso:
  echo "<access_token>" > /tmp/tok          # token client_credentials de Blizzard
  python3 tools/fetch_quest_names.py es_MX  # y otra vez con en_US

Reanudable: relee el JSON existente y solo pide lo que falta.
"""
import json, os, sys, threading, queue, time
import urllib.request, urllib.error

TOK = open('/tmp/tok').read().strip()
LOC = sys.argv[1]           # es_MX | en_US
OUT = f'quests_{LOC}.json'
ids = [int(x) for x in open('questids.txt').read().split()]

cache = {}
if os.path.exists(OUT):
    cache = {int(k): v for k, v in json.load(open(OUT)).items()}
todo = [i for i in ids if i not in cache]
print(f'{LOC}: {len(cache)} en cache, {len(todo)} por bajar', flush=True)

q = queue.Queue()
for i in todo:
    q.put(i)
lock = threading.Lock()
done = [0]
opener = urllib.request.build_opener()


def work():
    while True:
        try:
            qid = q.get_nowait()
        except queue.Empty:
            return
        url = (f'https://us.api.blizzard.com/data/wow/quest/{qid}'
               f'?namespace=static-us&locale={LOC}')
        rec = None
        for attempt in range(4):
            try:
                req = urllib.request.Request(url, headers={'Authorization': f'Bearer {TOK}'})
                with opener.open(req, timeout=30) as r:
                    d = json.loads(r.read())
                a = d.get('area') or {}
                rec = {'n': d.get('title') or '', 'a': a.get('id') or 0}
                rw = ((d.get('rewards') or {}).get('items') or {}).get('items') or []
                names = [x['item']['name'] for x in rw if x.get('item', {}).get('name')]
                if names:
                    rec['r'] = ', '.join(names)[:120]
                lvl = (d.get('requirements') or {}).get('min_character_level') or 0
                if lvl:
                    rec['l'] = lvl
                break
            except urllib.error.HTTPError as e:
                if e.code == 404:
                    rec = {'n': '', 'a': 0}
                    break
                time.sleep(0.5 * (attempt + 1))
            except Exception:
                time.sleep(0.5 * (attempt + 1))
        with lock:
            cache[qid] = rec if rec is not None else {'n': '', 'a': 0}
            done[0] += 1
            if done[0] % 1000 == 0:
                print(f'{LOC}: {done[0]}/{len(todo)}', flush=True)
                json.dump(cache, open(OUT, 'w'), ensure_ascii=False, separators=(',', ':'))
        q.task_done()


ts = [threading.Thread(target=work, daemon=True) for _ in range(32)]
for t in ts:
    t.start()
for t in ts:
    t.join()
json.dump(cache, open(OUT, 'w'), ensure_ascii=False, separators=(',', ':'))
print(f'{LOC}: listo, {len(cache)} misiones', flush=True)
