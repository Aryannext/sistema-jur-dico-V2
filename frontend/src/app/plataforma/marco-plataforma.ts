import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { Autenticacion } from '../nucleo/autenticacion';

/**
 * El marco de la plataforma: barra lateral y contenido. RN-10.
 *
 * <p>Es una zona aparte de la del despacho, y la diferencia no es cosmética.
 * El Administrador de Plataforma <strong>nunca accede al contenido de un
 * expediente</strong>: opera la plataforma, no ejerce la abogacía.
 *
 * <p>Mientras esta zona no existió, este rol caía por descarte en la del
 * despacho y veía un menú con Procesos, Clientes y Reportes. El backend le
 * negaba los datos —así que veía un sistema aparentemente roto—, pero además
 * la interfaz le estaba <em>ofreciendo</em> justo lo que RN-10 le prohíbe.
 *
 * <p>Por eso la barra lateral es corta y dice «La plataforma» en vez de «Su
 * despacho»: no es una versión recortada del menú del despacho, es otro
 * trabajo.
 */
@Component({
  selector: 'sgpj-marco-plataforma',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './marco-plataforma.html',
  // Se reutiliza la hoja del despacho en vez de copiarla: es el mismo lenguaje
  // visual, y duplicarla garantizaría que las dos se separen con el tiempo.
  styleUrl: '../despacho/marco/marco.css',
})
export class MarcoPlataforma {

  protected readonly autenticacion = inject(Autenticacion);

  async salir(): Promise<void> {
    await this.autenticacion.salir();
  }
}
