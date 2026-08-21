import { Component, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { Autenticacion } from '../nucleo/autenticacion';
import { PerfilCliente } from '../nucleo/modelos';
import { Portal } from './portal.servicio';

/**
 * El marco del portal del cliente. RF-28 · HU-32.
 *
 * <p>Marco propio y no el del despacho: la barra lateral del abogado nombra
 * cosas que el cliente no tiene —vencimientos, catálogos, usuarios— y verlas
 * apagadas le diría que hay un sistema más grande al que no llega. Aquí solo
 * está lo suyo.
 */
@Component({
  selector: 'sgpj-marco-portal',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './marco-portal.html',
  styleUrl: './portal.css',
})
export class MarcoPortal {

  private readonly servicio = inject(Portal);
  protected readonly autenticacion = inject(Autenticacion);

  protected readonly perfil = signal<PerfilCliente | null>(null);

  constructor() {
    void this.servicio.miPerfil()
      .then(p => this.perfil.set(p))
      .catch(() => this.perfil.set(null));
  }

  protected iniciales(nombre: string): string {
    const partes = nombre.trim().split(/\s+/);
    if (partes.length === 0 || partes[0] === '') return '?';
    const primera = partes[0][0];
    const segunda = partes.length > 1 ? partes[partes.length - 1][0] : '';
    return (primera + segunda).toUpperCase();
  }

  async salir(): Promise<void> {
    await this.autenticacion.salir();
  }
}
