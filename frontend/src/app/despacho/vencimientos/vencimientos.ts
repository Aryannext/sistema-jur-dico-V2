import { Component, computed, inject } from '@angular/core';

import { Autenticacion } from '../../nucleo/autenticacion';
import { Termino } from '../../nucleo/modelos';
import { Vigilancia } from '../vigilancia';

/**
 * Panel de vencimientos. RF-23 · RF-24 · HU-23.
 *
 * <p>Es la pantalla que justifica el producto: la propuesta nace de que un
 * despacho pierde términos porque nadie los vigila. Todo lo demás del sistema
 * existe para que esta pantalla pueda decir la verdad.
 */
@Component({
  selector: 'sgpj-vencimientos',
  templateUrl: './vencimientos.html',
  styleUrl: './vencimientos.css',
})
export class Vencimientos {

  protected readonly vigilancia = inject(Vigilancia);
  protected readonly autenticacion = inject(Autenticacion);

  private static readonly MESES = [
    'ene', 'feb', 'mar', 'abr', 'may', 'jun',
    'jul', 'ago', 'sep', 'oct', 'nov', 'dic',
  ];

  private static readonly DIAS = [
    'domingo', 'lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado',
  ];

  /** «Buenos días» / «Buenas tardes», según el reloj de quien mira. */
  protected readonly saludo = computed(() => {
    const hora = new Date().getHours();
    if (hora < 12) return 'Buenos días';
    if (hora < 19) return 'Buenas tardes';
    return 'Buenas noches';
  });

  protected readonly hoy = computed(() => {
    const ahora = new Date();
    return `${Vencimientos.DIAS[ahora.getDay()]} ${ahora.getDate()} de ${
      ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto',
        'septiembre', 'octubre', 'noviembre', 'diciembre'][ahora.getMonth()]}`;
  });

  /** El primer nombre. «Buenos días, Marcela Ríos Andrade» no lo dice nadie. */
  protected readonly primerNombre = computed(
    () => this.autenticacion.nombre().trim().split(/\s+/)[0] ?? '');

  async refrescar(): Promise<void> {
    await this.vigilancia.refrescar();
  }

  /**
   * Cuánto falta o cuánto lleva vencido, en palabras.
   *
   * <p>Se calcula sobre días de calendario, no restando milisegundos: entre
   * las 23:00 de hoy y las 01:00 de mañana hay dos horas, pero para un término
   * es «mañana», no «hoy».
   *
   * <p>Y se cuida el singular. Un sistema que dice «vence en 1 días» es un
   * sistema que el abogado deja de tomarse en serio, y este necesita que le
   * crean.
   */
  protected cuanto(termino: Termino): string {
    const dias = this.diasHasta(termino.fechaVencimiento);

    if (dias === 0) return 'Vence hoy';
    if (dias === 1) return 'Vence mañana';
    if (dias > 1) return `En ${dias} días`;
    if (dias === -1) return 'Venció ayer';
    return `Venció hace ${Math.abs(dias)} días`;
  }

  /** La fecha, corta: «17 ago 2026». */
  protected fechaCorta(iso: string): string {
    const [anio, mes, dia] = iso.split('-').map(Number);
    return `${dia} ${Vencimientos.MESES[mes - 1]} ${anio}`;
  }

  protected fechaHoraCorta(iso: string): string {
    const cuando = new Date(iso);
    const hora = cuando.toLocaleTimeString('es-CO', {
      hour: 'numeric', minute: '2-digit', hour12: true,
    });
    return `${cuando.getDate()} ${Vencimientos.MESES[cuando.getMonth()]} · ${hora}`;
  }

  protected diaDe(iso: string): number {
    return new Date(iso).getDate();
  }

  protected mesDe(iso: string): string {
    return Vencimientos.MESES[new Date(iso).getMonth()];
  }

  /** El semáforo de la izquierda: rojo hoy, ámbar esta semana, apagado luego. */
  protected urgencia(termino: Termino): string {
    const dias = this.diasHasta(termino.fechaVencimiento);
    if (dias <= 0) return 'hoy';
    if (dias <= 3) return 'pronto';
    return 'ok';
  }

  private diasHasta(iso: string): number {
    const [anio, mes, dia] = iso.split('-').map(Number);
    const vence = new Date(anio, mes - 1, dia);

    const ahora = new Date();
    const hoy = new Date(ahora.getFullYear(), ahora.getMonth(), ahora.getDate());

    return Math.round((vence.getTime() - hoy.getTime()) / 86_400_000);
  }
}
