import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Proceso, Termino } from '../../nucleo/modelos';
import { Procesos } from '../procesos/procesos.servicio';
import { Vigilancia } from '../vigilancia';
import { mensajeDeError } from '../../nucleo/mensajes';

/**
 * Términos de un proceso. RF-21 · RF-22 · RNF-16 · HU-22 · HU-24.
 *
 * <h2>Dos campos para registrar, y es el requisito</h2>
 *
 * <p>RNF-16 lo dice literalmente: no más de cinco campos obligatorios y una
 * sola pantalla. Aquí son dos —qué hay que hacer y para cuándo—. Si registrar
 * un término cuesta más que anotarlo en la agenda de papel, el abogado usa la
 * agenda, y entonces el sistema no vigila nada.
 *
 * <p>El formulario está en la misma pantalla que la lista, no detrás de otro
 * clic, por la misma razón.
 */
@Component({
  selector: 'sgpj-terminos',
  imports: [RouterLink],
  templateUrl: './terminos.html',
  styleUrl: './terminos.css',
})
export class Terminos {

  private readonly servicio = inject(Procesos);
  private readonly vigilancia = inject(Vigilancia);

  readonly id = input.required<string>();

  protected readonly proceso = signal<Proceso | null>(null);
  protected readonly terminos = signal<Termino[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);

  /** Cuál se está guardando o cerrando, para deshabilitar solo ese botón. */
  protected readonly ocupado = signal<number | null>(null);

  protected readonly descripcion = signal('');
  protected readonly fecha = signal('');
  protected readonly guardando = signal(false);
  protected readonly errorFormulario = signal<string | null>(null);

  protected readonly pendientes = computed(() =>
    this.terminos()
      .filter(t => t.estado === 'PENDIENTE')
      .sort((a, b) => a.fechaVencimiento.localeCompare(b.fechaVencimiento)));

  protected readonly cerrados = computed(() =>
    this.terminos()
      .filter(t => t.estado !== 'PENDIENTE')
      .sort((a, b) => b.fechaVencimiento.localeCompare(a.fechaVencimiento)));

  protected readonly puedeGuardar = computed(() =>
    !this.guardando()
    && this.descripcion().trim().length > 0
    && this.descripcion().length <= 300
    && this.fecha() !== '');

  private static readonly MESES = [
    'ene', 'feb', 'mar', 'abr', 'may', 'jun',
    'jul', 'ago', 'sep', 'oct', 'nov', 'dic',
  ];

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
      const [proceso, terminos] = await Promise.all([
        this.servicio.proceso(id),
        this.vigilancia.terminosDeProceso(id),
      ]);
      this.proceso.set(proceso);
      this.terminos.set(terminos);

    } catch {
      this.error.set('No se pudieron cargar los términos de este proceso.');

    } finally {
      this.cargando.set(false);
    }
  }

  protected escribirDescripcion(evento: Event): void {
    this.descripcion.set((evento.target as HTMLInputElement).value);
    this.errorFormulario.set(null);
  }

  protected escribirFecha(evento: Event): void {
    this.fecha.set((evento.target as HTMLInputElement).value);
    this.errorFormulario.set(null);
  }

  async registrar(evento: Event): Promise<void> {
    evento.preventDefault();
    if (!this.puedeGuardar()) return;

    this.guardando.set(true);
    this.errorFormulario.set(null);

    try {
      await this.vigilancia.registrarTermino(
        Number(this.id()), this.descripcion().trim(), this.fecha());

      this.descripcion.set('');
      this.fecha.set('');
      await this.cargar();

    } catch (fallo) {
      this.errorFormulario.set(mensajeDeError(fallo, 'No se pudo completar la operación. Inténtelo de nuevo.'));

    } finally {
      this.guardando.set(false);
    }
  }

  async cumplir(termino: Termino): Promise<void> {
    this.ocupado.set(termino.id);
    this.error.set(null);

    try {
      await this.vigilancia.cumplir(termino.id);
      await this.cargar();
    } catch (fallo) {
      this.error.set(mensajeDeError(fallo, 'No se pudo completar la operación. Inténtelo de nuevo.'));
    } finally {
      this.ocupado.set(null);
    }
  }

  async reabrir(termino: Termino): Promise<void> {
    this.ocupado.set(termino.id);
    this.error.set(null);

    try {
      await this.vigilancia.reabrir(termino.id);
      await this.cargar();
    } catch (fallo) {
      this.error.set(mensajeDeError(fallo, 'No se pudo completar la operación. Inténtelo de nuevo.'));
    } finally {
      this.ocupado.set(null);
    }
  }

  /** Igual que en el panel: días de calendario y singular cuidado. */
  protected cuanto(termino: Termino): string {
    const dias = this.diasHasta(termino.fechaVencimiento);

    if (termino.estado !== 'PENDIENTE') return termino.estadoDescripcion;
    if (dias === 0) return 'Vence hoy';
    if (dias === 1) return 'Vence mañana';
    if (dias > 1) return `En ${dias} días`;
    if (dias === -1) return 'Venció ayer';
    return `Venció hace ${Math.abs(dias)} días`;
  }

  protected fechaCorta(iso: string): string {
    const [anio, mes, dia] = iso.split('-').map(Number);
    return `${dia} ${Terminos.MESES[mes - 1]} ${anio}`;
  }

  protected urgencia(termino: Termino): string {
    if (termino.vencido) return 'vencido';
    const dias = this.diasHasta(termino.fechaVencimiento);
    if (dias <= 0) return 'hoy';
    if (dias <= 3) return 'pronto';
    return 'ok';
  }

  private diasHasta(iso: string): number {
    const [anio, mes, dia] = iso.split('-').map(Number);
    const vence = new Date(anio, mes - 1, dia);

    const ahora = new Date();
    const hoy = new Date(ahora.getFullYear(), ahora.getMonth(), ahora.getDate());

    return Math.round((vence.getTime() - hoy.getTime()) / 86_400_000);
  }

}
