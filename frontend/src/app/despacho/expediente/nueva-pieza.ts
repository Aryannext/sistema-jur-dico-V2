import { Component, computed, effect, inject, input, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Router, RouterLink } from '@angular/router';

import { Proceso, ValorCatalogo } from '../../nucleo/modelos';
import { Advertencia, Procesos } from '../procesos/procesos.servicio';

type Clase = 'ACTUACION' | 'DOCUMENTO' | 'NOTA';

/**
 * Agregar una pieza al expediente. RF-15 · RF-16 · RF-17 · RF-18 · HU-15.
 *
 * <h2>La pregunta no es «¿lo ve el cliente?»</h2>
 *
 * <p>El mockup planteaba la visibilidad como una casilla que se marca al
 * crear. <strong>El sistema no funciona así</strong> (D-12): documentos y
 * actuaciones son visibles <em>todos</em>, las notas <em>ninguna</em>, y no
 * hay selección pieza por pieza. Lo que el abogado elige es <strong>qué clase
 * de pieza está registrando</strong>, y la visibilidad se deduce de eso.
 *
 * <p>Es mejor así, y no solo más simple: una casilla invita a equivocarse una
 * vez y publicar algo reservado. Elegir «nota interna» es una decisión que se
 * entiende sin leer letra pequeña.
 *
 * <p>Por eso la advertencia de RF-16 se muestra <strong>antes</strong> de
 * guardar, junto a su alternativa —«regístrelo como nota interna»—, y el texto
 * lo redacta el backend, no esta pantalla.
 */
@Component({
  selector: 'sgpj-nueva-pieza',
  imports: [RouterLink],
  templateUrl: './nueva-pieza.html',
  styleUrl: './nueva-pieza.css',
})
export class NuevaPieza {

  private readonly servicio = inject(Procesos);
  private readonly router = inject(Router);

  readonly id = input.required<string>();

  protected readonly proceso = signal<Proceso | null>(null);
  protected readonly clase = signal<Clase>('ACTUACION');
  protected readonly advertencia = signal<Advertencia | null>(null);

  protected readonly tiposActuacion = signal<ValorCatalogo[]>([]);
  protected readonly tiposDocumento = signal<ValorCatalogo[]>([]);

  // --- Lo que el usuario escribe ---
  protected readonly tipoId = signal<number | null>(null);
  protected readonly fecha = signal(NuevaPieza.hoyIso());
  protected readonly descripcion = signal('');
  protected readonly contenido = signal('');
  protected readonly archivo = signal<File | null>(null);

  protected readonly guardando = signal(false);
  protected readonly error = signal<string | null>(null);

  /** Lo que el cliente verá de esto. Se muestra siempre, no solo al final. */
  protected readonly seraVisible = computed(() => this.clase() !== 'NOTA');

  protected readonly limite = computed(() => this.clase() === 'NOTA' ? 2000 : 1000);

  protected readonly escrito = computed(
    () => this.clase() === 'NOTA' ? this.contenido().length : this.descripcion().length);

  protected readonly puedeGuardar = computed(() => {
    if (this.guardando()) return false;

    switch (this.clase()) {
      case 'NOTA':
        return this.contenido().trim().length > 0 && this.escrito() <= this.limite();
      case 'DOCUMENTO':
        return this.archivo() !== null && this.tipoId() !== null;
      default:
        return this.tipoId() !== null
          && this.fecha() !== ''
          && this.descripcion().trim().length > 0
          && this.escrito() <= this.limite();
    }
  });

  constructor() {
    effect(() => {
      const id = Number(this.id());
      if (Number.isFinite(id)) void this.cargar(id);
    });
  }

  private async cargar(id: number): Promise<void> {
    const [proceso, actuacion, documento, advertencia] = await Promise.all([
      this.servicio.proceso(id).catch(() => null),
      this.servicio.catalogo('TIPO_ACTUACION').catch(() => []),
      this.servicio.catalogo('TIPO_DOCUMENTO').catch(() => []),
      this.servicio.advertenciaDeCarga(id).catch(() => null),
    ]);

    this.proceso.set(proceso);
    this.tiposActuacion.set(actuacion);
    this.tiposDocumento.set(documento);
    this.advertencia.set(advertencia);
    this.ajustarTipoPorDefecto();
  }

  protected elegirClase(cual: Clase): void {
    this.clase.set(cual);
    this.error.set(null);
    this.ajustarTipoPorDefecto();
  }

  protected elegirTipo(evento: Event): void {
    const valor = (evento.target as HTMLSelectElement).value;
    this.tipoId.set(valor === '' ? null : Number(valor));
  }

  protected escribirFecha(evento: Event): void {
    this.fecha.set((evento.target as HTMLInputElement).value);
  }

  protected escribirDescripcion(evento: Event): void {
    this.descripcion.set((evento.target as HTMLTextAreaElement).value);
  }

  protected escribirContenido(evento: Event): void {
    this.contenido.set((evento.target as HTMLTextAreaElement).value);
  }

  protected elegirArchivo(evento: Event): void {
    const archivos = (evento.target as HTMLInputElement).files;
    this.archivo.set(archivos && archivos.length > 0 ? archivos[0] : null);
    this.error.set(null);
  }

  protected quitarArchivo(): void {
    this.archivo.set(null);
  }

  /** «2,4 MB» — con coma decimal, que es como se escribe en Colombia. */
  protected tamano(bytes: number): string {
    if (bytes < 1024) return `${bytes} B`;
    if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)} KB`;
    return `${(bytes / (1024 * 1024)).toFixed(1).replace('.', ',')} MB`;
  }

  async guardar(evento: Event): Promise<void> {
    evento.preventDefault();
    if (!this.puedeGuardar()) return;

    const procesoId = Number(this.id());
    this.guardando.set(true);
    this.error.set(null);

    try {
      switch (this.clase()) {
        case 'NOTA':
          await this.servicio.registrarNota(procesoId, this.contenido().trim());
          break;
        case 'DOCUMENTO':
          await this.servicio.cargarDocumento(procesoId, this.tipoId()!, this.archivo()!);
          break;
        default:
          await this.servicio.registrarActuacion(
            procesoId, this.tipoId()!, this.fecha(), this.descripcion().trim());
      }

      await this.router.navigate(['/procesos', procesoId]);

    } catch (fallo) {
      this.error.set(this.mensajeDe(fallo));

    } finally {
      this.guardando.set(false);
    }
  }

  /**
   * El mensaje del backend, siempre que lo haya.
   *
   * <p>Las reglas de negocio las decide el backend y sus mensajes están
   * escritos para el abogado —«El tipo de documento X está desactivado»—. Un
   * «no se pudo guardar» genérico lo dejaría sin saber qué corregir.
   */
  private mensajeDe(fallo: unknown): string {
    if (fallo instanceof HttpErrorResponse) {
      const detalle = fallo.error?.detail;
      if (typeof detalle === 'string' && detalle.trim()) return detalle;

      if (fallo.status === 413) {
        return 'El archivo supera el máximo permitido de 20 MB.';
      }
      if (fallo.status === 0) {
        return 'No se pudo contactar con el servidor. Revise su conexión.';
      }
    }
    return 'No se pudo guardar. Inténtelo de nuevo.';
  }

  /** El primer valor del catálogo, para no obligar a abrir el desplegable. */
  private ajustarTipoPorDefecto(): void {
    if (this.clase() === 'NOTA') {
      this.tipoId.set(null);
      return;
    }
    const lista = this.clase() === 'DOCUMENTO' ? this.tiposDocumento() : this.tiposActuacion();
    this.tipoId.set(lista.length > 0 ? lista[0].id : null);
  }

  private static hoyIso(): string {
    const hoy = new Date();
    const mes = String(hoy.getMonth() + 1).padStart(2, '0');
    const dia = String(hoy.getDate()).padStart(2, '0');
    return `${hoy.getFullYear()}-${mes}-${dia}`;
  }
}
