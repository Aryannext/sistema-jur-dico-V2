import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

import { ValorCatalogo } from '../nucleo/modelos';

/** GET /api/esquema-alertas — RF-34. */
export interface Esquema {
  diasAnticipacion: number[];
  /**
   * La frase la redacta el BACKEND, no esta pantalla.
   *
   * <p>Igual que la advertencia de RF-16: si el texto viviera aquí, cambiar el
   * comportamiento del motor no obligaría a tocarlo, y la explicación acabaría
   * describiendo algo que ya no ocurre.
   */
  explicacion: string;
}

/** Los cinco catálogos del despacho, en el orden en que se usan. */
export const CATALOGOS = [
  { tipo: 'TIPO_PROCESO', nombre: 'Tipo de proceso', para: 'Clasifica cada proceso al crearlo.' },
  { tipo: 'ESTADO_PROCESAL', nombre: 'Estado procesal', para: 'En qué punto está cada proceso.' },
  { tipo: 'TIPO_ACTUACION', nombre: 'Tipo de actuación', para: 'Clasifica lo que ocurre en el expediente.' },
  { tipo: 'TIPO_DOCUMENTO', nombre: 'Tipo de documento', para: 'Clasifica los archivos del expediente.' },
  { tipo: 'JUZGADO', nombre: 'Juzgados', para: 'Los juzgados ante los que usted litiga.' },
] as const;

@Injectable({ providedIn: 'root' })
export class Configuracion {

  private readonly http = inject(HttpClient);

  /** Todos los valores del tipo, activos e inactivos: aquí se administran. */
  async catalogo(tipo: string): Promise<ValorCatalogo[]> {
    return firstValueFrom(this.http.get<ValorCatalogo[]>(`/api/catalogos/${tipo}`));
  }

  async agregar(tipo: string, nombre: string, orden: number | null): Promise<ValorCatalogo> {
    return firstValueFrom(
      this.http.post<ValorCatalogo>(`/api/catalogos/${tipo}`, { nombre, orden }));
  }

  /** RN-06: renombrar conserva el identificador, así lo ya clasificado no se pierde. */
  async renombrar(id: number, nombre: string, orden: number | null): Promise<ValorCatalogo> {
    return firstValueFrom(
      this.http.put<ValorCatalogo>(`/api/catalogos/valores/${id}`, { nombre, orden }));
  }

  /**
   * RN-06: un valor en uso no se borra, se desactiva.
   *
   * <p>No existe endpoint de borrado, y por eso tampoco existe botón: si
   * «Ejecutivo singular» desapareciera, los procesos que lo tienen quedarían
   * sin tipo y el reporte por tipo empezaría a mentir.
   */
  async cambiarEstado(id: number, activo: boolean): Promise<ValorCatalogo> {
    const accion = activo ? 'activar' : 'desactivar';
    return firstValueFrom(
      this.http.put<ValorCatalogo>(`/api/catalogos/valores/${id}/${accion}`, {}));
  }

  async esquemaAlertas(): Promise<Esquema> {
    return firstValueFrom(this.http.get<Esquema>('/api/esquema-alertas'));
  }

  /** El backend rechaza el conjunto vacío (RN-37b): sin avisos no hay vigilancia. */
  async cambiarEsquema(diasAnticipacion: number[]): Promise<Esquema> {
    return firstValueFrom(
      this.http.put<Esquema>('/api/esquema-alertas', { diasAnticipacion }));
  }
}
