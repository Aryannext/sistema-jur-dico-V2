import { Component, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';

import { AsientoBitacora } from '../../nucleo/modelos';
import { Bitacora as ServicioBitacora } from './bitacora.servicio';

/** Los cuatro tipos de acceso que quedan registrados. */
type Accion = AsientoBitacora['accion'];

/**
 * La bitácora de auditoría. RF-08 · RNF-07 · HU-08 · CU-06.
 *
 * <h2>Por qué esta pantalla no tiene ni un solo botón que modifique nada</h2>
 *
 * <p>No hay «borrar asiento», ni «marcar como revisado», ni «exportar y
 * limpiar». <em>«Una bitácora que el auditado puede editar no sirve como
 * evidencia»</em> (CA-08.2), y la forma más segura de que no se pueda editar es
 * que no haya por dónde — ni aquí ni en el backend, donde el propio motor de
 * base de datos rechaza cualquier UPDATE o DELETE sobre la tabla.
 *
 * <h2>Quién la ve, y por qué el abogado no</h2>
 *
 * <p>Solo el Administrador de Despacho, que es quien tiene que responder si se
 * cuestiona el manejo de información reservada. El abogado queda fuera a
 * propósito: la bitácora registra también <strong>sus</strong> accesos, y darle
 * la llave no le permitiría borrar nada —eso no puede nadie— pero sí saber qué
 * quedó registrado de él.
 *
 * <h2>Leer un expediente deja rastro, y esta pantalla lo dice</h2>
 *
 * <p>Abrir un expediente crea un asiento. También los del propio administrador
 * que está mirando esta lista. Se avisa en pantalla porque un sistema que
 * registra en silencio lo que hacen sus usuarios es peor que uno que no
 * registra nada.
 */
@Component({
  selector: 'sgpj-bitacora',
  templateUrl: './bitacora.html',
  styleUrl: './bitacora.css',
})
export class BitacoraDeAuditoria {

  private readonly servicio = inject(ServicioBitacora);

  protected readonly asientos = signal<AsientoBitacora[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);

  /** null = todas. */
  protected readonly filtro = signal<Accion | null>(null);

  protected readonly visibles = computed(() => {
    const f = this.filtro();
    return f === null ? this.asientos() : this.asientos().filter(a => a.accion === f);
  });

  /**
   * Cuántos accesos hubo desde el portal.
   *
   * <p>Se cuenta aparte porque es la cifra que más le importa al despacho: son
   * los accesos de sus clientes, los únicos que no controla desde dentro.
   */
  protected readonly desdeElPortal = computed(
    () => this.asientos().filter(a => a.accion.endsWith('PORTAL')).length);

  protected readonly descargas = computed(
    () => this.asientos().filter(a => a.accion.startsWith('DESCARGA')).length);

  constructor() {
    void this.cargar();
  }

  protected async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    try {
      this.asientos.set(await this.servicio.deMiDespacho());
    } catch (fallo) {
      this.error.set(this.mensajeDe(fallo));
    } finally {
      this.cargando.set(false);
    }
  }

  protected filtrarPor(accion: Accion | null): void {
    this.filtro.set(accion);
  }

  /**
   * El texto de la acción, en español y contando qué pasó de verdad.
   *
   * <p>No se muestra el nombre del enum. `DESCARGA_PORTAL` no le dice nada a
   * nadie; «el cliente descargó un documento» sí, y es justo la línea que un
   * administrador necesita reconocer de un vistazo si alguien pregunta quién
   * sacó un archivo del despacho.
   */
  protected textoDe(accion: Accion): string {
    switch (accion) {
      case 'CONSULTA_EXPEDIENTE': return 'Abrió el expediente';
      case 'DESCARGA_DOCUMENTO': return 'Descargó un documento';
      case 'CONSULTA_PORTAL': return 'El cliente consultó su expediente';
      case 'DESCARGA_PORTAL': return 'El cliente descargó un documento';
    }
  }

  /** Si el acceso vino del portal del cliente o de dentro del despacho. */
  protected esDelPortal(accion: Accion): boolean {
    return accion.endsWith('PORTAL');
  }

  protected esDescarga(accion: Accion): boolean {
    return accion.startsWith('DESCARGA');
  }

  /**
   * Fecha y hora completas, siempre.
   *
   * <p>Nada de «hace 3 horas». En una bitácora que puede acabar delante de un
   * juez disciplinario, un tiempo relativo no dice nada: lo que importa es el
   * día y la hora exactos, y que sigan significando lo mismo dentro de un año.
   */
  protected momentoDe(iso: string): string {
    const f = new Date(iso);
    return f.toLocaleDateString('es-CO', {
      day: '2-digit', month: 'short', year: 'numeric',
    }) + ' · ' + f.toLocaleTimeString('es-CO', {
      hour: '2-digit', minute: '2-digit',
    });
  }

  protected iniciales(correo: string): string {
    const nombre = correo.split('@')[0].replace(/[._-]+/g, ' ').trim();
    const partes = nombre.split(/\s+/);
    if (partes.length === 0 || partes[0] === '') return '?';
    const primera = partes[0][0];
    const segunda = partes.length > 1 ? partes[partes.length - 1][0] : '';
    return (primera + segunda).toUpperCase();
  }

  private mensajeDe(fallo: unknown): string {
    if (fallo instanceof HttpErrorResponse) {
      const detalle = fallo.error?.detail;
      if (typeof detalle === 'string' && detalle.trim()) return detalle;
    }
    return 'No se pudo consultar la bitácora. Inténtelo de nuevo.';
  }
}
