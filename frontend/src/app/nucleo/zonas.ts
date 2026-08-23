/**
 * A qué zona del sistema pertenece quien entra. RN-08 · RN-10.
 *
 * <h2>Por qué esto vive en un solo sitio</h2>
 *
 * <p>Porque no vivía, y de ahí salió un defecto. La decisión estaba repartida
 * entre `rutaInicial()` y cuatro guardianes, y cada uno la tomaba por su cuenta.
 * Uno de ellos —`exigeDespacho`— la tomaba **por descarte**: preguntaba «¿es
 * solo cliente?» y dejaba pasar todo lo demás.
 *
 * <p>Mientras hubo dos zonas eso funcionó. Al aparecer el Administrador de
 * Plataforma —que no es cliente— cayó por descarte en la zona del despacho y vio
 * un menú con Procesos, Clientes y Reportes: justo lo que **RN-10** le prohíbe.
 * El backend le negaba los datos, así que veía un sistema aparentemente roto,
 * pero la interfaz le estaba *ofreciendo* lo prohibido.
 *
 * <p>Con la decisión en un solo sitio, el próximo rol que se añada no puede
 * colarse por un hueco: o se le asigna zona aquí, o no tiene ninguna.
 *
 * <h2>El orden de las preguntas importa</h2>
 *
 * <p>Se pregunta primero por la plataforma y después por el cliente, y las dos
 * de forma <strong>afirmativa</strong>. Nunca «lo que no es X».
 */

/** Las tres zonas del sistema. No hay una cuarta, y esa es la idea. */
export type Zona = 'plataforma' | 'despacho' | 'portal';

const ADMIN_PLATAFORMA = 'ADMIN_PLATAFORMA';
const CLIENTE = 'CLIENTE';

/**
 * La zona de quien tiene estos roles, o `null` si no le corresponde ninguna.
 *
 * <p>`null` no es un caso teórico: un usuario **sin roles** no pertenece a
 * ninguna parte, y mandarlo a la del despacho —que es lo que hacía la lógica
 * anterior, por descarte— le daría un 403 en cada pantalla y la impresión de
 * que el sistema está roto.
 */
export function zonaDe(roles: readonly string[] | null | undefined): Zona | null {
  if (!roles || roles.length === 0) {
    return null;
  }

  // RN-10: opera la plataforma, no ejerce la abogacía. Va primero porque es el
  // único rol que NO pertenece a ningún despacho.
  if (roles.includes(ADMIN_PLATAFORMA)) {
    return 'plataforma';
  }

  // RN-08: el portal es para quien SOLO es cliente. Quien además es abogado
  // trabaja en el despacho — el caso del abogado que también es cliente de su
  // propio despacho existe, y su sitio es el despacho.
  if (roles.every(rol => rol === CLIENTE)) {
    return 'portal';
  }

  return 'despacho';
}

/** Dónde aterriza cada zona. */
export function rutaDe(zona: Zona | null): string {
  switch (zona) {
    case 'plataforma': return '/despachos';
    case 'portal': return '/portal';
    case 'despacho': return '/vencimientos';
    default: return '/ingreso';
  }
}

/**
 * Dónde debe estar quien tiene estos roles.
 *
 * <p>Es lo que usan el ingreso y los guardianes, para que los cinco sitios que
 * antes decidían por separado deriven todos de la misma respuesta.
 */
export function rutaInicialDe(roles: readonly string[] | null | undefined): string {
  return rutaDe(zonaDe(roles));
}
