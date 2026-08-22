import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Autenticacion } from '../nucleo/autenticacion';
import { Rol, Usuario as UsuarioModelo } from '../nucleo/modelos';
import { Cuenta } from '../nucleo/cuenta.servicio';
import { mensajeDeError } from '../nucleo/mensajes';

/**
 * Usuarios y roles del despacho. RF-05 · RF-06 · RF-40 · HU-05 · HU-44.
 *
 * <p>Los roles se asignan por <strong>unión</strong>, no por elección única
 * (RN-08): un abogado independiente es Administrador de Despacho **y** Abogado
 * a la vez, y el sistema tiene que dejarlo serlo con una sola cuenta.
 */
@Component({
  selector: 'sgpj-usuarios',
  imports: [RouterLink],
  templateUrl: './usuarios.html',
  styleUrl: './usuarios.css',
})
export class Usuarios {

  private readonly servicio = inject(Cuenta);
  protected readonly autenticacion = inject(Autenticacion);

  protected readonly usuarios = signal<UsuarioModelo[]>([]);
  protected readonly roles = signal<Rol[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly ocupado = signal<number | null>(null);

  // --- Nuevo usuario ---
  protected readonly creando = signal(false);
  protected readonly nombre = signal('');
  protected readonly correo = signal('');
  protected readonly clave = signal('');
  protected readonly rolesElegidos = signal<Set<string>>(new Set(['ABOGADO']));
  protected readonly guardando = signal(false);
  protected readonly errorFormulario = signal<string | null>(null);

  // --- Restablecer contraseña ---
  protected readonly restableciendo = signal<UsuarioModelo | null>(null);
  protected readonly claveNueva = signal('');
  protected readonly claveEntregada = signal<{ correo: string; clave: string } | null>(null);

  protected readonly MINIMO = 8;

  protected readonly activos = computed(() => this.usuarios().filter(u => u.activo).length);

  protected readonly puedeCrear = computed(() =>
    !this.guardando()
    && this.nombre().trim().length > 0
    && this.correo().trim().includes('@')
    && this.clave().length >= this.MINIMO
    && this.rolesElegidos().size > 0);

  protected readonly puedeRestablecer = computed(
    () => this.ocupado() === null && this.claveNueva().length >= this.MINIMO);

  constructor() {
    void this.cargar();
  }

  protected async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);

    try {
      const [usuarios, roles] = await Promise.all([
        this.servicio.usuarios(),
        this.servicio.rolesDisponibles().catch(() => []),
      ]);
      this.usuarios.set(usuarios.sort((a, b) => a.nombre.localeCompare(b.nombre, 'es')));
      this.roles.set(roles);

    } catch {
      this.usuarios.set([]);
      this.error.set('No se pudieron cargar los usuarios de su despacho.');

    } finally {
      this.cargando.set(false);
    }
  }

  protected escribir(campo: 'nombre' | 'correo' | 'clave' | 'claveNueva', evento: Event): void {
    this[campo].set((evento.target as HTMLInputElement).value);
    this.errorFormulario.set(null);
    this.error.set(null);
  }

  protected alternarRol(codigo: string): void {
    this.rolesElegidos.update(actuales => {
      const copia = new Set(actuales);
      if (copia.has(codigo)) copia.delete(codigo); else copia.add(codigo);
      return copia;
    });
  }

  protected tieneRol(usuario: UsuarioModelo, codigo: string): boolean {
    return usuario.roles.some(r => r.codigo === codigo);
  }

  /** Yo mismo. Sobre mi propia cuenta hay cosas que no puedo hacer aquí. */
  protected soyYo(usuario: UsuarioModelo): boolean {
    return usuario.id === this.autenticacion.sesion()?.usuarioId;
  }

  async crear(evento: Event): Promise<void> {
    evento.preventDefault();
    if (!this.puedeCrear()) return;

    this.guardando.set(true);
    this.errorFormulario.set(null);

    try {
      await this.servicio.crear(
        this.nombre().trim(), this.correo().trim(), this.clave(),
        [...this.rolesElegidos()]);

      this.claveEntregada.set({ correo: this.correo().trim(), clave: this.clave() });
      this.cerrarFormulario();
      await this.cargar();

    } catch (fallo) {
      this.errorFormulario.set(mensajeDeError(fallo, 'No se pudo completar la operación. Inténtelo de nuevo.'));

    } finally {
      this.guardando.set(false);
    }
  }

  protected cerrarFormulario(): void {
    this.creando.set(false);
    this.nombre.set('');
    this.correo.set('');
    this.clave.set('');
    this.rolesElegidos.set(new Set(['ABOGADO']));
    this.errorFormulario.set(null);
  }

  /**
   * Propone una contraseña pronunciable.
   *
   * <p>La alternativa real, cuando el sistema no propone nada, es «abogado123».
   */
  protected proponerClave(destino: 'clave' | 'claveNueva'): void {
    const silabas = ['ne', 'hui', 'la', 'sur', 'ju', 'ris', 'pa', 'lo', 'mo', 'ce', 'ti', 'va'];
    let clave = '';
    for (let i = 0; i < 4; i++) {
      clave += silabas[Math.floor(Math.random() * silabas.length)];
    }
    this[destino].set(clave + '-' + Math.floor(1000 + Math.random() * 9000));
  }

  protected abrirRestablecer(usuario: UsuarioModelo): void {
    this.restableciendo.set(usuario);
    this.claveNueva.set('');
    this.claveEntregada.set(null);
    this.error.set(null);
  }

  async restablecer(evento: Event): Promise<void> {
    evento.preventDefault();
    const usuario = this.restableciendo();
    if (!usuario || !this.puedeRestablecer()) return;

    this.ocupado.set(usuario.id);
    this.error.set(null);

    try {
      const clave = this.claveNueva();
      await this.servicio.restablecerContrasena(usuario.id, clave);

      // Se muestra una vez: se guarda con hash y nadie podrá volver a leerla.
      this.claveEntregada.set({ correo: usuario.correo, clave });
      this.restableciendo.set(null);
      this.claveNueva.set('');

    } catch (fallo) {
      this.error.set(mensajeDeError(fallo, 'No se pudo completar la operación. Inténtelo de nuevo.'));

    } finally {
      this.ocupado.set(null);
    }
  }

  async cambiarEstado(usuario: UsuarioModelo): Promise<void> {
    this.ocupado.set(usuario.id);
    this.error.set(null);

    try {
      await this.servicio.cambiarEstado(usuario.id, !usuario.activo);
      await this.cargar();
    } catch (fallo) {
      this.error.set(mensajeDeError(fallo, 'No se pudo completar la operación. Inténtelo de nuevo.'));
    } finally {
      this.ocupado.set(null);
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
