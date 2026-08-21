import { Injectable, inject, signal } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

import { Pieza, Proceso, ValorCatalogo } from '../../nucleo/modelos';

/** Lo que el usuario tiene puesto en la barra de filtros. */
export interface Filtros {
  radicado: string;
  estadoId: number | null;
  tipoProcesoId: number | null;
  juzgadoId: number | null;
}

export const SIN_FILTROS: Filtros = {
  radicado: '',
  estadoId: null,
  tipoProcesoId: null,
  juzgadoId: null,
};

/**
 * Procesos y expedientes. RF-11 · RF-13 · RF-31.
 */
@Injectable({ providedIn: 'root' })
export class Procesos {

  private readonly http = inject(HttpClient);

  private readonly _catalogos = signal<Record<string, ValorCatalogo[]>>({});
  readonly catalogos = this._catalogos.asReadonly();

  /**
   * Busca. RF-31 · CA-35.2: los criterios se combinan.
   *
   * <p>Solo se mandan los que tienen valor. Enviar `estadoId=` vacío no es lo
   * mismo que no enviarlo, y el backend acabaría filtrando por nada.
   */
  async buscar(filtros: Filtros): Promise<Proceso[]> {
    let params = new HttpParams();

    const radicado = filtros.radicado.trim();
    if (radicado) params = params.set('radicado', radicado);
    if (filtros.estadoId !== null) params = params.set('estadoId', filtros.estadoId);
    if (filtros.tipoProcesoId !== null) params = params.set('tipoProcesoId', filtros.tipoProcesoId);
    if (filtros.juzgadoId !== null) params = params.set('juzgadoId', filtros.juzgadoId);

    return firstValueFrom(this.http.get<Proceso[]>('/api/procesos', { params }));
  }

  async proceso(id: number): Promise<Proceso> {
    return firstValueFrom(this.http.get<Proceso>(`/api/procesos/${id}`));
  }

  /**
   * El expediente completo, notas incluidas.
   *
   * <p>Esta llamada <strong>deja asiento en la bitácora</strong> (RF-08): abrir
   * un expediente es un acceso al contenido, y la lectura se audita. No hay
   * nada que hacer aquí para que ocurra —lo hace el backend— pero conviene
   * saberlo: no es una consulta gratis.
   */
  async expediente(procesoId: number): Promise<Pieza[]> {
    return firstValueFrom(this.http.get<Pieza[]>(`/api/procesos/${procesoId}/expediente`));
  }

  /**
   * Los valores de un catálogo del despacho, para los desplegables.
   *
   * <p>Se piden los ACTIVOS: un tipo de proceso desactivado no debe ofrecerse
   * para clasificar nada nuevo. Se guardan en memoria porque un catálogo
   * cambia una vez al año y la pantalla se abre veinte veces al día.
   */
  async catalogo(tipo: string): Promise<ValorCatalogo[]> {
    const guardado = this._catalogos()[tipo];
    if (guardado) return guardado;

    const valores = await firstValueFrom(
      this.http.get<ValorCatalogo[]>(`/api/catalogos/${tipo}/activos`));

    this._catalogos.update(actual => ({ ...actual, [tipo]: valores }));
    return valores;
  }
}
