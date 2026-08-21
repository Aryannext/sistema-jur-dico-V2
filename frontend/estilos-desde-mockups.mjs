// Genera src/styles.css a partir del diseño aprobado.
//
//   node estilos-desde-mockups.mjs
//
// Los mockups de diseno/mockups/_base/ son la fuente: los tokens, la
// tipografia y los componentes se escriben ALLI y llegan aqui por copia
// automatica. Si se editaran a mano los dos lados, la interfaz real y el
// diseno aprobado empezarian a separarse sin que nadie lo notara.
//
// Lo unico que se anade es el bloque final: la app real no es una lamina de
// 1400 px de ancho.

import fs from 'node:fs'
import path from 'node:path'

const base = path.resolve(import.meta.dirname, '../diseno/mockups/_base')
const destino = path.resolve(import.meta.dirname, 'src/styles.css')

const fuente = fs.readFileSync(path.join(base, 'manrope.b64'), 'utf8').trim()

const css = ['estilos.css', 'componentes.css']
  .map(f => fs.readFileSync(path.join(base, f), 'utf8'))
  .join('\n')
  .replace('__FONT_B64__', fuente)

const adaptacion = `

/* ============================================================
   Adaptacion a la aplicacion real
   ------------------------------------------------------------
   Generado por estilos-desde-mockups.mjs — NO editar a mano.
   Lo de arriba viene tal cual del diseno aprobado; esto es lo
   unico que cambia entre una lamina y una pantalla de verdad.
   ============================================================ */

html, body { height: 100%; }

/* El mockup fija 1400 px porque es un artboard. La app ocupa lo que haya. */
.app {
  width: auto;
  min-width: 0;
  min-height: 100vh;
}

/* La barra lateral se queda fija y solo desplaza el contenido. */
.lado {
  position: sticky;
  top: 0;
  height: 100vh;
  overflow-y: auto;
}

/* En el mockup los enlaces no llevan a ninguna parte. Aqui si. */
.lado a, .btn, .sel, .pes a, .ir, .accion, .ver {
  cursor: pointer;
}
.btn:disabled {
  opacity: .55;
  cursor: not-allowed;
}
.lado a:focus-visible,
.btn:focus-visible,
input:focus-visible {
  outline: 2px solid var(--marca);
  outline-offset: 2px;
}

/* Los campos del mockup son cajas pintadas; estos se escriben. */
.campo input {
  font: inherit;
  color: inherit;
  background: transparent;
  border: 0;
  outline: none;
  width: 100%;
  padding: 0;
}
.campo input::placeholder { color: var(--apagado); }
.caja-c:focus-within {
  border-color: var(--marca);
  box-shadow: 0 0 0 3px var(--marca-suave);
}

/* Estados que una lamina no tiene: cargando y vacio. */
.cargando {
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 34px 24px;
  color: var(--apagado);
  font-size: 14px;
}
.girando {
  width: 17px;
  height: 17px;
  border: 2px solid var(--hilo);
  border-top-color: var(--marca);
  border-radius: 50%;
  animation: girar .7s linear infinite;
}
@keyframes girar { to { transform: rotate(360deg); } }

@media (prefers-reduced-motion: reduce) {
  .girando { animation-duration: 2.4s; }
}
`

fs.writeFileSync(destino, css + adaptacion)
console.log(`src/styles.css  ${(fs.statSync(destino).size / 1024).toFixed(0)} KB  (desde diseno/mockups/_base)`)
