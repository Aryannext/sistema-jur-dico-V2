import { Component, computed, inject, signal } from '@angular/core';

import { Despacho } from '../nucleo/modelos';
import { Despachos as ServicioDespachos } from './despachos.servicio';
import { mensajeDeError } from '../nucleo/mensajes';

/**
 * Alta y estado de los despachos. RF-01 · RF-02 · RF-03 · HU-01 · HU-02.
 *
 * <h2>Por qué el alta pide también un administrador</h2>
 *
 * <p>CA-01.2 exige que el despacho nazca con uno. No es un campo más del
 * formulario: un despacho sin administrador es un despacho al que nadie puede
 * entrar, y del que solo el Administrador de Plataforma podría rescatar. Por
 * eso los dos bloques viajan en la misma petición.
 *
 * <h2>Por qué las credenciales se muestran una sola vez</h2>
 *
 * <p>La contraseña se guarda con hash (RNF-05): ni el sistema ni nadie puede
 * volver a leerla. Si el aviso pasara desapercibido, quedaría un despacho
 * recién creado con un administrador cuya clave no conoce nadie.
 */
@Component({
  selector: 'sgpj-despachos',
  templateUrl: './despachos.html',
  // Mismo lenguaje visual que «Usuarios y roles», que es la pantalla gemela de
  // esta en la zona del despacho: lista con alta y cambio de estado.
  styleUrl: '../cuenta/usuarios.css',
})
export class ListaDespachos {

  private readonly servicio = inject(ServicioDespachos);

  protected readonly despachos = signal<Despacho[]>([]);
  protected readonly cargando = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly ocupado = signal<number | null>(null);

  // --- Nuevo despacho ---
  protected readonly creando = signal(false);
  protected readonly nombre = signal('');
  protected readonly nit = signal('');
  protected readonly correoContacto = signal('');
  protected readonly telefono = signal('');
  protected readonly adminNombre = signal('');
  protected readonly adminCorreo = signal('');
  protected readonly clave = signal('');
  protected readonly guardando = signal(false);
  protected readonly errorFormulario = signal<string | null>(null);

  /** Las credenciales del administrador recién creado. Se ven una vez. */
  protected readonly claveEntregada =
    signal<{ despacho: string; correo: string; clave: string } | null>(null);

  protected readonly MINIMO = 8;

  protected readonly activos = computed(
    () => this.despachos().filter(d => d.estado === 'ACTIVO').length);

  constructor() {
    void this.cargar();
  }

  protected async cargar(): Promise<void> {
    this.cargando.set(true);
    this.error.set(null);
    try {
      this.despachos.set(await this.servicio.listar());
    } catch (fallo) {
      this.error.set(mensajeDeError(fallo, 'No se pudo completar la operación. Inténtelo de nuevo.'));
    } finally {
      this.cargando.set(false);
    }
  }

  protected readonly puedeCrear = computed(() =>
    !this.guardando()
    && this.nombre().trim().length > 0
    && this.correoContacto().trim().length > 0
    && this.adminNombre().trim().length > 0
    && this.adminCorreo().trim().length > 0
    && this.clave().length >= this.MINIMO);

  protected async crear(evento: Event): Promise<void> {
    evento.preventDefault();
    if (!this.puedeCrear()) return;

    this.guardando.set(true);
    this.errorFormulario.set(null);
    try {
      const alta = await this.servicio.registrar(
        this.nombre().trim(),
        this.nit().trim() || null,
        this.correoContacto().trim(),
        this.telefono().trim() || null,
        {
          nombre: this.adminNombre().trim(),
          correo: this.adminCorreo().trim(),
          contrasena: this.clave(),
        });

      this.claveEntregada.set({
        despacho: alta.despacho.nombre,
        correo: alta.administrador.correo,
        clave: this.clave(),
      });

      this.cerrarFormulario();
      await this.cargar();
    } catch (fallo) {
      this.errorFormulario.set(
        mensajeDeError(fallo, 'No se pudo completar la operación. Inténtelo de nuevo.'));
    } finally {
      this.guardando.set(false);
    }
  }

  /**
   * RF-02 · CA-02.1: surte efecto de inmediato.
   *
   * <p>Desactivar un despacho detiene su vigilancia de términos, así que no se
   * hace de refilón: se confirma. No es una precaución genérica — es la única
   * acción de esta pantalla que puede hacer que un plazo se venza sin aviso.
   */
  protected async cambiarEstado(despacho: Despacho): Promise<void> {
    const activar = despacho.estado !== 'ACTIVO';

    if (!activar) {
      const seguro = confirm(
        `¿Desactivar «${despacho.nombre}»?\n\n`
        + 'Su gente dejará de entrar de inmediato, aunque tenga la sesión abierta, '
        + 'y el sistema dejará de vigilar sus términos y audiencias.\n\n'
        + 'No se borra nada: al reactivarlo, todo sigue ahí.');
      if (!seguro) return;
    }

    this.ocupado.set(despacho.id);
    this.error.set(null);
    try {
      const actualizado = await this.servicio.cambiarEstado(despacho.id, activar);
      this.despachos.update(lista =>
        lista.map(d => d.id === actualizado.id ? actualizado : d));
    } catch (fallo) {
      this.error.set(mensajeDeError(fallo, 'No se pudo completar la operación. Inténtelo de nuevo.'));
    } finally {
      this.ocupado.set(null);
    }
  }

  protected proponerClave(): void {
    const silabas = ['ne', 'hui', 'la', 'sur', 'ju', 'ris', 'pa', 'lo', 'mo', 'ce', 'ti', 'va'];
    let clave = '';
    for (let i = 0; i < 4; i++) {
      clave += silabas[Math.floor(Math.random() * silabas.length)];
    }
    this.clave.set(clave + '-' + Math.floor(1000 + Math.random() * 9000));
  }

  protected cerrarFormulario(): void {
    this.creando.set(false);
    this.errorFormulario.set(null);
    this.nombre.set('');
    this.nit.set('');
    this.correoContacto.set('');
    this.telefono.set('');
    this.adminNombre.set('');
    this.adminCorreo.set('');
    this.clave.set('');
  }

  protected escribir(campo: 'nombre' | 'nit' | 'correoContacto' | 'telefono'
    | 'adminNombre' | 'adminCorreo' | 'clave', evento: Event): void {
    this[campo].set((evento.target as HTMLInputElement).value);
  }

  protected iniciales(nombre: string): string {
    const partes = nombre.trim().split(/\s+/);
    if (partes.length === 0 || partes[0] === '') return '';
    const primera = partes[0][0];
    const segunda = partes.length > 1 ? partes[partes.length - 1][0] : '';
    return (primera + segunda).toUpperCase();
  }

}
