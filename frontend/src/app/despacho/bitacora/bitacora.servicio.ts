import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

import { AsientoBitacora } from '../../nucleo/modelos';

/**
 * La bitácora de auditoría. RF-08 · RNF-07.
 *
 * <p>Solo hay lecturas, y esa ausencia es el requisito: los asientos los crea
 * el sistema cuando alguien accede, no un usuario cuando le apetece, y no hay
 * forma de corregirlos ni de borrarlos. «Una bitácora que el auditado puede
 * editar no sirve como evidencia» (CA-08.2). Aquí no hay `crear`, `actualizar`
 * ni `borrar` porque en el backend tampoco existen.
 */
@Injectable({ providedIn: 'root' })
export class Bitacora {

  private readonly http = inject(HttpClient);

  async deMiDespacho(): Promise<AsientoBitacora[]> {
    return firstValueFrom(this.http.get<AsientoBitacora[]>('/api/bitacora'));
  }

  async deProceso(procesoId: number): Promise<AsientoBitacora[]> {
    return firstValueFrom(
      this.http.get<AsientoBitacora[]>(`/api/bitacora/proceso/${procesoId}`));
  }
}
