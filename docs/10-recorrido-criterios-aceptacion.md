# Recorrido de criterios de aceptación

**Qué es esto.** La verificación a mano de los criterios de aceptación de la
Fase 4, contra el sistema corriendo. No es un resumen de las pruebas
automáticas: es el recorrido que ninguna prueba hace.

**Por qué hacía falta.** Los nueve defectos encontrados hasta ahora aparecieron
**usando el sistema**, no leyendo código ni corriendo la suite: el `spring.mail.host`
vacío, el `lower(bytea)`, RF-08 sin existir, el 500 del esquema de alertas, el
«6:00 a.m.» que era falso, las tres pantallas que faltaban. La suite pasa en
verde y no vio ninguno.

---

## 1. Por dónde se empezó

Los criterios del proyecto se citan en el código y en las pruebas, así que lo
primero fue cruzarlos para no repetir trabajo ya hecho:

| | |
|---:|---|
| **126** | criterios de aceptación en la Fase 4 |
| 34 | citados en una **prueba automática** |
| 33 | citados solo en el **código** |
| **59** | **sin ninguna huella: nadie los había verificado** |

El recorrido ataca los 59. Que un criterio no esté citado **no significa que no
esté implementado** —la mayoría lo está— sino que nadie lo había comprobado
nunca contra el sistema.

De esos 59, **6 son de HU-39 y HU-40** (integración con la Rama Judicial), que
están declarados fuera del alcance de la propuesta y marcados *posterior*. No se
recorren: no hay nada que verificar.

---

## 2. Resultados

Leyenda: **✅** cumple · **⚠** cumple con reserva · **❌** no cumple · **⛔** el
criterio es negativo (verifica que algo **no** ocurre).

### EP1 · Plataforma y despachos

| Criterio | Qué exige | Resultado | Cómo se comprobó |
|---|---|---|---|
| **CA-01.3** ⛔ | El alta no tiene campos de plan, precio ni facturación | ✅ | El formulario tiene 7 campos y ninguno lo es; la tabla `despacho` tiene 7 columnas y ninguna lo es; **0 coincidencias** de plan/precio/factura/pago/tarifa/suscripción en todo el proyecto |
| **CA-03.2** ⛔ | Un despacho inactivo no pierde ni altera ningún dato | ✅ | Censo antes y después de desactivar el despacho 709: `usuarios=2 clientes=1 procesos=1 catalogos=27` **idéntico**, y otra vez igual al reactivarlo |

De paso quedó comprobado **CA-02.1**: al desactivar, su abogado recibió **HTTP
403** en el acto, sin esperar a que caducara la sesión.

### EP2 · Seguridad y acceso

| Criterio | Qué exige | Resultado | Cómo se comprobó |
|---|---|---|---|
| **CA-04.1** | Credenciales válidas dan acceso a las funciones de mis roles | ✅ | Entra y `/yo` devuelve `["ABOGADO","ADMIN_DESPACHO"]` |
| **CA-04.3** ⛔ | La contraseña no es legible en el almacén | ✅ | En base: `$2a$10$h6yGdBXAYvqAn3GiyIhwnuv…` (bcrypt con salt). La tabla `usuario` tiene 7 columnas y **ninguna** guarda texto plano |
| **CA-04.4** | Toda comunicación viaja cifrada | ❌ **en local, por decisión** | Es HTTP plano en `localhost`. Es el **control 3 de D-23**, diferido a propósito. No es un defecto: es un control que **debe verificarse antes de exponer el VPS**, y sigue pendiente |
| **CA-05.4** | Un usuario pertenece a un solo despacho | ✅ | `usuario.despacho_id` es un `bigint` escalar, no una tabla puente. 0 usuarios con más de uno |
| **CA-06.1** | Con dos roles accedo a la **unión**, sin cambiar de cuenta | ✅ | `admin.cat` alcanza `/api/usuarios` (solo admin) **y** `/api/procesos` (abogado) en la misma sesión |
| **CA-06.2** | El permiso se calcula por unión, no por un rol principal | ✅ | El contraste lo prueba: `raso@cantillo.co`, **abogado sin administrador**, recibe **403** en `/api/usuarios`. Si hubiera un «rol principal», los dos casos no podrían diferir así |

### EP3 · Clientes, procesos y expedientes

| Criterio | Qué exige | Resultado | Cómo se comprobó |
|---|---|---|---|
| **CA-09.3** ⛔ | Un cliente de otro despacho no aparece **ni en búsqueda** | ✅ | Desde el despacho Uno: acceso directo al cliente 4 → **403**, y **0 apariciones** en su listado. Las dos mitades importan: un 403 con el registro asomando en la lista seguiría siendo una fuga |
| **CA-10.2** | Un proceso tiene **exactamente un** titular | ✅ | `clienteTitular` es un campo singular en la respuesta, no una lista |
| **CA-11.2** | Omitir un campo obligatorio se impide **e indica cuál** | ✅ | El backend devuelve `"errores":{"juzgadoId":"Debe indicar el juzgado."}` y **ahora la interfaz lo muestra**. Se descubrió sin mostrarlo; ver H-1, ya corregido |
| **CA-11.3** | El destinatario de la alerta es el abogado responsable | ✅ | Proceso con responsable «Admin Cat» → alerta con `destinatario: "Admin Cat"`, `correoDestinatario: admin.cat@despacho.co` |
| **CA-11.4** | El juzgado se elige del catálogo, no se escribe libre | ✅ | `proceso.juzgado_id` es una referencia; **no existe ninguna columna de texto** para juzgado |
| **CA-12.2** | El mismo radicado **sí** se permite en otro despacho | ✅ | El radicado `41001 31 03 001 2026 00777 00` existe en Despacho Catálogos y se aceptó en Cantillo (proceso 500). La unicidad es por despacho (RN-17) |
| **CA-14.1** | El cambio de estado se refleja en los reportes | ✅ | Al archivar el proceso 499: `Activo 2→1`, `Archivado 0→1` en `/api/reportes/procesos-por-estado` |

### EP4 · Expediente digital

| Criterio | Qué exige | Resultado | Cómo se comprobó |
|---|---|---|---|
| **CA-15.1** | Un documento clasificado queda disponible para consulta y descarga | ✅ | Cargado por la API, aparece en el expediente y **se descarga con su contenido intacto** |
| **CA-15.2** ⛔ | El documento está **cifrado** en el almacén, no legible en claro | ✅ | Se cargó un fichero con una frase reconocible. **No aparece en ninguno de los 8 ficheros** del almacén, y los bytes crudos del más reciente son `a 327 356 334 A , 343 352 9 214 227 @ D`… |
| **CA-15.3** | Un archivo de hasta 20 MB se acepta | ✅ | 20 MB → **201**; 25 MB → **413**. Se probó **por los dos lados**: que entren 20 MB no dice nada si no hay límite, porque entonces entrarían también 500 |
| **CA-16.2** | Lo que cargo, mi cliente **ya lo ve**: sin borrador oculto | ✅ | El documento recién cargado aparece en la vista del cliente **y la nota interna no** (D-12) |
| **CA-16.3** | El sistema ofrece la nota interna como alternativa | ✅ | La advertencia de RF-16 responde `"alternativa":"Registrar como nota interna"` |
| **CA-17.1** | Fecha y tipo son obligatorios en una actuación | ✅ | Sin ellos: `"errores":{"fecha":"La fecha de la actuación es obligatoria.","tipoActuacionId":"Debe indicar el tipo de actuación."}` |
| **CA-18.1** | La nota queda visible para el despacho | ✅ | Aparece en el expediente del despacho |
| **CA-19.3** ⛔ | **No existe** la opción de eliminar una pieza | ✅ | `DELETE` → **404**: el endpoint no existe. Y **0 menciones** de eliminar/borrar en la pantalla del expediente. Se corrige rectificando (RN-27) |

### EP5 · Audiencias y términos

| Criterio | Qué exige | Resultado | Cómo se comprobó |
|---|---|---|---|
| **CA-20.3** | Al guardar la audiencia, sus alertas quedan programadas solas | ✅ | Audiencia 655 → **3 alertas** sin intervención (RN-29 exige 48 h, 24 h y el día) |
| **CA-21.1** | Las audiencias se ven ubicadas en su fecha | ✅ | Aparece en el calendario con su `fechaHora` — ver la nota sobre el rango |
| **CA-21.2** | Desde la audiencia se llega a su proceso y expediente | ✅ | La fila trae `procesoId` y `radicado` |
| **CA-22.1** | La fecha de vencimiento la indica **el abogado** | ✅ | Se guardó `2026-12-01`, exactamente lo enviado. El sistema no la tocó |
| **CA-22.2** ⛔ | El sistema **no calcula ni sugiere** la fecha a partir de normas procesales | ✅ | Ninguna sugerencia en la pantalla y ningún cálculo de vencimiento en el backend — ver la nota sobre el `grep` |
| **CA-22.4** | Queda **al menos una** alerta anticipada | ✅ | 3 alertas (RN-37b: nunca cero) |
| **CA-23.1** | El cambio de estado del término queda registrado | ✅ | `PENDIENTE → CUMPLIDO` |
| **CA-24.2** | Si la alerta por correo falló, el vencimiento **sigue visible** en el sistema | ✅ | El panel muestra sus 3 vencimientos con independencia del correo. Es la segunda vía de defensa contra **R-02** |

#### Dos falsas alarmas, y por qué se cuentan

**El calendario «no mostraba» la audiencia.** Falso: el calendario devuelve por
defecto **los próximos 30 días** (`inicio.plusDays(30)`), y la audiencia de
prueba estaba a tres meses. Rehecho con una audiencia dentro de la ventana,
aparece; y pidiendo noviembre explícitamente, la primera también. **La ausencia
era del rango de la consulta, no del sistema.**

**CA-22.2 saltó con «2 cálculos de fecha en el backend».** Falso también: los
dos son **rangos de consulta** —la ventana del panel de vencimientos y los 30
días del calendario—, no el cómputo de un plazo. El `grep` era demasiado ancho.

Se anotan porque una comprobación que da un falso negativo es tan inútil como
una que da un falso positivo: las dos hacen perder la confianza en el recorrido
entero. Y porque el criterio de este proyecto ha sido, desde el principio, que
un resultado sin verificar no es un resultado.

---

## 3. Hallazgos

### H-1 · El detalle por campo no llega a la pantalla

**Dónde.** Los catorce componentes que muestran errores. Se detectó en
`nuevo-proceso.ts`, la pantalla más nueva.

**Qué pasa.** Cuando falta un campo obligatorio, el backend responde:

```json
{"detail":"Revise los datos enviados.",
 "errores":{"juzgadoId":"Debe indicar el juzgado."}}
```

El extractor de mensajes lee solo `detail`, así que el usuario vería «Revise los
datos enviados» **sin saber cuál campo revisar**. El dato útil viaja y se tira.

**Y no eran dos pantallas: eran catorce.** Al ir a corregirlo apareció que
**cada componente tenía su propia copia** de la función, catorce en total, y
**ninguna** leía `errores`. Ese es el argumento contra duplicar: no es que
catorce copias ocupen más sitio, es que un defecto en una **es un defecto en las
catorce**, y se corrige catorce veces o no se corrige.

**Corregido.** Una sola función en `nucleo/mensajes.ts`, usada por los catorce,
con `errores` ganando a `detail` — «Debe indicar el juzgado» le dice al abogado
qué hacer, «Revise los datos enviados» no. Cada pantalla conserva **su** texto
por defecto, que es información y no ruido: «no se pudo consultar la bitácora»
orienta y «error inesperado» no.

Tiene **10 pruebas**, incluidas cuatro negativas: que nunca se enseñe el nombre
técnico del campo (`juzgadoId`), que un `errores` vacío no deje el aviso en
blanco, que valores que no sean texto no rompan la pantalla, y que un fallo que
no venga del servidor no invente un mensaje de servidor.

**Verificado en pantalla**, no solo en pruebas: al registrar un despacho con dos
correos mal escritos, el aviso dice *«El correo del administrador no tiene un
formato válido. El correo de contacto no tiene un formato válido.»* Antes habría
dicho «Revise los datos enviados».

**Estado:** cerrado. **CA-11.2 pasa de ⚠ a ✅.**

### H-2 · CA-04.4 no se puede cumplir en local, y eso ya estaba decidido

No es un defecto nuevo: es el **control 3 de la lista de D-23**, diferido junto
con otros cinco hasta el despliegue. Se anota aquí porque un recorrido de
criterios que lo marcara «cumple» estaría mintiendo, y uno que lo marcara
«defecto» estaría contando dos veces algo ya registrado.

**Estado:** pendiente de despliegue, con D-23 como referencia.

---

## 4. Lo que falta recorrer

Quedan **24 criterios** de EP6 a EP11: motor de alertas, portal del cliente,
búsqueda y reportes, administración del despacho y garantías transversales.
(Los 6 de EP10 —Rama Judicial— no se recorren: están fuera de alcance.)

### Rastro que dejó el recorrido

Verificar CA-15.3 exigió cargar de verdad un archivo de **20 MB**, y RN-27
impide borrar una pieza del expediente. Ese documento —bytes al azar— queda en
el expediente del proceso 499 y ocupa 20 MB cifrados en el almacén local. No es
un fallo: es el precio de comprobar el límite con un archivo real en vez de
suponerlo.

## 5. Cómo se repite

Los guiones usan `curl` con **sesiones simultáneas separadas** —un fichero de
cookies por usuario—, que es lo único que permite comprobar de verdad el
aislamiento entre despachos: hace falta estar dentro de dos a la vez para
intentar el cruce.

Las credenciales están en `09-entorno-local.md`. El despacho **Consultorio
Jurídico Cantillo** y sus dos usuarios existen para esto: son el segundo
despacho contra el que se contrastan los criterios de aislamiento, y
`raso@cantillo.co` es el único abogado sin rol de administrador del entorno.
