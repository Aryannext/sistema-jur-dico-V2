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

/** Un valor de catálogo tal como lo devuelve el backend anidado. */
export interface Referencia {
  id: number;
  nombre: string;
}

/** GET /api/procesos y /api/procesos/{id} — RF-11 · RF-31. */
export interface Proceso {
  id: number;
  radicado: string;
  juzgado: Referencia;
  tipoProceso: Referencia;
  estadoProcesal: Referencia;
  clienteTitular: Referencia;
  abogadoResponsable: Referencia | null;
  descripcion: string | null;
  archivado: boolean;
  expedienteId: number;
  fechaCreacion: string;
}

/**
 * Una pieza del expediente. RF-15, RF-17, RF-18, RF-38.
 *
 * Las tres clases —documento, actuación y nota— llegan en la misma lista
 * porque para quien lee el expediente son lo mismo: cosas que pasaron, en
 * orden. `visibleParaCliente` lo decide el backend pieza por pieza (RN-24); el
 * frontend no vuelve a razonarlo, solo lo muestra.
 */
export interface Pieza {
  id: number;
  tipo: 'DOCUMENTO' | 'ACTUACION' | 'NOTA';
  tipoParaMostrar: string;
  visibleParaCliente: boolean;
  autor: string;
  creadoEn: string;
  descripcion: string | null;
  /** Solo actuaciones: la fecha del hecho, distinta de cuándo se registró. */
  fechaActuacion: string | null;
  /** Actuaciones y documentos: el valor del catálogo del despacho. */
  tipoActuacion: string | null;
  origen: string | null;
}

/** GET /api/catalogos/{tipo}/activos — RF-33. */
export interface ValorCatalogo {
  id: number;
  tipo: string;
  nombre: string;
  activo: boolean;
  protegido: boolean;
  orden: number;
}

/** Un rol tal como lo nombra el backend. */
export interface Rol {
  codigo: string;
  nombre: string;
}

/** GET /api/usuarios — RF-05 · RF-06. */
export interface Usuario {
  id: number;
  despachoId: number;
  nombre: string;
  correo: string;
  activo: boolean;
  roles: Rol[];
  fechaRegistro: string;
}

/**
 * Una alerta del motor. RF-24 a RF-27 · RNF-08, RNF-09.
 *
 * <p>`detalleError` es el motivo real del fallo, conservado a propósito:
 * cuando un despacho pregunte por qué no le llegó el aviso, «falló el envío»
 * no es una respuesta.
 */
export interface Alerta {
  id: number;
  eventoId: number;
  tipoEvento: string;
  radicado: string;
  resumen: string;
  destinatario: string;
  correoDestinatario: string;
  programadaPara: string;
  enviadaEn: string | null;
  estado: 'PROGRAMADA' | 'ENVIADA' | 'FALLIDA';
  estadoDescripcion: string;
  intentos: number;
  detalleError: string | null;
}
