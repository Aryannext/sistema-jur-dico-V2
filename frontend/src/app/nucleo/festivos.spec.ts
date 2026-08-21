import { describe, expect, it } from 'vitest';

import { esInhabil, festivosDe, motivoInhabil, nombreDeFestivo } from './festivos';

/**
 * Los festivos no se prueban «que devuelva algo»: se prueban contra las fechas
 * REALES del calendario colombiano. Un cálculo de festivos que no coincide con
 * el país es peor que no tenerlo, porque el abogado le cree.
 */
describe('Festivos de Colombia', () => {

  it('2026 coincide exactamente con el calendario oficial', () => {
    const esperados = new Map(Object.entries({
      '1-1': 'Año Nuevo',
      '1-12': 'Reyes Magos',
      '3-23': 'San José',
      '4-2': 'Jueves Santo',
      '4-3': 'Viernes Santo',
      '5-1': 'Día del Trabajo',
      '5-18': 'Ascensión del Señor',
      '6-8': 'Corpus Christi',
      '6-15': 'Sagrado Corazón',
      '6-29': 'San Pedro y San Pablo',
      '7-20': 'Independencia',
      '8-7': 'Batalla de Boyacá',
      '8-17': 'Asunción de la Virgen',
      '10-12': 'Día de la Raza',
      '11-2': 'Todos los Santos',
      '11-16': 'Independencia de Cartagena',
      '12-8': 'Inmaculada Concepción',
      '12-25': 'Navidad',
    }));

    // Se comparan los mapas ENTEROS: así también falla si sobra un festivo,
    // no solo si falta. Un día marcado inhábil que no lo es también engaña.
    expect(festivosDe(2026)).toEqual(esperados);
  });

  it('la Ley Emiliani traslada al lunes: en 2025 la Asunción es el 18, no el 15', () => {
    // Este caso concreto apareció dibujando los mockups: había un término
    // venciendo el 18 de agosto de 2025, que es festivo.
    expect(nombreDeFestivo(new Date(2025, 7, 18))).toBe('Asunción de la Virgen');
    expect(nombreDeFestivo(new Date(2025, 7, 15))).toBeNull();
  });

  it('los que NO son trasladables se quedan donde caen', () => {
    // El 7 de agosto de 2025 es jueves y sigue siendo festivo: la Batalla de
    // Boyacá no se mueve. Si el traslado se aplicara a todo, este caería en
    // lunes y el calendario mentiría dos veces.
    expect(nombreDeFestivo(new Date(2025, 7, 7))).toBe('Batalla de Boyacá');
    expect(nombreDeFestivo(new Date(2025, 7, 11))).toBeNull();
  });

  it('la Pascua arrastra a los suyos: 2026 cae el 5 de abril', () => {
    expect(nombreDeFestivo(new Date(2026, 3, 2))).toBe('Jueves Santo');
    expect(nombreDeFestivo(new Date(2026, 3, 3))).toBe('Viernes Santo');
    // El domingo de Pascua NO es festivo de ley: es domingo.
    expect(nombreDeFestivo(new Date(2026, 3, 5))).toBeNull();
  });

  it('distingue por qué un día es inhábil', () => {
    expect(motivoInhabil(new Date(2026, 7, 8))).toBe('es sábado');
    expect(motivoInhabil(new Date(2026, 7, 9))).toBe('es domingo');
    expect(motivoInhabil(new Date(2026, 7, 7))).toBe('es festivo (Batalla de Boyacá)');
    expect(motivoInhabil(new Date(2026, 7, 10))).toBeNull();
  });

  it('un día corriente no es inhábil', () => {
    // La contraparte: sin esto, una función que devolviera «inhábil» siempre
    // pasaría todas las comprobaciones anteriores.
    expect(esInhabil(new Date(2026, 7, 10))).toBe(false);
    expect(esInhabil(new Date(2026, 7, 7))).toBe(true);
  });
});
