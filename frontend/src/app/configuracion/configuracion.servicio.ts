import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

import { ValorCatalogo } from '../nucleo/modelos';
import { Procesos } from '../despacho/procesos/procesos.servicio';

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

  /**
   * Para avisarle de que sus desplegables ya no son ciertos.
   *
   * <p>Nace de un defecto real: `Procesos` guarda los catálogos en memoria y
   * nadie los invalidaba nunca. Un abogado agregaba su primer juzgado aquí,
   * iba a Nuevo proceso y seguía leyendo que su catálogo estaba vacío; solo
   * recargar la página entera lo arreglaba.
   *
   * <p>Se olvidan <strong>todos</strong> y no solo el que cambió: renombrar y
   * cambiar el estado reciben un id, no un tipo, así que averiguar cuál tocar
   * costaría más de lo que ahorra. Volver a pedir cuatro listas diminutas la
   * vez que alguien edita un catálogo es un precio que no se nota.
   */
  private readonly procesos = inject(Procesos);

  /** Todos los valores del tipo, activos e inactivos: aquí se administran. */
  async catalogo(tipo: string): Promise<ValorCatalogo[]> {
    return firstValueFrom(this.http.get<ValorCatalogo[]>(`/api/catalogos/${tipo}`));
  }

  /**
   * Los juzgados de Neiva que el sistema sugiere. RF-33 · CA-37.5.
   *
   * <p>Son sugerencias, no un catálogo sembrado: el del despacho sigue naciendo
   * vacío. Esto solo evita teclear los mismos nombres y, sobre todo, evita que
   * se escriban distinto — que es lo que degrada la búsqueda por juzgado dentro
   * del propio despacho.
   */
  async juzgadosSugeridos(): Promise<string[]> {
    return firstValueFrom(this.http.get<string[]>('/api/catalogos/JUZGADO/sugerencias'));
  }

  async agregar(tipo: string, nombre: string, orden: number | null): Promise<ValorCatalogo> {
    const valor = await firstValueFrom(
      this.http.post<ValorCatalogo>(`/api/catalogos/${tipo}`, { nombre, orden }));

    this.procesos.olvidarCatalogo();
    return valor;
  }

  /** RN-06: renombrar conserva el identificador, así lo ya clasificado no se pierde. */
  async renombrar(id: number, nombre: string, orden: number | null): Promise<ValorCatalogo> {
    const valor = await firstValueFrom(
      this.http.put<ValorCatalogo>(`/api/catalogos/valores/${id}`, { nombre, orden }));

    this.procesos.olvidarCatalogo();
    return valor;
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
    const valor = await firstValueFrom(
      this.http.put<ValorCatalogo>(`/api/catalogos/valores/${id}/${accion}`, {}));

    // Desactivar importa tanto como agregar: un valor desactivado NO debe
    // seguir ofreciendose para clasificar cosas nuevas, y sin esto seguiria
    // apareciendo en el desplegable hasta que alguien recargara.
    this.procesos.olvidarCatalogo();
    return valor;
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
