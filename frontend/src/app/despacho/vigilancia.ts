import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

import { Audiencia, Resumen, Termino } from '../nucleo/modelos';

/**
 * Lo que el despacho tiene que vigilar. RF-23 · RF-20 · RF-32.
 *
 * <p>Es un servicio y no una carga dentro del componente porque la barra
 * lateral necesita el mismo número que el panel: cuántos términos hay
 * vencidos. Si cada uno lo pidiera por su cuenta, serían dos peticiones para
 * el mismo dato y —peor— podrían mostrar cifras distintas durante un instante.
 * Un sistema que dice «2 vencidos» arriba y «3» en el cuerpo no lo cree nadie.
 */
@Injectable({ providedIn: 'root' })
export class Vigilancia {

  private readonly http = inject(HttpClient);

  private readonly _resumen = signal<Resumen | null>(null);
  private readonly _terminos = signal<Termino[]>([]);
  private readonly _audiencias = signal<Audiencia[]>([]);
  private readonly _cargando = signal(false);
  private readonly _error = signal<string | null>(null);
  private cargadoAlguna = false;

  readonly resumen = this._resumen.asReadonly();
  readonly cargando = this._cargando.asReadonly();
  readonly error = this._error.asReadonly();

  /**
   * Los vencidos, primero los que llevan más tiempo así.
   *
   * <p>El orden importa: lo que más tiempo lleva vencido es lo que más urge
   * explicar, no lo que se venció ayer.
   */
  readonly vencidos = computed(() =>
    this._terminos()
      .filter(t => t.vencido)
      .sort((a, b) => a.fechaVencimiento.localeCompare(b.fechaVencimiento)));

  /** Los que aún no vencen, por orden de cuánto queda. */
  readonly porVencer = computed(() =>
    this._terminos()
      .filter(t => !t.vencido)
      .sort((a, b) => a.fechaVencimiento.localeCompare(b.fechaVencimiento)));

  readonly proximasAudiencias = computed(() =>
    [...this._audiencias()].sort((a, b) => a.fechaHora.localeCompare(b.fechaHora)));

  /** El número del distintivo de la barra lateral. */
  readonly cuantosVencidos = computed(() => this.vencidos().length);

  /** Los términos de un proceso concreto, cumplidos incluidos. RF-22. */
  async terminosDeProceso(procesoId: number): Promise<Termino[]> {
    return firstValueFrom(
      this.http.get<Termino[]>(`/api/procesos/${procesoId}/terminos`));
  }

  /**
   * Registrar un término. RF-21 · RNF-16.
   *
   * <p>Dos campos y nada más, y no es una simplificación de esta pantalla: es
   * el requisito. Si registrar un término en el sistema cuesta más que
   * anotarlo en la agenda de papel, el abogado usa la agenda — y entonces el
   * sistema no vigila nada.
   */
  async registrarTermino(
    procesoId: number, descripcion: string, fechaVencimiento: string,
  ): Promise<Termino> {
    const creado = await firstValueFrom(this.http.post<Termino>(
      `/api/procesos/${procesoId}/terminos`, { descripcion, fechaVencimiento }));

    await this.refrescar();
    return creado;
  }

  /**
   * Marcar cumplido. RF-22 · CA-24.1.
   *
   * <p>Se refresca todo después. Un término que sigue en rojo en el panel
   * después de haberlo cerrado haría dudar al abogado de si se guardó — y la
   * duda es exactamente lo que este sistema existe para quitar.
   */
  async cumplir(terminoId: number): Promise<void> {
    await firstValueFrom(this.http.put(`/api/terminos/${terminoId}/cumplir`, {}));
    await this.refrescar();
  }

  /** Reabrir uno cerrado por error. RF-22. */
  async reabrir(terminoId: number): Promise<void> {
    await firstValueFrom(this.http.put(`/api/terminos/${terminoId}/reabrir`, {}));
    await this.refrescar();
  }

  /** Carga la primera vez y no más; para volver a pedir está `refrescar`. */
  async asegurarCargado(): Promise<void> {
    if (!this.cargadoAlguna) {
      await this.refrescar();
    }
  }

  /**
   * Pide las tres cosas a la vez.
   *
   * <p>En paralelo y no en cadena: son independientes, y encadenarlas haría
   * que el panel tardara la suma de las tres en aparecer.
   */
  async refrescar(): Promise<void> {
    this._cargando.set(true);
    this._error.set(null);

    try {
      const [resumen, terminos, audiencias] = await Promise.all([
        firstValueFrom(this.http.get<Resumen>('/api/reportes/resumen')),
        firstValueFrom(this.http.get<Termino[]>('/api/vencimientos')),
        firstValueFrom(this.http.get<Audiencia[]>('/api/calendario')),
      ]);

      this._resumen.set(resumen);
      this._terminos.set(terminos);
      this._audiencias.set(audiencias);
      this.cargadoAlguna = true;

    } catch {
      // Deliberadamente NO se dejan los datos anteriores en pantalla junto a un
      // aviso pequeño: un panel de vencimientos desactualizado que parece al
      // día es peor que un panel que dice que falló.
      this._resumen.set(null);
      this._terminos.set([]);
      this._audiencias.set([]);
      this._error.set('No se pudieron cargar sus vencimientos. Vuelva a intentarlo.');

    } finally {
      this._cargando.set(false);
    }
  }
}
