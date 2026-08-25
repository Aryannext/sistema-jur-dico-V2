import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';

import { Procesos } from './procesos.servicio';

/**
 * La memoria de los catálogos. Defecto encontrado en producción.
 *
 * <p>Un abogado abría <em>Nuevo proceso</em>, leía que su catálogo de juzgados
 * estaba vacío, iba a Catálogos, agregaba su primer juzgado, volvía… y seguía
 * leyendo lo mismo. Solo recargar la página entera lo arreglaba.
 *
 * <p>Dos causas, y las dos se prueban aquí:
 *
 * <ol>
 *   <li>Lo guardado no se invalidaba <strong>nunca</strong>. El comentario del
 *       código lo justificaba con «un catálogo cambia una vez al año» — falso
 *       justo el primer día, que es cuando el despacho lo está montando.
 *   <li>Lo que se guardaba era una lista <strong>vacía</strong>, y en
 *       JavaScript {@code []} es verdadero: el «¿ya lo tengo?» daba que sí y
 *       devolvía el vacío para siempre.
 * </ol>
 *
 * <h2>Sobre la fuerza de estas pruebas</h2>
 *
 * <p>El arreglo lleva <strong>dos guardas redundantes</strong>: una al leer
 * —{@code guardado.length > 0}— y otra al escribir —{@code valores.length > 0}—.
 * Cualquiera de las dos por separado ya evita el fallo.
 *
 * <p>Eso tiene una consecuencia que conviene saber antes de sacar conclusiones:
 * al romper <em>una sola</em> de las dos, estas pruebas <strong>siguen en
 * verde</strong>, porque la otra la compensa. No es que no prueben nada — se
 * comprobó quitando las dos a la vez, que es el código original, y entonces la
 * primera prueba de aquí abajo falla como debe.
 */
describe('Procesos · memoria de catálogos', () => {

  let servicio: Procesos;
  let http: HttpTestingController;

  const unJuzgado = [{ id: 1, nombre: 'Juzgado Primero Civil', activo: true, orden: 1 }];

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    servicio = TestBed.inject(Procesos);
    http = TestBed.inject(HttpTestingController);
  });

  /** Responde una petición pendiente a ese catálogo. */
  function responder(tipo: string, cuerpo: unknown[]): void {
    http.expectOne(`/api/catalogos/${tipo}/activos`).flush(cuerpo);
  }

  it('⛔ una lista VACÍA no se guarda: es el defecto que hubo', async () => {
    // Es el corazón del fallo. Si el vacío se guardara, el abogado que acaba
    // de agregar su primer juzgado seguiría viendo cero.
    const primera = servicio.catalogo('JUZGADO');
    responder('JUZGADO', []);
    expect(await primera).toEqual([]);

    // La segunda llamada TIENE que volver a preguntar. Si no hubiera petición
    // pendiente, expectOne falla — que es justo lo que queremos detectar.
    const segunda = servicio.catalogo('JUZGADO');
    responder('JUZGADO', unJuzgado);

    expect(await segunda).toEqual(unJuzgado);
  });

  it('una lista con valores sí se guarda, que para eso está', async () => {
    const primera = servicio.catalogo('TIPO_PROCESO');
    responder('TIPO_PROCESO', unJuzgado);
    await primera;

    // Sin petición nueva: lo sirve de memoria.
    expect(await servicio.catalogo('TIPO_PROCESO')).toEqual(unJuzgado);
    http.expectNone('/api/catalogos/TIPO_PROCESO/activos');
  });

  it('olvidarCatalogo(tipo) obliga a volver a preguntar por ESE', async () => {
    const primera = servicio.catalogo('JUZGADO');
    responder('JUZGADO', unJuzgado);
    await primera;

    servicio.olvidarCatalogo('JUZGADO');

    const segunda = servicio.catalogo('JUZGADO');
    responder('JUZGADO', []);
    expect(await segunda).toEqual([]);
  });

  it('⛔ olvidarCatalogo(tipo) NO se lleva por delante los demás', async () => {
    const j = servicio.catalogo('JUZGADO');
    responder('JUZGADO', unJuzgado);
    await j;

    const t = servicio.catalogo('TIPO_PROCESO');
    responder('TIPO_PROCESO', unJuzgado);
    await t;

    servicio.olvidarCatalogo('JUZGADO');

    // El otro sigue en memoria: olvidar de más costaría peticiones inútiles.
    expect(await servicio.catalogo('TIPO_PROCESO')).toEqual(unJuzgado);
    http.expectNone('/api/catalogos/TIPO_PROCESO/activos');
  });

  it('olvidarCatalogo() sin argumento los olvida todos', async () => {
    // Es la que usa el servicio de configuración: renombrar y desactivar
    // reciben un id, no un tipo, así que se olvidan todos y se acabó.
    const j = servicio.catalogo('JUZGADO');
    responder('JUZGADO', unJuzgado);
    await j;

    const t = servicio.catalogo('TIPO_PROCESO');
    responder('TIPO_PROCESO', unJuzgado);
    await t;

    servicio.olvidarCatalogo();

    const j2 = servicio.catalogo('JUZGADO');
    responder('JUZGADO', unJuzgado);
    await j2;

    const t2 = servicio.catalogo('TIPO_PROCESO');
    responder('TIPO_PROCESO', unJuzgado);
    await t2;

    http.verify();
  });
});
