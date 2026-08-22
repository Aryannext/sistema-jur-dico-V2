import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Cliente, Clientes as ServicioClientes } from './clientes.servicio';
import { mensajeDeError } from '../../nucleo/mensajes';

type Filtro = 'todos' | 'con' | 'sin';

/**
 * Clientes del despacho. RF-09 · RF-10 · HU-09.
 *
 * <p>No hay botón de borrar, y no falta: un cliente con procesos es parte del
 * historial del despacho (RN-19 aplicado a su titular). Lo que se puede quitar
 * es su acceso al portal, y eso no borra nada.
 */
@Component({
  selector: 'sgpj-clientes',
  imports: [RouterLink],
  templateUrl: './clientes.html',
  styleUrl: './clientes.css',
})
export class ListaClientes {

  private readonly servicio = inject(ServicioClientes);

  protected readonly clientes = signal<Cliente[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly filtro = signal<Filtro>('todos');
  protected readonly busqueda = signal('');

  protected readonly abierto = signal(false);
  protected readonly nombre = signal('');
  protected readonly documento = signal('');
  protected readonly telefono = signal('');
  protected readonly correo = signal('');
  protected readonly guardando = signal(false);
  protected readonly errorFormulario = signal<string | null>(null);

  protected readonly conPortal = computed(
    () => this.clientes().filter(c => c.tieneAccesoAlPortal).length);

  protected readonly sinPortal = computed(
    () => this.clientes().length - this.conPortal());

  protected readonly mostrados = computed(() => {
    const texto = this.busqueda().trim().toLowerCase();
    const cual = this.filtro();

    return this.clientes()
      .filter(c => cual === 'todos'
        || (cual === 'con' && c.tieneAccesoAlPortal)
        || (cual === 'sin' && !c.tieneAccesoAlPortal))
      .filter(c => texto === ''
        || c.nombre.toLowerCase().includes(texto)
        || (c.documentoIdentidad ?? '').toLowerCase().includes(texto))
      .sort((a, b) => a.nombre.localeCompare(b.nombre, 'es'));
  });

  protected readonly puedeGuardar = computed(
    () => !this.guardando() && this.nombre().trim().length > 0);

  constructor() {
    void this.cargar();
  }

  protected async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);

    try {
      this.clientes.set(await this.servicio.listar());
    } catch {
      this.clientes.set([]);
      this.error.set('No se pudieron cargar los clientes. Vuelva a intentarlo.');
    } finally {
      this.cargando.set(false);
    }
  }

  /**
   * La búsqueda se hace en memoria, no en el servidor.
   *
   * <p>A diferencia de los procesos —que pueden ser cientos y llevan un
   * buscador en el backend—, la lista de clientes de un despacho cabe entera en
   * pantalla. Pedirla al servidor con cada tecla sería trabajo de red para
   * filtrar algo que ya está aquí.
   */
  protected escribirBusqueda(evento: Event): void {
    this.busqueda.set((evento.target as HTMLInputElement).value);
  }

  protected escribir(campo: 'nombre' | 'documento' | 'telefono' | 'correo', evento: Event): void {
    this[campo].set((evento.target as HTMLInputElement).value);
    this.errorFormulario.set(null);
  }

  protected abrirFormulario(): void {
    this.abierto.set(true);
    this.errorFormulario.set(null);
  }

  protected cerrarFormulario(): void {
    this.abierto.set(false);
    this.nombre.set('');
    this.documento.set('');
    this.telefono.set('');
    this.correo.set('');
    this.errorFormulario.set(null);
  }

  async registrar(evento: Event): Promise<void> {
    evento.preventDefault();
    if (!this.puedeGuardar()) return;

    this.guardando.set(true);
    this.errorFormulario.set(null);

    try {
      await this.servicio.registrar({
        nombre: this.nombre().trim(),
        documentoIdentidad: this.documento().trim() || null,
        telefono: this.telefono().trim() || null,
        correo: this.correo().trim() || null,
      });

      this.cerrarFormulario();
      await this.cargar();

    } catch (fallo) {
      this.errorFormulario.set(mensajeDeError(fallo, 'No se pudo registrar el cliente. Inténtelo de nuevo.'));

    } finally {
      this.guardando.set(false);
    }
  }

  protected iniciales(nombre: string): string {
    const partes = nombre.trim().split(/\s+/);
    if (partes.length === 0 || partes[0] === '') return '?';
    const primera = partes[0][0];
    const segunda = partes.length > 1 ? partes[partes.length - 1][0] : '';
    return (primera + segunda).toUpperCase();
  }

}
