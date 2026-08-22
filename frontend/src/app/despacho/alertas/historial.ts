import { Component, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { Alerta } from '../../nucleo/modelos';
import { mensajeDeError } from '../../nucleo/mensajes';

type Filtro = 'todas' | 'FALLIDA' | 'PROGRAMADA';

/**
 * Historial de alertas. RF-26 · RF-27 · RNF-08 · RNF-09 · HU-30.
 *
 * <p>Esta pantalla existe para poder <strong>responder después</strong>: si
 * alguien pregunta por qué se venció un término, aquí está si el aviso salió,
 * cuándo y a quién. Es el respaldo del despacho, no un adorno.
 *
 * <p>Las fallidas van primero y en rojo. <strong>No existe un estado
 * «descartada»</strong> en este sistema: una alerta que no salió y desaparece
 * de la lista es peor que ninguna alerta, porque el despacho cree que le
 * avisaron.
 */
@Component({
  selector: 'sgpj-historial-alertas',
  imports: [RouterLink],
  templateUrl: './historial.html',
  styleUrl: './historial.css',
})
export class HistorialAlertas {

  private readonly http = inject(HttpClient);

  protected readonly fallidas = signal<Alerta[]>([]);
  protected readonly programadas = signal<Alerta[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly barriendo = signal(false);
  protected readonly resultadoBarrido = signal<string | null>(null);
  protected readonly filtro = signal<Filtro>('todas');

  protected readonly hayFallidas = computed(() => this.fallidas().length > 0);

  /**
   * Fallidas primero, y dentro de cada grupo lo más reciente arriba.
   *
   * <p>El orden no es estético: lo que falló es lo único de esta pantalla
   * sobre lo que hay que hacer algo hoy.
   */
  protected readonly mostradas = computed<Alerta[]>(() => {
    const porFecha = (a: Alerta, b: Alerta) =>
      b.programadaPara.localeCompare(a.programadaPara);

    switch (this.filtro()) {
      case 'FALLIDA':
        return [...this.fallidas()].sort(porFecha);
      case 'PROGRAMADA':
        return [...this.programadas()].sort(porFecha);
      default:
        return [...[...this.fallidas()].sort(porFecha),
                ...[...this.programadas()].sort(porFecha)];
    }
  });

  private static readonly MESES = [
    'ene', 'feb', 'mar', 'abr', 'may', 'jun',
    'jul', 'ago', 'sep', 'oct', 'nov', 'dic',
  ];

  constructor() {
    void this.cargar();
  }

  protected async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);

    try {
      const [fallidas, programadas] = await Promise.all([
        firstValueFrom(this.http.get<Alerta[]>('/api/alertas/fallidas')),
        firstValueFrom(this.http.get<Alerta[]>('/api/alertas/programadas')),
      ]);
      this.fallidas.set(fallidas);
      this.programadas.set(programadas);

    } catch {
      this.fallidas.set([]);
      this.programadas.set([]);
      this.error.set('No se pudo cargar el historial de alertas.');

    } finally {
      this.cargando.set(false);
    }
  }

  /**
   * Forzar un barrido. RF-24.
   *
   * <p>El motor barre solo cada pocos minutos; esto es para no tener que
   * esperarlo — al probar el correo, o cuando el despacho quiere comprobar
   * ahora mismo que sus avisos salen.
   */
  async barrer(): Promise<void> {
    this.barriendo.set(true);
    this.error.set(null);
    this.resultadoBarrido.set(null);

    try {
      await firstValueFrom(this.http.post('/api/alertas/barrer', {}));
      await this.cargar();
      this.resultadoBarrido.set(
        'Barrido ejecutado. Lo que estuviera pendiente de salir ya salió, y lo que falló quedó marcado abajo.');

    } catch (fallo) {
      this.error.set(mensajeDeError(fallo, 'No se pudo ejecutar el barrido. Inténtelo de nuevo.'));

    } finally {
      this.barriendo.set(false);
    }
  }

  /** «9 sep 2026 · 4:59 a. m.» */
  protected cuando(iso: string): string {
    const c = new Date(iso);
    const hora = c.toLocaleTimeString('es-CO', {
      hour: 'numeric', minute: '2-digit', hour12: true,
    });
    return `${c.getDate()} ${HistorialAlertas.MESES[c.getMonth()]} ${c.getFullYear()} · ${hora}`;
  }

  protected esFallida(alerta: Alerta): boolean {
    return alerta.estado === 'FALLIDA';
  }

}
