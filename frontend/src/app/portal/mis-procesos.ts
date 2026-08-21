import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { AudienciaDelCliente, ProcesoDelCliente } from '../nucleo/modelos';
import { Autenticacion } from '../nucleo/autenticacion';
import { Portal } from './portal.servicio';

/**
 * Mis procesos, vistos por el cliente. RF-28 · HU-32.
 *
 * <p>Todo aquí es de <strong>solo lectura</strong> (RN-11, CA-32.2). No hay un
 * botón que envíe, suba ni modifique nada, y esa ausencia es el requisito: el
 * portal informa, no permite intervenir en el proceso.
 */
@Component({
  selector: 'sgpj-mis-procesos',
  imports: [RouterLink],
  templateUrl: './mis-procesos.html',
  styleUrl: './portal.css',
})
export class MisProcesos {

  private readonly servicio = inject(Portal);
  protected readonly autenticacion = inject(Autenticacion);

  protected readonly procesos = signal<ProcesoDelCliente[]>([]);
  protected readonly audiencias = signal<AudienciaDelCliente[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly activos = computed(
    () => this.procesos().filter(p => !this.esArchivado(p.estadoProcesal)).length);

  /** La próxima audiencia, que es lo que un cliente viene a mirar. */
  protected readonly proxima = computed(() => {
    const ahora = new Date().toISOString();
    return [...this.audiencias()]
      .filter(a => a.fechaHora >= ahora)
      .sort((a, b) => a.fechaHora.localeCompare(b.fechaHora))[0] ?? null;
  });

  protected readonly primerNombre = computed(
    () => this.autenticacion.nombre().trim().split(/\s+/)[0] ?? '');

  private static readonly MESES = [
    'enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio',
    'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre',
  ];

  constructor() {
    void this.cargar();
  }

  protected async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);

    try {
      const [procesos, audiencias] = await Promise.all([
        this.servicio.misProcesos(),
        this.servicio.misAudiencias().catch(() => []),
      ]);
      this.procesos.set(procesos);
      this.audiencias.set(audiencias);

    } catch {
      this.procesos.set([]);
      this.audiencias.set([]);
      this.error.set('No se pudo cargar su información. Vuelva a intentarlo.');

    } finally {
      this.cargando.set(false);
    }
  }

  protected esArchivado(estado: string): boolean {
    return estado.toLowerCase().includes('archivado');
  }

  /** «desde agosto de 2026» — al cliente no le sirve la hora exacta. */
  protected desde(iso: string): string {
    const c = new Date(iso);
    return `${MisProcesos.MESES[c.getMonth()]} de ${c.getFullYear()}`;
  }

  protected cuando(iso: string): string {
    const c = new Date(iso);
    const hora = c.toLocaleTimeString('es-CO', {
      hour: 'numeric', minute: '2-digit', hour12: true,
    });
    return `${c.getDate()} de ${MisProcesos.MESES[c.getMonth()]}, ${hora}`;
  }

  protected diaDe(iso: string): number {
    return new Date(iso).getDate();
  }

  protected mesDe(iso: string): string {
    return MisProcesos.MESES[new Date(iso).getMonth()].slice(0, 3);
  }
}
