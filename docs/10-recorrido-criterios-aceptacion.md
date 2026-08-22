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
| **CA-11.2** | Omitir un campo obligatorio se impide **e indica cuál** | ⚠ | El backend cumple: devuelve `"errores":{"juzgadoId":"Debe indicar el juzgado."}`. **La interfaz no lo muestra** — ver hallazgo H-1 |
| **CA-11.3** | El destinatario de la alerta es el abogado responsable | ✅ | Proceso con responsable «Admin Cat» → alerta con `destinatario: "Admin Cat"`, `correoDestinatario: admin.cat@despacho.co` |
| **CA-11.4** | El juzgado se elige del catálogo, no se escribe libre | ✅ | `proceso.juzgado_id` es una referencia; **no existe ninguna columna de texto** para juzgado |
| **CA-12.2** | El mismo radicado **sí** se permite en otro despacho | ✅ | El radicado `41001 31 03 001 2026 00777 00` existe en Despacho Catálogos y se aceptó en Cantillo (proceso 500). La unicidad es por despacho (RN-17) |
| **CA-14.1** | El cambio de estado se refleja en los reportes | ✅ | Al archivar el proceso 499: `Activo 2→1`, `Archivado 0→1` en `/api/reportes/procesos-por-estado` |

---

## 3. Hallazgos

### H-1 · El detalle por campo no llega a la pantalla

**Dónde.** `nuevo-proceso.ts` y `despachos.ts` (las dos pantallas nuevas).

**Qué pasa.** Cuando falta un campo obligatorio, el backend responde:

```json
{"detail":"Revise los datos enviados.",
 "errores":{"juzgadoId":"Debe indicar el juzgado."}}
```

El extractor de mensajes lee solo `detail`, así que el usuario vería «Revise los
datos enviados» **sin saber cuál campo revisar**. El dato útil viaja y se tira.

**Gravedad: baja, hoy.** Los formularios validan antes de enviar, así que en la
práctica no se llega a ese estado. Pero es una red de seguridad que no atrapa
nada, y CA-11.2 exige explícitamente que el sistema *indique cuál falta*.

**Estado:** abierto.

### H-2 · CA-04.4 no se puede cumplir en local, y eso ya estaba decidido

No es un defecto nuevo: es el **control 3 de la lista de D-23**, diferido junto
con otros cinco hasta el despliegue. Se anota aquí porque un recorrido de
criterios que lo marcara «cumple» estaría mintiendo, y uno que lo marcara
«defecto» estaría contando dos veces algo ya registrado.

**Estado:** pendiente de despliegue, con D-23 como referencia.

---

## 4. Lo que falta recorrer

Quedan **40 criterios** de EP4 a EP11: expediente digital, audiencias y
términos, motor de alertas, portal del cliente, búsqueda y reportes, y las
garantías transversales.

## 5. Cómo se repite

Los guiones usan `curl` con **sesiones simultáneas separadas** —un fichero de
cookies por usuario—, que es lo único que permite comprobar de verdad el
aislamiento entre despachos: hace falta estar dentro de dos a la vez para
intentar el cruce.

Las credenciales están en `09-entorno-local.md`. El despacho **Consultorio
Jurídico Cantillo** y sus dos usuarios existen para esto: son el segundo
despacho contra el que se contrastan los criterios de aislamiento, y
`raso@cantillo.co` es el único abogado sin rol de administrador del entorno.
