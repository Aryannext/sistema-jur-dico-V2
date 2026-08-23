import { describe, expect, it } from 'vitest';

import { rutaInicialDe, zonaDe } from './zonas';

/**
 * A qué zona pertenece cada quien. RN-08 · RN-10.
 *
 * <p><strong>Esta prueba existe por un defecto real.</strong> La decisión de
 * zona estaba repartida entre cinco sitios y uno la tomaba por descarte
 * —«¿es solo cliente?», y si no, adentro—. Cuando apareció el Administrador de
 * Plataforma cayó por ese hueco en la zona del despacho y vio un menú con
 * Procesos, Clientes y Reportes, que es justo lo que RN-10 le prohíbe.
 *
 * <p>Ninguna prueba lo detectó porque no había ninguna. Lo encontró el analista
 * entrando al sistema.
 *
 * <p>Casi todos los casos son negativos: lo que hay que demostrar no es que
 * cada rol llegue a su sitio —eso es fácil— sino que <strong>no llega a los
 * otros dos</strong>.
 */
describe('zonas (RN-08 · RN-10)', () => {

  describe('El Administrador de Plataforma', () => {

    it('⛔ NO entra a la zona del despacho, que es el defecto que hubo', () => {
      // El caso exacto que falló. Si esto se pusiera en verde por «despacho»,
      // el rol volvería a ver Procesos y Clientes.
      expect(zonaDe(['ADMIN_PLATAFORMA'])).toBe('plataforma');
      expect(zonaDe(['ADMIN_PLATAFORMA'])).not.toBe('despacho');
    });

    it('aterriza en Despachos, no en el panel de vencimientos', () => {
      expect(rutaInicialDe(['ADMIN_PLATAFORMA'])).toBe('/despachos');
    });

    it('⛔ gana a cualquier otro rol que llevara encima', () => {
      // No debería ocurrir —opera la plataforma, no pertenece a un despacho—
      // pero si ocurriera, RN-10 pesa más: es el rol que NO puede ver
      // expedientes, y ante la duda no se le abre la puerta.
      expect(zonaDe(['ADMIN_PLATAFORMA', 'ABOGADO'])).toBe('plataforma');
      expect(zonaDe(['CLIENTE', 'ADMIN_PLATAFORMA'])).toBe('plataforma');
    });
  });

  describe('El cliente', () => {

    it('va al portal cuando SOLO es cliente', () => {
      expect(zonaDe(['CLIENTE'])).toBe('portal');
      expect(rutaInicialDe(['CLIENTE'])).toBe('/portal');
    });

    it('⛔ pero NO si además trabaja en el despacho', () => {
      // Un abogado puede ser cliente de su propio despacho. Su sitio es el
      // despacho: el portal está escrito desde el punto de vista del cliente y
      // «sus procesos» significaría allí otra cosa.
      expect(zonaDe(['CLIENTE', 'ABOGADO'])).toBe('despacho');
      expect(zonaDe(['ABOGADO', 'CLIENTE'])).toBe('despacho');
    });
  });

  describe('El despacho', () => {

    it('RN-08: el abogado independiente lleva los dos roles y va al despacho', () => {
      expect(zonaDe(['ADMIN_DESPACHO', 'ABOGADO'])).toBe('despacho');
    });

    it('el abogado raso y el administrador van al mismo sitio', () => {
      // La zona es la misma; lo que cambia dentro es qué ve, y eso lo deciden
      // los enlaces de la barra lateral y el backend, no esto.
      expect(zonaDe(['ABOGADO'])).toBe('despacho');
      expect(zonaDe(['ADMIN_DESPACHO'])).toBe('despacho');
    });
  });

  describe('Lo que no tiene zona', () => {

    it('⛔ sin roles NO se cae en la zona del despacho', () => {
      // Es la misma trampa que causó el defecto, en su forma general: la lógica
      // anterior mandaba al despacho todo lo que no fuera «solo cliente», y un
      // usuario sin roles cumple eso. Habría recibido un 403 en cada pantalla
      // y la impresión de que el sistema está roto.
      expect(zonaDe([])).toBeNull();
      expect(zonaDe([])).not.toBe('despacho');
      expect(rutaInicialDe([])).toBe('/ingreso');
    });

    it('⛔ nulo y sin definir tampoco', () => {
      expect(zonaDe(null)).toBeNull();
      expect(zonaDe(undefined)).toBeNull();
      expect(rutaInicialDe(null)).toBe('/ingreso');
    });

    it('⛔ un rol desconocido no abre ninguna puerta especial', () => {
      // Si mañana se añade un rol y se olvida asignarle zona aquí, cae en
      // «despacho», que es el comportamiento razonable — pero NUNCA en
      // plataforma, que es la que no ve expedientes por diseño.
      expect(zonaDe(['ROL_QUE_NO_EXISTE'])).not.toBe('plataforma');
      expect(zonaDe(['ROL_QUE_NO_EXISTE'])).not.toBe('portal');
    });
  });

  describe('Las tres zonas son distintas entre sí', () => {

    it('⛔ ningún conjunto de roles lleva a dos zonas a la vez', () => {
      // Comprobación estructural: la función es total y determinista, así que
      // dos llamadas iguales deben dar lo mismo. Suena obvio; deja de serlo el
      // día que alguien meta aquí una condición que dependa del orden.
      const casos = [
        ['ADMIN_PLATAFORMA'], ['CLIENTE'], ['ABOGADO'],
        ['ADMIN_DESPACHO', 'ABOGADO'], ['CLIENTE', 'ABOGADO'], [],
      ];
      for (const roles of casos) {
        expect(zonaDe(roles)).toBe(zonaDe([...roles]));
        expect(zonaDe(roles)).toBe(zonaDe([...roles].reverse()));
      }
    });
  });
});
