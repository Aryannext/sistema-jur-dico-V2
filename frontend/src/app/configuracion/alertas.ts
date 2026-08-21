import { Component, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink, RouterLinkActive } from '@angular/router';

import { Configuracion, Esquema } from './configuracion.servicio';

/**
 * Esquema de alertas de términos. RF-34 · HU-38 · D-16.
 *
 * <p>El despacho decide con cuánta anticipación quiere que le avisen. Lo que
 * <strong>no</strong> puede es quedarse sin ninguno (RN-37b): un despacho sin
 * avisos configurados tendría un sistema de vigilancia que no vigila, y nadie
 * se enteraría hasta que se venciera algo.
 *
 * <p>Las alertas de <em>audiencias</em> no se configuran aquí: son las tres que
 * la propuesta fija literalmente —48 h, 24 h y el mismo día— y no son
 * negociables. Se dice en la pantalla para que su ausencia no parezca un
 * olvido.
 */
@Component({
  selector: 'sgpj-alertas',
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './alertas.html',
  styleUrl: './configuracion.css',
})
export class Alertas {

  private readonly servicio = inject(Configuracion);

  protected readonly esquema = signal<Esquema | null>(null);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly guardando = signal(false);
  protected readonly hecho = signal(false);

  /** Los días que el usuario tiene marcados ahora mismo, sin guardar todavía. */
  protected readonly elegidos = signal<Set<number>>(new Set());
  protected readonly personalizado = signal('');

  /**
   * Las anticipaciones que se ofrecen de un vistazo.
   *
   * <p>No son un límite: abajo se puede escribir cualquier otra. Son los
   * plazos con los que de verdad se trabaja —la víspera, la semana, la
   * quincena— para que el caso normal sea un clic.
   */
  protected readonly sugeridos = [1, 2, 3, 5, 8, 10, 15, 30];

  protected readonly ordenados = computed(
    () => [...this.elegidos()].sort((a, b) => b - a));

  /** RN-37b: sin ningún aviso, la vigilancia no vigila. */
  protected readonly sinNinguno = computed(() => this.elegidos().size === 0);

  protected readonly hayCambios = computed(() => {
    const guardados = [...(this.esquema()?.diasAnticipacion ?? [])].sort((a, b) => a - b).join(',');
    const actuales = [...this.elegidos()].sort((a, b) => a - b).join(',');
    return guardados !== actuales;
  });

  protected readonly puedeGuardar = computed(
    () => !this.guardando() && !this.sinNinguno() && this.hayCambios());

  constructor() {
    void this.cargar();
  }

  protected async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);

    try {
      const esquema = await this.servicio.esquemaAlertas();
      this.esquema.set(esquema);
      this.elegidos.set(new Set(esquema.diasAnticipacion));

    } catch {
      this.esquema.set(null);
      this.error.set('No se pudo cargar la configuración de alertas.');

    } finally {
      this.cargando.set(false);
    }
  }

  protected alternar(dias: number): void {
    this.elegidos.update(actuales => {
      const copia = new Set(actuales);
      if (copia.has(dias)) copia.delete(dias); else copia.add(dias);
      return copia;
    });
    this.hecho.set(false);
    this.error.set(null);
  }

  protected escribirPersonalizado(evento: Event): void {
    this.personalizado.set((evento.target as HTMLInputElement).value);
  }

  protected agregarPersonalizado(evento: Event): void {
    evento.preventDefault();

    // El backend rechaza 0 —«avisar el mismo día del vencimiento llega
    // tarde»— y también más de 365. Se comprueba aquí para no mandar al
    // usuario a chocar contra un error que se puede explicar antes.
    const dias = Number(this.personalizado());
    if (!Number.isInteger(dias) || dias < 1 || dias > 365) {
      this.error.set(
        'Indique un número de días entre 1 y 365. Avisar el mismo día del vencimiento llegaría tarde.');
      return;
    }

    this.alternar(dias);
    if (!this.elegidos().has(dias)) this.alternar(dias); // si lo quitó, volver a ponerlo
    this.personalizado.set('');
  }

  protected quitar(dias: number): void {
    this.elegidos.update(actuales => {
      const copia = new Set(actuales);
      copia.delete(dias);
      return copia;
    });
    this.hecho.set(false);
  }

  protected descansar(): void {
    this.elegidos.set(new Set(this.esquema()?.diasAnticipacion ?? []));
    this.hecho.set(false);
    this.error.set(null);
  }

  async guardar(): Promise<void> {
    if (!this.puedeGuardar()) return;

    this.guardando.set(true);
    this.error.set(null);

    try {
      const esquema = await this.servicio.cambiarEsquema([...this.elegidos()]);
      this.esquema.set(esquema);
      this.elegidos.set(new Set(esquema.diasAnticipacion));
      this.hecho.set(true);

    } catch (fallo) {
      this.error.set(this.mensajeDe(fallo));

    } finally {
      this.guardando.set(false);
    }
  }

  /** «1 día antes» — no «1 días antes». */
  protected comoSeLee(dias: number): string {
    if (dias === 1) return '1 día antes';
    return `${dias} días antes`;
  }

  private mensajeDe(fallo: unknown): string {
    if (fallo instanceof HttpErrorResponse) {
      const detalle = fallo.error?.detail;
      if (typeof detalle === 'string' && detalle.trim()) return detalle;
    }
    return 'No se pudo guardar la configuración. Inténtelo de nuevo.';
  }
}
