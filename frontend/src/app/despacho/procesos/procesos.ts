import { Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Proceso, ValorCatalogo } from '../../nucleo/modelos';
import { Filtros, Procesos as ServicioProcesos, SIN_FILTROS } from './procesos.servicio';

/**
 * Lista y búsqueda de procesos. RF-11 · RF-31 · HU-35.
 *
 * <p>La búsqueda por radicado <strong>no exige escribirlo completo</strong>:
 * el backend busca por fragmento. Un radicado de 23 dígitos que hay que
 * teclear exacto es un buscador que nadie usa.
 */
@Component({
  selector: 'sgpj-procesos',
  imports: [RouterLink],
  templateUrl: './procesos.html',
  styleUrl: './procesos.css',
})
export class ListaProcesos implements OnDestroy {

  private readonly servicio = inject(ServicioProcesos);

  protected readonly procesos = signal<Proceso[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly filtros = signal<Filtros>({ ...SIN_FILTROS });

  protected readonly estados = signal<ValorCatalogo[]>([]);
  protected readonly tipos = signal<ValorCatalogo[]>([]);
  protected readonly juzgados = signal<ValorCatalogo[]>([]);

  protected readonly hayFiltros = computed(() => {
    const f = this.filtros();
    return f.radicado.trim() !== ''
      || f.estadoId !== null || f.tipoProcesoId !== null || f.juzgadoId !== null;
  });

  /**
   * Espera a que el usuario deje de escribir antes de consultar.
   *
   * <p>Sin esto, escribir un radicado de 23 dígitos dispararía 23 búsquedas, y
   * las respuestas podrían llegar desordenadas: la del prefijo corto después
   * de la del largo, dejando en pantalla resultados que no corresponden a lo
   * que se ve escrito.
   */
  private temporizador: ReturnType<typeof setTimeout> | null = null;

  constructor() {
    void this.cargarCatalogos();
    void this.buscar();
  }

  ngOnDestroy(): void {
    if (this.temporizador) clearTimeout(this.temporizador);
  }

  protected escribirRadicado(evento: Event): void {
    const texto = (evento.target as HTMLInputElement).value;
    this.filtros.update(f => ({ ...f, radicado: texto }));

    if (this.temporizador) clearTimeout(this.temporizador);
    this.temporizador = setTimeout(() => void this.buscar(), 300);
  }

  protected elegir(campo: 'estadoId' | 'tipoProcesoId' | 'juzgadoId', evento: Event): void {
    const valor = (evento.target as HTMLSelectElement).value;
    this.filtros.update(f => ({ ...f, [campo]: valor === '' ? null : Number(valor) }));
    void this.buscar();
  }

  protected limpiar(): void {
    this.filtros.set({ ...SIN_FILTROS });
    void this.buscar();
  }

  protected async buscar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);

    try {
      this.procesos.set(await this.servicio.buscar(this.filtros()));
    } catch {
      this.procesos.set([]);
      this.error.set('No se pudieron cargar los procesos. Vuelva a intentarlo.');
    } finally {
      this.cargando.set(false);
    }
  }

  /**
   * El color del estado.
   *
   * <p>Se decide por nombre y no por identificador porque los catálogos son de
   * cada despacho: el «Activo» de una firma no tiene el mismo id que el de
   * otra. Lo que sí comparten es cómo lo llaman.
   */
  protected colorEstado(nombre: string): string {
    const limpio = nombre.toLowerCase();
    if (limpio.includes('activo')) return 'verde';
    if (limpio.includes('archivado')) return 'gris';
    if (limpio.includes('suspend')) return 'ambar';
    return '';
  }

  private async cargarCatalogos(): Promise<void> {
    // En paralelo: son tres desplegables independientes y encadenarlos haría
    // esperar a la barra de filtros el triple sin ninguna razón.
    const [estados, tipos, juzgados] = await Promise.all([
      this.servicio.catalogo('ESTADO_PROCESAL').catch(() => []),
      this.servicio.catalogo('TIPO_PROCESO').catch(() => []),
      this.servicio.catalogo('JUZGADO').catch(() => []),
    ]);

    this.estados.set(estados);
    this.tipos.set(tipos);
    this.juzgados.set(juzgados);
  }
}
