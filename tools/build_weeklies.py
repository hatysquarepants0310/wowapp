#!/usr/bin/env python3
"""Deriva los IDs de misión de cada semanal a partir de los nombres del catálogo.

Hasta ahora los IDs de las semanales se escogían a mano y eso produjo tres
clases de error que este script elimina de raíz:

  1. Colisiones: 96442 ("Búsqueda de conocimiento semana 3 de 5: Asaltos a
     líneas ley") estaba también en `weekly_void_assaults`, así que los asaltos
     del Vacío se marcaban solos.
  2. Misiones de una sola vez tratadas como semanales: `weekly_housing` usaba
     "Mi lugar, mi hogar" y "Mejoras hogareñas", que son la introducción a las
     viviendas. Quien tuviera casa veía la semanal marcada para siempre.
  3. Familias incompletas: solo se listaban los IDs que alguien había visto,
     no la familia entera.

La fuente son los nombres ya rastreados de la API oficial y guardados en
`app/src/main/assets/catalog/quests_{es,en}.json` (30.318 misiones). El patrón
de nombre define la familia, así que añadir una zona o una rotación nueva no
exige tocar el código: basta con volver a rastrear y ejecutar esto.

Uso:  python3 tools/build_weeklies.py [--write]
"""
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, 'app/src/main/assets/catalog')

# El contenido de Midnight empieza alrededor del ID 86000; por debajo están
# las Delves de The War Within, que no cuentan para la Bóveda de esta temporada.
MIDNIGHT_MIN_ID = 86000

# id de la tarea -> lista de grupos. Cada grupo es (patrón, repetible).
#
# repetible=True  -> Blizzard borra la marca en cada reset, así que verla
#                    completada ya significa "hecha esta semana".
# repetible=False -> serie rotatoria: cada semana es una misión distinta que
#                    queda completada para siempre. Solo se puede afirmar que
#                    es de esta semana comparando con un snapshot anterior al
#                    reset, y así lo trata DetectionEngine.
FAMILIES = {
    'weekly_seeking_knowledge': [
        (r"^Seeking Knowledge Week \d+ of \d+|^Semana \d+ de \d+ de Búsqueda de conocimiento", False),
    ],
    'weekly_arcantina': [
        (r"^The Arcantina$|^La Arcantina$|^Request of the Arcantina$|^Pedido de la Arcantina$", True),
    ],
    'weekly_housing': [
        (r"^Decor Treasure Hunt$|^Búsqueda del tesoro de decoraciones$", True),
    ],
    'weekly_saltheril': [
        (r"^Saltheril's (Haven|Favor)$|^(Refugio|El favor) de Saltheril$", True),
    ],
    'weekly_abundance': [
        (r"^The Abundant |^Blessings of Abundance$|^An Abundance of Wealth$"
         r"|^(El|La|Las) [Aa]bundante|^Bendiciones de abundancia$|^Riqueza en abundancia$", True),
    ],
    'weekly_stormarion': [
        (r"^To the (North|Central|South) Tower$|^A la torre (norte|central|sur)$"
         r"|^Stormarion Assault$|^Ataque de Tormentarion$", True),
    ],
    'weekly_delves': [
        (r"^Delver's Call: |^Se busca surcabismos: ", True),
    ],
    'weekly_prey': [
        (r"^Prey: |^Presa: ", True),
    ],
    'weekly_world_tour': [
        (r"^Midnight: World Tour$|^Midnight: Tour mundial$", True),
    ],
    'weekly_void_assaults': [
        (r"^Void Assaults: |^Asaltos del Vacío: ", True),
    ],
    'weekly_ritual_sites': [
        (r"^Ritual Site Challenge Report: |^Informe de desafíos de sitio de ritual: ", True),
        (r"^(Advanced )?Ritual Site Studies: Week|^Estudios (avanzados )?de sitios de ritual: Semana", False),
    ],
    'weekly_offworld': [
        (r"^Showdown on |^Enfrentamiento en |^Sparks of War: |^Chispas de guerra: ", True),
    ],
}


def load_names():
    names = {}
    for loc in ('es', 'en'):
        with open(os.path.join(ASSETS, f'quests_{loc}.json'), encoding='utf-8') as fh:
            for k, v in json.load(fh).items():
                names.setdefault(int(k), []).append(v)
    return names


def build(names):
    out = {}
    for task, groups in FAMILIES.items():
        out[task] = []
        for pattern, repeatable in groups:
            rx = re.compile(pattern)
            ids = sorted(
                qid for qid, titles in names.items()
                if qid >= MIDNIGHT_MIN_ID and any(rx.search(t) for t in titles)
            )
            out[task].append((ids, repeatable))
    return out


def rule_for(groups):
    parts = [
        {'type': 'QuestCompleted', 'questIds': ids, 'repeatable': rep}
        for ids, rep in groups if ids
    ]
    if not parts:
        return None
    if len(parts) == 1:
        return parts[0]
    return {'type': 'AnyOf', 'rules': parts}


def main():
    names = load_names()
    built = build(names)

    # Ningún ID puede pertenecer a dos semanales: es exactamente el fallo que
    # hacía que los asaltos del Vacío se marcaran junto con el Omnium.
    seen = {}
    clashes = []
    for task, groups in built.items():
        for ids, _ in groups:
            for qid in ids:
                if qid in seen and seen[qid] != task:
                    clashes.append((qid, seen[qid], task))
                seen[qid] = task
    if clashes:
        print('COLISIONES:', clashes, file=sys.stderr)
        return 1

    for task, groups in built.items():
        total = sum(len(i) for i, _ in groups)
        detail = ' + '.join(f"{len(i)}{'' if r else ' (rotatoria)'}" for i, r in groups)
        print(f'{task:26} {total:>4} misiones   [{detail}]')

    if '--write' not in sys.argv:
        print('\n(sin --write no se toca el catálogo)')
        return 0

    path = os.path.join(ASSETS, 'catalog.json')
    with open(path, encoding='utf-8') as fh:
        catalog = json.load(fh)
    changed = 0
    for task in catalog['weeklyTasks']:
        groups = built.get(task['id'])
        if not groups:
            continue
        rule = rule_for(groups)
        if rule is None:
            continue
        old = task.get('detectionRule')
        # Las reglas que no son de misión (estadística, reputación) se conservan
        # como alternativa: cubren al jugador sin la misión concreta.
        extra = [r for r in (old.get('rules', []) if old.get('type') == 'AnyOf' else [old])
                 if r.get('type') not in ('QuestCompleted', 'AnyOf')]
        if extra:
            rule = {'type': 'AnyOf', 'rules': (rule['rules'] if rule['type'] == 'AnyOf' else [rule]) + extra}
        if old != rule:
            task['detectionRule'] = rule
            changed += 1
    catalog['catalogVersion'] = catalog.get('catalogVersion', 0) + 1
    with open(path, 'w', encoding='utf-8') as fh:
        json.dump(catalog, fh, ensure_ascii=False, indent=2)
        fh.write('\n')
    print(f'\ncatalog.json actualizado: {changed} reglas, versión {catalog["catalogVersion"]}')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
