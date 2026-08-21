# Fase 3 — Requisitos Funcionales (RF) y No Funcionales (RNF)

**Proyecto:** Iuris / SGPJ — Sistema de Gestión de Procesos Jurídicos
**Deriva de:** [`01-idea-y-definicion-de-negocio.md`](01-idea-y-definicion-de-negocio.md) · [`02-reglas-de-negocio.md`](02-reglas-de-negocio.md) · [`00-decisiones-y-trazabilidad.md`](00-decisiones-y-trazabilidad.md)
**Versión:** 1.2 · **Fecha:** 2026-08-20 · **Estado: CERRADA** — decisiones D-17 a D-20 incorporadas; ningún supuesto abierto

---

## 1. Método y criterios

### 1.1 Criterio de cantidad

Se aplicó el criterio fijado: **pocos requisitos, cada uno muy bien definido**. El resultado es **40 RF y 16 RNF** — 56 requisitos.

La contención se logró de tres formas, y conviene saber cuáles para no "descubrir" después requisitos que ya están dentro:

1. **Un requisito cubre el ciclo completo de una entidad** (crear, consultar, modificar) en lugar de partirse en cuatro requisitos CRUD. Partirlos habría dado más de 120 requisitos sin añadir una sola definición útil.
2. **Lo transversal se escribió una sola vez.** El aislamiento entre despachos no se repite en cada módulo: es un RNF único (RNF-01) que aplica a todo.
3. **Lo que es restricción de calidad fue a RNF, no a RF.** Un RF dice *qué hace*; un RNF dice *con qué cualidad*.

### 1.2 Estructura de cada requisito

| Campo | Qué contiene |
|---|---|
| **Código** | RF-nn / RNF-nn. Estable: no se renumera aunque se elimine uno. |
| **Requisito** | Qué debe hacer el sistema, en una frase verificable. |
| **Definición** | Precisión del alcance: qué incluye y qué **no**. Es el campo que evita la ambigüedad. |
| **Reglas** | RN de la Fase 2 que el requisito realiza. **Es la trazabilidad hacia atrás.** |
| **Origen** | **[P]** propuesta · **[D-nn]** decisión · **[R-nn]** riesgo tratado. |
| **Sprint** | Sprint de la propuesta **[P]** en que se implementa. |

### 1.3 Convención de códigos — importante para no confundirse

La propuesta trae sus propios códigos RF01–RF05 y RNF01–RNF03. **No son los de este documento.** Para distinguirlos:

| Notación | Significa |
|---|---|
| **P-RF01 … P-RF05** | Los cinco enunciados de la propuesta **[P]** |
| **P-RNF01 … P-RNF03** | Los tres enunciados no funcionales de la propuesta **[P]** |
| **RF-01 … RF-40** | Los requisitos funcionales de ingeniería de este documento |
| **RNF-01 … RNF-16** | Los requisitos no funcionales de ingeniería de este documento |

Un enunciado de la propuesta se desarrolla en varios requisitos de ingeniería. Por ejemplo, P-RF03 ("calendario de audiencias con alertas") se convierte en RF-18, RF-19, RF-22 y RF-23.

### 1.4 Hallazgo de planificación que debe conocerse antes de leer

Al mapear los requisitos contra los sprints de la propuesta apareció esto:

> **El Sprint 1 de la propuesta —"Registro de clientes y creación de expedientes"— da por supuesto que ya existen autenticación, despachos, usuarios y roles. Ninguno de esos está enumerado en la propuesta, y sin ellos no se puede registrar un cliente: no habría a qué despacho pertenece ni quién lo registra.**

No es un error de la propuesta: es lo que ocurre siempre cuando se enumeran funcionalidades de negocio sin la base que las sostiene. Se resuelve así:

- Los requisitos de base (**RF-01 a RF-08**) se marcan **Sprint 1**, junto a lo que la propuesta pide para ese sprint.
- **Consecuencia real: el Sprint 1 es el más cargado de los cuatro**, y dura una semana **[P]**. Es un riesgo de cronograma que queda registrado como **R-09** y que debe conocerse antes del Sprint Planning, no durante.

---

## 2. Módulos funcionales

Los 40 RF se agrupan en 12 módulos. La agrupación no es decorativa: será la base de los componentes en la Fase 5 y de la arquitectura en la Fase 6.

| Módulo | Nombre | RF | Sprint |
|---|---|---|---|
| **M1** | Plataforma y despachos | RF-01 → RF-03 | 1 |
| **M2** | Seguridad, usuarios y roles | RF-04 → RF-08, RF-39, RF-40 | 1 |
| **M3** | Clientes | RF-09 → RF-10 | 1 |
| **M4** | Procesos y expedientes | RF-11 → RF-14 | 1 |
| **M5** | Documentos, actuaciones y notas | RF-15 → RF-18, RF-38 | 2 |
| **M6** | Audiencias | RF-19 → RF-20 | 3 |
| **M7** | Términos judiciales | RF-21 → RF-23 | 3 |
| **M8** | Motor de alertas ★ | RF-24 → RF-27, RF-37 | 3 |
| **M9** | Portal del cliente | RF-28 → RF-30 | 4 |
| **M10** | Búsqueda y reportes | RF-31 → RF-32 | 4 |
| **M11** | Administración del despacho | RF-33 → RF-34 | 2–3 |
| **M12** | Integración Rama Judicial | RF-35 → RF-36 | posterior |

★ **M8 es el módulo que justifica el proyecto.** Es el único que opera sin que ningún usuario lo invoque.

---

## 3. Requisitos Funcionales

### M1 · Plataforma y despachos

| Código | Requisito | Definición | Reglas | Origen | Sprint |
|---|---|---|---|---|---|
| **RF-01** | El sistema debe permitir al Administrador de Plataforma **registrar un despacho** y consultar y modificar sus datos. | Incluye datos de identificación del despacho y la creación de su primer usuario administrador. **No incluye** planes, precios ni datos de facturación. | RN-01, RN-13 | **[D-01]** | 1 |
| **RF-02** | El sistema debe permitir al Administrador de Plataforma **cambiar el estado de un despacho** entre *activo* e *inactivo*. | Son los dos únicos estados. El cambio es manual y refleja una gestión comercial externa al sistema. **No existe eliminación de despachos.** | RN-03, RN-05 | **[D-06] [D-10]** | 1 |
| **RF-03** | El sistema debe **impedir toda operación** a los usuarios de un despacho inactivo, conservando íntegros sus datos. | Aplica a **todos** sus usuarios, clientes incluidos. Al usuario se le informa que su despacho está inactivo; no se le muestra un error genérico. Los datos no se alteran ni se eliminan. | RN-04, RN-05, RN-52 | **[D-10]** | 1 |

### M2 · Seguridad, usuarios y roles

| Código | Requisito | Definición | Reglas | Origen | Sprint |
|---|---|---|---|---|---|
| **RF-04** | El sistema debe **autenticar** a todo usuario antes de permitir cualquier operación. | Aplica por igual a administradores, abogados y clientes. Sin excepciones ni áreas públicas con datos. | RN-40 | **[P]** P-RNF03 | 1 |
| **RF-05** | El sistema debe permitir al Administrador de Despacho **gestionar los usuarios de su despacho** y asignarles uno o varios roles. | La asignación es **multi-rol**: un usuario puede ser Administrador de Despacho y Abogado a la vez. Solo alcanza a usuarios de su propio despacho. | RN-07, RN-08, RN-09, RN-13 | **[D-07] [D-11] [D-14]** | 1 |
| **RF-06** | El sistema debe **autorizar cada operación** según la unión de los roles del usuario. | Los permisos se calculan por unión, nunca por un rol único. Un usuario con dos roles tiene los permisos de ambos. | RN-08 | **[D-07]** | 1 |
| **RF-07** | El sistema debe permitir al despacho **habilitar el acceso al portal a un cliente** registrado. | El acceso lo habilita el despacho. **No existe autorregistro de clientes.** El cliente recibe su acceso; no lo solicita por sí mismo. | RN-43, RN-11 | **[P]** P-RNF03, **[D-15]** | 4 |
| **RF-08** | El sistema debe **registrar en bitácora de auditoría** todo acceso al contenido de un expediente. | Registra quién, qué expediente y cuándo. Es consulta, no solo modificación: el acceso de lectura es precisamente lo que interesa auditar. La bitácora **no se puede alterar ni borrar** desde la aplicación. | RN-12 | **[D-11]** | 2 |
| **RF-39** | El sistema debe permitir a **todo usuario autenticado cambiar su propia contraseña**, exigiéndole la actual. | Exigir la actual es lo que impide que una sesión abandonada en un equipo compartido se convierta en el secuestro de la cuenta. La contraseña anterior deja de servir en el mismo momento. Aplica a los cuatro roles, cliente incluido. | RN-53, RN-54 | **[D-24]** | 1 |
| **RF-40** | El sistema debe permitir al **Administrador de Despacho restablecer la contraseña** de un usuario de su despacho, incluidos los clientes con acceso al portal, **sin conocer la anterior**. | Es la vía de vuelta para quien olvidó la suya: sin ella, la única salida sería desactivar la cuenta y crear otra, perdiendo su historial (RF-38). El administrador **fija** una nueva; nunca lee la anterior, porque no es legible (RNF-05). Solo alcanza a usuarios de su propio despacho. | RN-53, RN-54, RN-09, RN-13 | **[D-24]** | 1 |

### M3 · Clientes

| Código | Requisito | Definición | Reglas | Origen | Sprint |
|---|---|---|---|---|---|
| **RF-09** | El sistema debe permitir **registrar un cliente** con sus datos personales y el tipo de proceso, y consultarlo y modificarlo. | Enunciado directo de P-RF01. El cliente pertenece al despacho que lo registra. | RN-01, RN-14 | **[P]** P-RF01 | 1 |
| **RF-10** | El sistema debe permitir **asociar varios procesos a un mismo cliente**, manteniendo un único titular por proceso. | Un cliente, muchos procesos. Un proceso, **un solo titular** — sin esa unicidad el portal no podría determinar a quién mostrar el expediente. | RN-15 | **[P]** P-RF01 | 1 |

### M4 · Procesos y expedientes

| Código | Requisito | Definición | Reglas | Origen | Sprint |
|---|---|---|---|---|---|
| **RF-11** | El sistema debe permitir **crear un proceso** con radicado, juzgado, tipo de proceso, estado procesal, cliente titular y abogado responsable. | Los seis datos son **obligatorios**. El **juzgado se selecciona del catálogo del despacho**, no se escribe libre: con texto libre la búsqueda de P-RNF02 devolvería resultados incompletos. | RN-16, RN-31, RN-44 | **[P]** P-RF01, P-RNF02 · **[D-17]** | 1 |
| **RF-12** | El sistema debe **rechazar un radicado duplicado dentro del mismo despacho**. | La unicidad es **por despacho, no global**: dos despachos distintos pueden llevar el mismo proceso representando a partes diferentes. | RN-17 | **[P]** P-RNF02 | 1 |
| **RF-13** | El sistema debe **crear automáticamente el expediente digital** al crear el proceso. | Relación uno a uno. No hay acción de usuario: no puede existir un proceso sin expediente donde guardar sus piezas. | RN-18 | **[P]** P-RF02 | 1 |
| **RF-14** | El sistema debe permitir **cambiar el estado procesal** de un proceso, incluido su archivo. | Archivar es un cambio de estado, **no una eliminación**: los procesos no se borran nunca. Al pasar a *Archivado*, el proceso deja de generar alertas (ver RF-27). | RN-19, RN-20 | **[P]** P-RF05 | 1 |

### M5 · Documentos, actuaciones y notas

| Código | Requisito | Definición | Reglas | Origen | Sprint |
|---|---|---|---|---|---|
| **RF-15** | El sistema debe permitir **cargar documentos** al expediente, clasificados por tipo, y descargarlos. | Se almacenan cifrados (RNF-04). Quedan asociados al expediente, no al proceso suelto. | RN-21, RN-22 | **[P]** P-RF02, P-RNF01 | 2 |
| **RF-16** | El sistema debe **advertir al abogado, en el momento de cargar un documento, que quedará visible para el cliente de inmediato.** | La advertencia va **en la pantalla de carga**, no en un manual. Es el único punto donde el abogado puede rectificar antes de exponer algo que no quería mostrar. | RN-25, RN-26 | **[D-12]** | 2 |
| **RF-17** | El sistema debe permitir **registrar actuaciones** con fecha y tipo, y consultarlas en orden cronológico. | La fecha es obligatoria: sin ella no hay historial ni punto de partida para un término. | RN-23 | **[P]** P-RF02 | 2 |
| **RF-18** | El sistema debe permitir **registrar notas internas** en el expediente, visibles solo para los usuarios del despacho. | **Nunca** aparecen en el portal del cliente, bajo ninguna circunstancia. Es el contrapeso de RF-16: lo que no debe verse, va aquí. | RN-24 | **[D-09]** | 2 |

| **RF-38** | El sistema debe registrar en **cada pieza del expediente** (documento, actuación o nota) **quién la creó y cuándo**, y mostrarlo al consultarla. | Es lo que convierte al expediente digital en respaldo demostrable de la gestión del despacho. El dato es automático, no lo escribe el usuario. | RN-50 | **[P]** P-RF02 | 2 |

> **RF-15 a RF-18 no se eliminan.** Ninguna pieza del expediente admite borrado: una pieza errónea se corrige registrando otra que la rectifica (RN-27). Esto es deliberado — el expediente es el respaldo del despacho ante una reclamación.

### M6 · Audiencias

| Código | Requisito | Definición | Reglas | Origen | Sprint |
|---|---|---|---|---|---|
| **RF-19** | El sistema debe permitir **registrar una audiencia** de un proceso con **fecha y hora**, y consultarla y modificarla. | **La hora es obligatoria.** Sin ella no puede calcularse el instante de las alertas de 48h y 24h que exige P-RF03. | RN-28 | **[P]** P-RF03 | 3 |
| **RF-20** | El sistema debe presentar un **calendario de audiencias** del despacho. | Enunciado directo de P-RF03. Es la vista; las alertas son otra cosa (M8). El calendario **no sustituye** a la alerta: es respaldo visual. | RN-28 | **[P]** P-RF03 | 3 |

### M7 · Términos judiciales

| Código | Requisito | Definición | Reglas | Origen | Sprint |
|---|---|---|---|---|---|
| **RF-21** | El sistema debe permitir **registrar un término judicial** con su fecha de vencimiento, indicada por el abogado. | **El sistema no calcula la fecha: la recibe.** El cómputo del plazo es responsabilidad profesional del abogado. Esta frontera no se cruza en ninguna versión del producto. | RN-35, RN-36 | **[P]** P-RF04 | 3 |
| **RF-22** | El sistema debe permitir **cambiar el estado de un término** entre *pendiente*, *cumplido* y *vencido*. | Sin estado explícito no se distingue un término atendido de uno olvidado, y las alertas seguirían sonando sobre algo ya resuelto. | RN-38 | **[P]** P-RF04 | 3 |
| **RF-23** | El sistema debe presentar un **panel de términos próximos a vencer y vencidos**. | Es el **respaldo visual** de las alertas: si el correo falla, el vencimiento sigue estando visible al iniciar sesión. Trata R-02 por una segunda vía. | RN-37, RN-38 | **[P]** P-RF04, **[R-02]** | 3 |

### M8 · Motor de alertas ★ *núcleo del sistema*

| Código | Requisito | Definición | Reglas | Origen | Sprint |
|---|---|---|---|---|---|
| **RF-24** | El sistema debe **generar y enviar automáticamente** las alertas de audiencias y términos, **sin intervención de ningún usuario**. | Es el requisito que justifica el proyecto. Ningún usuario lo invoca: el sistema evalúa por sí mismo qué vence y avisa. El destinatario es el **abogado responsable** del proceso, por **correo electrónico**. | RN-30, RN-31, RN-32 | **[P]** P-RF03, P-RF04, **[D-03]** | 3 |
| **RF-25** | El sistema debe emitir, por cada audiencia, **como mínimo las tres alertas obligatorias**: 48 horas antes, 24 horas antes y el día de la audiencia. | Las tres están fijadas literalmente por P-RF03: son piso, **no configuración**. El despacho puede añadir alertas adicionales, nunca quitar estas tres. | RN-29 | **[P]** P-RF03, **[D-16]** | 3 |
| **RF-26** | El sistema debe emitir las alertas de cada término **según el esquema configurado por el despacho**, garantizando **siempre al menos una alerta anticipada** no desactivable. | ★ El esquema define cuántas y con cuánta anticipación. **No admite el valor cero.** La configuración decide *cuántas más* y *cuándo*, **nunca *si*** — sin este límite, un despacho podría apagar su propia vigilancia sin advertirlo. | RN-37, RN-37a, RN-37b | **[P]** P-RF04, **[D-16] [R-08]** | 3 |
| **RF-27** | El sistema **no debe emitir alertas** de procesos archivados ni de términos ya cumplidos. | Evita el ruido. Un abogado que recibe alertas irrelevantes empieza a ignorarlas — y ese es el modo silencioso en que muere la utilidad del sistema. | RN-20, RN-39 | **[P]** P-RF04, **[R-05]** | 3 |
| **RF-37** | El sistema debe emitir un **aviso final por correo** al despacho cuando este pasa a **inactivo**, informando que la vigilancia de audiencias y términos queda suspendida. | Es una **notificación de corte**, no una funcionalidad de la plataforma: por eso es compatible con el bloqueo total de RF-03. Sin ella, el despacho seguiría confiando en un sistema que ya dejó de avisarle. | RN-51 | **[D-10] [R-07]** | 3 |

### M9 · Portal del cliente

| Código | Requisito | Definición | Reglas | Origen | Sprint |
|---|---|---|---|---|---|
| **RF-28** | El sistema debe permitir al cliente **consultar sus procesos** en el portal, en modo **solo lectura**. | El cliente no crea, modifica ni elimina nada. El portal informa; no permite intervenir en el proceso. | RN-11, RN-40 | **[P]** P-RNF03 | 4 |
| **RF-29** | El portal debe mostrar al cliente **datos del proceso, estado procesal, actuaciones, documentos y audiencias programadas**. | Muestra **todos** los documentos y actuaciones, sin selección pieza por pieza. La visibilidad depende del **tipo** de pieza, no de una marca individual. | RN-25, RN-42 | **[D-12]** | 4 |
| **RF-30** | El portal **no debe mostrar notas internas** en ninguna circunstancia. | Requisito redactado en negativo de forma deliberada: es una **prohibición verificable**, y se probará explícitamente. | RN-24, RN-42 | **[D-09] [R-06]** | 4 |

### M10 · Búsqueda y reportes

| Código | Requisito | Definición | Reglas | Origen | Sprint |
|---|---|---|---|---|---|
| **RF-31** | El sistema debe permitir **buscar procesos por radicado, cliente, juzgado o tipo de proceso**. | Los cuatro criterios son los de P-RNF02, literales. Deben poder combinarse. | RN-44 | **[P]** P-RNF02 · **[D-20]** | 4 |
| **RF-32** | El sistema debe generar **reportes de procesos activos, archivados y por estado procesal**. | Enunciado directo de P-RF05. Fija *Activo* y *Archivado* como estados obligatorios del catálogo. | RN-46, RN-06a | **[P]** P-RF05 | 4 |

### M11 · Administración del despacho

| Código | Requisito | Definición | Reglas | Origen | Sprint |
|---|---|---|---|---|---|
| **RF-33** | El sistema debe permitir al Administrador de Despacho **gestionar los cinco catálogos de su despacho**: estados procesales, tipos de proceso, tipos de documento, tipos de actuación y **juzgados**. | Puede añadir, renombrar y **desactivar** valores. **No puede eliminar** un valor en uso, ni desactivar los estados *Activo* y *Archivado*. El catálogo de juzgados empieza vacío y se construye con el uso. | RN-06, RN-06a, RN-06b | **[D-13] [D-17]** | 2 |
| **RF-34** | El sistema debe permitir al Administrador de Despacho **configurar el esquema de alertas de términos**: cuántas y con cuánta anticipación. | El sistema **rechaza** una configuración de cero alertas. Se propone definirlo por despacho, con ajuste puntual por término *(confirmación C-2)*. | RN-37a, RN-37b | **[D-16]** | 3 |

### M12 · Integración Rama Judicial *(ampliación fuera de la propuesta)*

| Código | Requisito | Definición | Reglas | Origen | Sprint |
|---|---|---|---|---|---|
| **RF-35** | El sistema debe permitir **consultar por radicado** las actuaciones publicadas del proceso en el servicio de la Rama Judicial. | La consulta la inicia el abogado. La información se presenta **siempre identificada como no oficial**, de apoyo al seguimiento. | RN-47, RN-48 | **[D-04]** | posterior |
| **RF-36** | El sistema debe **seguir operando con normalidad si el servicio externo no está disponible**, permitiendo el registro manual de actuaciones. | Degradación limpia. **Ningún requisito de la propuesta (RF-01 a RF-34) puede depender de esta integración.** | RN-49 | **[D-04] [R-01]** | posterior |

---

## 4. Requisitos No Funcionales

### 4.1 Seguridad y confidencialidad — *categoría crítica*

| Código | Requisito | Criterio de verificación | Reglas | Origen |
|---|---|---|---|---|
| **RNF-01** ★ | Ningún usuario debe poder acceder a datos de un despacho distinto del suyo, por ninguna vía: interfaz, búsqueda, reporte, API o manipulación de identificadores. | Prueba explícita de intento de acceso cruzado entre dos despachos en **cada** módulo que expone datos. El resultado debe ser negación, no un resultado vacío ambiguo. | RN-02, RN-45 | **[D-01] [R-04]** |
| **RNF-02** | Toda operación debe verificar el **estado del despacho** en un **único punto de control** transversal. | La verificación no se implementa repetida en cada funcionalidad: repartirla garantiza que alguna se olvide. Se prueba una funcionalidad de cada módulo con despacho inactivo. | RN-04 | **[D-10]** |
| **RNF-03** | La autorización debe evaluarse por la **unión de los roles** del usuario, nunca por un rol único. | Caso de prueba obligatorio: usuario con rol Administrador de Despacho **y** Abogado simultáneamente — el abogado independiente. | RN-08 | **[D-07]** |
| **RNF-04** | Los documentos del expediente deben almacenarse **cifrados**. | Enunciado literal de P-RNF01. El algoritmo y la gestión de claves se especifican en la Fase 6 (arquitectura). | RN-22 | **[P]** P-RNF01 |
| **RNF-05** | Las credenciales deben almacenarse mediante **función de hash con salt**, nunca en texto plano ni cifrado reversible. | Ninguna consulta al almacén de datos debe poder devolver una contraseña legible. | RN-40 | **[P]** P-RNF03 |
| **RNF-06** | La comunicación entre cliente y servidor debe ir **cifrada en tránsito**. | Sin excepciones, incluido el portal del cliente. | RN-40 | **[P]** P-RNF03 |
| **RNF-07** | La bitácora de auditoría debe ser **inalterable desde la aplicación**: no puede modificarse ni eliminarse por ningún rol. | Una bitácora que el auditado puede editar no sirve como evidencia. | RN-12 | **[D-11]** |

### 4.2 Confiabilidad — *categoría crítica: es la razón de ser del sistema*

| Código | Requisito | Criterio de verificación | Reglas | Origen |
|---|---|---|---|---|
| **RNF-08** ★ | Ninguna alerta debe perderse en silencio. Todo envío fallido debe **reintentarse** y, si sigue fallando, quedar **visible dentro del sistema** para el despacho. | Prueba con el servicio de correo caído: la alerta debe reintentarse y aparecer marcada como fallida en la aplicación. **Nunca desaparecer.** | RN-34 | **[R-02]** |
| **RNF-09** | Toda alerta emitida debe quedar **registrada** con fecha, destinatario y resultado del envío, de forma consultable. | Ante una reclamación, ese registro es la defensa del despacho y la del producto. Debe poder demostrarse que el sistema avisó. | RN-33 | **[R-02]** |
| **RNF-10** | El sistema debe emitir cada alerta **una sola vez** por evento y momento programado. | Ni duplicados —que erosionan la confianza— ni omisiones. Verificable ante reinicios del servicio de alertas durante la ventana de envío. | RN-29, RN-30 | **[P]** P-RF03 |
| **RNF-11** | Las alertas deben emitirse dentro de una **tolerancia máxima de 15 minutos** respecto de su momento programado, con el planificador ejecutándose **cada 5 minutos**. | ★ Con tolerancia de 1 hora y planificador horario, la alerta de 24h podría salir a las 23h05 — perdería una hora de margen justo en el aviso que más importa. El costo de bajarla es nulo: la tarea casi siempre encontrará cero alertas pendientes. | RN-29, RN-37 | **[P]** P-RF03, **[D-19]** |

### 4.3 Rendimiento y capacidad

> **Cierre del supuesto S-03 → [D-19].** La propuesta no da cifras de volumen. Las siguientes se adoptan como **línea base revisable** y se convierten en pruebas automatizadas. No hay un despacho real con volumen medido a quien validárselas; lo relevante no es que sean exactas, sino que sean **verificables**.

| Código | Requisito | Criterio de verificación | Origen |
|---|---|---|---|
| **RNF-12** | Las consultas y búsquedas habituales deben responder en **menos de 3 segundos** con el volumen objetivo. | Volumen objetivo: **50 despachos**, **500 procesos por despacho**, **50 documentos por expediente**. Línea base adoptada. | **[D-19]** |
| **RNF-13** | El sistema debe admitir documentos de hasta **20 MB** por archivo. | Cubre PDF escaneados de expedientes extensos. Línea base adoptada. | **[D-19]** |

### 4.4 Disponibilidad, respaldo y conservación

> **Cierre del supuesto S-04 → [D-19].**

| Código | Requisito | Criterio de verificación | Reglas | Origen |
|---|---|---|---|---|
| **RNF-14** | Debe existir **respaldo diario** de la base de datos y del almacén de documentos, con procedimiento de restauración **probado**. | Un respaldo que nunca se ha restaurado no es un respaldo. La prueba de restauración es parte del requisito. | RN-52 | **[D-19]** |
| **RNF-15** | La información de un despacho debe **conservarse íntegra** mientras el despacho exista, esté activo o inactivo. | Verificable desactivando y reactivando un despacho: los datos deben quedar exactamente como estaban. | RN-05, RN-52 | **[D-10]** |

### 4.5 Usabilidad

| Código | Requisito | Criterio de verificación | Reglas | Origen |
|---|---|---|---|---|
| **RNF-16** | El registro de una **audiencia** o de un **término** debe completarse en **no más de 5 campos obligatorios y una sola pantalla**. **[S]** | ★ **No es un requisito cosmético.** Si registrar en el sistema cuesta más que anotar en la agenda de papel, el abogado no lo usa, el sistema queda desactualizado y sus alertas dejan de ser fiables. Es el tratamiento del riesgo de adopción. | RN-28, RN-35 | **[R-05]** |

---

## 5. Trazabilidad

### 5.1 Cobertura de la propuesta — verificación de que nada quedó fuera

| Enunciado **[P]** | Requisitos que lo desarrollan | ¿Cubierto? |
|---|---|---|
| **P-RF01** · Registro de clientes | RF-09, RF-10, RF-11 | ✅ |
| **P-RF02** · Expediente digital | RF-13, RF-15, RF-17, RF-18, RF-38 | ✅ |
| **P-RF03** · Calendario y alertas 48/24/día | RF-19, RF-20, RF-24, RF-25 · RNF-10, RNF-11 | ✅ |
| **P-RF04** · Control de términos | RF-21, RF-22, RF-23, RF-26, RF-27 | ✅ |
| **P-RF05** · Reportes | RF-14, RF-32 | ✅ |
| **P-RNF01** · Cifrado de documentos | RNF-04 | ✅ |
| **P-RNF02** · Búsqueda | RF-11, RF-12, RF-31 | ✅ |
| **P-RNF03** · Portal restringido del cliente | RF-04, RF-07, RF-28, RF-29, RF-30 · RNF-05, RNF-06 | ✅ |

**Cobertura: 8 de 8.**

### 5.2 Requisitos que NO vienen de la propuesta

Transparencia sobre el alcance añadido:

| Origen | Requisitos | Motivo |
|---|---|---|
| **[D-01]** multi-tenencia | RF-01, RF-05 · RNF-01 | La plataforma atiende varios despachos |
| **[D-06] [D-10]** estado del despacho | RF-02, RF-03, RF-37 · RNF-02, RNF-15 | La monetización externa deja esta huella |
| **[D-07] [D-11]** roles | RF-05, RF-06, RF-08 · RNF-03, RNF-07 | Roles acumulables y auditoría |
| **[D-12] [D-09]** visibilidad | RF-16, RF-30 | Transparencia total salvo notas |
| **[D-13] [D-16]** administración | RF-33, RF-34 | Catálogos y alertas configurables |
| **[D-04]** Rama Judicial | RF-35, RF-36 | **Ampliación fuera de la propuesta** |
| **[R-02] [R-05]** riesgos | RF-23, RF-27 · RNF-08, RNF-09, RNF-16 | Protegen la razón de ser del sistema |
| **[D-24]** contraseñas | RF-39, RF-40 | Detectado durante la construcción |

**28 de los 56 requisitos no tienen origen en la propuesta.** No es desviación: es la consecuencia trazable de las decisiones tomadas y de los riesgos identificados. Cada uno tiene su origen documentado, y **solo 2 de los 28 (RF-35 y RF-36) amplían realmente el alcance funcional** — los demás son la base que la propuesta daba por supuesta o la protección de los riesgos que ella misma señala.

> **Corrección de una cifra.** Esta línea decía «23 de los 52» desde la Fase 3. Al recontar para incorporar RF-39 y RF-40 se comprobó que ya no cuadraba: la Fase 4 había añadido RF-37 y RF-38 sin actualizarla, y el recuento real era **26 de 54**. La cifra se corrige aquí y se registra el desajuste en lugar de reescribirla en silencio — un documento que se corrige a sí mismo sin decirlo deja de servir como trazabilidad.

### 5.3 Los requisitos que no se negocian

Si hubiera que recortar alcance, estos **no** entran en la conversación:

| Requisito | Por qué |
|---|---|
| **RNF-01** — aislamiento entre despachos | Su fallo es una fuga de información bajo reserva profesional |
| **RNF-08** — ninguna alerta se pierde en silencio | Su fallo es exactamente el daño que el sistema existe para evitar |
| **RF-26** — mínimo una alerta no desactivable | Impide que la configuración apague la vigilancia |
| **RF-21** — el sistema no calcula plazos | Cruzar esa frontera traslada responsabilidad profesional al software |
| **RF-30** — notas nunca visibles al cliente | Su fallo daña la relación abogado-cliente |

---

## 6. Distribución por sprint y riesgo de cronograma

### 6.1 Distribución tras la redistribución [D-20]

| Sprint **[P]** | Alcance de la propuesta | Requisitos | Total |
|---|---|---|---|
| **1** | Registro de clientes y creación de expedientes | RF-01, RF-04 → RF-06, RF-09 → RF-14, RF-39, RF-40 · RNF-01, RNF-03, RNF-05, RNF-06 | **15** |
| **2** | Documentación y actuaciones procesales | RF-02, RF-03, RF-08, RF-15 → RF-20, RF-33, RF-38 · RNF-02, RNF-04, RNF-07, RNF-13 | **15** |
| **3** | Calendario de audiencias y control de términos | RF-21 → RF-27, RF-34, RF-37 · RNF-08 → RNF-11, RNF-16 | **13** |
| **4** | Portal del cliente y reportes | RF-07, RF-28 → RF-32 · RNF-12, RNF-14, RNF-15 | **9** |
| *posterior* | *(fuera de la propuesta)* | RF-35, RF-36 | **2** |

### 6.2 R-09 y su tratamiento

**Distribución original y problema detectado.** Al mapear los requisitos contra los sprints aparecieron dos concentraciones: Sprint 1 con **18** requisitos (la base que la propuesta daba por supuesta) y Sprint 3 con **16** (el núcleo del producto y cuatro de los cinco requisitos críticos). Los sprints duran **una semana** **[P]**.

**Principio de la redistribución [D-20]:** *el Sprint 3 es el único irreductible* — contiene el motor de alertas, que es la razón de ser del producto. Todo lo demás puede moverse; eso no.

| Requisitos movidos | De → a | Por qué se puede |
|---|---|---|
| RF-02, RF-03 · estado del despacho | 1 → 2 | No hace falta poder desactivar despachos para registrar clientes |
| RF-31 · búsqueda completa | 1 → 4 | Ya estaba repartido "base en 1, refinamiento en 4"; queda entero en el 4 |
| **RF-19, RF-20 · audiencia y calendario** | 3 → 2 | **Movimiento clave.** Registrar una audiencia es un formulario que no depende del motor de alertas |

**Resultado:** 18 → **13** · 10 → **15** · 16 → **13** · 8 → **9**.

El Sprint 2 se carga, pero es el más seguro: formularios y almacenamiento, sin lógica crítica. **Se carga el sprint barato para descargar el caro.**

**Dos condiciones que deben respetarse:**
- Mover RF-19 al Sprint 2 estira su título *("Documentación y actuaciones procesales")*. **Requiere el visto bueno del Product Owner**; no se aplica unilateralmente.
- Si el PO no acepta mover nada, la alternativa es aceptar R-09 y recortar **dentro** del Sprint 3: RF-20 se entrega como lista de próximas audiencias en lugar de calendario completo. **Nada del motor de alertas se recorta.**

---

## 7. Cierre de la fase

### Supuestos cerrados

| ID | Desenlace |
|---|---|
| **S-03** volumen | Cuantificado en RNF-12 y RNF-13 · **adoptado como línea base → D-19** |
| **S-04** retención y respaldo | Cuantificado en RNF-14 y RNF-15 · **adoptado como línea base → D-19** |

### Pendientes — todos cerrados

| # | Punto | Desenlace |
|---|---|---|
| 1 | RNF-12 volumen objetivo | Adoptado: 50 despachos · 500 procesos/despacho · < 3 s · **D-19** |
| 2 | RNF-13 tamaño máximo de documento | Adoptado: 20 MB · **D-19** |
| 3 | RNF-11 puntualidad de la alerta | **Corregido**: de 1 hora a **15 minutos**, planificador cada 5 min · **D-19** |
| 4 | RNF-14 respaldo | Adoptado: diario con restauración probada · **D-19** |
| 5 | R-09 concentración de sprints | **Tratado** con la redistribución de §6.2 · **D-20** |

Las cifras se adoptan como **línea base revisable** y se convierten en pruebas automatizadas: si cambian, cambia una constante de la prueba, no la estructura.

### Qué habilita la Fase 4

Cada **historia de usuario** nacerá de uno o varios de estos requisitos, y sus **criterios de aceptación** saldrán del campo *Definición* y del *Criterio de verificación*. La cadena quedará completa:

```
Propuesta [P] → Decisión [D] → Regla RN → Requisito RF/RNF → Historia de Usuario
```

Los actores de la Fase 1 dan los "Como…", los módulos M1–M12 dan la agrupación en épicas, y los requisitos críticos (§5.3) marcan qué historias necesitan criterios de aceptación negativos — probar que algo **no** ocurre.
