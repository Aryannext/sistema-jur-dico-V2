/**
 * Lo que devuelve el backend, tal cual.
 *
 * Son copias fieles de lo que sale por la API — verificadas contra las
 * respuestas reales, no deducidas de los DTO de Java. Si el backend cambia una
 * forma, este archivo es el único sitio del frontend que hay que tocar.
 *
 * Nombres en español porque son del dominio (D-21): un `Termino` es un término
 * judicial, no un «Deadline».
 */

/** GET /api/autenticacion/yo */
export interface Sesion {
  usuarioId: number;
  nombre: string;
  correo: string;
  despachoId: number | null;
  roles: string[];
}

/** GET /api/vencimientos — RF-23 · el panel que justifica el sistema. */
export interface Termino {
  id: number;
  procesoId: number;
  radicado: string;
  descripcion: string;
  /** ISO, solo fecha: los términos vencen el día, no a una hora. */
  fechaVencimiento: string;
  estado: 'PENDIENTE' | 'CUMPLIDO' | 'VENCIDO';
  estadoDescripcion: string;
  /** Lo calcula el backend. El frontend no vuelve a decidir qué está vencido. */
  vencido: boolean;
  seVigila: boolean;
  destinatarioAlertas: string | null;
}

/** GET /api/calendario — RF-20. */
export interface Audiencia {
  id: number;
  procesoId: number;
  radicado: string;
  /** ISO con hora: una audiencia sin hora no permite calcular sus alertas. */
  fechaHora: string;
  lugar: string | null;
  observaciones: string | null;
  asistio: boolean | null;
  seVigila: boolean;
  destinatarioAlertas: string | null;
}

/** GET /api/reportes/resumen — RF-32. */
export interface Resumen {
  totalProcesos: number;
  procesosNoArchivados: number;
  procesosArchivados: number;
  terminosPorVencer: number;
  terminosVencidos: number;
  desglosePorEstado: Conteo[];
}

export interface Conteo {
  id: number;
  nombre: string;
  cantidad: number;
}
