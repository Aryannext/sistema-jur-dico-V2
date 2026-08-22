import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

import { ValorCatalogo } from '../nucleo/modelos';
import { CATALOGOS, Configuracion } from './configuracion.servicio';
import { mensajeDeError } from '../nucleo/mensajes';

/**
 * Catálogos del despacho. RF-33 · HU-37 · D-13.
 *
 * <p>Los cinco catálogos son <strong>de cada despacho</strong>, no de la
 * plataforma: una firma de familia y una penal no clasifican igual sus
 * actuaciones, y fijar las listas en código nos obligaría a inventar una
 * clasificación jurídica e imponérsela a todos.
 *
 * <p>Aquí no hay botón de borrar, y no falta: un valor en uso se desactiva
 * (RN-06). Si desapareciera, los procesos que lo tienen quedarían sin
 * clasificación y el reporte por tipo empezaría a mentir.
 */
@Component({
  selector: 'sgpj-catalogos',
  imports: [RouterLink, RouterLinkActive],
  templateUrl: './catalogos.html',
  styleUrl: './configuracion.css',
})
export class Catalogos {

  private readonly servicio = inject(Configuracion);

  protected readonly catalogos = CATALOGOS;
  protected readonly elegido = signal<string>(CATALOGOS[0].tipo);

  protected readonly valores = signal<ValorCatalogo[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly ocupado = signal<number | null>(null);

  protected readonly nuevo = signal('');

  /** Las sugerencias de juzgados. Solo se piden una vez. */
  protected readonly sugerencias = signal<string[]>([]);

  /**
   * Las que el despacho todavía NO tiene.
   *
   * <p>Las ya agregadas desaparecen de la lista en vez de quedar marcadas: una
   * sugerencia que no se puede usar es ruido, y con más de cuarenta la lista
   * necesita encogerse a medida que se trabaja, no crecer en tachones.
   */
  protected readonly sugerenciasPendientes = computed(() => {
    if (this.elegido() !== 'JUZGADO') return [];
    const puestos = new Set(this.valores().map(v => v.nombre.trim().toLowerCase()));
    return this.sugerencias().filter(s => !puestos.has(s.trim().toLowerCase()));
  });
  protected readonly guardando = signal(false);

  /** Cuál se está renombrando, y con qué texto. */
  protected readonly editando = signal<number | null>(null);
  protected readonly nombreEditado = signal('');

  protected readonly ficha = computed(
    () => CATALOGOS.find(c => c.tipo === this.elegido()) ?? CATALOGOS[0]);

  protected readonly activos = computed(() => this.valores().filter(v => v.activo).length);

  protected readonly puedeAgregar = computed(
    () => !this.guardando() && this.nuevo().trim().length > 0);

  constructor() {
    void this.cargar();
    // Se piden una sola vez: son una constante del sistema, no del despacho.
    void this.servicio.juzgadosSugeridos()
        .then(js => this.sugerencias.set(js))
        .catch(() => this.sugerencias.set([]));   // sin sugerencias se teclea, sin más
  }

  protected async elegir(tipo: string): Promise<void> {
    this.elegido.set(tipo);
    this.editando.set(null);
    this.nuevo.set('');
    await this.cargar();
  }

  protected async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);

    try {
      const valores = await this.servicio.catalogo(this.elegido());
      this.valores.set(valores.sort((a, b) => a.orden - b.orden));
    } catch {
      this.valores.set([]);
      this.error.set('No se pudo cargar el catálogo. Vuelva a intentarlo.');
    } finally {
      this.cargando.set(false);
    }
  }

  /**
   * Pone la sugerencia en el campo, <strong>sin agregarla</strong>.
   *
   * <p>Se eligió así y no con un alta directa porque la lista no es oficial: los
   * números pueden haber cambiado. Que el nombre pase por el campo deja al
   * abogado corregirlo antes de guardar, que es quien sabe ante cuál litiga.
   */
  protected usarSugerencia(nombre: string): void {
    this.nuevo.set(nombre);
    this.error.set(null);
  }

  protected escribirNuevo(evento: Event): void {
    this.nuevo.set((evento.target as HTMLInputElement).value);
    this.error.set(null);
  }

  protected escribirEditado(evento: Event): void {
    this.nombreEditado.set((evento.target as HTMLInputElement).value);
    this.error.set(null);
  }

  async agregar(evento: Event): Promise<void> {
    evento.preventDefault();
    if (!this.puedeAgregar()) return;

    this.guardando.set(true);
    this.error.set(null);

    try {
      // El orden se calcula: pedirlo sería un campo más para algo que casi
      // siempre es «al final».
      const siguiente = this.valores().reduce((mayor, v) => Math.max(mayor, v.orden), 0) + 1;
      await this.servicio.agregar(this.elegido(), this.nuevo().trim(), siguiente);

      this.nuevo.set('');
      await this.cargar();

    } catch (fallo) {
      this.error.set(mensajeDeError(fallo, 'No se pudo completar la operación. Inténtelo de nuevo.'));

    } finally {
      this.guardando.set(false);
    }
  }

  protected abrirEdicion(valor: ValorCatalogo): void {
    this.editando.set(valor.id);
    this.nombreEditado.set(valor.nombre);
    this.error.set(null);
  }

  async renombrar(valor: ValorCatalogo, evento: Event): Promise<void> {
    evento.preventDefault();
    const nombre = this.nombreEditado().trim();
    if (nombre.length === 0 || nombre === valor.nombre) {
      this.editando.set(null);
      return;
    }

    this.ocupado.set(valor.id);
    this.error.set(null);

    try {
      await this.servicio.renombrar(valor.id, nombre, valor.orden);
      this.editando.set(null);
      await this.cargar();
    } catch (fallo) {
      this.error.set(mensajeDeError(fallo, 'No se pudo completar la operación. Inténtelo de nuevo.'));
    } finally {
      this.ocupado.set(null);
    }
  }

  async cambiarEstado(valor: ValorCatalogo): Promise<void> {
    this.ocupado.set(valor.id);
    this.error.set(null);

    try {
      await this.servicio.cambiarEstado(valor.id, !valor.activo);
      await this.cargar();
    } catch (fallo) {
      this.error.set(mensajeDeError(fallo, 'No se pudo completar la operación. Inténtelo de nuevo.'));
    } finally {
      this.ocupado.set(null);
    }
  }

}
