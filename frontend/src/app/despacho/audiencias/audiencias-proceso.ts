import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { motivoInhabil } from '../../nucleo/festivos';
import { Audiencia, Proceso } from '../../nucleo/modelos';
import { Procesos } from '../procesos/procesos.servicio';
import { Vigilancia } from '../vigilancia';
import { mensajeDeError } from '../../nucleo/mensajes';

/**
 * Audiencias de un proceso. RF-19 · RNF-16 · HU-21.
 *
 * <p>Tres campos, de los cuales <strong>uno solo es obligatorio</strong>: la
 * fecha y hora. El lugar y las observaciones ayudan pero no impiden registrar,
 * porque cuando el juzgado avisa de una audiencia lo primero que se sabe es
 * cuándo, y a veces es lo único.
 *
 * <p>La <strong>hora</strong> sí es obligatoria y no es un capricho: sin ella
 * no hay desde dónde restar 48 y 24 horas, y las tres alertas de la propuesta
 * no se podrían calcular.
 */
@Component({
  selector: 'sgpj-audiencias-proceso',
  imports: [RouterLink],
  templateUrl: './audiencias-proceso.html',
  styleUrl: './audiencias-proceso.css',
})
export class AudienciasDeProceso {

  private readonly http = inject(HttpClient);
  private readonly servicio = inject(Procesos);
  private readonly vigilancia = inject(Vigilancia);

  readonly id = input.required<string>();

  protected readonly proceso = signal<Proceso | null>(null);
  protected readonly audiencias = signal<Audiencia[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly ocupado = signal<number | null>(null);

  protected readonly fecha = signal('');
  protected readonly hora = signal('09:00');
  protected readonly lugar = signal('');
  protected readonly observaciones = signal('');
  protected readonly guardando = signal(false);
  protected readonly errorFormulario = signal<string | null>(null);

  /**
   * Aviso si la fecha elegida es sábado, domingo o festivo.
   *
   * <p>No lo impide: los juzgados hacen diligencias en días raros y el sistema
   * no está para discutir con el juzgado. Solo lo señala, porque la causa más
   * común de una fecha inhábil es haberse equivocado al teclear.
   */
  protected readonly avisoDeDia = computed(() => {
    if (!this.fecha()) return null;

    const [anio, mes, dia] = this.fecha().split('-').map(Number);
    const motivo = motivoInhabil(new Date(anio, mes - 1, dia));

    return motivo ? `El día elegido ${motivo}.` : null;
  });

  protected readonly puedeGuardar = computed(
    () => !this.guardando() && this.fecha() !== '' && this.hora() !== '');

  protected readonly proximas = computed(() =>
    [...this.audiencias()]
      .filter(a => a.asistio === null)
      .sort((a, b) => a.fechaHora.localeCompare(b.fechaHora)));

  protected readonly pasadas = computed(() =>
    [...this.audiencias()]
      .filter(a => a.asistio !== null)
      .sort((a, b) => b.fechaHora.localeCompare(a.fechaHora)));

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
      const [proceso, audiencias] = await Promise.all([
        this.servicio.proceso(id),
        firstValueFrom(this.http.get<Audiencia[]>(`/api/procesos/${id}/audiencias`)),
      ]);
      this.proceso.set(proceso);
      this.audiencias.set(audiencias);

    } catch {
      this.error.set('No se pudieron cargar las audiencias de este proceso.');

    } finally {
      this.cargando.set(false);
    }
  }

  protected escribir(campo: 'fecha' | 'hora' | 'lugar' | 'observaciones', evento: Event): void {
    const valor = (evento.target as HTMLInputElement).value;
    this[campo].set(valor);
    this.errorFormulario.set(null);
  }

  async registrar(evento: Event): Promise<void> {
    evento.preventDefault();
    if (!this.puedeGuardar()) return;

    this.guardando.set(true);
    this.errorFormulario.set(null);

    try {
      // El backend espera fecha y hora con desfase horario. Se construye desde
      // la hora LOCAL de quien registra: una audiencia a las 9:00 en Neiva es a
      // las 9:00 en Neiva, y convertirla mal la movería de día.
      const [anio, mes, dia] = this.fecha().split('-').map(Number);
      const [h, min] = this.hora().split(':').map(Number);
      const cuando = new Date(anio, mes - 1, dia, h, min);

      await firstValueFrom(this.http.post(`/api/procesos/${this.id()}/audiencias`, {
        fechaHora: cuando.toISOString(),
        lugar: this.lugar().trim() || null,
        observaciones: this.observaciones().trim() || null,
      }));

      this.fecha.set('');
      this.lugar.set('');
      this.observaciones.set('');

      await this.cargar();
      await this.vigilancia.refrescar();

    } catch (fallo) {
      this.errorFormulario.set(mensajeDeError(fallo, 'No se pudo completar la operación. Inténtelo de nuevo.'));

    } finally {
      this.guardando.set(false);
    }
  }

  /** RF-19: dejar constancia de si se asistió. */
  async marcarAsistencia(audiencia: Audiencia, asistio: boolean): Promise<void> {
    this.ocupado.set(audiencia.id);
    this.error.set(null);

    try {
      await firstValueFrom(
        this.http.put(`/api/audiencias/${audiencia.id}/asistencia/${asistio}`, {}));
      await this.cargar();
      await this.vigilancia.refrescar();

    } catch (fallo) {
      this.error.set(mensajeDeError(fallo, 'No se pudo completar la operación. Inténtelo de nuevo.'));

    } finally {
      this.ocupado.set(null);
    }
  }

  protected cuando(iso: string): string {
    const cuando = new Date(iso);
    const meses = ['ene', 'feb', 'mar', 'abr', 'may', 'jun',
      'jul', 'ago', 'sep', 'oct', 'nov', 'dic'];
    const hora = cuando.toLocaleTimeString('es-CO', {
      hour: 'numeric', minute: '2-digit', hour12: true,
    });
    return `${cuando.getDate()} ${meses[cuando.getMonth()]} ${cuando.getFullYear()} · ${hora}`;
  }

  protected diaDe(iso: string): number {
    return new Date(iso).getDate();
  }

  protected mesDe(iso: string): string {
    return ['ene', 'feb', 'mar', 'abr', 'may', 'jun',
      'jul', 'ago', 'sep', 'oct', 'nov', 'dic'][new Date(iso).getMonth()];
  }

}
