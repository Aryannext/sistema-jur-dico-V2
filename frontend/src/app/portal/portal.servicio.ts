import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

import {
  AudienciaDelCliente,
  PerfilCliente,
  PiezaDelCliente,
  ProcesoDelCliente,
} from '../nucleo/modelos';

/**
 * El portal del cliente. RF-28 · RF-29 · RF-30 · HU-32 a HU-34.
 *
 * <p><strong>Aquí no hay un solo método que escriba.</strong> Ni POST, ni PUT,
 * ni DELETE: esa ausencia es el requisito (CA-32.2). El portal informa; no
 * permite intervenir en el proceso.
 */
@Injectable({ providedIn: 'root' })
export class Portal {

  private readonly http = inject(HttpClient);

  async miPerfil(): Promise<PerfilCliente> {
    return firstValueFrom(this.http.get<PerfilCliente>('/api/portal/mi-perfil'));
  }

  /** RF-28 · CA-32.1: mis procesos, y solo los míos. */
  async misProcesos(): Promise<ProcesoDelCliente[]> {
    return firstValueFrom(this.http.get<ProcesoDelCliente[]>('/api/portal/mis-procesos'));
  }

  async miProceso(id: number): Promise<ProcesoDelCliente> {
    return firstValueFrom(this.http.get<ProcesoDelCliente>(`/api/portal/procesos/${id}`));
  }

  /**
   * RF-29 · RF-30: el expediente tal como lo ve el cliente.
   *
   * <p>Las notas internas no llegan, y el filtro ocurre en el servidor
   * (RN-24). Esta pantalla no las oculta: nunca las recibe.
   */
  async miExpediente(procesoId: number): Promise<PiezaDelCliente[]> {
    return firstValueFrom(
      this.http.get<PiezaDelCliente[]>(`/api/portal/procesos/${procesoId}/expediente`));
  }

  async misAudiencias(): Promise<AudienciaDelCliente[]> {
    return firstValueFrom(
      this.http.get<AudienciaDelCliente[]>('/api/portal/mis-audiencias'));
  }

  async audienciasDe(procesoId: number): Promise<AudienciaDelCliente[]> {
    return firstValueFrom(
      this.http.get<AudienciaDelCliente[]>(`/api/portal/procesos/${procesoId}/audiencias`));
  }
}
