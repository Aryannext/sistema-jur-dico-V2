import { Component, computed, inject, signal } from '@angular/core';

import { Autenticacion } from '../nucleo/autenticacion';
import { Cuenta } from '../nucleo/cuenta.servicio';
import { mensajeDeError } from '../nucleo/mensajes';

/**
 * Mi cuenta. RF-39 · HU-43 · D-24.
 *
 * <p>La única pantalla que alcanzan los cuatro roles, cliente del portal
 * incluido. Por eso no vive dentro de la administración del despacho: quien
 * más necesita cambiar su contraseña es justamente quien no tiene acceso a esa
 * zona — el cliente, que hoy usa la clave que le escribió su abogado.
 */
@Component({
  selector: 'sgpj-mi-cuenta',
  templateUrl: './mi-cuenta.html',
  styleUrl: './mi-cuenta.css',
})
export class MiCuenta {

  private readonly servicio = inject(Cuenta);
  protected readonly autenticacion = inject(Autenticacion);

  protected readonly actual = signal('');
  protected readonly nueva = signal('');
  protected readonly repetida = signal('');
  protected readonly verClaves = signal(false);

  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly hecho = signal(false);

  protected static readonly MINIMO = 8;
  protected readonly minimo = MiCuenta.MINIMO;

  protected readonly cortaDemas = computed(
    () => this.nueva().length > 0 && this.nueva().length < MiCuenta.MINIMO);

  protected readonly noCoinciden = computed(
    () => this.repetida().length > 0 && this.nueva() !== this.repetida());

  /**
   * Se avisa aquí, antes de enviar, aunque el backend también lo rechace.
   *
   * <p>Quien cambia su contraseña por la misma suele estar reaccionando a una
   * sospecha de filtración. Decírselo al pulsar el botón, después de escribirla
   * tres veces, es peor que decírselo mientras escribe.
   */
  protected readonly esLaMisma = computed(
    () => this.nueva().length > 0 && this.nueva() === this.actual());

  protected readonly puedeGuardar = computed(() =>
    !this.guardando()
    && this.actual().length > 0
    && this.nueva().length >= MiCuenta.MINIMO
    && this.nueva() === this.repetida()
    && !this.esLaMisma());

  protected escribir(campo: 'actual' | 'nueva' | 'repetida', evento: Event): void {
    this[campo].set((evento.target as HTMLInputElement).value);
    this.error.set(null);
    this.hecho.set(false);
  }

  async cambiar(evento: Event): Promise<void> {
    evento.preventDefault();
    if (!this.puedeGuardar()) return;

    this.guardando.set(true);
    this.error.set(null);

    try {
      await this.servicio.cambiarMiContrasena(this.actual(), this.nueva());

      this.actual.set('');
      this.nueva.set('');
      this.repetida.set('');
      this.verClaves.set(false);
      this.hecho.set(true);

    } catch (fallo) {
      this.error.set(mensajeDeError(fallo, 'No se pudo cambiar la contraseña. Inténtelo de nuevo.'));

    } finally {
      this.guardando.set(false);
    }
  }

}
