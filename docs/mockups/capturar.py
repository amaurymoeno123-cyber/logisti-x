"""Captura cada pantalla del prototipo (con anotaciones) y algunos estados.
Uso: python docs/mockups/capturar.py"""
from pathlib import Path
from playwright.sync_api import sync_playwright

RAIZ = Path(__file__).parent
URL = (RAIZ / "index.html").resolve().as_uri()
SALIDA = RAIZ / "capturas"
SALIDA.mkdir(exist_ok=True)

PANTALLAS = ['mapa','acceso','tablero','registrar','detalle','asignar','guia','ruta','entregar',
             'novedad','reprogramar','devolver','trazabilidad','reportes','transportadores','rutas','integracion']
ESTADOS = [('tablero','carga'),('tablero','vacio'),('registrar','error'),('asignar','error'),
           ('entregar','vacio'),('reprogramar','error'),('integracion','error')]

with sync_playwright() as p:
    nav = p.chromium.launch()
    pag = nav.new_page(viewport={"width": 1680, "height": 1000}, device_scale_factor=1.5)
    trabajos = [(k, 'normal') for k in PANTALLAS] + ESTADOS
    for i, (k, e) in enumerate(trabajos):
        sufijo = '' if e == 'normal' else '/' + e
        pag.goto(URL + '#' + k + sufijo)
        pag.reload()
        pag.wait_for_timeout(250)
        nombre = f"{i:02d}-{k}{'' if e=='normal' else '-'+e}.png"
        pag.locator('.stage').screenshot(path=str(SALIDA / nombre))
        print(nombre)
    nav.close()
