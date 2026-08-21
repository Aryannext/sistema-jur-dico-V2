import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { nombreDeFestivo } from '../../nucleo/festivos';
import { Vigilancia } from '../vigilancia';

/** Una casilla del mes. */
interface Casilla {
  fecha: Date;
  dia: number;
  delMes: boolean;
  finDeSemana: boolean;
  festivo: string | null;
  hoy: boolean;
  audiencias: { id: number; procesoId: number; hora: string; que: string }[];
  terminos: { id: number; procesoId: number; que: string; urgencia: string }[];
}

/**
 * Calendario del despacho. RF-20 · HU-21.
 *
 * <h2>Audiencias y términos comparten calendario</h2>
 *
 * <p>En derecho son cosas distintas —una es una cita, el otro es un plazo—
 * pero para quien organiza su semana ocupan el mismo lugar: días en los que
 * algo hay que hacer. Se distinguen por forma (pastilla sólida contra punto),
 * no por pantalla.
 *
 * <h2>Los festivos salen, y son solo información</h2>
 *
 * <p>El sistema <strong>no cuenta días hábiles</strong> en ninguna parte: la
 * fecha de vencimiento la escribe el abogado. Marcar los festivos le ayuda a
 * no fijar un plazo en un día inhábil y a entender por qué el juzgado no
 * responde — pero no cambia ningún cálculo, porque no hay ninguno que cambiar.
 */
@Component({
  selector: 'sgpj-calendario',
  imports: [RouterLink],
  templateUrl: './calendario.html',
  styleUrl: './calendario.css',
})
export class Calendario {

  protected readonly vigilancia = inject(Vigilancia);

  private static readonly MESES = [
    'enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio',
    'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre',
  ];

  /** El primer día del mes que se está viendo. */
  protected readonly mes = signal(Calendario.primeroDelMesDe(new Date()));

  protected readonly titulo = computed(() => {
    const m = this.mes();
    return `${Calendario.MESES[m.getMonth()]} de ${m.getFullYear()}`;
  });

  protected readonly esMesActual = computed(() => {
    const hoy = new Date();
    return this.mes().getMonth() === hoy.getMonth()
      && this.mes().getFullYear() === hoy.getFullYear();
  });

  /**
   * Las seis semanas de la rejilla, de lunes a domingo.
   *
   * <p>Siempre seis, aunque el mes quepa en cinco: si la rejilla cambiara de
   * alto al pasar de mes, el contenido de debajo saltaría en cada clic.
   */
  protected readonly semanas = computed<Casilla[][]>(() => {
    const primero = this.mes();
    const desplazamiento = (primero.getDay() + 6) % 7; // lunes = 0
    const inicio = new Date(
      primero.getFullYear(), primero.getMonth(), primero.getDate() - desplazamiento);

    const hoy = new Date();
    const claveHoy = Calendario.clave(hoy);

    const semanas: Casilla[][] = [];
    for (let s = 0; s < 6; s++) {
      const semana: Casilla[] = [];
      for (let d = 0; d < 7; d++) {
        const fecha = new Date(
          inicio.getFullYear(), inicio.getMonth(), inicio.getDate() + s * 7 + d);
        const diaSemana = fecha.getDay();

        semana.push({
          fecha,
          dia: fecha.getDate(),
          delMes: fecha.getMonth() === primero.getMonth(),
          finDeSemana: diaSemana === 0 || diaSemana === 6,
          festivo: nombreDeFestivo(fecha),
          hoy: Calendario.clave(fecha) === claveHoy,
          audiencias: this.audienciasDe(fecha),
          terminos: this.terminosDe(fecha),
        });
      }
      semanas.push(semana);
    }
    return semanas;
  });

  protected mesAnterior(): void {
    const m = this.mes();
    this.mes.set(new Date(m.getFullYear(), m.getMonth() - 1, 1));
  }

  protected mesSiguiente(): void {
    const m = this.mes();
    this.mes.set(new Date(m.getFullYear(), m.getMonth() + 1, 1));
  }

  protected volverAHoy(): void {
    this.mes.set(Calendario.primeroDelMesDe(new Date()));
  }

  protected async refrescar(): Promise<void> {
    await this.vigilancia.refrescar();
  }

  protected fechaHoraCorta(iso: string): string {
    const cuando = new Date(iso);
    const hora = cuando.toLocaleTimeString('es-CO', {
      hour: 'numeric', minute: '2-digit', hour12: true,
    });
    return `${cuando.getDate()} ${Calendario.MESES[cuando.getMonth()].slice(0, 3)} · ${hora}`;
  }

  protected diaDe(iso: string): number {
    return new Date(iso).getDate();
  }

  protected mesDe(iso: string): string {
    return Calendario.MESES[new Date(iso).getMonth()].slice(0, 3);
  }

  private audienciasDe(fecha: Date): Casilla['audiencias'] {
    const clave = Calendario.clave(fecha);

    return this.vigilancia.proximasAudiencias()
      .filter(a => Calendario.clave(new Date(a.fechaHora)) === clave)
      .map(a => ({
        id: a.id,
        procesoId: a.procesoId,
        hora: new Date(a.fechaHora).toLocaleTimeString('es-CO', {
          hour: 'numeric', minute: '2-digit', hour12: true,
        }),
        que: a.observaciones || 'Audiencia',
      }));
  }

  private terminosDe(fecha: Date): Casilla['terminos'] {
    const clave = `${fecha.getFullYear()}-${String(fecha.getMonth() + 1).padStart(2, '0')}-${String(fecha.getDate()).padStart(2, '0')}`;

    const todos = [...this.vigilancia.vencidos(), ...this.vigilancia.porVencer()];

    return todos
      .filter(t => t.fechaVencimiento === clave)
      .map(t => ({
        id: t.id,
        procesoId: t.procesoId,
        que: t.descripcion,
        urgencia: t.vencido ? 'roj' : 'amb',
      }));
  }

  private static clave(fecha: Date): string {
    return `${fecha.getFullYear()}-${fecha.getMonth()}-${fecha.getDate()}`;
  }

  private static primeroDelMesDe(fecha: Date): Date {
    return new Date(fecha.getFullYear(), fecha.getMonth(), 1);
  }
}
