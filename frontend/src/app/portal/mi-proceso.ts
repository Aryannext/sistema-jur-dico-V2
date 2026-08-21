import { Component, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { AudienciaDelCliente, PiezaDelCliente, ProcesoDelCliente } from '../nucleo/modelos';
import { Portal } from './portal.servicio';

/**
 * Un proceso, visto por su cliente. RF-29 · RF-30 · HU-33 · HU-34.
 *
 * <p>Es la pantalla espejo del expediente del despacho, y la diferencia es lo
 * que importa: <strong>aquí no hay notas internas</strong>. No porque esta
 * pantalla las oculte —eso bastaría con abrir las herramientas del navegador—
 * sino porque el backend no las envía (RN-24, CA-34.2). Los datos que llegan
 * ni siquiera tienen un campo donde pudieran venir.
 */
@Component({
  selector: 'sgpj-mi-proceso',
  imports: [RouterLink],
  templateUrl: './mi-proceso.html',
  styleUrl: './portal.css',
})
export class MiProceso {

  private readonly servicio = inject(Portal);

  readonly id = input.required<string>();

  protected readonly proceso = signal<ProcesoDelCliente | null>(null);
  protected readonly piezas = signal<PiezaDelCliente[]>([]);
  protected readonly audiencias = signal<AudienciaDelCliente[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);

  private static readonly MESES = [
    'enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio',
    'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre',
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
      const [proceso, piezas, audiencias] = await Promise.all([
        this.servicio.miProceso(id),
        this.servicio.miExpediente(id),
        this.servicio.audienciasDe(id).catch(() => []),
      ]);
      this.proceso.set(proceso);
      this.piezas.set(piezas);
      this.audiencias.set(audiencias);

    } catch {
      this.proceso.set(null);
      this.piezas.set([]);
      // Se dice sin tecnicismos y sin acusar: para el cliente, un proceso que
      // no es suyo y uno que no existe son lo mismo — algo que no puede ver.
      this.error.set('No se pudo abrir este proceso. Si cree que debería verlo, escríbale a su abogado.');

    } finally {
      this.cargando.set(false);
    }
  }

  protected esArchivado(estado: string): boolean {
    return estado.toLowerCase().includes('archivado');
  }

  /** «10 de agosto de 2026» — completo, que es como se lee una fecha. */
  protected fecha(iso: string): string {
    const [anio, mes, dia] = iso.split('-').map(Number);
    return `${dia} de ${MiProceso.MESES[mes - 1]} de ${anio}`;
  }

  protected fechaHora(iso: string): string {
    const c = new Date(iso);
    const hora = c.toLocaleTimeString('es-CO', {
      hour: 'numeric', minute: '2-digit', hour12: true,
    });
    return `${c.getDate()} de ${MiProceso.MESES[c.getMonth()]} de ${c.getFullYear()}, ${hora}`;
  }

  protected soloFecha(iso: string): string {
    const c = new Date(iso);
    return `${c.getDate()} de ${MiProceso.MESES[c.getMonth()]} de ${c.getFullYear()}`;
  }

  protected diaDe(iso: string): number {
    return new Date(iso).getDate();
  }

  protected mesDe(iso: string): string {
    return MiProceso.MESES[new Date(iso).getMonth()].slice(0, 3);
  }
}
