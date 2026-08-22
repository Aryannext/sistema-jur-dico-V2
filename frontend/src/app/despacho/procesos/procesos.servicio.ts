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

/** GET /api/procesos/{id}/documentos/advertencia — RF-16. */
export interface Advertencia {
  mensaje: string;
  alternativa: string;
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

  /**
   * RF-11 · RF-13: crea el proceso y, con él, su expediente.
   *
   * <p>El cliente titular va en el alta y no se asigna después: **crear el
   * proceso ES vincularlo al cliente**. Un proceso sin titular no existe en la
   * realidad que este sistema modela, y permitirlo dejaría procesos huérfanos
   * que ningún cliente vería en su portal.
   *
   * <p>El despacho no viaja en la petición: lo fija el backend desde la sesión
   * (ADR-03, control 1). Mandarlo desde aquí sería ofrecerle a un navegador la
   * posibilidad de crear procesos en otro despacho.
   */
  async crear(datos: {
    radicado: string;
    juzgadoId: number;
    tipoProcesoId: number;
    estadoProcesalId: number;
    clienteTitularId: number;
    abogadoResponsableId: number;
    descripcion: string | null;
  }): Promise<Proceso> {
    return firstValueFrom(this.http.post<Proceso>('/api/procesos', datos));
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
   * La advertencia de RF-16, tal como la redacta el backend.
   *
   * <p>No se escribe aquí una versión propia. El requisito dice que el sistema
   * debe advertir; si el texto viviera en el frontend, cambiarlo no requeriría
   * tocar nada del backend y la advertencia podría acabar diciendo algo que ya
   * no es verdad. Que venga del servidor la ata a la regla que la sostiene.
   */
  async advertenciaDeCarga(procesoId: number): Promise<Advertencia> {
    return firstValueFrom(
      this.http.get<Advertencia>(`/api/procesos/${procesoId}/documentos/advertencia`));
  }

  /** RF-17: registrar una actuación. */
  async registrarActuacion(
    procesoId: number, tipoActuacionId: number, fecha: string, descripcion: string,
  ): Promise<Pieza> {
    return firstValueFrom(this.http.post<Pieza>(
      `/api/procesos/${procesoId}/actuaciones`, { tipoActuacionId, fecha, descripcion }));
  }

  /** RF-18: registrar una nota interna. No lleva marca de privacidad: lo es. */
  async registrarNota(procesoId: number, contenido: string): Promise<Pieza> {
    return firstValueFrom(
      this.http.post<Pieza>(`/api/procesos/${procesoId}/notas`, { contenido }));
  }

  /**
   * RF-15: cargar un documento. Se guarda cifrado (RNF-04).
   *
   * <p>Va como multipart y no como base64 dentro de un JSON: un PDF escaneado
   * de 20 MB se convertiría en unos 27 MB de texto, y habría que tenerlo entero
   * en memoria dos veces antes de empezar a subirlo.
   */
  async cargarDocumento(
    procesoId: number, tipoDocumentoId: number, archivo: File,
  ): Promise<Pieza> {
    const cuerpo = new FormData();
    cuerpo.append('tipoDocumentoId', String(tipoDocumentoId));
    cuerpo.append('archivo', archivo, archivo.name);

    return firstValueFrom(
      this.http.post<Pieza>(`/api/procesos/${procesoId}/documentos`, cuerpo));
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
