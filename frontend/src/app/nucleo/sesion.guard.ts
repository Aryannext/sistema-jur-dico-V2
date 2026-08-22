import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { Autenticacion } from './autenticacion';

/**
 * Deja pasar solo con sesión. RF-04.
 *
 * <p><strong>Esto no es seguridad, es cortesía.</strong> Lo que protege los
 * datos es el backend, que responde 403 aunque el navegador insista. Estos
 * guardias existen para que el usuario no vea una pantalla rota llena de
 * errores cuando su sesión ha caducado o cuando el sitio no es suyo.
 */
export const exigeSesion: CanActivateFn = async () => {
  const autenticacion = inject(Autenticacion);
  const router = inject(Router);

  const sesion = autenticacion.sesion() ?? await autenticacion.recuperarSesion();

  return sesion ? true : router.createUrlTree(['/ingreso']);
};

/**
 * La zona del despacho. Un cliente que llegue aquí va a su portal.
 *
 * <p>Sin esto, el cliente vería el panel de vencimientos devolviendo 403 en
 * cada consulta y concluiría que el sistema está roto — cuando lo que ocurre
 * es que esa zona no le corresponde.
 */
export const exigeDespacho: CanActivateFn = async () => {
  const autenticacion = inject(Autenticacion);
  const router = inject(Router);

  const sesion = autenticacion.sesion() ?? await autenticacion.recuperarSesion();
  if (!sesion) return router.createUrlTree(['/ingreso']);

  // RN-10: el Administrador de Plataforma no entra aquí. Antes sí entraba, y
  // no por permitirlo sino por descarte: la comprobación era «¿es solo
  // cliente?», y como no lo es, pasaba. Se comprueba ahora de forma
  // afirmativa, que es lo que impide que el próximo rol que se añada vuelva a
  // colarse por el mismo hueco.
  if (autenticacion.esAdministradorDePlataforma()) {
    return router.createUrlTree(['/despachos']);
  }

  return autenticacion.soloCliente() ? router.createUrlTree(['/portal']) : true;
};

/**
 * La zona de la plataforma. RF-01 · RF-02 · RN-10.
 *
 * <p>Solo el Administrador de Plataforma. Quien trabaja en un despacho que
 * llegue aquí vuelve a lo suyo: estas pantallas hablan de despachos como
 * clientes de la plataforma, no de procesos.
 */
export const exigePlataforma: CanActivateFn = async () => {
  const autenticacion = inject(Autenticacion);
  const router = inject(Router);

  const sesion = autenticacion.sesion() ?? await autenticacion.recuperarSesion();
  if (!sesion) return router.createUrlTree(['/ingreso']);

  return autenticacion.esAdministradorDePlataforma()
    ? true
    : router.createUrlTree([autenticacion.rutaInicial()]);
};

/**
 * El portal. Quien trabaja en el despacho no entra aquí.
 *
 * <p>No por secreto —el abogado ve más, no menos— sino porque el portal está
 * escrito desde el punto de vista del cliente: «sus procesos» significaría otra
 * cosa para un abogado, y la pantalla mentiría.
 */
export const exigeCliente: CanActivateFn = async () => {
  const autenticacion = inject(Autenticacion);
  const router = inject(Router);

  const sesion = autenticacion.sesion() ?? await autenticacion.recuperarSesion();
  if (!sesion) return router.createUrlTree(['/ingreso']);

  return autenticacion.soloCliente() ? true : router.createUrlTree(['/vencimientos']);
};

/**
 * Las pantallas exclusivas del Administrador de Despacho. RF-05 · RF-08.
 *
 * <p>Usuarios y Bitácora, que el backend niega con un 403 a un abogado. Se
 * esconden del menú <em>y</em> se protege la ruta: sin lo segundo, escribir la
 * dirección a mano llevaría a una pantalla que dice «los abogados de su
 * despacho no ven esto» mientras la está viendo un abogado.
 *
 * <p>Esto no es seguridad —lo que protege los datos es el backend— sino
 * coherencia: la interfaz no debe prometer ni negar nada distinto de lo que la
 * regla dice.
 */
export const exigeAdministrador: CanActivateFn = async () => {
  const autenticacion = inject(Autenticacion);
  const router = inject(Router);

  const sesion = autenticacion.sesion() ?? await autenticacion.recuperarSesion();
  if (!sesion) return router.createUrlTree(['/ingreso']);

  return autenticacion.esAdministradorDeDespacho()
    ? true
    : router.createUrlTree([autenticacion.rutaInicial()]);
};

/**
 * Lo contrario de exigeSesion: si ya hay sesión, el ingreso no tiene sentido.
 *
 * <p>Cada quien vuelve a SU zona, no a una fija.
 */
export const exigeAnonimo: CanActivateFn = async () => {
  const autenticacion = inject(Autenticacion);
  const router = inject(Router);

  const sesion = autenticacion.comprobada()
    ? autenticacion.sesion()
    : await autenticacion.recuperarSesion();

  return sesion ? router.createUrlTree([autenticacion.rutaInicial()]) : true;
};
