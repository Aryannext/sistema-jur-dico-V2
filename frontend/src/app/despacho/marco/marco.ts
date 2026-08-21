import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { Autenticacion } from '../../nucleo/autenticacion';
import { Vigilancia } from '../vigilancia';

/**
 * El marco del despacho: barra lateral y contenido.
 *
 * <p>Es una ruta con hijos y no un componente que se incrusta en cada
 * pantalla. Así la barra lateral <strong>no se vuelve a montar</strong> al
 * cambiar de sección: no parpadea, no vuelve a pedir el contador de vencidos y
 * no pierde el punto donde estaba desplazada.
 */
@Component({
  selector: 'sgpj-marco',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './marco.html',
  styleUrl: './marco.css',
})
export class Marco {

  protected readonly autenticacion = inject(Autenticacion);
  protected readonly vigilancia = inject(Vigilancia);

  constructor() {
    // El contador de la barra lateral necesita los mismos datos que el panel.
    // Pedirlo aquí evita que aparezca vacío mientras carga la pantalla hija.
    void this.vigilancia.asegurarCargado();
  }

  async salir(): Promise<void> {
    await this.autenticacion.salir();
  }
}
