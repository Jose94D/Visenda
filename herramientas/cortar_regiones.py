"""Corta las teselas de Valhalla de todo Chile en un paquete .tar por región.

Todas las regiones salen del MISMO build nacional: así las teselas de borde son idénticas
en ambos paquetes y, al juntarlos en el teléfono, las rutas cruzan regiones sin problemas.

Uso: python cortar_regiones.py <dir_teselas> <regiones.json> <dir_salida>
"""
import json
import math
import sys
import tarfile
from pathlib import Path

# Tamaño en grados de las teselas de cada nivel de la jerarquía de Valhalla (3 = transporte público, no se usa).
TAMANO_NIVEL = {0: 4.0, 1: 1.0, 2: 0.25}


def id_tesela(nivel, lon, lat):
    tam = TAMANO_NIVEL[nivel]
    columnas = int(360 / tam)
    return math.floor((lat + 90) / tam) * columnas + math.floor((lon + 180) / tam)


def rectangulo_tesela(nivel, tid):
    tam = TAMANO_NIVEL[nivel]
    fila, col = divmod(tid, int(360 / tam))
    oeste, sur = -180 + col * tam, -90 + fila * tam
    return oeste, sur, oeste + tam, sur + tam


def nivel_e_id(ruta_relativa):
    """'2/000/305/707.gph' -> (2, 305707)"""
    partes = ruta_relativa.parts
    return int(partes[0]), int("".join(partes[1:]).removesuffix(".gph"))


def se_cruzan(a, b):
    return a[0] < b[2] and b[0] < a[2] and a[1] < b[3] and b[1] < a[3]


def cortar(dir_teselas, regiones, dir_salida):
    teselas = [p.relative_to(dir_teselas) for p in dir_teselas.rglob("*.gph")]
    print(f"{len(teselas)} teselas en el build nacional")
    dir_salida.mkdir(parents=True, exist_ok=True)
    for nombre, rect in regiones.items():
        if nombre.startswith("_"):
            continue
        elegidas = []
        for rel in teselas:
            nivel, tid = nivel_e_id(rel)
            if nivel in TAMANO_NIVEL and se_cruzan(rectangulo_tesela(nivel, tid), rect):
                elegidas.append(rel)
        destino = dir_salida / f"{nombre}.tar"
        with tarfile.open(destino, "w") as tar:
            for rel in elegidas:
                tar.add(dir_teselas / rel, arcname=rel.as_posix())
        print(f"{nombre}: {len(elegidas)} teselas, {destino.stat().st_size / 1e6:.1f} MB")


def autoprueba():
    # Concepción debe caer dentro de la tesela que le corresponde, en cada nivel.
    lon, lat = -73.05, -36.82
    for nivel in TAMANO_NIVEL:
        o, s, e, n = rectangulo_tesela(nivel, id_tesela(nivel, lon, lat))
        assert o <= lon < e and s <= lat < n, nivel
    assert nivel_e_id(Path("2/000/305/707.gph")) == (2, 305707)
    assert se_cruzan((0, 0, 1, 1), (0.5, 0.5, 2, 2)) and not se_cruzan((0, 0, 1, 1), (1, 1, 2, 2))


if __name__ == "__main__":
    autoprueba()
    if len(sys.argv) == 4:
        cortar(Path(sys.argv[1]), json.loads(Path(sys.argv[2]).read_text(encoding="utf-8")), Path(sys.argv[3]))
    else:
        print("autoprueba OK. Uso: python cortar_regiones.py <dir_teselas> <regiones.json> <dir_salida>")
