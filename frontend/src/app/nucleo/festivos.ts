/**
 * Festivos de Colombia.
 *
 * <h2>Qué es y qué NO es</h2>
 *
 * <p>Es <strong>información para el abogado</strong>: le dice qué días son
 * inhábiles para que no fije un término en uno de ellos, y por qué el
 * calendario tiene huecos.
 *
 * <p><strong>No calcula plazos.</strong> El sistema no cuenta días hábiles en
 * ninguna parte: la fecha de vencimiento la escribe quien registra el término,
 * y esa fecha es la que vale. Si esto contara días y el backend no, el
 * calendario diría una cosa y el motor de alertas otra — y la que decide es
 * siempre la del backend.
 *
 * <h2>Cómo se calculan</h2>
 *
 * <p>La <strong>Ley 51 de 1983 («Ley Emiliani»)</strong> traslada varios
 * festivos al lunes siguiente. Por eso no basta con una lista de fechas fijas:
 * la Asunción cae el 15 de agosto, pero en 2026 ese día es sábado y el festivo
 * es el lunes 17.
 *
 * <p>Los de Semana Santa y los que dependen de ella se calculan desde la
 * Pascua, que no tiene fecha fija.
 */

interface Festivo {
  mes: number;
  dia: number;
  nombre: string;
  /** Si se traslada al lunes siguiente cuando no cae en lunes (Ley Emiliani). */
  trasladable: boolean;
}

const FIJOS: Festivo[] = [
  { mes: 1, dia: 1, nombre: 'Año Nuevo', trasladable: false },
  { mes: 1, dia: 6, nombre: 'Reyes Magos', trasladable: true },
  { mes: 3, dia: 19, nombre: 'San José', trasladable: true },
  { mes: 5, dia: 1, nombre: 'Día del Trabajo', trasladable: false },
  { mes: 6, dia: 29, nombre: 'San Pedro y San Pablo', trasladable: true },
  { mes: 7, dia: 20, nombre: 'Independencia', trasladable: false },
  { mes: 8, dia: 7, nombre: 'Batalla de Boyacá', trasladable: false },
  { mes: 8, dia: 15, nombre: 'Asunción de la Virgen', trasladable: true },
  { mes: 10, dia: 12, nombre: 'Día de la Raza', trasladable: true },
  { mes: 11, dia: 1, nombre: 'Todos los Santos', trasladable: true },
  { mes: 11, dia: 11, nombre: 'Independencia de Cartagena', trasladable: true },
  { mes: 12, dia: 8, nombre: 'Inmaculada Concepción', trasladable: false },
  { mes: 12, dia: 25, nombre: 'Navidad', trasladable: false },
];

/** Días después de la Pascua, ya con el traslado incorporado donde aplica. */
const DESDE_PASCUA: { dias: number; nombre: string }[] = [
  { dias: -3, nombre: 'Jueves Santo' },
  { dias: -2, nombre: 'Viernes Santo' },
  { dias: 43, nombre: 'Ascensión del Señor' },
  { dias: 64, nombre: 'Corpus Christi' },
  { dias: 71, nombre: 'Sagrado Corazón' },
];

/** Domingo de Pascua por el algoritmo gregoriano anónimo. */
function pascua(anio: number): Date {
  const a = anio % 19;
  const b = Math.floor(anio / 100);
  const c = anio % 100;
  const d = Math.floor(b / 4);
  const e = b % 4;
  const f = Math.floor((b + 8) / 25);
  const g = Math.floor((b - f + 1) / 3);
  const h = (19 * a + b - d - g + 15) % 30;
  const i = Math.floor(c / 4);
  const k = c % 4;
  const l = (32 + 2 * e + 2 * i - h - k) % 7;
  const m = Math.floor((a + 11 * h + 22 * l) / 451);
  const mes = Math.floor((h + l - 7 * m + 114) / 31);
  const dia = ((h + l - 7 * m + 114) % 31) + 1;

  return new Date(anio, mes - 1, dia);
}

/** Al lunes siguiente, si no es ya lunes. */
function alLunes(fecha: Date): Date {
  const dia = fecha.getDay();
  if (dia === 1) return fecha;

  const faltan = dia === 0 ? 1 : 8 - dia;
  return new Date(fecha.getFullYear(), fecha.getMonth(), fecha.getDate() + faltan);
}

const memoria = new Map<number, Map<string, string>>();

function clave(fecha: Date): string {
  return `${fecha.getMonth() + 1}-${fecha.getDate()}`;
}

/** Los festivos de un año: clave «mes-día» → nombre. */
export function festivosDe(anio: number): Map<string, string> {
  const guardado = memoria.get(anio);
  if (guardado) return guardado;

  const mapa = new Map<string, string>();

  for (const f of FIJOS) {
    const fecha = new Date(anio, f.mes - 1, f.dia);
    mapa.set(clave(f.trasladable ? alLunes(fecha) : fecha), f.nombre);
  }

  const domingoDePascua = pascua(anio);
  for (const f of DESDE_PASCUA) {
    const fecha = new Date(
      domingoDePascua.getFullYear(),
      domingoDePascua.getMonth(),
      domingoDePascua.getDate() + f.dias);
    mapa.set(clave(fecha), f.nombre);
  }

  memoria.set(anio, mapa);
  return mapa;
}

/** El nombre del festivo, o null si es un día corriente. */
export function nombreDeFestivo(fecha: Date): string | null {
  return festivosDe(fecha.getFullYear()).get(clave(fecha)) ?? null;
}

/** Sábado, domingo o festivo. */
export function esInhabil(fecha: Date): boolean {
  const dia = fecha.getDay();
  return dia === 0 || dia === 6 || nombreDeFestivo(fecha) !== null;
}

/** Por qué un día es inhábil, para decírselo al usuario. */
export function motivoInhabil(fecha: Date): string | null {
  const dia = fecha.getDay();
  if (dia === 6) return 'es sábado';
  if (dia === 0) return 'es domingo';

  const festivo = nombreDeFestivo(fecha);
  return festivo ? `es festivo (${festivo})` : null;
}
