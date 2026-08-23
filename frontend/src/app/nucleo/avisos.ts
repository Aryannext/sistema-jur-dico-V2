/**
 * Las dos preguntas que se hacen sobre los avisos de un término. CA-27.3 · RN-37c.
 *
 * <h2>Por qué son funciones y no métodos de la pantalla</h2>
 *
 * <p>Por la misma razón que {@link ./zonas}: son decisiones, no presentación.
 * Dentro del componente vivirían atadas a señales y a un `TestBed`, y en la
 * práctica eso significa que no se probarían. Aquí se prueban con una llamada.
 *
 * <p>Las dos fallan <strong>en silencio</strong> si se equivocan —una deja de
 * advertir de un aviso que no va a salir, la otra deja de distinguir un
 * término ajustado— y un fallo silencioso en un sistema cuyo trabajo es
 * avisar es el peor de los fallos.
 */

/**
 * Cuáles de estas anticipaciones ya no alcanzan a salir.
 *
 * <p><strong>Es la razón de ser del ajuste por término.</strong> Un término
 * que vence en dos días con el esquema corriente de 15/5/1 solo recibiría el
 * aviso de un día: los otros dos caen en el pasado y no se envían nunca. El
 * abogado creería tener tres avisos y tendría uno.
 *
 * <p>Se devuelven de mayor a menor, que es como se leen.
 *
 * @param dias las anticipaciones elegidas, en días
 * @param diasRestantes cuántos días faltan para el vencimiento; puede ser
 *        cero —vence hoy— o negativo si ya venció
 */
export function avisosQueNoAlcanzan(
  dias: readonly number[], diasRestantes: number,
): number[] {
  // Estricto y no «>=» a propósito: un aviso de 5 días para un término que
  // vence en 5 días sale HOY, y hoy todavía es a tiempo. Tratarlo como
  // perdido advertiría de algo que sí va a ocurrir, y una advertencia falsa
  // gasta la atención que hará falta cuando la advertencia sea cierta.
  return [...dias].filter(d => d > diasRestantes).sort((a, b) => b - a);
}

/**
 * Si estas anticipaciones se apartan de las del despacho.
 *
 * <p>Sirve para marcar en la lista el término que lleva avisos propios: uno
 * ajustado que parece corriente es una sorpresa esperando a ocurrir.
 *
 * <p>Compara <em>conjuntos</em>: ni el orden ni los repetidos cambian con qué
 * anticipación avisa el sistema, así que tampoco pueden hacer que un término
 * parezca ajustado cuando no lo está.
 *
 * @param delDespacho `null` cuando no se pudo leer el esquema — entonces no
 *        se afirma nada, que es distinto de afirmar que son iguales
 */
export function seApartaDelDespacho(
  delTermino: readonly number[], delDespacho: readonly number[] | null | undefined,
): boolean {
  if (!delDespacho) return false;

  return normalizar(delTermino) !== normalizar(delDespacho);
}

function normalizar(dias: readonly number[]): string {
  return [...new Set(dias)].sort((a, b) => a - b).join(',');
}

/**
 * Cómo se leen unas anticipaciones de corrido: «15, 5 y 1 días antes».
 *
 * <p>Existe por un defecto que se vio en pantalla: la lista se armaba pegando
 * los números y añadiendo «días antes», y un término con un solo aviso decía
 * <em>«1 días antes»</em>. Es pequeño y no rompe nada, pero es la clase de
 * descuido que hace dudar de lo que sí importa.
 *
 * <p>Se ordenan de mayor a menor, que es el orden en que ocurren.
 */
export function comoSeLeen(dias: readonly number[]): string {
  if (dias.length === 0) return 'sin avisos';

  const orden = [...new Set(dias)].sort((a, b) => b - a);
  const unidad = orden.length === 1 && orden[0] === 1 ? 'día' : 'días';

  if (orden.length === 1) return `${orden[0]} ${unidad} antes`;

  // «15, 5 y 1» — la última va con «y», como se dice en voz alta.
  const ultimo = orden[orden.length - 1];
  return `${orden.slice(0, -1).join(', ')} y ${ultimo} ${unidad} antes`;
}
