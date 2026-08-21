import { Component, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { Conteo, Resumen } from '../../nucleo/modelos';

/**
 * Reportes del despacho. RF-32 · HU-36.
 *
 * <p>Responde a la parte del problema que la propuesta enuncia como <em>«no
 * sabe cuántos casos activos tiene ni en qué estado»</em>.
 *
 * <h2>Dos criterios opuestos, y los dos a propósito</h2>
 *
 * <p>Los <strong>estados con cero</strong> se muestran: un estado ausente del
 * reporte no se distingue de uno vacío, y quien mira quiere saber que
 * <em>no hay</em> procesos suspendidos, no quedarse con la duda.
 *
 * <p>Los <strong>abogados con cero</strong> no se muestran: eso lo decide el
 * backend, que solo devuelve a quienes tienen procesos abiertos. Sacar a una
 * persona en cero dentro de una tabla de carga de trabajo diría algo sobre ella
 * que el reporte no pretende decir.
 */
@Component({
  selector: 'sgpj-reportes',
  imports: [RouterLink],
  templateUrl: './reportes.html',
  styleUrl: './reportes.css',
})
export class Reportes {

  private readonly http = inject(HttpClient);

  protected readonly resumen = signal<Resumen | null>(null);
  protected readonly porTipo = signal<Conteo[]>([]);
  protected readonly cargaPorAbogado = signal<Conteo[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);

  /** El desglose por estado viene dentro del resumen: no se vuelve a pedir. */
  protected readonly porEstado = computed(() => this.resumen()?.desglosePorEstado ?? []);

  protected readonly totalProcesos = computed(() => this.resumen()?.totalProcesos ?? 0);

  /**
   * El mayor conteo de cada tabla, para que las barras sean comparables
   * <em>entre sí</em>.
   *
   * <p>Si cada barra se midiera contra el total, un despacho con nueve tipos
   * repartidos tendría nueve barras diminutas e indistinguibles. Medir contra
   * el máximo hace visible la diferencia, que es lo que se viene a ver.
   */
  protected readonly maximoEstado = computed(() => this.maximoDe(this.porEstado()));
  protected readonly maximoTipo = computed(() => this.maximoDe(this.porTipo()));
  protected readonly maximoCarga = computed(() => this.maximoDe(this.cargaPorAbogado()));

  protected readonly hayVencidos = computed(() => (this.resumen()?.terminosVencidos ?? 0) > 0);

  constructor() {
    void this.cargar();
  }

  protected async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);

    try {
      // En paralelo: son tres consultas independientes y encadenarlas haría
      // esperar al usuario la suma de las tres.
      const [resumen, tipos, carga] = await Promise.all([
        firstValueFrom(this.http.get<Resumen>('/api/reportes/resumen')),
        firstValueFrom(this.http.get<Conteo[]>('/api/reportes/procesos-por-tipo')),
        firstValueFrom(this.http.get<Conteo[]>('/api/reportes/carga-por-abogado')),
      ]);

      this.resumen.set(resumen);
      this.porTipo.set(tipos);
      this.cargaPorAbogado.set(carga);

    } catch {
      // Igual que en el panel: no se dejan cifras viejas con un aviso pequeño.
      // Un reporte desactualizado que parece al día es peor que uno que
      // reconoce que falló, porque las cifras se copian a otros sitios.
      this.resumen.set(null);
      this.porTipo.set([]);
      this.cargaPorAbogado.set([]);
      this.error.set('No se pudieron cargar los reportes. Vuelva a intentarlo.');

    } finally {
      this.cargando.set(false);
    }
  }

  protected ancho(cantidad: number, maximo: number): number {
    if (maximo <= 0) return 0;
    return Math.round((cantidad / maximo) * 100);
  }

  /** El porcentaje sobre el total, que es lo que se dice en voz alta. */
  protected porcentaje(cantidad: number): string {
    const total = this.totalProcesos();
    if (total === 0) return '—';
    return `${Math.round((cantidad / total) * 100)} %`;
  }

  protected esArchivado(nombre: string): boolean {
    return nombre.toLowerCase().includes('archivado');
  }

  protected hoy(): string {
    const meses = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio',
      'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre'];
    const ahora = new Date();
    return `${ahora.getDate()} de ${meses[ahora.getMonth()]} de ${ahora.getFullYear()}`;
  }

  private maximoDe(conteos: Conteo[]): number {
    return conteos.reduce((mayor, c) => Math.max(mayor, c.cantidad), 0);
  }
}
