import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

import { AltaDespacho, Despacho } from '../nucleo/modelos';

/**
 * Los despachos de la plataforma. RF-01 · RF-02 · RF-03 · RN-10.
 *
 * <p>Es el único servicio del frontend que habla con `/api/despachos`, y el
 * backend lo restringe a `ROL_ADMIN_PLATAFORMA`.
 */
@Injectable({ providedIn: 'root' })
export class Despachos {

  private readonly http = inject(HttpClient);

  async listar(): Promise<Despacho[]> {
    return firstValueFrom(this.http.get<Despacho[]>('/api/despachos'));
  }

  /** RF-01 · CA-01.2: el despacho nace con su primer administrador. */
  async registrar(
    nombre: string, nit: string | null, correoContacto: string, telefono: string | null,
    administrador: { nombre: string; correo: string; contrasena: string },
  ): Promise<AltaDespacho> {
    return firstValueFrom(this.http.post<AltaDespacho>('/api/despachos', {
      nombre, nit, correoContacto, telefono, administrador,
    }));
  }

  /**
   * RF-02 · CA-02.1: surte efecto de inmediato, aunque su gente tenga la
   * sesión abierta.
   *
   * <p>Activar y desactivar son acciones propias y no un campo del formulario,
   * igual que en el backend: así el cambio de estado nunca ocurre de refilón al
   * guardar un teléfono.
   */
  async cambiarEstado(id: number, activo: boolean): Promise<Despacho> {
    const accion = activo ? 'activar' : 'desactivar';
    return firstValueFrom(this.http.put<Despacho>(`/api/despachos/${id}/${accion}`, {}));
  }
}
