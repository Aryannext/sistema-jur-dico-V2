// Constructor de mockups de Iuris.
//
// Cada pantalla de pantallas/*.html trae solo su contenido; el estilo, la
// tipografia y el marco (barra lateral) se escriben UNA vez y se comparten.
// Cambiar el color de marca es cambiar una linea de _base/estilos.css, no
// diecisiete archivos.
//
//   node _base/construir.mjs            -> genera todos los artboards
//   node _base/construir.mjs Reportes   -> genera solo uno
//
// Cabecera esperada al inicio de cada pantalla:
//   <!--@ marco:despacho | activo:Reportes | nombre:Reportes -->
//
//   marco   despacho | portal | plataforma | ninguno
//   activo  texto del enlace que queda marcado en la barra lateral
//   nombre  nombre del artboard (por defecto, el del archivo capitalizado)

import fs from 'node:fs'
import path from 'node:path'

const raiz = path.resolve(import.meta.dirname, '..')
const base = path.join(raiz, '_base')

const fuente = fs.readFileSync(path.join(base, 'manrope.b64'), 'utf8').trim()
// El orden importa: los tokens y el marco primero, los componentes despues.
const estilos = ['estilos.css', 'componentes.css']
  .map(f => fs.readFileSync(path.join(base, f), 'utf8'))
  .join('\n')
  .replace('__FONT_B64__', fuente)

const marcos = {}
for (const f of fs.readdirSync(base)) {
  const m = f.match(/^lado-(.+)\.html$/)
  if (m) marcos[m[1]] = fs.readFileSync(path.join(base, f), 'utf8')
}

/**
 * Marca el enlace activo de la barra lateral por su texto visible.
 *
 * Se busca por texto y no por un identificador aparte para que la barra siga
 * siendo HTML legible: quien la abra ve exactamente lo que se renderiza.
 */
function marcarActivo(html, etiqueta) {
  const limpio = html.replace(/\s+class="on"/g, '')
  if (!etiqueta) return limpio

  // Las tildes viajan como entidades HTML; hay que devolverlas antes de
  // comparar, o "Configuración" nunca encontraria a "Configuraci&oacute;n".
  const tildes = {
    aacute: 'á', eacute: 'é', iacute: 'í', oacute: 'ó', uacute: 'ú',
    ntilde: 'ñ', Aacute: 'Á', Eacute: 'É', Iacute: 'Í', Oacute: 'Ó',
    Uacute: 'Ú', Ntilde: 'Ñ'
  }
  const legible = s => s
    .replace(/<[^>]+>/g, ' ')
    .replace(/&([A-Za-z]+);/g, (todo, n) => tildes[n] ?? ' ')
    .toLowerCase()

  const enlaces = [...limpio.matchAll(/<a\s[^>]*>[\s\S]*?<\/a>/g)]
  const objetivo = enlaces.find(a => legible(a[0]).includes(etiqueta.toLowerCase()))
  if (!objetivo) {
    console.warn(`  aviso: no hay enlace "${etiqueta}" en la barra lateral`)
    return limpio
  }
  return limpio.replace(objetivo[0], objetivo[0].replace('<a ', '<a class="on" '))
}

function cabecera(texto) {
  const m = texto.match(/^<!--@([\s\S]*?)-->/)
  const datos = { marco: 'despacho' }
  if (m) {
    for (const par of m[1].split('|')) {
      const i = par.indexOf(':')
      if (i > 0) datos[par.slice(0, i).trim()] = par.slice(i + 1).trim()
    }
  }
  return datos
}

const soloEste = process.argv[2]
const dir = path.join(raiz, 'pantallas')
let hechos = 0

for (const archivo of fs.readdirSync(dir).filter(f => f.endsWith('.html')).sort()) {
  const texto = fs.readFileSync(path.join(dir, archivo), 'utf8')
  const meta = cabecera(texto)
  const cuerpo = texto.replace(/^<!--@[\s\S]*?-->\s*/, '')

  const nombre = meta.nombre
    || archivo.replace(/\.html$/, '').replace(/(^|-)([a-z])/g, (_, s, c) => c.toUpperCase())

  if (soloEste && nombre !== soloEste) continue

  if (meta.marco !== 'ninguno' && !marcos[meta.marco]) {
    console.error(`  ERROR: ${archivo} pide el marco "${meta.marco}" y no existe`)
    process.exitCode = 1
    continue
  }

  // Estilos propios de la pantalla: van despues de los compartidos para poder
  // afinar sin tocar la base.
  const propio = cuerpo.match(/<style>[\s\S]*?<\/style>/)
  const contenido = propio ? cuerpo.replace(propio[0], '') : cuerpo

  const lado = meta.marco === 'ninguno' ? '' : marcarActivo(marcos[meta.marco], meta.activo)
  const clase = meta.marco === 'ninguno' ? 'app suelta' : 'app'

  const salida = [
    '<script src="./support.js"></script>',
    '<style>',
    estilos,
    '</style>',
    propio ? propio[0] : '',
    `<div class="${clase}">`,
    lado,
    contenido.trim(),
    '</div>',
    ''
  ].filter(Boolean).join('\n')

  const destino = path.join(raiz, `${nombre}.dc.html`)
  fs.writeFileSync(destino, salida)
  console.log(`  ${nombre}.dc.html  ${(salida.length / 1024).toFixed(0)} KB  (${meta.marco})`)
  hechos++
}

if (soloEste && hechos === 0) {
  console.error(`  ERROR: no hay ninguna pantalla llamada "${soloEste}"`)
  process.exitCode = 1
}
console.log(`\n${hechos} pantalla(s)`)
