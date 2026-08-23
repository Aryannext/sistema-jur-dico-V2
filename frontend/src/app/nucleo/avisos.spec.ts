import { describe, expect, it } from 'vitest';

import { avisosQueNoAlcanzan, comoSeLeen, seApartaDelDespacho } from './avisos';

/**
 * Los avisos propios de un término. CA-27.3 · RN-37c.
 *
 * <p>Casi todo lo que se comprueba aquí es una advertencia. Y una advertencia
 * tiene dos formas de fallar, no una: <strong>no aparecer cuando hace falta</strong>
 * —el abogado cree tener tres avisos y tiene uno— y <strong>aparecer cuando no</strong>,
 * que gasta la atención que hará falta el día que la advertencia sea cierta.
 * Las dos están cubiertas.
 */
describe('avisos de un término (CA-27.3 · RN-37c)', () => {

  describe('Qué avisos ya no alcanzan a salir', () => {

    it('el caso que motiva toda la pantalla: término corto, esquema largo', () => {
      // Un término de dos días con el esquema corriente del despacho. De los
      // tres avisos configurados solo saldría el de un día; los otros dos
      // caen en el pasado y no se envían nunca.
      expect(avisosQueNoAlcanzan([15, 5, 1], 2)).toEqual([15, 5]);
    });

    it('⛔ el aviso que sale HOY no se cuenta como perdido', () => {
      // 5 días de anticipación para un término que vence en 5 días sale hoy,
      // y hoy todavía es a tiempo. Si esto se contara como perdido, la
      // pantalla advertiría de algo que sí va a ocurrir. Es la diferencia
      // entre «>» y «>=», y por eso está escrita en el código.
      expect(avisosQueNoAlcanzan([5], 5)).toEqual([]);
      expect(avisosQueNoAlcanzan([15, 5, 1], 15)).toEqual([]);
    });

    it('⛔ no advierte de nada cuando todos los avisos llegan a tiempo', () => {
      // Es el caso corriente. Una advertencia permanente no se lee.
      expect(avisosQueNoAlcanzan([15, 5, 1], 60)).toEqual([]);
    });

    it('un término que vence hoy ya no admite ningún aviso anticipado', () => {
      // RN-37: la alerta es ANTICIPADA. Si vence hoy, ninguna anticipación
      // positiva alcanza — y hay que decirlo, no callarlo.
      expect(avisosQueNoAlcanzan([15, 5, 1], 0)).toEqual([15, 5, 1]);
    });

    it('un término ya vencido tampoco', () => {
      expect(avisosQueNoAlcanzan([3, 1], -4)).toEqual([3, 1]);
    });

    it('se leen de mayor a menor, como se escriben en la advertencia', () => {
      expect(avisosQueNoAlcanzan([1, 30, 5, 10], 2)).toEqual([30, 10, 5]);
    });

    it('⛔ no modifica la lista que recibe', () => {
      // Se le pasa el arreglo de un `computed`. Ordenarlo en el sitio
      // reordenaría lo que el usuario ve marcado mientras elige.
      const elegidos = [1, 30, 5];
      avisosQueNoAlcanzan(elegidos, 2);
      expect(elegidos).toEqual([1, 30, 5]);
    });

    it('sin avisos no hay nada que advertir', () => {
      expect(avisosQueNoAlcanzan([], 2)).toEqual([]);
    });
  });

  describe('Si el término se apartó del esquema del despacho', () => {

    it('marca el que lleva avisos propios', () => {
      expect(seApartaDelDespacho([30, 20, 10], [15, 5, 1])).toBe(true);
    });

    it('⛔ NO marca el que sigue el esquema, aunque venga en otro orden', () => {
      // El backend los devuelve de mayor a menor y el esquema podría llegar
      // al revés. Si el orden decidiera, TODOS los términos aparecerían como
      // ajustados y el distintivo dejaría de significar nada.
      expect(seApartaDelDespacho([15, 5, 1], [1, 5, 15])).toBe(false);
      expect(seApartaDelDespacho([5, 15, 1], [15, 1, 5])).toBe(false);
    });

    it('⛔ un repetido no convierte un término corriente en ajustado', () => {
      // Con qué anticipación avisa el sistema no cambia por repetir un día.
      expect(seApartaDelDespacho([15, 15, 5, 1], [15, 5, 1])).toBe(false);
    });

    it('detecta que sobra un aviso, y que falta uno', () => {
      expect(seApartaDelDespacho([15, 5, 1, 30], [15, 5, 1])).toBe(true);
      expect(seApartaDelDespacho([15, 5], [15, 5, 1])).toBe(true);
    });

    it('⛔ sin esquema del despacho NO se afirma que esté ajustado', () => {
      // El esquema se carga aparte y puede fallar. «No lo sé» no es «son
      // distintos»: marcar todos los términos como ajustados por no haber
      // podido leer una configuración sería inventarse un dato.
      expect(seApartaDelDespacho([15, 5, 1], null)).toBe(false);
      expect(seApartaDelDespacho([30, 20], undefined)).toBe(false);
    });

    it('⛔ no modifica ninguna de las dos listas', () => {
      const delTermino = [1, 30, 5];
      const delDespacho = [5, 1, 15];
      seApartaDelDespacho(delTermino, delDespacho);

      expect(delTermino).toEqual([1, 30, 5]);
      expect(delDespacho).toEqual([5, 1, 15]);
    });
  });
});

describe('Cómo se leen las anticipaciones', () => {

  it('⛔ «1 día antes», no «1 días antes»', () => {
    // El defecto que se vio en pantalla. Es pequeño y no rompe nada, pero es
    // la clase de descuido que hace dudar de lo que sí importa.
    expect(comoSeLeen([1])).toBe('1 día antes');
  });

  it('varias se leen como se dicen en voz alta', () => {
    expect(comoSeLeen([15, 5, 1])).toBe('15, 5 y 1 días antes');
    expect(comoSeLeen([5, 1])).toBe('5 y 1 días antes');
  });

  it('⛔ con varias el plural es correcto aunque una de ellas sea 1', () => {
    // «15, 5 y 1 días antes» está bien: el plural lo manda el conjunto, no el
    // último número. Es la trampa contraria a la anterior.
    expect(comoSeLeen([15, 1])).toBe('15 y 1 días antes');
  });

  it('una sola distinta de 1 va en plural', () => {
    expect(comoSeLeen([30])).toBe('30 días antes');
  });

  it('se ordenan de mayor a menor, que es como ocurren', () => {
    expect(comoSeLeen([1, 30, 5])).toBe('30, 5 y 1 días antes');
  });

  it('⛔ sin ninguna NO se lee como una lista vacía de días', () => {
    // «y  días antes» o « días antes» delataría el hueco. Un término sin
    // avisos es un término sin vigilancia, y la frase tiene que decirlo.
    expect(comoSeLeen([])).toBe('sin avisos');
  });

  it('⛔ no modifica la lista que recibe', () => {
    const dias = [1, 30, 5];
    comoSeLeen(dias);
    expect(dias).toEqual([1, 30, 5]);
  });
});
