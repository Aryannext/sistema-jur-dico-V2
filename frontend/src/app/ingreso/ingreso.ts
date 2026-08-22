import { Component, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

import { Autenticacion } from '../nucleo/autenticacion';
import { mensajeDeError } from '../nucleo/mensajes';

/**
 * Pantalla de ingreso. RF-04 · HU-04.
 *
 * <p>No hay «crear cuenta», y no es un olvido: los usuarios los crea el
 * despacho y a los clientes los habilita su abogado (RN-43 · D-15). Si
 * cualquiera pudiera registrarse diciendo un nombre, un tercero reclamaría el
 * expediente de otra persona.
 */
@Component({
  selector: 'sgpj-ingreso',
  templateUrl: './ingreso.html',
  styleUrl: './ingreso.css',
})
export class Ingreso {

  private readonly autenticacion = inject(Autenticacion);
  private readonly router = inject(Router);

  readonly correo = signal('');
  readonly contrasena = signal('');
  readonly verClave = signal(false);
  readonly enviando = signal(false);
  readonly error = signal<string | null>(null);

  escribirCorreo(evento: Event): void {
    this.correo.set((evento.target as HTMLInputElement).value);
    this.error.set(null);
  }

  escribirContrasena(evento: Event): void {
    this.contrasena.set((evento.target as HTMLInputElement).value);
    this.error.set(null);
  }

  async entrar(evento: Event): Promise<void> {
    evento.preventDefault();

    if (this.enviando() || !this.correo().trim() || !this.contrasena()) {
      return;
    }

    this.enviando.set(true);
    this.error.set(null);

    try {
      await this.autenticacion.entrar(this.correo().trim(), this.contrasena());

      // Cada quien a SU zona, no a una fija. Estaba escrito '/vencimientos'
      // a secas y funcionaba de rebote: los guardianes reexpedían al cliente
      // a su portal. Funcionar de rebote no es funcionar — con el
      // Administrador de Plataforma el rebote no existía, porque su zona
      // tampoco, y aterrizaba en un panel del despacho que RN-10 le prohíbe.
      await this.router.navigate([this.autenticacion.rutaInicial()]);

    } catch (fallo) {
      this.error.set(mensajeDeError(fallo, 'No se pudo iniciar sesión. Inténtelo de nuevo.'));

    } finally {
      this.enviando.set(false);
    }
  }

}
