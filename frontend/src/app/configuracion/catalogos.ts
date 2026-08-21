import { Component, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink, RouterLinkActive } from '@angular/router';

import { ValorCatalogo } from '../nucleo/modelos';
import { CATALOGOS, Configuracion } from './configuracion.servicio';

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
      this.error.set(this.mensajeDe(fallo));

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
      this.error.set(this.mensajeDe(fallo));
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
      this.error.set(this.mensajeDe(fallo));
    } finally {
      this.ocupado.set(null);
    }
  }

  private mensajeDe(fallo: unknown): string {
    if (fallo instanceof HttpErrorResponse) {
      const detalle = fallo.error?.detail;
      if (typeof detalle === 'string' && detalle.trim()) return detalle;
    }
    return 'No se pudo completar la operación. Inténtelo de nuevo.';
  }
}
