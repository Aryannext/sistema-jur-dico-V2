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

### EP6 · Motor de alertas ★

Es la épica que sostiene el producto. Los criterios negativos de aquí son los
más importantes del sistema: comprobar que **no** se avisa de un proceso
archivado o de un término ya cumplido es lo que separa un sistema en el que el
abogado confía de uno que acaba ignorando por ruidoso (**R-05**).

El envío se forzó **adelantando el momento programado en la base**, no
esperando: esperar a la fecha real haría la verificación irrepetible, y mover el
reloj del sistema afectaría a todo lo demás.

| Criterio | Qué exige | Resultado | Cómo se comprobó |
|---|---|---|---|
| **CA-25.1** | La alerta se emite **sin que ningún usuario la solicite** | ✅ | Momento adelantado 2 min → un barrido la dejó en `ENVIADA` |
| **CA-25.2** | El destinatario es el abogado responsable, por correo | ✅ | `admin.cat@despacho.co`, que es el responsable del proceso |
| **CA-25.4** | Sale dentro de **15 minutos** de su momento | ⚠ | Salió a los **2,00 min**. Cumple **por alerta**; con el pico de 2.499 simultáneas **no** — es **A-05**, ya registrado |
| **CA-26.1** | Alertas a 48 h, 24 h y el día de la audiencia | ✅ | Anticipaciones medidas: **[48, 24, 0]** horas |
| **CA-26.4** ⛔ | Se emite **una sola vez**: ni duplicada ni omitida | ✅ | Se descubrió incumplido —un barrido interrumpido reenviaba— y se corrigió. Ver **H-6** |
| **CA-28.1** ⛔ | Un proceso **archivado** no genera alerta | ✅ | Con el proceso archivado la alerta quedó **`DESCARTADA`**, no enviada (RN-20) |
| **CA-28.2** ⛔ | Un término **cumplido** no genera alerta | ✅ | Igual: **`DESCARTADA`** (RN-39) |
| **CA-30.1** | El historial muestra fecha, destinatario y resultado | ✅ | Se descubrió sin cumplirse en la interfaz; ver **H-3**, ya corregido |
| **CA-30.2** | Ante un término vencido, se puede saber si el sistema avisó y cuándo | ✅ | Igual; ver **H-3** |

**CA-26.4 tiene un límite honesto.** El criterio habla de un reinicio del
servicio a media ventana de envío, y eso no se puede provocar desde aquí. Lo que
sí se comprobó es el mecanismo que hace que ese reinicio sea seguro: que un
segundo barrido sobre una alerta ya enviada **no la reenvía**. Es la mitad del
criterio, y se dice que es la mitad.

### EP6 · Configuración del esquema (lo que faltaba)

| Criterio | Qué exige | Resultado | Cómo se comprobó |
|---|---|---|---|
| **CA-26.3** | Se pueden **añadir** avisos al esquema del despacho | ✅ | `[15,5,1]` → `[20,15,5,1]`, guardado |
| **CA-27.1 · CA-38.1** | Los términos **nuevos** usan el esquema configurado | ✅ | Un término creado justo después nació con **[20, 15, 5, 1]** días de anticipación |
| **CA-27.3** | Se puede ajustar el esquema de **un término** sin cambiar el del despacho | ✅ | Se descubrió sin existir; corregido en **H-5** (D-29 · RN-37c) y **con pantalla** desde D-31. Comprobado en el sistema: un término pasó a **5/3/1**, el de al lado siguió en **15/5/1** y el esquema del despacho no se movió |
| **CA-31.1** | Al desactivar el despacho, recibe **un correo** avisando de la suspensión | ✅ | Se desactivó el despacho de prueba y el aviso salió a `contacto@cantillo.co` |
| **CA-31.2** | Ese aviso es **notificación de corte**, no acceso | ✅ | El mensaje **no contiene ningún enlace, token ni vía de acceso**: dice qué deja de ocurrir y que la información se conserva |

### EP8 · Búsqueda y reportes

| Criterio | Qué exige | Resultado | Cómo se comprobó |
|---|---|---|---|
| **CA-35.3** ⛔ | La búsqueda **solo** devuelve procesos de mi despacho | ✅ | Un fragmento que coincide con **1 proceso del otro despacho y 0 de los míos**: la búsqueda devolvió 0. Había algo real que fugar y no se fugó |
| **CA-35.4** | Con el volumen objetivo, responde en menos de 3 s | ✅ | **153 ms** sobre 25.000 procesos, ya medido en RNF-12 |

### EP9 · Administración del despacho

| Criterio | Qué exige | Resultado | Cómo se comprobó |
|---|---|---|---|
| **CA-37.5** | El catálogo de **juzgados** nace vacío | ✅ | El despacho creado hoy nació con 4 estados, 7 tipos de actuación, 8 de documento y 7 de proceso sembrados — y **cero juzgados**. Ninguna migración los siembra |

### EP11 · Garantías transversales

| Criterio | Qué exige | Resultado | Cómo se comprobó |
|---|---|---|---|
| **CA-41.3** | Existe una prueba de acceso cruzado **para cada módulo** | ✅ | Se descubrió con una sola, la de usuario; ahora son **41 en 9 módulos**. Ver **H-4**, ya corregido |
| **CA-42.1** ⛔ | Con el despacho inactivo, una funcionalidad de **cada módulo** queda bloqueada | ✅ | Los **10 módulos** pasaron de `200` a `401`/`403` **con la sesión ya abierta**. Ver la nota de abajo |
| **CA-42.2** | La verificación está en **un único punto de control** | ✅ | Una sola definición de `despachoActual()`, y **cero** parámetros `despachoId` en peticiones: el despacho no puede llegar desde fuera |

**CA-42.1 solo significa algo con la sesión ya abierta.** Desactivar primero e
intentar entrar después habría comprobado únicamente que el ingreso falla, y eso
no dice nada sobre los módulos. Se entró, se comprobó que los diez respondían
`200`, se desactivó **sin tocar la sesión**, y se repitió: los diez pasaron a
`401`/`403`.

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

### H-3 · «Historial de alertas» no muestra el historial

**Dónde.** `despacho/alertas/historial.ts` y su pantalla.

**Qué pasa.** La pantalla se anuncia así:

> Historial de alertas
> *Todo lo que el sistema **intentó** avisar · y lo que no pudo*

Y consulta exactamente dos cosas: `/api/alertas/fallidas` y
`/api/alertas/programadas`. **Nunca las `ENVIADA`.** Ni siquiera su pestaña
«Todas», que suma fallidas + programadas.

Es decir: la pantalla muestra lo que falló y lo que está por salir, y **oculta
todo lo que el sistema sí envió** — que es la mayoría, y la única evidencia de
que la vigilancia funciona. Medido ahora mismo en el despacho de prueba: 0
fallidas, 23 programadas, y **1 enviada que ninguna pantalla consulta**.

**Es el mismo defecto que el «6:00 a.m.»**: texto en pantalla que promete más de
lo que el sistema hace. Y aquí duele más, porque lo que promete es justamente lo
que un despacho necesitaría enseñar ante una reclamación.

**Y hay una capacidad entera sin usar.** El backend expone
`GET /api/alertas/de-evento/{id}`, documentado literalmente como *«lo que permite
responder ¿el sistema avisó, y cuándo? Ante una reclamación, ese registro es la
defensa del despacho»*. Tiene **0 referencias en todo el frontend**. Verificado
por API: devuelve `programadaPara`, `enviadaEn`, `estado` y
`correoDestinatario` — exactamente lo que piden CA-30.1 y CA-30.2.

**Consecuencia.** CA-30.1 y CA-30.2 **se cumplen en el backend y no en la
interfaz**. Desde la aplicación no hay forma de saber si un término vencido fue
avisado.

**Corregido.** Tres cambios:

1. **`GET /api/alertas/enviadas`** en el backend. No existía: el listado por
   estado estaba escrito para las FALLIDA y las PROGRAMADA, y las ENVIADA se
   quedaron fuera.
2. **La pestaña «Enviadas»** en la pantalla, y las tres sumadas en «Todas».
3. **El subtítulo dice ahora la verdad**: «Lo que salió, lo que no pudo salir y
   lo que está por salir».

El vacío de esa pestaña lleva su propio texto, y es lo contrario del de
«Fallidas»: una lista de fallidas vacía es buena noticia; una de enviadas vacía
significa que **o el despacho acaba de empezar, o el motor no está funcionando**.
Decirlo evita que se lea como tranquilizador.

**Dos pruebas nuevas** en `MotorAlertasIntegracionTest`, una de ellas negativa:
que una alerta enviada se pueda encontrar después **con su fecha de envío** —sin
la fecha no se responde «¿y cuándo?»— y que el listado de enviadas **no mezcle
las pendientes**, porque decir que salió un aviso que no ha salido es peor que
no mostrarlo: el despacho creería que su cliente ya fue avisado.

**Verificado en pantalla:** «Todas 24 · Fallidas 0 · **Enviadas 1** ·
Programadas 23», con la enviada mostrando destinatario y fecha real de envío.

**Estado:** cerrado. **CA-30.1 y CA-30.2 pasan a ✅.**

### H-4 · Solo un módulo tiene prueba de acceso cruzado

**CA-41.3 lo dice con todas las letras:** *«existe una prueba de acceso cruzado
para ese módulo. **La prueba no se hace una vez: se hace en cada módulo**»*. Y su
razón de ser es que hacerla una sola vez garantiza que algún módulo se olvide.

Es exactamente lo que pasó. `AislamientoEntreDespachosTest` existe y es bueno
—siete pruebas—, pero **todas son del módulo de usuarios**. Del resto:

| Módulo | Prueba de acceso cruzado |
|---|---|
| usuario | ✅ 7 pruebas |
| cliente | ❌ *no tiene ni carpeta de pruebas* |
| proceso · expediente · portal · catálogo · reportes · bitácora | ❌ ninguna crea dos despachos |

Sin crear dos despachos en la prueba, **no se puede probar el cruce**: no hay
desde dónde intentarlo.

**Lo importante: el sistema no está mal, la red de seguridad sí.** El aislamiento
funciona — se verificó a mano hoy en **CA-09.3** (clientes) y **CA-35.3**
(búsqueda de procesos), y las dos pasaron contra datos reales. Lo que falta es
que algo lo *siga* comprobando: hoy, un cambio que rompiera el aislamiento en
procesos o en el portal no haría fallar ninguna prueba.

Es el mismo patrón que D-25 dejó escrito sobre A-05: *ninguna prueba lo detectó
porque todas comprobaban una alerta, y el fallo estaba en el conjunto*. Aquí
todas comprueban un módulo, y el requisito habla de todos.

**Corregido.** Ahora hay **una prueba de acceso cruzado en cada módulo**, en el
paquete de su módulo, no en un fichero central: una prueba que vive junto al
módulo se borra, se mueve y se rompe con él; una central sobre nueve módulos
sobrevive intacta a que alguien se lleve uno por delante.

| Módulo | Pruebas |
|---|---:|
| cliente · proceso · expediente · vigilancia · alertas · catálogo · reportes · bitácora | **34** |
| usuario *(ya existía)* | 7 |
| **Total** | **41** |

Lo único compartido es el andamiaje: `PruebaDeAislamiento` monta **dos despachos
con datos reales** —cliente, proceso, expediente con nota, término y audiencia
en cada uno—. Sin datos no se prueba nada: una consulta cruzada contra un
despacho vacío devuelve vacío *siempre*, haya filtro o no. Es el error que este
mismo recorrido cometió en CA-35.3.

**Y se comprobó que las pruebas sirven, no solo que pasan.** Se rompió el filtro
de clientes a propósito —`findAll()` en lugar de filtrar por despacho— y la
prueba falló nombrando el registro fugado:

```
Expected an empty value at JSON path "$[?(@.id == 613)]"
but found: [{"id":613,"nombre":"Cliente de B",...}]
```

Filtro restaurado después. Sin esa comprobación, 41 pruebas en verde solo
demostrarían que 41 pruebas están en verde.

**Tres de las 41 fallaron al escribirlas, y ninguna era una fuga:**

- El historial de alertas y la bitácora de un proceso ajeno devuelven **200 con
  lista vacía**, no 403 — porque el despacho va *dentro* de la consulta. Mi
  aserción pedía 4xx. El reflejo de «cruce = 403» es fácil y, aplicado a una
  colección, da por rota una implementación correcta.
- El reporte resumen usa `totalProcesos`, no `procesosActivos`.

**Estado:** cerrado. **CA-41.3 pasa a ✅.**

### H-6 · Un barrido interrumpido reenvía lo que ya había salido

**Encontrado después de cerrar el recorrido**, al elegir en qué seguir: revisando
D-26 apareció que separar el envío de la transacción hace falta con *cualquiera*
de las dos salidas de A-05, y mirando por qué, salió esto.

**Qué pasa.** `MotorAlertas.ejecutarBarrido()` es **una sola transacción para
todo el lote**. Dentro de ella se envía el correo —que es irreversible— y se
marca la alerta como enviada —que no lo es, porque no se ha hecho *commit*.

Si algo revierte esa transacción después de que hayan salido varios correos —un
reinicio durante el despliegue, una caída de la conexión con la base, cualquier
error no capturado—, las alertas vuelven a `PROGRAMADA` **con los correos ya
enviados**, y el siguiente barrido los manda otra vez.

**Reproducido** (`BarridoInterrumpidoTest`, etiqueta `defecto-abierto`):

```
Correos que ya habían salido antes de la caída : 2
Alertas que volvieron a PROGRAMADA             : 4 de 4
Correos REPETIDOS en el segundo barrido        : 2
```

**Incumple CA-26.4** literalmente: *«cuando el servicio de alertas se reinicia
durante la ventana de envío, entonces la alerta se emite **una sola vez**: ni
duplicada ni omitida»*. Es el caso que este mismo recorrido dio por no
comprobable —se verificó solo que un segundo barrido normal no reenvía— y que
resultó ser el que fallaba.

**Por qué importa más ahora.** Hoy la ventana de riesgo dura milisegundos: el
lote es de 100 y el emisor escribe en un log. **Cualquiera de las dos salidas de
A-05 la alarga a minutos** —SMTP real, lotes mayores—, y minutos bastan para que
un despliegue parta un barrido por la mitad.

**Y el daño es el que R-05 describe.** Un abogado que recibe dos veces el mismo
aviso deja de fiarse de todos, y a partir de ahí el sistema tiene alertas pero ya
no tiene vigilancia.

**Corregido.** El barrido dejó de ser una sola transacción. Ahora **coordina** y
no persiste: toma la lista de identificadores y le pasa cada alerta a
`EnvioDeUnaAlerta`, que la envía y la confirma **en su propia transacción**,
inmediatamente después del envío. La ventana entre «el correo salió» y «consta
que salió» pasa de durar todo el lote a durar una alerta.

**Lo que esto NO consigue, y está escrito en el código.** No hay forma de
garantizar «exactamente una vez» con un efecto externo: el correo sale por la
red y el *commit* ocurre después. Lo que se elige es **qué se arriesga**:
duplicar como mucho un aviso en lugar de repetir el lote entero. La alternativa
—marcar como enviada antes de enviar— cambia el riesgo por el contrario, y una
alerta perdida en silencio es exactamente el fallo que el sistema existe para
evitar (**R-02**). Entre repetir un aviso y no darlo, se repite.

**Dos cosas que hubo que resolver por el camino, y las dos son la misma trampa:**

1. **`idsPendientes()` se llamaba a sí mismo dentro del motor.** Spring aplica
   `@Transactional` con un proxy, y una llamada interna no pasa por él: la
   anotación no hacía nada y el bloqueo pesimista falló con «No active
   transaction». Es la trampa que el javadoc de `EnvioDeUnaAlerta` advierte, y
   caí en ella en la clase de al lado. Resuelto poniendo la transacción en el
   repositorio.
2. **Seis pruebas de `MotorAlertasIntegracionTest` se pusieron rojas.** La clase
   era `@Transactional`, y `REQUIRES_NEW` **suspende** la transacción de la
   prueba: desde la nueva no se ven los datos que la prueba creó sin confirmar.
   No era un fallo del motor — era la prueba apoyándose en que el barrido
   compartía su transacción, que es justo lo que había que romper.

**Verificado por mutación**, no solo por pasar: al devolver el motor a su
estructura anterior la prueba volvió a fallar con las mismas cifras (4 de 4
revertidas, 2 correos repetidos). Restaurado después.

`BarridoInterrumpidoTest` pasa de `defecto-abierto` a `integracion`: nació
fallando y ahora es el guardián de que no vuelva.

**Y hubo una tercera víctima, la peor de las tres.** `PicoDeAlertasTest` —la
prueba que sostiene la evidencia de A-05— también era `@Transactional`, y al
quedarse ciega **no falló rápido: degeneró en un bucle de 41 minutos**,
barriendo en vacío 509 veces y consultando 500 eventos en cada vuelta —254.500
consultas—. Terminó «fallando» por no haber drenado el pico, que es el mensaje
equivocado: **no medía nada y parecía medir**.

Es el peor modo de fallo posible, porque las otras dos se delataron solas. Ahora
tiene dos defensas: el montaje se confirma, y **si el primer barrido no envía
nada la prueba se detiene en el acto** diciendo que el motor no ve el montaje,
en vez de insistir 508 veces. Vuelta a medir: 24 segundos, y las cifras reales
de A-05.

**Estado:** cerrado. **CA-26.4 pasa a ✅.**

### H-5 · No se puede ajustar el esquema de alertas de un término

**CA-27.3** pide poder darle a un término concreto una anticipación distinta sin
cambiar la del despacho. **No existe**: ni endpoint, ni columna en `termino`. El
esquema es del despacho y se aplica a todo por igual.

**Gravedad: baja.** No rompe nada y RN-37b sigue garantizando el mínimo. Pero es
un criterio de aceptación escrito y aprobado que no está implementado, y el caso
que lo motiva es real: un término de dos días no se vigila igual que uno de
sesenta, y con un esquema de 15/5/1 el primero solo recibiría el aviso de un día.

**Corregido** (**D-29** · **RN-37c**). Cada término lleva ahora sus propias
anticipaciones, copiadas del esquema del despacho al registrarlo y ajustables
después sin tocarlo.

**Y apareció una trampa que no se buscaba.** Las anticipaciones eran
`@Transient`, así que `actualizarTermino` **releía el esquema del despacho** al
reprogramar por cambio de fecha: un ajuste individual se habría perdido **en
silencio** la próxima vez que alguien corrigiera la fecha del término — sin
error, sin pista. Tiene prueba propia, y es la más valiosa de las siete.

Persistirlas hace además explícito lo que **CA-38.3** ya exigía: cambiar el
esquema del despacho no reprograma los términos existentes. Antes era cierto de
rebote; ahora lo es por diseño.

**7 pruebas**, cinco negativas — incluida que **las alertas ya enviadas no se
borran** al reprogramar: son el registro de que el sistema avisó (RNF-09).

**Estado:** cerrado. **CA-27.3 pasa a ✅.**

**Y después quedó a medias otra vez.** El criterio se cumplía por API pero no
había **pantalla**: un abogado no podía usarlo. Era el mismo hueco que se les
había encontrado a otras tres pantallas en este mismo recorrido —endpoint sin
interfaz—, recién creado. Cerrado en **D-31**.

### H-2 · CA-04.4 no se puede cumplir en local, y eso ya estaba decidido

No es un defecto nuevo: es el **control 3 de la lista de D-23**, diferido junto
con otros cinco hasta el despliegue. Se anota aquí porque un recorrido de
criterios que lo marcara «cumple» estaría mintiendo, y uno que lo marcara
«defecto» estaría contando dos veces algo ya registrado.

**Estado:** pendiente de despliegue, con D-23 como referencia.

---

## 4. Recuento final

**El recorrido está completo.**

| | |
|---:|---|
| **54** | criterios recorridos |
| **53** | ✅ cumplen |
| **0** | ❌ ninguno incumple |
| **1** | ❌ no puede cumplirse en local por decisión: **CA-04.4** (TLS) |
| **1** | ⚠ cumple por alerta, no en el pico: **CA-25.4**, que es **A-05** |

Los 5 restantes de los 59 son de **HU-39 y HU-40** (Rama Judicial), fuera del
alcance declarado de la propuesta.

### Hallazgos, y en qué acabaron

| | Qué era | Estado |
|---|---|---|
| **H-1** | El detalle por campo no llegaba a la pantalla — en las **14** copias del extractor de mensajes | ✅ corregido |
| **H-3** | «Historial de alertas» no mostraba las enviadas, y su subtítulo lo prometía | ✅ corregido |
| **H-2** | CA-04.4 (TLS) no se cumple en local | pendiente de despliegue (**D-23**, control 3) |
| **H-4** | Solo el módulo de usuarios tenía prueba de acceso cruzado | ✅ corregido |
| **H-5** | No se podía ajustar el esquema de alertas de un término | ✅ corregido |
| **H-6** | Un barrido interrumpido reenviaba lo que ya había salido | ✅ corregido |

### Cuatro veces me equivoqué yo, y no el sistema

Se anotan porque un recorrido en el que no se puede confiar no sirve para nada,
y porque las cuatro tienen la misma forma: **la comprobación miraba mal, y el
resultado parecía un defecto del sistema**.

1. **CA-11.2 «pasó» por un `grep` que acertó en el sitio equivocado.** Cumplía,
   pero por una razón distinta de la que mi comprobación afirmaba.
2. **CA-21.1 «falló»** porque el calendario devuelve los próximos 30 días y creé
   la audiencia a tres meses.
3. **CA-30.1 «falló»** porque lo busqué en `/programadas`, que por diseño solo
   devuelve las pendientes.
4. **CA-26.3 y CA-27.1 «fallaron»** porque mandé el campo `dias` y el contrato
   dice `diasAnticipacion`.

Y una quinta, distinta y peor: **CA-35.3 «pasó» estando hueco.** Busqué desde un
despacho con **cero procesos**: cero resultados no prueba aislamiento, prueba que
no hay nada. Rehecho contra un despacho con procesos propios y un fragmento que
sí coincidía con uno ajeno, entonces sí significó algo. Es el mismo error que la
primera medición de RNF-12, donde tres consultas devolvían vacío y salían con
tiempos excelentes.

## 5. Cómo se repite

Los guiones usan `curl` con **sesiones simultáneas separadas** —un fichero de
cookies por usuario—, que es lo único que permite comprobar de verdad el
aislamiento entre despachos: hace falta estar dentro de dos a la vez para
intentar el cruce.

Las credenciales están en `09-entorno-local.md`. El despacho **Consultorio
Jurídico Cantillo** y sus dos usuarios existen para esto: son el segundo
despacho contra el que se contrastan los criterios de aislamiento, y
`raso@cantillo.co` es el único abogado sin rol de administrador del entorno.
