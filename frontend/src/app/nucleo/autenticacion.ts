import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { Sesion } from './modelos';
import { rutaDe, zonaDe } from './zonas';

/**
 * La sesión del usuario. RF-04 · RF-06.
 *
 * <h2>Por qué la sesión no se guarda en el navegador</h2>
 *
 * No hay `localStorage` ni token en memoria que sobreviva a un refresco. La
 * autoridad es la cookie de sesión del backend, y quién es el usuario se
 * pregunta con `GET /api/autenticacion/yo`.
 *
 * Es más trabajo que guardar un token, y es lo correcto: si el despacho se
 * desactiva o al usuario le quitan un rol, la siguiente petición ya lo refleja
 * (CA-02.1). Un frontend que confía en lo que guardó al entrar seguiría
 * mostrando el panel a alguien que ya no puede operar.
 */
@Injectable({ providedIn: 'root' })
export class Autenticacion {

  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly _sesion = signal<Sesion | null>(null);
  private readonly _comprobada = signal(false);

  /** Quién está dentro, o null. */
  readonly sesion = this._sesion.asReadonly();

  /** Si ya se preguntó al backend. Antes de esto no se sabe nada. */
  readonly comprobada = this._comprobada.asReadonly();

  readonly nombre = computed(() => this._sesion()?.nombre ?? '');

  readonly iniciales = computed(() => {
    const partes = (this._sesion()?.nombre ?? '').trim().split(/\s+/);
    if (partes.length === 0 || partes[0] === '') return '';
    const primera = partes[0][0];
    const segunda = partes.length > 1 ? partes[partes.length - 1][0] : '';
    return (primera + segunda).toUpperCase();
  });

  readonly esAdministradorDeDespacho = computed(
    () => this._sesion()?.roles.includes('ADMIN_DESPACHO') ?? false);

  readonly esAbogado = computed(
    () => this._sesion()?.roles.includes('ABOGADO') ?? false);

  readonly esCliente = computed(
    () => this._sesion()?.roles.includes('CLIENTE') ?? false);

  /**
   * El Administrador de Plataforma. RN-10.
   *
   * <p>Opera la plataforma, no ejerce la abogacía: da de alta despachos y los
   * activa o desactiva, y <strong>nunca accede al contenido de un
   * expediente</strong>.
   *
   * <p>Su zona es aparte, y eso no es una comodidad de navegación. Al no
   * existir, este rol caía por descarte en la zona del despacho —no era
   * cliente, luego iba a `/vencimientos`— y veía un menú con Procesos,
   * Clientes y Reportes. El backend le negaba los datos, así que lo que veía
   * era un sistema aparentemente roto; pero además la interfaz le estaba
   * OFRECIENDO lo que RN-10 le prohíbe. Una regla que el backend cumple y la
   * pantalla contradice sigue siendo una regla mal implementada.
   */
  readonly esAdministradorDePlataforma = computed(() => this.zona() === 'plataforma');

  /**
   * Solo cliente y nada más.
   *
   * <p>Se comprueba así y no con {@code esCliente()} a secas porque un usuario
   * podría acumular roles (RN-08). Quien además es abogado trabaja en el
   * despacho; el portal es para quien SOLO es cliente.
   */
  readonly soloCliente = computed(() => this.zona() === 'portal');

  /**
   * La zona a la que pertenece quien está dentro. RN-08 · RN-10.
   *
   * <p>La decisión vive en `nucleo/zonas.ts`, no aquí, y de ahí derivan también
   * los guardianes. Antes cada uno la tomaba por su cuenta y uno la tomaba por
   * descarte: así fue como el Administrador de Plataforma acabó viendo el menú
   * del despacho.
   */
  readonly zona = computed(() => zonaDe(this._sesion()?.roles));

  /**
   * Dónde aterriza cada quien al entrar.
   *
   * <p>Mandar a todo el mundo al panel del despacho dejaba al cliente en una
   * pantalla que le devuelve 403 en cada consulta: vería el sistema roto
   * cuando lo que pasa es que ese sitio no es suyo.
   */
  readonly rutaInicial = computed(() => rutaDe(this.zona()));

  /**
   * Pregunta al backend quién está dentro.
   *
   * <p>Un 401 aquí no es un error que reportar: es la respuesta —nadie— y así
   * se trata. Por eso no se propaga.
   */
  async recuperarSesion(): Promise<Sesion | null> {
    try {
      const sesion = await firstValueFrom(this.http.get<Sesion>('/api/autenticacion/yo'));
      this._sesion.set(sesion);
      return sesion;
    } catch {
      this._sesion.set(null);
      return null;
    } finally {
      this._comprobada.set(true);
    }
  }

  /**
   * Entrar. RF-04.
   *
   * <p>Primero se pide el token CSRF: la petición de entrada es un POST y el
   * backend la rechazaría sin él. Angular lee la cookie que deja esa llamada y
   * pone la cabecera solo.
   */
  async entrar(correo: string, contrasena: string): Promise<Sesion> {
    await firstValueFrom(this.http.get('/api/autenticacion/csrf'));

    const sesion = await firstValueFrom(
      this.http.post<Sesion>('/api/autenticacion/entrar', { correo, contrasena }));

    this._sesion.set(sesion);
    this._comprobada.set(true);
    return sesion;
  }

  /**
   * Salir.
   *
   * <p>Se limpia el estado local pase lo que pase con la petición. Si el
   * backend no respondiera, dejar al usuario viendo el panel sería peor que
   * devolverlo al ingreso de más.
   */
  async salir(): Promise<void> {
    try {
      await firstValueFrom(this.http.post('/logout', {}));
    } finally {
      this._sesion.set(null);
      await this.router.navigate(['/ingreso']);
    }
  }
}
