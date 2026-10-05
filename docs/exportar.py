"""
Exporta un documento Markdown de `docs/` a PDF y Word (.docx).

Por que existe: la entrega academica se presenta en Word editable + PDF, pero la
fuente de verdad es el Markdown del repositorio. Mantener tres copias a mano es
como se desincronizan los documentos; aqui las dos salidas se generan del mismo
archivo cada vez.

Uso:
    python docs/exportar.py docs/08-casos-de-uso.md "C:/ruta/salida"

Que hace:
  1. Renderiza cada bloque ```mermaid``` a PNG con Chromium (Playwright).
  2. Arma un HTML con estilos de impresion y lo convierte a PDF.
  3. Arma un .docx con encabezados, tablas, listas, citas y las mismas imagenes.

Requisitos: playwright (con chromium instalado), markdown, python-docx.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

from docx import Document
from docx.enum.table import WD_TABLE_ALIGNMENT
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.shared import Pt, RGBColor, Inches
from playwright.sync_api import sync_playwright

MERMAID_CDN = "https://cdn.jsdelivr.net/npm/mermaid@11/dist/mermaid.min.js"
ANCHO_UTIL_PULGADAS = 6.3  # ancho de pagina carta menos margenes
ALTO_UTIL_PULGADAS = 8.4   # alto util, para que un diagrama alto no se parta


# ---------------------------------------------------------------- utilidades

def partir_bloques(texto: str):
    """Divide el markdown en bloques (parrafo, tabla, lista, cita, mermaid...)."""
    lineas = texto.split("\n")
    bloques = []
    i = 0
    while i < len(lineas):
        linea = lineas[i]

        if linea.startswith("```"):
            lenguaje = linea[3:].strip()
            i += 1
            cuerpo = []
            while i < len(lineas) and not lineas[i].startswith("```"):
                cuerpo.append(lineas[i])
                i += 1
            i += 1
            bloques.append(("mermaid" if lenguaje == "mermaid" else "codigo",
                            "\n".join(cuerpo)))
            continue

        if not linea.strip():
            i += 1
            continue

        if linea.startswith("#"):
            nivel = len(linea) - len(linea.lstrip("#"))
            bloques.append(("h%d" % nivel, linea.lstrip("#").strip()))
            i += 1
            continue

        imagen = re.fullmatch(r"!\[([^\]]*)\]\(([^)]+)\)", linea.strip())
        if imagen:
            bloques.append(("imagen", (imagen.group(2), imagen.group(1))))
            i += 1
            continue

        if re.match(r"^-{3,}\s*$", linea):
            bloques.append(("hr", ""))
            i += 1
            continue

        if linea.lstrip().startswith("|"):
            filas = []
            while i < len(lineas) and lineas[i].lstrip().startswith("|"):
                filas.append(lineas[i].strip())
                i += 1
            bloques.append(("tabla", filas))
            continue

        if linea.startswith(">"):
            cita = []
            while i < len(lineas) and lineas[i].startswith(">"):
                cita.append(lineas[i].lstrip(">").strip())
                i += 1
            bloques.append(("cita", " ".join(c for c in cita if c)))
            continue

        if re.match(r"^\s*\d+\.\s", linea) or re.match(r"^\s*[-*]\s", linea):
            items = []
            ordenada = bool(re.match(r"^\s*\d+\.\s", linea))
            while i < len(lineas) and (re.match(r"^\s*\d+\.\s", lineas[i])
                                       or re.match(r"^\s*[-*]\s", lineas[i])
                                       or (lineas[i].startswith("   ") and lineas[i].strip())):
                actual = lineas[i]
                if re.match(r"^\s*\d+\.\s", actual) or re.match(r"^\s*[-*]\s", actual):
                    items.append(re.sub(r"^\s*(?:\d+\.|[-*])\s+", "", actual))
                elif items:
                    items[-1] += " " + actual.strip()
                i += 1
            bloques.append(("lista-num" if ordenada else "lista", items))
            continue

        parrafo = []
        while i < len(lineas) and lineas[i].strip() and not lineas[i].startswith(("#", ">", "|", "```")) \
                and not re.match(r"^\s*(?:\d+\.|[-*])\s", lineas[i]) \
                and not re.match(r"^-{3,}\s*$", lineas[i]):
            parrafo.append(lineas[i].strip())
            i += 1
        if parrafo:
            bloques.append(("parrafo", " ".join(parrafo)))
    return bloques


def celdas(fila: str):
    return [c.strip() for c in fila.strip().strip("|").split("|")]


def es_separador(fila: str):
    return all(re.fullmatch(r":?-{2,}:?", c.replace(" ", "")) for c in celdas(fila) if c)


# ------------------------------------------------------------------ mermaid

PAGINA_MERMAID = """<!doctype html><html><head><meta charset="utf-8">
<script src="%s"></script>
<style>body{margin:0;padding:24px;background:#fff;font-family:'Segoe UI',system-ui,sans-serif}
#lienzo{display:inline-block}</style></head>
<body><div id="lienzo"></div></body></html>""" % MERMAID_CDN

# El codigo del diagrama se le pasa a mermaid.render() COMO DATO, nunca escrito
# dentro del HTML: un classDiagram trae <<raiz del agregado>> y <br/>, que el
# navegador interpretaria como etiquetas y el diagrama llegaria mutilado
# ("Syntax error in text").
JS_RENDER = """async (codigo) => {
  const destino = document.getElementById('lienzo');
  destino.innerHTML = '';
  const { svg } = await mermaid.render('g' + Date.now(), codigo);
  destino.innerHTML = svg;
  // Mermaid deja el SVG con width:100%, y dentro de un contenedor que se ajusta
  // al contenido eso colapsa el diagrama a un pañuelo. Se le devuelve su tamaño
  // natural, tomado del viewBox.
  const grafico = destino.querySelector('svg');
  const caja = grafico.viewBox.baseVal;
  grafico.style.maxWidth = 'none';
  grafico.style.width = caja.width + 'px';
  grafico.style.height = caja.height + 'px';
  return [caja.width, caja.height];
}"""


def renderizar_mermaid(diagramas, destino: Path, escala: int = 2):
    """Renderiza cada diagrama a PNG y devuelve las rutas generadas."""
    destino.mkdir(parents=True, exist_ok=True)
    rutas = []
    with sync_playwright() as p:
        navegador = p.chromium.launch()
        pagina = navegador.new_page(viewport={"width": 2400, "height": 1400},
                                    device_scale_factor=escala)
        pagina.set_content(PAGINA_MERMAID, wait_until="networkidle")
        pagina.wait_for_function("typeof mermaid !== 'undefined'")
        pagina.evaluate("""mermaid.initialize({startOnLoad:false,theme:'base',
 themeVariables:{fontFamily:"'Segoe UI',system-ui,sans-serif",fontSize:'15px',
 primaryColor:'#eff4ff',primaryTextColor:'#1e40af',primaryBorderColor:'#7aa0f5',
 lineColor:'#57534e',secondaryColor:'#f5f5f4',tertiaryColor:'#fbfbfa'}});""")

        for indice, codigo in enumerate(diagramas, start=1):
            pagina.evaluate(JS_RENDER, codigo)
            pagina.wait_for_selector("#lienzo svg", timeout=30000)
            pagina.wait_for_timeout(250)
            ruta = destino / ("diagrama-%02d.png" % indice)
            pagina.locator("#lienzo").screenshot(path=str(ruta))
            rutas.append(ruta)
            print("  diagrama %d renderizado" % indice)
        navegador.close()
    return rutas


# ---------------------------------------------------------------------- PDF

CSS_IMPRESION = """
@page { size: letter; margin: 20mm 18mm 18mm 18mm; }
body { font-family: 'Segoe UI', system-ui, -apple-system, sans-serif; font-size: 10.5pt;
       line-height: 1.55; color: #1c1917; }
h1 { font-size: 21pt; color: #1d4ed8; margin: 0 0 4pt; letter-spacing: -0.02em; }
h1:not(:first-of-type) { page-break-before: always; padding-top: 6pt; }
h2 { font-size: 15pt; margin: 22pt 0 6pt; padding-bottom: 4pt;
     border-bottom: 1.5px solid #d6d3d1; page-break-after: avoid; }
h3 { font-size: 12.5pt; color: #1d4ed8; margin: 16pt 0 4pt; page-break-after: avoid; }
h4 { font-size: 11pt; margin: 12pt 0 3pt; page-break-after: avoid; }
p { margin: 0 0 7pt; }
table { border-collapse: collapse; width: 100%; margin: 6pt 0 12pt; font-size: 9pt;
        page-break-inside: avoid; }
th, td { border: 1px solid #d6d3d1; padding: 4pt 6pt; text-align: left; vertical-align: top; }
th { background: #f5f5f4; font-weight: 600; }
tr:nth-child(even) td { background: #fbfbfa; }
code { font-family: 'Cascadia Mono', Consolas, monospace; font-size: 8.8pt;
       background: #f5f5f4; padding: 1px 4px; border-radius: 3px; color: #b45309; }
blockquote { margin: 8pt 0; padding: 7pt 12pt; background: #f8fafc;
             border-left: 3px solid #1d4ed8; page-break-inside: avoid; }
blockquote p { margin: 0; }
ol, ul { margin: 0 0 9pt; padding-left: 18pt; }
li { margin-bottom: 3pt; }
hr { border: none; border-top: 1px solid #e7e5e4; margin: 16pt 0; }
img { max-width: 100%; max-height: 205mm; display: block; margin: 10pt auto;
      page-break-inside: avoid; }
.portada { text-align: center; padding: 40pt 0 24pt; border-bottom: 2px solid #1d4ed8;
           margin-bottom: 20pt; }
"""


def markdown_a_html(texto: str, imagenes, incrustar: bool = False, base: Path = None):
    """Convierte el markdown a HTML.

    Con incrustar=True las imagenes van como data URI y el HTML queda en un
    solo archivo: se puede mandar por correo sin la carpeta de imagenes al lado.
    """
    import base64
    import markdown as md

    contador = {"i": 0}

    def sustituir(match):
        ruta = imagenes[contador["i"]]
        contador["i"] += 1
        if incrustar:
            origen = "data:image/png;base64," + base64.b64encode(ruta.read_bytes()).decode("ascii")
        else:
            origen = ruta.as_uri()
        return '\n<img src="%s" alt="Diagrama %d">\n' % (origen, contador["i"])

    texto = re.sub(r"```mermaid\n.*?\n```", sustituir, texto, flags=re.DOTALL)

    def resolver_imagen(match):
        """Las imagenes propias del markdown (capturas) se resuelven contra la
        carpeta del documento: una ruta relativa no sirve ni en el PDF ni en un
        HTML que se manda por correo."""
        ruta = Path(match.group(2))
        if not ruta.is_absolute():
            ruta = (base / ruta).resolve()
        if not ruta.exists():
            return match.group(0)
        if incrustar:
            origen = "data:image/png;base64," + base64.b64encode(ruta.read_bytes()).decode("ascii")
        else:
            origen = ruta.as_uri()
        return "![%s](%s)" % (match.group(1), origen)

    if base is not None:
        texto = re.sub(r"!\[([^\]]*)\]\(([^)]+)\)", resolver_imagen, texto)

    cuerpo = md.markdown(texto, extensions=["tables", "fenced_code", "sane_lists"])
    # Los identificadores (CU-01, RF-11, RN-03) no se pueden partir en dos lineas:
    # Chromium rompe despues del guion y el PDF queda con "CU-" arriba y "18" abajo.
    cuerpo = re.sub("(CU|RF|RNF|RN|ADR)-(?=[A-Z0-9])",
                    lambda m: m.group(1) + chr(0x2011), cuerpo)
    return "<!doctype html><html><head><meta charset='utf-8'><style>%s</style></head><body>%s</body></html>" % (
        CSS_IMPRESION, cuerpo)


def exportar_pdf(html: str, destino: Path):
    destino.parent.mkdir(parents=True, exist_ok=True)
    with sync_playwright() as p:
        navegador = p.chromium.launch()
        pagina = navegador.new_page()
        pagina.set_content(html, wait_until="networkidle")
        # Sin esta espera, page.pdf() imprime antes de que las imagenes esten
        # decodificadas y el documento sale con huecos donde van los diagramas.
        pagina.wait_for_function(
            "Array.from(document.images).every(i => i.complete && i.naturalWidth > 0)",
            timeout=60000)
        pagina.wait_for_timeout(400)
        pagina.pdf(path=str(destino), format="Letter", print_background=True,
                   margin={"top": "20mm", "bottom": "18mm", "left": "18mm", "right": "18mm"},
                   display_header_footer=True,
                   header_template="<div></div>",
                   footer_template="<div style='width:100%;font-size:8pt;color:#78716c;"
                                   "font-family:Segoe UI,sans-serif;padding:0 18mm;"
                                   "display:flex;justify-content:space-between'>"
                                   "<span>LOGISTI-X &middot; INTEGRAX</span>"
                                   "<span class='pageNumber'></span></div>")
        navegador.close()


# --------------------------------------------------------------------- DOCX

def escribir_texto(parrafo, texto: str, base_negrita=False):
    """Interpreta **negrita**, *cursiva*, `codigo` y [enlace](url)."""
    texto = re.sub(r"\[([^\]]+)\]\(([^)]+)\)", r"\1", texto)
    for pieza in re.split(r"(\*\*[^*]+\*\*|`[^`]+`|\*[^*]+\*)", texto):
        if not pieza:
            continue
        if pieza.startswith("**") and pieza.endswith("**"):
            r = parrafo.add_run(pieza[2:-2]); r.bold = True
        elif pieza.startswith("`") and pieza.endswith("`"):
            r = parrafo.add_run(pieza[1:-1])
            r.font.name = "Consolas"; r.font.size = Pt(9)
            r.font.color.rgb = RGBColor(0xB4, 0x53, 0x09)
        elif pieza.startswith("*") and pieza.endswith("*") and len(pieza) > 2:
            r = parrafo.add_run(pieza[1:-1]); r.italic = True
        else:
            r = parrafo.add_run(pieza)
        if base_negrita:
            r.bold = True


def medida_imagen(ruta: Path):
    """Ancho o alto, el que toque, para que el diagrama quepa en una pagina.

    Un diagrama de clases alto puesto a ancho completo se parte entre dos
    paginas de Word y queda ilegible; cuando es mas alto que la pagina, manda
    el alto."""
    from PIL import Image

    with Image.open(ruta) as imagen:
        ancho, alto = imagen.size
    if alto / ancho > ALTO_UTIL_PULGADAS / ANCHO_UTIL_PULGADAS:
        return {"height": Inches(ALTO_UTIL_PULGADAS)}
    return {"width": Inches(ANCHO_UTIL_PULGADAS)}


def exportar_docx(bloques, imagenes, destino: Path, titulo: str, base_imagenes: Path):
    doc = Document()

    normal = doc.styles["Normal"]
    normal.font.name = "Segoe UI"
    normal.font.size = Pt(10.5)
    normal.paragraph_format.space_after = Pt(6)

    seccion = doc.sections[0]
    seccion.top_margin = Inches(0.9)
    seccion.bottom_margin = Inches(0.8)
    seccion.left_margin = Inches(0.8)
    seccion.right_margin = Inches(0.8)

    indice_imagen = 0
    capitulos = 0
    for tipo, contenido in bloques:
        if tipo.startswith("h") and tipo[1:].isdigit():
            nivel = min(int(tipo[1:]), 4)
            if nivel == 1:
                capitulos += 1
                if capitulos > 1:        # cada capitulo empieza en pagina nueva
                    doc.add_page_break()
            p = doc.add_heading(level=nivel)
            escribir_texto(p, contenido)
            continue

        if tipo == "parrafo":
            escribir_texto(doc.add_paragraph(), contenido)
            continue

        if tipo == "cita":
            p = doc.add_paragraph()
            p.paragraph_format.left_indent = Inches(0.3)
            p.paragraph_format.space_before = Pt(6)
            escribir_texto(p, contenido)
            for run in p.runs:
                run.font.size = Pt(10)
                run.font.color.rgb = RGBColor(0x44, 0x40, 0x3C)
            continue

        if tipo == "lista":
            for item in contenido:
                escribir_texto(doc.add_paragraph(style="List Bullet"), item)
            continue

        if tipo == "lista-num":
            for item in contenido:
                escribir_texto(doc.add_paragraph(style="List Number"), item)
            continue

        if tipo == "codigo":
            p = doc.add_paragraph()
            r = p.add_run(contenido)
            r.font.name = "Consolas"; r.font.size = Pt(8.5)
            continue

        if tipo == "mermaid":
            if indice_imagen < len(imagenes):
                doc.add_picture(str(imagenes[indice_imagen]),
                                **medida_imagen(imagenes[indice_imagen]))
                doc.paragraphs[-1].alignment = WD_ALIGN_PARAGRAPH.CENTER
                indice_imagen += 1
            continue

        if tipo == "imagen":
            ruta = Path(contenido[0])
            if not ruta.is_absolute():
                ruta = (base_imagenes / ruta).resolve()
            if ruta.exists():
                doc.add_picture(str(ruta), **medida_imagen(ruta))
                doc.paragraphs[-1].alignment = WD_ALIGN_PARAGRAPH.CENTER
                if contenido[1]:
                    pie = doc.add_paragraph()
                    pie.alignment = WD_ALIGN_PARAGRAPH.CENTER
                    r = pie.add_run(contenido[1])
                    r.italic = True
                    r.font.size = Pt(9)
                    r.font.color.rgb = RGBColor(0x57, 0x53, 0x4E)
            continue

        if tipo == "tabla":
            filas = [f for f in contenido if not es_separador(f)]
            if not filas:
                continue
            datos = [celdas(f) for f in filas]
            columnas = max(len(f) for f in datos)
            tabla = doc.add_table(rows=0, cols=columnas)
            tabla.style = "Table Grid"
            tabla.alignment = WD_TABLE_ALIGNMENT.CENTER
            for numero, fila in enumerate(datos):
                celdas_tabla = tabla.add_row().cells
                for columna in range(columnas):
                    texto = fila[columna] if columna < len(fila) else ""
                    celda = celdas_tabla[columna]
                    celda.text = ""
                    p = celda.paragraphs[0]
                    p.paragraph_format.space_after = Pt(2)
                    escribir_texto(p, texto, base_negrita=(numero == 0))
                    for run in p.runs:
                        run.font.size = Pt(8.5)
            doc.add_paragraph()
            continue

        if tipo == "hr":
            continue

    doc.core_properties.title = titulo
    doc.core_properties.author = "Amaury Moreno Vidal"
    destino.parent.mkdir(parents=True, exist_ok=True)
    doc.save(str(destino))


# --------------------------------------------------------------------- main

PORTADA = """# LOGISTI-X — Gestión de Despachos y Entregas

**Documentación técnica completa del microproyecto 2.5 de INTEGRAX**

Corporación Universitaria Unisabaneta

Electiva Profesional II · Integración de Aplicaciones y Sistemas

Docente: Esp. Mauricio Páez Agudelo

Estudiante: Amaury Moreno Vidal

Entrega definitiva: viernes 18 de septiembre de 2026

---

## Contenido

| Capítulo | Qué contiene |
|---|---|
| 01 · Análisis y requisitos | El problema, el alcance, los actores, los 11 RF, los 8 RNF y las 14 reglas de negocio |
| 02 · Modelo de la solución | Modelo de dominio, modelo de datos, máquina de estados y diagramas de secuencia |
| 03 · Arquitectura | Capas, puertos y adaptadores, decisiones técnicas y despliegue |
| 04 · Contratos de integración | El JSON exacto que se espera de SALES-X y CRM-X, y el que entrega LOGISTI-X |
| 05 · Pruebas | Las 99 pruebas automáticas: qué verifica cada una y por qué |
| 06 · Demostración | Guion de la sustentación |
| 07 · Plan de integración | Cómo conectarse con los otros cinco microproyectos sin desarmar el modelo |
| 08 · Casos de uso | Los 20 casos de uso completos, con flujos, excepciones y trazabilidad |
| Anexo A · Decisiones de arquitectura | Las 11 ADR, con contexto, decisión y consecuencias |
| Anexo B · El sistema funcionando | Capturas del panel operativo, en escritorio y en móvil |

"""


def documento_completo(carpeta_docs: Path) -> str:
    """Une portada + capitulos 01..08 + anexo de ADR en un solo markdown."""
    partes = [PORTADA]
    for archivo in sorted(carpeta_docs.glob("0*.md")):
        partes.append(archivo.read_text(encoding="utf-8").strip())
    partes.append("# Anexo A · Decisiones de arquitectura (ADR)")
    for adr in sorted((carpeta_docs / "adr").glob("ADR-*.md")):
        texto = adr.read_text(encoding="utf-8").strip()
        # Las ADR empiezan con "# ADR-00X ..."; se bajan un nivel para que
        # queden como secciones del anexo y no como capitulos sueltos.
        texto = re.sub(r"^# ", "## ", texto, count=1, flags=re.MULTILINE)
        partes.append(texto)

    capturas = sorted((carpeta_docs / "capturas").glob("*.png"))
    if capturas:
        partes.append("# Anexo B · El sistema funcionando")
        partes.append("Capturas del panel operativo tomadas de la aplicación en "
                      "ejecución. La última es el panel en un teléfono emulado, "
                      "que es como se verificó el RNF-07.")
        for captura in capturas:
            partes.append("![%s](capturas/%s)"
                          % (captura.stem.split(" - ", 1)[-1], captura.name))
    return "\n\n".join(partes)


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        raise SystemExit(1)

    if sys.argv[1] == "--todo":
        carpeta = Path(__file__).resolve().parent
        salida = Path(sys.argv[2]).resolve() if len(sys.argv) > 2 else carpeta / "_export"
        texto = documento_completo(carpeta)
        origen = carpeta / "LOGISTI-X - Documentacion completa.md"
        origen.write_text(texto, encoding="utf-8")
    else:
        origen = Path(sys.argv[1]).resolve()
        salida = Path(sys.argv[2]).resolve() if len(sys.argv) > 2 else origen.parent / "_export"
        texto = origen.read_text(encoding="utf-8")

    diagramas = re.findall(r"```mermaid\n(.*?)\n```", texto, flags=re.DOTALL)
    print("%d diagrama(s) por renderizar" % len(diagramas))
    imagenes = renderizar_mermaid(diagramas, salida / "_diagramas") if diagramas else []

    titulo = texto.split("\n", 1)[0].lstrip("#").strip()
    nombre = origen.stem

    # El PDF se arma con las imagenes incrustadas: asi no hay peticiones de
    # archivo que puedan llegar tarde a la impresion.
    html_incrustado = markdown_a_html(texto, imagenes, incrustar=True, base=origen.parent)
    exportar_pdf(html_incrustado, salida / (nombre + ".pdf"))
    print("PDF  ->", salida / (nombre + ".pdf"))

    # HTML de un solo archivo: es la version que se abre sin Word ni lector de
    # PDF, y la que se puede mandar por correo sin carpeta de imagenes al lado.
    (salida / (nombre + ".html")).write_text(html_incrustado, encoding="utf-8")
    print("HTML ->", salida / (nombre + ".html"))

    exportar_docx(partir_bloques(texto), imagenes, salida / (nombre + ".docx"), titulo,
                  origen.parent)
    print("DOCX ->", salida / (nombre + ".docx"))


if __name__ == "__main__":
    main()
