import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { Autenticacion } from './autenticacion';

/**
 * Deja pasar solo con sesión. RF-04.
 *
 * <p><strong>Esto no es seguridad, es cortesía.</strong> Lo que protege los
 * datos es el backend, que responde 403 aunque el navegador insista. Este
 * guardia existe para que el usuario no vea una pantalla rota llena de errores
 * cuando su sesión ha caducado — se le lleva al ingreso, que es donde puede
 * hacer algo.
 *
 * <p>Por eso pregunta al backend en vez de mirar una variable: la única forma
 * de saber si la sesión sigue viva es usarla.
 */
export const exigeSesion: CanActivateFn = async () => {
  const autenticacion = inject(Autenticacion);
  const router = inject(Router);

  const sesion = autenticacion.sesion() ?? await autenticacion.recuperarSesion();

  return sesion ? true : router.createUrlTree(['/ingreso']);
};

/**
 * Lo contrario: si ya hay sesión, el ingreso no tiene sentido.
 *
 * <p>Evita que quien ya entró vuelva al formulario desde el historial del
 * navegador y crea que se ha desconectado.
 */
export const exigeAnonimo: CanActivateFn = async () => {
  const autenticacion = inject(Autenticacion);
  const router = inject(Router);

  const sesion = autenticacion.comprobada()
    ? autenticacion.sesion()
    : await autenticacion.recuperarSesion();

  return sesion ? router.createUrlTree(['/vencimientos']) : true;
};
