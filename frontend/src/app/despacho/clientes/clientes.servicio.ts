import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

import { Proceso } from '../../nucleo/modelos';

/** GET /api/clientes — RF-09 · RF-10. */
export interface Cliente {
  id: number;
  nombre: string;
  documentoIdentidad: string | null;
  telefono: string | null;
  correo: string | null;
  tieneAccesoAlPortal: boolean;
  fechaRegistro: string;
}

export interface DatosCliente {
  nombre: string;
  documentoIdentidad: string | null;
  telefono: string | null;
  correo: string | null;
}

@Injectable({ providedIn: 'root' })
export class Clientes {

  private readonly http = inject(HttpClient);

  async listar(): Promise<Cliente[]> {
    return firstValueFrom(this.http.get<Cliente[]>('/api/clientes'));
  }

  async cliente(id: number): Promise<Cliente> {
    return firstValueFrom(this.http.get<Cliente>(`/api/clientes/${id}`));
  }

  /** RN-15: los procesos de los que este cliente es titular. */
  async procesosDe(clienteId: number): Promise<Proceso[]> {
    return firstValueFrom(
      this.http.get<Proceso[]>(`/api/procesos/de-cliente/${clienteId}`));
  }

  async registrar(datos: DatosCliente): Promise<Cliente> {
    return firstValueFrom(this.http.post<Cliente>('/api/clientes', datos));
  }

  async actualizar(id: number, datos: DatosCliente): Promise<Cliente> {
    return firstValueFrom(this.http.put<Cliente>(`/api/clientes/${id}`, datos));
  }

  /**
   * Habilitar el portal. RF-07 · CA-07.1.
   *
   * <p><strong>El despacho fija la contraseña</strong> y se la comunica al
   * cliente. No hay correo de invitación ni enlace para que la defina él: el
   * sistema no tiene ese flujo. Tampoco existe —en ninguna parte— una forma de
   * cambiar la contraseña después, así que la que se escriba aquí es la que el
   * cliente usará siempre.
   *
   * <p>Se dice tal cual en la pantalla. Prometer un correo que nunca sale
   * dejaría al cliente esperando y al despacho sin entender por qué no entra.
   */
  async habilitarPortal(clienteId: number, correo: string, contrasena: string): Promise<void> {
    await firstValueFrom(this.http.post(
      `/api/clientes/${clienteId}/acceso-portal`, { correo, contrasena }));
  }

  /** RF-07 · CA-07.3: revocar no borra nada; el expediente queda intacto. */
  async revocarPortal(clienteId: number): Promise<void> {
    await firstValueFrom(this.http.delete(`/api/clientes/${clienteId}/acceso-portal`));
  }
}
