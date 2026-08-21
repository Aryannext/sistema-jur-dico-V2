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

  return autenticacion.soloCliente() ? router.createUrlTree(['/portal']) : true;
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
