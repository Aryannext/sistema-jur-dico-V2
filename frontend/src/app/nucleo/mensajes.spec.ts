// Necesario antes de tocar @angular/common/http: al cargarlo se inicializa
// BrowserXhr, que sin el compilador cargado falla con «the Angular Linker has
// not processed the library». No es de esta prueba, es del arranque de Angular
// fuera de una aplicación.
import '@angular/compiler';

import { HttpErrorResponse } from '@angular/common/http';
import { describe, expect, it } from 'vitest';

import { mensajeDeError } from './mensajes';

/**
 * El mensaje de error que ve el usuario. CA-11.2.
 *
 * <p>Existe por un defecto real: había catorce copias de esta lógica repartidas
 * por los componentes y ninguna leía `errores`, así que el detalle por campo
 * que el backend sí manda —«Debe indicar el juzgado»— se tiraba en las catorce.
 * Lo destapó el recorrido de criterios de aceptación.
 *
 * <p>La prueba más importante de este fichero es la primera: comprueba que el
 * mensaje concreto <strong>gana</strong> al genérico. Si algún día alguien
 * «simplifica» esta función devolviendo `detail` a secas, CA-11.2 vuelve a
 * incumplirse en silencio y esta prueba es lo único que lo dice.
 */
describe('mensajeDeError', () => {

  const respuesta = (cuerpo: unknown, status = 400) =>
    new HttpErrorResponse({ error: cuerpo, status });

  it('CA-11.2: prefiere el detalle POR CAMPO sobre el mensaje genérico', () => {
    const fallo = respuesta({
      detail: 'Revise los datos enviados.',
      errores: { juzgadoId: 'Debe indicar el juzgado.' },
    });

    expect(mensajeDeError(fallo, 'por defecto')).toBe('Debe indicar el juzgado.');
  });

  it('une varios campos en una sola frase, siempre en el mismo orden', () => {
    const fallo = respuesta({
      detail: 'Revise los datos enviados.',
      errores: {
        tipoProcesoId: 'Debe indicar el tipo de proceso.',
        juzgadoId: 'Debe indicar el juzgado.',
      },
    });

    // Ordenado por nombre de campo: juzgadoId antes que tipoProcesoId. Un aviso
    // que cambia de orden entre intentos parece un aviso distinto.
    expect(mensajeDeError(fallo, 'por defecto'))
      .toBe('Debe indicar el juzgado. Debe indicar el tipo de proceso.');
  });

  it('⛔ nunca enseña el NOMBRE del campo, que es del programa y no del abogado', () => {
    const fallo = respuesta({ errores: { juzgadoId: 'Debe indicar el juzgado.' } });

    expect(mensajeDeError(fallo, 'por defecto')).not.toContain('juzgadoId');
  });

  it('usa `detail` cuando no hay detalle por campo', () => {
    const fallo = respuesta({
      detail: 'El radicado 41001 ya está registrado en su despacho.',
    }, 409);

    expect(mensajeDeError(fallo, 'por defecto'))
      .toBe('El radicado 41001 ya está registrado en su despacho.');
  });

  it('traduce la caída de conexión, que no trae cuerpo', () => {
    expect(mensajeDeError(respuesta(null, 0), 'por defecto'))
      .toBe('No se pudo contactar con el servidor. Revise su conexión.');
  });

  it('traduce el archivo demasiado grande', () => {
    expect(mensajeDeError(respuesta(null, 413), 'por defecto'))
      .toBe('El archivo supera el máximo permitido de 20 MB.');
  });

  it('cae al mensaje propio de la pantalla cuando el backend no dice nada útil', () => {
    expect(mensajeDeError(respuesta({}, 500), 'No se pudo consultar la bitácora.'))
      .toBe('No se pudo consultar la bitácora.');
  });

  it('⛔ un fallo que no es del servidor no inventa un mensaje de servidor', () => {
    expect(mensajeDeError(new TypeError('undefined is not a function'), 'No se pudo guardar.'))
      .toBe('No se pudo guardar.');
  });

  it('⛔ `errores` vacío no deja el aviso en blanco', () => {
    const fallo = respuesta({ detail: 'Revise los datos enviados.', errores: {} });

    // Sin esto, un backend que mandara el mapa vacío produciría una franja roja
    // sin texto: el usuario vería que algo falló y nada más.
    expect(mensajeDeError(fallo, 'por defecto')).toBe('Revise los datos enviados.');
  });

  it('⛔ `errores` con valores que no son texto no rompe la pantalla', () => {
    const fallo = respuesta({
      detail: 'Revise los datos enviados.',
      errores: { juzgadoId: null, tipoProcesoId: 42 },
    });

    expect(mensajeDeError(fallo, 'por defecto')).toBe('Revise los datos enviados.');
  });
});
