import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Proceso, Termino } from '../../nucleo/modelos';
import { Configuracion } from '../../configuracion/configuracion.servicio';
import { Procesos } from '../procesos/procesos.servicio';
import { Vigilancia } from '../vigilancia';
import { mensajeDeError } from '../../nucleo/mensajes';
import { avisosQueNoAlcanzan, comoSeLeen, seApartaDelDespacho } from '../../nucleo/avisos';

/**
 * Términos de un proceso. RF-21 · RF-22 · RNF-16 · HU-22 · HU-24.
 *
 * <h2>Dos campos para registrar, y es el requisito</h2>
 *
 * <p>RNF-16 lo dice literalmente: no más de cinco campos obligatorios y una
 * sola pantalla. Aquí son dos —qué hay que hacer y para cuándo—. Si registrar
 * un término cuesta más que anotarlo en la agenda de papel, el abogado usa la
 * agenda, y entonces el sistema no vigila nada.
 *
 * <p>El formulario está en la misma pantalla que la lista, no detrás de otro
 * clic, por la misma razón.
 */
@Component({
  selector: 'sgpj-terminos',
  imports: [RouterLink],
  templateUrl: './terminos.html',
  styleUrl: './terminos.css',
})
export class Terminos {

  private readonly servicio = inject(Procesos);
  private readonly vigilancia = inject(Vigilancia);
  private readonly configuracion = inject(Configuracion);

  readonly id = input.required<string>();

  protected readonly proceso = signal<Proceso | null>(null);
  protected readonly terminos = signal<Termino[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);

  /** Cuál se está guardando o cerrando, para deshabilitar solo ese botón. */
  protected readonly ocupado = signal<number | null>(null);

  /**
   * El esquema del despacho, solo para saber qué término se apartó de él.
   *
   * <p>Un abogado puede LEERLO aunque no pueda cambiarlo, que es exactamente
   * el reparto que pide CA-27.3: ajusta el término que lleva, no la política
   * del despacho. Si no se pudiera leer no se rompe nada — se deja de mostrar
   * el distintivo «avisos propios» y ya.
   */
  private readonly esquemaDelDespacho = signal<number[] | null>(null);

  /**
   * Qué término tiene el editor de avisos abierto. CA-27.3.
   *
   * <p>Uno a la vez, y en la misma pantalla. Mandar a otra vista a cambiar
   * tres números rompería el «una sola pantalla» de RNF-16 por la puerta de
   * atrás.
   */
  protected readonly ajustando = signal<number | null>(null);
  protected readonly elegidos = signal<Set<number>>(new Set());
  protected readonly otro = signal('');
  protected readonly guardandoAvisos = signal(false);
  protected readonly errorAvisos = signal<string | null>(null);

  /** Los plazos con los que de verdad se trabaja. No son un límite. */
  protected readonly sugeridos = [1, 2, 3, 5, 8, 10, 15, 30];

  protected readonly descripcion = signal('');
  protected readonly fecha = signal('');
  protected readonly guardando = signal(false);
  protected readonly errorFormulario = signal<string | null>(null);

  protected readonly pendientes = computed(() =>
    this.terminos()
      .filter(t => t.estado === 'PENDIENTE')
      .sort((a, b) => a.fechaVencimiento.localeCompare(b.fechaVencimiento)));

  protected readonly cerrados = computed(() =>
    this.terminos()
      .filter(t => t.estado !== 'PENDIENTE')
      .sort((a, b) => b.fechaVencimiento.localeCompare(a.fechaVencimiento)));

  protected readonly ordenados = computed(
    () => [...this.elegidos()].sort((a, b) => b - a));

  /** RN-37b: sin ningún aviso, el término deja de estar vigilado. */
  protected readonly sinNinguno = computed(() => this.elegidos().size === 0);

  protected readonly hayCambiosEnAvisos = computed(() => {
    const termino = this.terminos().find(t => t.id === this.ajustando());
    const guardados = [...(termino?.diasAnticipacion ?? [])].sort((a, b) => a - b).join(',');
    return guardados !== [...this.elegidos()].sort((a, b) => a - b).join(',');
  });

  protected readonly puedeGuardarAvisos = computed(
    () => !this.guardandoAvisos() && !this.sinNinguno() && this.hayCambiosEnAvisos());

  /**
   * Cuántos de los avisos elegidos ya no alcanzan a salir.
   *
   * <p><strong>Es la razón de ser de esta pantalla.</strong> Un término que
   * vence en dos días con el esquema corriente de 15/5/1 solo recibiría el
   * aviso de un día: los otros dos quedan en el pasado y no se envían nunca.
   * El abogado creería tener tres avisos y tendría uno.
   *
   * <p>Decirlo mientras elige convierte un fallo silencioso en una decisión.
   */
  protected readonly avisosQueNoAlcanzan = computed(() => {
    const termino = this.terminos().find(t => t.id === this.ajustando());
    if (!termino) return [];

    return avisosQueNoAlcanzan(
      this.ordenados(), this.diasHasta(termino.fechaVencimiento));
  });

  protected readonly puedeGuardar = computed(() =>
    !this.guardando()
    && this.descripcion().trim().length > 0
    && this.descripcion().length <= 300
    && this.fecha() !== '');

  private static readonly MESES = [
    'ene', 'feb', 'mar', 'abr', 'may', 'jun',
    'jul', 'ago', 'sep', 'oct', 'nov', 'dic',
  ];

  constructor() {
    effect(() => {
      const id = Number(this.id());
      if (Number.isFinite(id)) void this.cargar(id);
    });
  }

  protected async cargar(id = Number(this.id())): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);

    try {
      const [proceso, terminos] = await Promise.all([
        this.servicio.proceso(id),
        this.vigilancia.terminosDeProceso(id),
        // Va aparte y sin `await`: es un adorno informativo, y si falla no
        // debe llevarse por delante la lista de términos, que es lo que el
        // abogado vino a ver.
        this.configuracion.esquemaAlertas()
          .then(e => this.esquemaDelDespacho.set(e.diasAnticipacion))
          .catch(() => this.esquemaDelDespacho.set(null)),
      ]);
      this.proceso.set(proceso);
      this.terminos.set(terminos);

    } catch {
      this.error.set('No se pudieron cargar los términos de este proceso.');

    } finally {
      this.cargando.set(false);
    }
  }

  protected escribirDescripcion(evento: Event): void {
    this.descripcion.set((evento.target as HTMLInputElement).value);
    this.errorFormulario.set(null);
  }

  protected escribirFecha(evento: Event): void {
    this.fecha.set((evento.target as HTMLInputElement).value);
    this.errorFormulario.set(null);
  }

  async registrar(evento: Event): Promise<void> {
    evento.preventDefault();
    if (!this.puedeGuardar()) return;

    this.guardando.set(true);
    this.errorFormulario.set(null);

    try {
      await this.vigilancia.registrarTermino(
        Number(this.id()), this.descripcion().trim(), this.fecha());

      this.descripcion.set('');
      this.fecha.set('');
      await this.cargar();

    } catch (fallo) {
      this.errorFormulario.set(mensajeDeError(fallo, 'No se pudo completar la operación. Inténtelo de nuevo.'));

    } finally {
      this.guardando.set(false);
    }
  }

  async cumplir(termino: Termino): Promise<void> {
    this.ocupado.set(termino.id);
    this.error.set(null);

    try {
      await this.vigilancia.cumplir(termino.id);
      await this.cargar();
    } catch (fallo) {
      this.error.set(mensajeDeError(fallo, 'No se pudo completar la operación. Inténtelo de nuevo.'));
    } finally {
      this.ocupado.set(null);
    }
  }

  async reabrir(termino: Termino): Promise<void> {
    this.ocupado.set(termino.id);
    this.error.set(null);

    try {
      await this.vigilancia.reabrir(termino.id);
      await this.cargar();
    } catch (fallo) {
      this.error.set(mensajeDeError(fallo, 'No se pudo completar la operación. Inténtelo de nuevo.'));
    } finally {
      this.ocupado.set(null);
    }
  }

  // --- Avisos de un término (CA-27.3) ----------------------------------

  protected abrirAvisos(termino: Termino): void {
    // Cerrar si ya estaba abierto: el mismo botón abre y cierra.
    if (this.ajustando() === termino.id) {
      this.cerrarAvisos();
      return;
    }

    this.ajustando.set(termino.id);
    this.elegidos.set(new Set(termino.diasAnticipacion));
    this.otro.set('');
    this.errorAvisos.set(null);
  }

  protected cerrarAvisos(): void {
    this.ajustando.set(null);
    this.elegidos.set(new Set());
    this.otro.set('');
    this.errorAvisos.set(null);
  }

  protected alternar(dias: number): void {
    this.elegidos.update(actuales => {
      const copia = new Set(actuales);
      if (copia.has(dias)) copia.delete(dias); else copia.add(dias);
      return copia;
    });
    this.errorAvisos.set(null);
  }

  protected escribirOtro(evento: Event): void {
    this.otro.set((evento.target as HTMLInputElement).value);
  }

  protected agregarOtro(evento: Event): void {
    evento.preventDefault();

    // El backend rechaza el 0 —avisar el mismo día del vencimiento llega
    // tarde (RN-37)— y también más de 365. Se comprueba aquí para explicarlo
    // antes, en vez de mandar al usuario a chocar contra el error.
    const dias = Number(this.otro());
    if (!Number.isInteger(dias) || dias < 1 || dias > 365) {
      this.errorAvisos.set(
        'Indique un número de días entre 1 y 365. Avisar el mismo día del vencimiento llegaría tarde.');
      return;
    }

    this.elegidos.update(actuales => new Set(actuales).add(dias));
    this.otro.set('');
    this.errorAvisos.set(null);
  }

  async guardarAvisos(): Promise<void> {
    const id = this.ajustando();
    if (id === null || !this.puedeGuardarAvisos()) return;

    this.guardandoAvisos.set(true);
    this.errorAvisos.set(null);

    try {
      await this.vigilancia.ajustarAnticipaciones(id, [...this.elegidos()]);
      await this.cargar();
      this.cerrarAvisos();

    } catch (fallo) {
      this.errorAvisos.set(
        mensajeDeError(fallo, 'No se pudieron cambiar los avisos. Inténtelo de nuevo.'));

    } finally {
      this.guardandoAvisos.set(false);
    }
  }

  /**
   * Si este término lleva avisos distintos a los del despacho.
   *
   * <p>Se compara contra el esquema del despacho, que es lo que el abogado
   * espera por defecto. Si difiere hay que decirlo: un término ajustado que
   * parece corriente es una sorpresa esperando a ocurrir.
   */
  protected estaAjustado(termino: Termino): boolean {
    return seApartaDelDespacho(termino.diasAnticipacion, this.esquemaDelDespacho());
  }

  /** «1 día antes» — no «1 días antes». */
  protected comoSeLee(dias: number): string {
    return comoSeLeen([dias]);
  }

  /** Los avisos de un término, listos para leer de corrido. */
  protected avisosDe(termino: Termino): string {
    return comoSeLeen(termino.diasAnticipacion);
  }

  /** Los que ya no alcanzan, con la misma redacción cuidada. */
  protected losQueNoAlcanzan(): string {
    return comoSeLeen(this.avisosQueNoAlcanzan());
  }

  /** Igual que en el panel: días de calendario y singular cuidado. */
  protected cuanto(termino: Termino): string {
    const dias = this.diasHasta(termino.fechaVencimiento);

    if (termino.estado !== 'PENDIENTE') return termino.estadoDescripcion;
    if (dias === 0) return 'Vence hoy';
    if (dias === 1) return 'Vence mañana';
    if (dias > 1) return `En ${dias} días`;
    if (dias === -1) return 'Venció ayer';
    return `Venció hace ${Math.abs(dias)} días`;
  }

  protected fechaCorta(iso: string): string {
    const [anio, mes, dia] = iso.split('-').map(Number);
    return `${dia} ${Terminos.MESES[mes - 1]} ${anio}`;
  }

  protected urgencia(termino: Termino): string {
    if (termino.vencido) return 'vencido';
    const dias = this.diasHasta(termino.fechaVencimiento);
    if (dias <= 0) return 'hoy';
    if (dias <= 3) return 'pronto';
    return 'ok';
  }

  private diasHasta(iso: string): number {
    const [anio, mes, dia] = iso.split('-').map(Number);
    const vence = new Date(anio, mes - 1, dia);

    const ahora = new Date();
    const hoy = new Date(ahora.getFullYear(), ahora.getMonth(), ahora.getDate());

    return Math.round((vence.getTime() - hoy.getTime()) / 86_400_000);
  }

}
