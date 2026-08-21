import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

import { Rol, Usuario } from './modelos';

/**
 * Cuenta propia y usuarios del despacho. RF-05 · RF-39 · RF-40 · D-24.
 */
@Injectable({ providedIn: 'root' })
export class Cuenta {

  private readonly http = inject(HttpClient);

  /**
   * RF-39 · CA-43.1: cambiar mi propia contraseña.
   *
   * <p>La ruta no cuelga de `/api/usuarios` a propósito: esa rama es solo del
   * administrador, y este requisito alcanza también al cliente del portal.
   */
  async cambiarMiContrasena(contrasenaActual: string, contrasenaNueva: string): Promise<void> {
    await firstValueFrom(this.http.put('/api/mi-contrasena', {
      contrasenaActual, contrasenaNueva,
    }));
  }

  /** RF-40 · CA-44.1: restablecer la de alguien de mi despacho. */
  async restablecerContrasena(usuarioId: number, contrasenaNueva: string): Promise<void> {
    await firstValueFrom(
      this.http.put(`/api/usuarios/${usuarioId}/contrasena`, { contrasenaNueva }));
  }

  async usuarios(): Promise<Usuario[]> {
    return firstValueFrom(this.http.get<Usuario[]>('/api/usuarios'));
  }

  async rolesDisponibles(): Promise<Rol[]> {
    return firstValueFrom(this.http.get<Rol[]>('/api/usuarios/roles-disponibles'));
  }

  /** RF-05 · CA-05.1: crear un usuario en MI despacho. El backend fija cuál. */
  async crear(nombre: string, correo: string, contrasena: string, roles: string[]): Promise<Usuario> {
    return firstValueFrom(
      this.http.post<Usuario>('/api/usuarios', { nombre, correo, contrasena, roles }));
  }

  /** RF-05 · CA-05.2: los roles se reemplazan en bloque, no se suman de a uno. */
  async reemplazarRoles(usuarioId: number, roles: string[]): Promise<Usuario> {
    return firstValueFrom(
      this.http.put<Usuario>(`/api/usuarios/${usuarioId}/roles`, { roles }));
  }

  /**
   * CA-02.1 aplicado al usuario: surte efecto de inmediato, aunque tenga la
   * sesión abierta. No borra nada de lo que registró (RF-38).
   */
  async cambiarEstado(usuarioId: number, activo: boolean): Promise<Usuario> {
    const accion = activo ? 'activar' : 'desactivar';
    return firstValueFrom(this.http.put<Usuario>(`/api/usuarios/${usuarioId}/${accion}`, {}));
  }
}
