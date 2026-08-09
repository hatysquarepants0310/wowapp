#!/usr/bin/env python3
"""Genera el índice de objetos (nombre y calidad) para la casa de subastas.

La API de subastas solo devuelve IDs de objeto: 251.000 pujas con 10.000
objetos distintos y ni un nombre. Resolverlos uno a uno serían 10.000
peticiones por actualización, así que los nombres se hornean aquí desde
ItemSparse y viajan dentro del APK. El resultado es que la app resuelve
cualquier ID al instante y sin red.

Solo se guardan los objetos que de verdad aparecen en la casa de subastas: la
lista sale de un muestreo real (mercancías de la región más dos reinos
conectados grandes), unos 26.000 objetos frente a los 164.000 de ItemSparse.
Hornear el catálogo entero añadiría 5 MB al APK para nombres que nadie va a
mirar; lo que falte se resuelve en caliente contra /data/wow/item/{id} y se
guarda en la caché local.

Uso:  python3 tools/build_items.py <ItemSparse_esES.csv> <ItemSparse_enUS.csv> <ids.txt>
"""
import csv
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'app/src/main/assets/catalog')

csv.field_size_limit(10 * 1024 * 1024)


def read(path, keep):
    names, quality = {}, {}
    with open(path, encoding='utf-8', newline='') as fh:
        for row in csv.DictReader(fh):
            name = (row.get('Display_lang') or '').strip()
            if not name:
                continue
            try:
                item_id = int(row['ID'])
            except (KeyError, ValueError):
                continue
            if item_id not in keep:
                continue
            names[item_id] = name
            q = row.get('OverallQualityID')
            if q not in (None, ''):
                quality[item_id] = int(q)
    return names, quality


def main():
    if len(sys.argv) < 4:
        print(__doc__)
        return 1
    with open(sys.argv[3], encoding='utf-8') as fh:
        keep = {int(line) for line in fh if line.strip()}
    es, quality = read(sys.argv[1], keep)
    en, quality_en = read(sys.argv[2], keep)
    quality = {**quality_en, **quality}

    # El índice de calidad solo hace falta para los objetos que tienen nombre.
    known = set(es) | set(en)
    quality = {k: v for k, v in quality.items() if k in known and v}

    for loc, data in (('es', es), ('en', en)):
        path = os.path.join(OUT, f'items_{loc}.json')
        with open(path, 'w', encoding='utf-8') as fh:
            json.dump({str(k): v for k, v in sorted(data.items())}, fh,
                      ensure_ascii=False, separators=(',', ':'))
        print(f'items_{loc}.json  {len(data)} objetos  {os.path.getsize(path) // 1024} KB')

    path = os.path.join(OUT, 'item_quality.json')
    with open(path, 'w', encoding='utf-8') as fh:
        json.dump({str(k): v for k, v in sorted(quality.items())}, fh, separators=(',', ':'))
    print(f'item_quality.json {len(quality)} objetos  {os.path.getsize(path) // 1024} KB')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
