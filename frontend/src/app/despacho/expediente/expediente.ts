import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Pieza, Proceso } from '../../nucleo/modelos';
import { Procesos } from '../procesos/procesos.servicio';

type Solapa = 'todo' | 'ACTUACION' | 'DOCUMENTO' | 'NOTA';

/**
 * Expediente de un proceso. RF-13 · RF-15 · RF-17 · RF-18 · RF-38 · HU-20.
 *
 * <p>Muestra las tres clases de pieza en una sola línea de tiempo, porque para
 * quien lee el expediente son lo mismo: cosas que pasaron, en orden. Lo que sí
 * se distingue con claridad es <strong>qué ve el cliente y qué no</strong>
 * (RN-24): esa marca es la que evita que alguien escriba una nota creyendo que
 * es interna cuando no lo es.
 *
 * <p>Abrir esta pantalla deja asiento en la bitácora de auditoría (RF-08). No
 * es gratis, y está bien que no lo sea.
 */
@Component({
  selector: 'sgpj-expediente',
  imports: [RouterLink],
  templateUrl: './expediente.html',
  styleUrl: './expediente.css',
})
export class Expediente {

  private readonly servicio = inject(Procesos);

  /** Llega de la ruta gracias a `withComponentInputBinding()`. */
  readonly id = input.required<string>();

  protected readonly proceso = signal<Proceso | null>(null);
  protected readonly piezas = signal<Pieza[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly solapa = signal<Solapa>('todo');

  protected readonly visibles = computed(
    () => this.piezas().filter(p => p.visibleParaCliente).length);

  protected readonly internas = computed(
    () => this.piezas().filter(p => !p.visibleParaCliente).length);

  protected readonly cuantas = computed(() => ({
    todo: this.piezas().length,
    ACTUACION: this.piezas().filter(p => p.tipo === 'ACTUACION').length,
    DOCUMENTO: this.piezas().filter(p => p.tipo === 'DOCUMENTO').length,
    NOTA: this.piezas().filter(p => p.tipo === 'NOTA').length,
  }));

  protected readonly mostradas = computed(() => {
    const filtro = this.solapa();
    return filtro === 'todo' ? this.piezas() : this.piezas().filter(p => p.tipo === filtro);
  });

  private static readonly MESES = [
    'ene', 'feb', 'mar', 'abr', 'may', 'jun',
    'jul', 'ago', 'sep', 'oct', 'nov', 'dic',
  ];

  constructor() {
    // El identificador viene de la URL: si el usuario navega de un expediente
    // a otro sin pasar por la lista, el componente se reutiliza y hay que
    // volver a cargar.
    effect(() => {
      const id = Number(this.id());
      if (Number.isFinite(id)) void this.cargar(id);
    });
  }

  protected async cargar(id = Number(this.id())): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);

    try {
      // En paralelo: la cabecera y el contenido no dependen uno del otro.
      const [proceso, piezas] = await Promise.all([
        this.servicio.proceso(id),
        this.servicio.expediente(id),
      ]);
      this.proceso.set(proceso);
      this.piezas.set(piezas);

    } catch {
      this.proceso.set(null);
      this.piezas.set([]);
      this.error.set('No se pudo abrir el expediente. Puede que no exista o que no sea de su despacho.');

    } finally {
      this.cargando.set(false);
    }
  }

  protected verSolapa(cual: Solapa): void {
    this.solapa.set(cual);
  }

  /** «20 ago 2026, 4:32 p. m.» — cuándo quedó registrada la pieza (RF-38). */
  protected sello(iso: string): string {
    const cuando = new Date(iso);
    const hora = cuando.toLocaleTimeString('es-CO', {
      hour: 'numeric', minute: '2-digit', hour12: true,
    });
    return `${cuando.getDate()} ${Expediente.MESES[cuando.getMonth()]} ${cuando.getFullYear()}, ${hora}`;
  }

  /** La fecha del hecho, que no es la de registro. */
  protected fechaDelHecho(iso: string): string {
    const [anio, mes, dia] = iso.split('-').map(Number);
    return `${dia} ${Expediente.MESES[mes - 1]} ${anio}`;
  }

  protected clasePieza(pieza: Pieza): string {
    if (pieza.tipo === 'NOTA') return 'nota';
    if (pieza.tipo === 'DOCUMENTO') return 'doc';
    return 'act';
  }
}
