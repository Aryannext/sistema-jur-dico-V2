import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Proceso } from '../../nucleo/modelos';
import { Cliente, Clientes } from './clientes.servicio';
import { mensajeDeError } from '../../nucleo/mensajes';

/**
 * Ficha del cliente y su acceso al portal. RF-07 · RF-10 · HU-07 · HU-10.
 *
 * <h2>El cliente no se crea una cuenta: se la habilita el despacho</h2>
 *
 * <p>RN-43 · D-15. Solo el despacho sabe a quién representa; si cualquiera
 * pudiera registrarse diciendo un nombre, un tercero reclamaría el expediente
 * de otra persona. Por eso esto está aquí y no hay página pública de registro.
 */
@Component({
  selector: 'sgpj-ficha-cliente',
  imports: [RouterLink],
  templateUrl: './ficha-cliente.html',
  styleUrl: './ficha-cliente.css',
})
export class FichaCliente {

  private readonly servicio = inject(Clientes);

  readonly id = input.required<string>();

  protected readonly cliente = signal<Cliente | null>(null);
  protected readonly procesos = signal<Proceso[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);

  // --- Habilitar el portal ---
  protected readonly abriendoAcceso = signal(false);
  protected readonly correoAcceso = signal('');
  protected readonly clave = signal('');
  protected readonly verClave = signal(false);
  protected readonly trabajando = signal(false);
  protected readonly errorAcceso = signal<string | null>(null);

  /** Se muestra una sola vez, tras habilitar: hay que copiarla ahora. */
  protected readonly claveEntregada = signal<string | null>(null);

  protected readonly activos = computed(
    () => this.procesos().filter(p => !p.archivado).length);

  protected readonly puedeHabilitar = computed(() =>
    !this.trabajando()
    && this.correoAcceso().trim().includes('@')
    && this.clave().length >= 8);

  constructor() {
    effect(() => {
      const id = Number(this.id());
      if (Number.isFinite(id)) void this.cargar(id);
    });
  }

  protected async cargar(id = Number(this.id())): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);

    try {
      const [cliente, procesos] = await Promise.all([
        this.servicio.cliente(id),
        this.servicio.procesosDe(id).catch(() => []),
      ]);
      this.cliente.set(cliente);
      this.procesos.set(procesos);

      // El correo del cliente es el candidato natural para entrar.
      if (!this.correoAcceso() && cliente.correo) {
        this.correoAcceso.set(cliente.correo);
      }

    } catch {
      this.error.set('No se pudo cargar el cliente. Puede que no sea de su despacho.');

    } finally {
      this.cargando.set(false);
    }
  }

  protected escribir(campo: 'correoAcceso' | 'clave', evento: Event): void {
    this[campo].set((evento.target as HTMLInputElement).value);
    this.errorAcceso.set(null);
  }

  /**
   * Propone una contraseña razonable.
   *
   * <p>Ayuda porque el despacho tiene que inventarse una, y la alternativa
   * real —«cliente123»— es la que se usa cuando el sistema no propone nada.
   */
  protected proponerClave(): void {
    const silabas = ['ne', 'hui', 'la', 'sur', 'ju', 'ris', 'pa', 'lo', 'mo', 'ce', 'ti', 'va'];
    let clave = '';
    for (let i = 0; i < 4; i++) {
      clave += silabas[Math.floor(Math.random() * silabas.length)];
    }
    this.clave.set(clave + '-' + Math.floor(1000 + Math.random() * 9000));
    this.verClave.set(true);
    this.errorAcceso.set(null);
  }

  async habilitar(evento: Event): Promise<void> {
    evento.preventDefault();
    if (!this.puedeHabilitar()) return;

    this.trabajando.set(true);
    this.errorAcceso.set(null);

    try {
      const clave = this.clave();
      await this.servicio.habilitarPortal(
        Number(this.id()), this.correoAcceso().trim(), clave);

      // Se guarda para mostrarla una vez: nadie puede volver a consultarla
      // después, ni el despacho ni nosotros. Se almacena con hash (RNF-05).
      this.claveEntregada.set(clave);
      this.clave.set('');
      this.abriendoAcceso.set(false);
      await this.cargar();

    } catch (fallo) {
      this.errorAcceso.set(mensajeDeError(fallo, 'No se pudo completar la operación. Inténtelo de nuevo.'));

    } finally {
      this.trabajando.set(false);
    }
  }

  async revocar(): Promise<void> {
    this.trabajando.set(true);
    this.errorAcceso.set(null);

    try {
      await this.servicio.revocarPortal(Number(this.id()));
      this.claveEntregada.set(null);
      await this.cargar();

    } catch (fallo) {
      this.errorAcceso.set(mensajeDeError(fallo, 'No se pudo completar la operación. Inténtelo de nuevo.'));

    } finally {
      this.trabajando.set(false);
    }
  }

  protected iniciales(nombre: string): string {
    const partes = nombre.trim().split(/\s+/);
    if (partes.length === 0 || partes[0] === '') return '?';
    const primera = partes[0][0];
    const segunda = partes.length > 1 ? partes[partes.length - 1][0] : '';
    return (primera + segunda).toUpperCase();
  }

  protected desde(iso: string): string {
    const meses = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio',
      'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'];
    const cuando = new Date(iso);
    return `${meses[cuando.getMonth()]} de ${cuando.getFullYear()}`;
  }

  protected colorEstado(nombre: string): string {
    const limpio = nombre.toLowerCase();
    if (limpio.includes('activo')) return 'verde';
    if (limpio.includes('archivado')) return 'gris';
    if (limpio.includes('suspend')) return 'ambar';
    return '';
  }

}
