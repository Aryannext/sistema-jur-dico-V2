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

  // Se pregunta por la zona, no por lo que NO es. Antes se preguntaba «¿es
  // solo cliente?» y todo lo demás pasaba: así entró el Administrador de
  // Plataforma a un menú que RN-10 le prohíbe. Ahora, cualquiera cuya zona no
  // sea esta vuelve a la suya — incluido un rol que todavía no existe.
  return autenticacion.zona() === 'despacho'
      ? true
      : router.createUrlTree([autenticacion.rutaInicial()]);
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

  return autenticacion.zona() === 'plataforma'
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

  return autenticacion.zona() === 'portal'
    ? true
    : router.createUrlTree([autenticacion.rutaInicial()]);
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
