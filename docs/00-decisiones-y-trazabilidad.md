# Registro de Decisiones y Trazabilidad de Origen

**Proyecto:** Sistema de Gestión de Procesos Jurídicos para Consultorios de Abogados
**Fuente base:** `24_propuesta.pdf` — Propuesta 24, Competencia 220501094 (Propuestas Técnicas de Software), pág. 27
**Fecha de apertura:** 2026-08-20
**Rol asumido:** Analista y Desarrollador de Software

---

## 1. Propósito de este documento

Toda la documentación de este proyecto (Idea de Negocio → RN → RF/RNF → Historias de Usuario → Diagramas → Arquitectura) debe poder rastrearse hasta su origen. Este registro distingue tres orígenes posibles para cualquier afirmación del proyecto:

| Marca | Origen | Significado |
|---|---|---|
| **[P]** | Propuesta | Está escrito literalmente en `24_propuesta.pdf`. Es verdad de proyecto, no se discute. |
| **[D]** | Decisión | Vacío de la propuesta resuelto por decisión explícita del equipo con el interesado. Queda registrado abajo. |
| **[S]** | Supuesto | Aún no decidido. Se trabaja bajo un supuesto declarado y debe validarse antes de cerrar la fase que lo consume. |

Ninguna afirmación de la documentación puede carecer de una de estas tres marcas.

---

## 2. Contenido literal de la propuesta [P]

Transcripción fiel del PDF, sin interpretación:

**Título:** PROPUESTA 24: SISTEMA DE GESTIÓN DE PROCESOS JURÍDICOS PARA CONSULTORIOS DE ABOGADOS
**Sector:** Jurídico / Legal

**NECESIDAD**
> Los consultorios jurídicos y abogados independientes de Neiva gestionan sus procesos judiciales con carpetas físicas, agendas personales y recordatorios manuales. Las fechas de audiencias se olvidan, los términos judiciales vencen por falta de seguimiento y la documentación de los expedientes se desorganiza, afectando la calidad del servicio al cliente y generando riesgos de sanciones disciplinarias.

**OBJETIVO**
> Desarrollar un sistema web de gestión de procesos jurídicos que administre el expediente digital de cada caso, controle fechas de audiencias y términos, gestione la documentación asociada y genere alertas automáticas de vencimiento para el abogado.

**REQUERIMIENTOS (los de la propuesta, tal cual)**

| Código | Enunciado literal |
|---|---|
| RF01 | Registro de clientes con datos personales y tipo de proceso. |
| RF02 | Expediente digital por proceso con documentos adjuntos, actuaciones y notas. |
| RF03 | Calendario de audiencias con alertas automáticas (48h, 24h y día de la audiencia). |
| RF04 | Control de términos judiciales con fecha de vencimiento y alerta. |
| RF05 | Generación de reportes de procesos activos, archivados y por estado procesal. |
| RNF01 | Almacenamiento seguro de documentos con cifrado. |
| RNF02 | Búsqueda de procesos por radicado, cliente, juzgado o tipo de proceso. |
| RNF03 | Acceso restringido del cliente a su expediente vía portal web. |

**TECNOLOGÍAS:** *(celda vacía en el PDF — la propuesta no define stack)*

**SPRINTS (Scrum)**
- Sprint 1: Registro de clientes y creación de expedientes.
- Sprint 2: Documentación y actuaciones procesales.
- Sprint 3: Calendario de audiencias y control de términos.
- Sprint 4: Portal del cliente y reportes.

**METODOLOGÍA**
> Scrum con tablero Kanban (Trello/Jira). Roles: Product Owner (instructor), Scrum Master (líder del equipo), Development Team (aprendiz). Ceremonias: Sprint Planning, Daily Standup, Sprint Review, Sprint Retrospective. Duración de Sprint: 1 semana.
> Columnas del Tablero Kanban: Backlog → To Do → In Progress → Code Review → Testing → Done

**Nota importante:** los códigos RF01–RF05 y RNF01–RNF03 de la propuesta son *enunciados de intención*, no requisitos de ingeniería. En la Fase 3 se descomponen en requisitos formales con código propio, y cada uno mantendrá la referencia a su enunciado de origen.

---

## 3. Vacíos detectados en la propuesta

Puntos que la propuesta **no** define y que bloquean o condicionan el diseño:

| # | Vacío | Fase que lo consume | Estado |
|---|---|---|---|
| V-01 | ¿Un solo consultorio o plataforma para varios? | Modelo de negocio, modelo de datos | **Resuelto** → D-01 |
| V-02 | Stack tecnológico (celda vacía) | Arquitectura, despliegue, componentes | **Resuelto** → D-02 |
| V-03 | Canal por el que se emiten las alertas | RF de notificaciones, arquitectura | **Resuelto** → D-03 |
| V-04 | Integraciones externas | Arquitectura, alcance | **Resuelto** → D-04 |
| V-05 | Modelo de monetización y precios del SaaS | Modelo de negocio | **Resuelto** → D-06 |
| V-06 | Roles y permisos exactos dentro del despacho | RN, RF de seguridad | **Resuelto** → D-07 y D-11 |
| V-07 | Volumen esperado (despachos, procesos, documentos) | RNF de rendimiento y capacidad | **Resuelto** → D-19 |
| V-08 | Política de retención y respaldo de expedientes | RNF, cumplimiento | **Resuelto** → D-19 |
| V-09 | ¿Se gestionan honorarios o facturación al cliente? | Alcance | **Resuelto** → D-08 |
| V-10 | Nombre comercial del producto | Identidad | **Resuelto** → D-05 |
| V-11 | Visibilidad de las notas internas en el portal del cliente | RN, RF del portal | **Resuelto** → D-09 |

Los vacíos se resolvieron en las fases donde se consumen, no antes.

| # | Vacío | Fase que lo consume | Estado |
|---|---|---|---|
| V-12 | Formato de identificación del juzgado para la búsqueda de P-RNF02 | Modelo de datos (Fase 5) | **Resuelto** → D-17 |

**Estado final: 12 de 12 vacíos cerrados.**

---

## 4. Decisiones tomadas [D]

### D-01 — Alcance de tenencia: plataforma multi-consultorio (SaaS)
**Decisión:** el sistema es una plataforma en la nube donde **varios** consultorios jurídicos y abogados independientes se registran y operan de forma aislada entre sí.
**Justificación:** la propuesta describe la necesidad en plural — *"Los consultorios jurídicos y abogados independientes de Neiva"* — no la de un despacho concreto.
**Consecuencias:**
- Aparece la entidad raíz **Despacho** (tenant); todo dato del sistema cuelga de un despacho.
- El aislamiento de datos entre despachos se convierte en requisito no funcional crítico de seguridad, no en un detalle de implementación.
- Se requiere un proceso de alta/registro de despacho y un rol administrador por despacho.
- Aparece un actor nuevo, no presente en la propuesta: el **Administrador de la Plataforma**.

### D-02 — Stack tecnológico: Java + Spring Boot (backend) y Angular (frontend)
**Decisión:** backend Java con Spring Boot exponiendo API REST; frontend Angular como SPA; base de datos relacional; almacenamiento de documentos en almacén de objetos con cifrado.
**Justificación:** la propuesta deja la celda TECNOLOGÍAS vacía; se elige un stack empresarial consolidado, con soporte nativo para los mecanismos que exigen los RNF (Spring Security para el control de acceso del RNF03, cifrado en reposo para el RNF01, tareas programadas para las alertas del RF03/RF04).
**Consecuencias:** el detalle de versiones, motor de base de datos y proveedor de almacenamiento se fija en la Fase de Arquitectura (IEEE 42010), no aquí.

### D-03 — Canal de alerta: correo electrónico
**Decisión:** las alertas automáticas de audiencias (48h, 24h, día del evento) y de vencimiento de términos se emiten por **correo electrónico** al abogado responsable.
**Justificación:** la propuesta exige *"alertas automáticas"* pero no fija el medio. El correo es verificable, no depende de proveedores de pago y es auditable.
**Alcance preciso — para evitar malentendidos:**
- El **canal de alerta** es el correo. Es lo que cumple *"genera alertas automáticas"*.
- El **calendario de audiencias en pantalla** y la visualización del estado de los términos son funcionalidad del RF03/RF04 (visualizar), no un canal de alerta. Existen igual.
- No se implementan WhatsApp ni SMS.

### D-04 — Integración externa: Consulta de Procesos de la Rama Judicial
**Decisión:** el sistema consulta el servicio de Consulta de Procesos Nacional Unificada de la Rama Judicial de Colombia para traer las actuaciones publicadas de un proceso a partir de su número de radicado.
**Justificación:** decisión del interesado. Refuerza directamente la necesidad de la propuesta —*"los términos judiciales vencen por falta de seguimiento"*— porque un término suele contarse desde una actuación publicada.
**⚠ Advertencia de alcance:** esta integración **no está en la propuesta**. Amplía el alcance más allá de lo propuesto y trae dos riesgos que deben quedar visibles desde ahora:
- **R-01:** la disponibilidad, estabilidad y condiciones de uso del servicio público de consulta son externas y no controlables por el equipo. Debe verificarse técnicamente antes de comprometer requisitos que dependan de ella.
- **R-02:** el sistema no puede presentar la información consultada como fuente oficial ni sustituir la verificación del abogado. Es un apoyo al seguimiento.
**Mitigación de diseño:** la integración se diseña como **componente desacoplado y degradable**. Si el servicio externo no responde, el sistema sigue operando al 100% con registro manual de actuaciones. Ningún requisito núcleo (RF01–RF05) puede depender de que la integración funcione.

---

### D-05 — Nombre comercial: *Iuris*
**Decisión:** el producto se denomina comercialmente **Iuris**; el nombre técnico en la documentación de ingeniería sigue siendo **SGPJ**.
**Justificación:** decisión del interesado. *Iuris* proviene del latín *ius, iuris* ("del derecho"), coherente con el sector.
**Consecuencias:** el nombre comercial se usa en interfaz, portal del cliente y comunicaciones; el técnico en diagramas, requisitos y arquitectura. Cierra V-10.

### D-06 — La monetización ocurre fuera del sistema
**Decisión:** el sistema **no** gestiona suscripciones, cobros, pasarelas de pago ni facturación de la plataforma. Su única responsabilidad al respecto es mantener un **estado del despacho: activo / inactivo**, que el Administrador de Plataforma modifica manualmente según lo que ocurra por fuera.
**Justificación:** decisión del interesado. Cierra V-05 y anula el supuesto S-01.
**Consecuencias — importantes:**
- Desaparecen del alcance planes, precios, ciclos de facturación y pasarelas de pago.
- El modelo de datos del despacho gana un atributo `estado` con exactamente dos valores.
- **El estado del despacho se convierte en una condición transversal del sistema**: aparece una pregunta que ninguna otra decisión resuelve — *¿qué deja de funcionar cuando un despacho pasa a inactivo?* Concretamente: si se siguen emitiendo las alertas, si el abogado puede entrar, si el cliente puede entrar al portal y si los datos se conservan. **Esto se resuelve como regla de negocio en la Fase 2 → asunto A-02.**

### D-07 — Roles: Administrador de Despacho es distinto de Abogado, y son acumulables
**Decisión:** dentro de un despacho existen dos roles internos **diferenciados** — Administrador de Despacho y Abogado — más el rol externo Cliente. **Una misma persona puede tener ambos roles internos a la vez**, que es exactamente el caso del abogado independiente.
**Justificación:** decisión del interesado. Cierra parcialmente V-06 y anula el supuesto S-02.
**Consecuencias — la más relevante del modelo de datos:**
- La relación usuario–rol es **de uno a muchos, no de uno a uno**. Un usuario tiene un *conjunto* de roles dentro de su despacho. Modelar "un usuario = un rol" haría imposible el caso del abogado independiente, que es el segmento explícito de la propuesta **[P]**.
- Los permisos se evalúan por **unión** de los roles del usuario, no por un rol único.
- El alta de un despacho independiente crea **un usuario con los dos roles**, no dos usuarios.
- **Queda abierto el alcance real del Administrador de Despacho sobre el contenido de los expedientes** (¿administra usuarios y configuración solamente, o también lee todos los expedientes del despacho?). Es una cuestión de confidencialidad, no de permisos técnicos. **Se resuelve en Fase 2 → asunto A-01.**

### D-08 — Honorarios y facturación al cliente: fuera de alcance
**Decisión:** el sistema **no** gestiona honorarios, facturación al cliente, pagos, contabilidad ni nómina del despacho.
**Justificación:** decisión del interesado; la propuesta tampoco los menciona. Cierra V-09 y anula el supuesto S-05.
**Aclaración necesaria para evitar una confusión de términos:** lo excluido son los **honorarios** (dinero). Los **horarios** (fecha y hora) **sí están dentro y son esenciales**: sin la hora de la audiencia no puede calcularse la alerta de 48h ni la de 24h que exige el RF03 **[P]**.

### D-09 — Las notas internas nunca son visibles para el cliente
**Decisión:** las notas del abogado son de **uso exclusivamente interno del despacho** y no se muestran en ninguna circunstancia en el portal del cliente.
**Justificación:** decisión del interesado. Cierra V-11 y trata el riesgo R-06.
**Consecuencias:**
- La nota es la primera pieza del expediente con **visibilidad restringida por rol**. Esto obliga a que el expediente no sea un bloque homogéneo: sus componentes tienen reglas de visibilidad distintas.
- Se deriva una pregunta que la decisión no cubre y que no voy a inventar: **¿los documentos y las actuaciones sí son todos visibles para el cliente, o también se decide pieza por pieza?** **Fase 2 → asunto A-03.**
- Será regla de negocio de obligado cumplimiento y criterio de aceptación explícito en las historias del portal.

### D-10 — Despacho inactivo: el sistema se bloquea por completo, pero no se borra nada
**Resuelve:** asunto A-02 (abierto por D-06).
**Decisión:** cuando un despacho pasa a **inactivo**, ninguno de sus usuarios —abogados, administrador del despacho **ni clientes**— puede realizar ninguna operación en la plataforma. **Toda la información del despacho se conserva íntegra**, precisamente para el caso de que el despacho olvide pagar o regularice después. La reactivación restituye el acceso al estado exacto en que quedó, sin pérdida.
**Justificación:** decisión del interesado, tomada con conocimiento del riesgo R-07 (advertido antes de decidir).
**Consecuencias:**
- El estado del despacho es una **condición transversal previa a toda operación**. Debe verificarse en **un único punto de control** (filtro o interceptor de seguridad), nunca repetida en cada funcionalidad: repartirla garantiza que alguna se olvide.
- La **suspensión de alertas queda dentro del bloqueo**. Es la consecuencia deliberadamente aceptada del riesgo R-07: mientras el despacho esté inactivo, el sistema **deja de vigilar** sus audiencias y términos.
- **Desactivar ≠ eliminar.** Son operaciones distintas; la propuesta no contempla eliminación de despachos y no se implementa.
- **Mitigación derivada (no contradice la decisión, la completa):** al pasar a inactivo, el sistema emite **un último aviso por correo** al despacho informando que la vigilancia de audiencias y términos queda suspendida. Es el corte limpio: el abogado sabe con certeza a partir de qué momento vuelve a ser él quien vigila. Sin ese aviso, el despacho seguiría confiando en un sistema que ya dejó de avisarle — que es exactamente el fallo R-02. Este aviso es una **notificación de corte**, no una funcionalidad de la plataforma, por lo que es compatible con el bloqueo total.

### D-11 — El Administrador de Despacho administra y además lee los expedientes
**Resuelve:** asunto A-01 (abierto por D-07). Cierra V-06 por completo.
**Decisión:** el Administrador de Despacho realiza **ambas cosas**: gestiona usuarios, configuración y catálogos de su despacho, **y** accede al contenido de todos los expedientes del despacho.
**Justificación:** decisión del interesado.
**Consecuencias:**
- Dentro de un despacho, el Administrador es un **superconjunto de permisos** respecto del Abogado.
- Para el **abogado independiente** ambos roles se solapan casi por completo; aun así se mantienen separados, porque en un consultorio con varios abogados el Administrador puede ser una persona distinta de quien lleva los casos.
- **Consecuencia de confidencialidad que debe quedar visible:** si el Administrador de Despacho llegara a ser una persona no abogada (por ejemplo, personal administrativo), tendría acceso a información sometida a reserva profesional. La decisión es del despacho, no del sistema, pero el sistema debe dejar rastro: **todo acceso al contenido de un expediente queda registrado en bitácora de auditoría** (quién, qué expediente, cuándo). Esto no restringe la decisión; la hace verificable.
- El Administrador de **Plataforma** sigue **sin** acceso al contenido de los expedientes **[D-01]**. Son dos roles administrativos con alcances opuestos y no deben confundirse.

### D-12 — El cliente ve todos los documentos y todas las actuaciones de su expediente
**Resuelve:** asunto A-03 (abierto por D-09).
**Decisión:** en el portal, el cliente ve **la totalidad** de los documentos y actuaciones de su propio expediente. No hay selección pieza por pieza.
**Justificación:** decisión del interesado.
**Consecuencias:**
- **Simplifica el modelo de datos de forma notable:** la visibilidad se determina por el **tipo de pieza**, no por una marca en cada pieza individual. No se necesita atributo `visible` en documentos ni actuaciones.
- El expediente queda partido en **dos zonas fijas**:

  | Zona | Contenido | Ve el cliente |
  |---|---|---|
  | Compartida | Documentos, actuaciones, datos del proceso | **Sí, todo** |
  | Interna | Notas del abogado | **No, nunca** **[D-09]** |

- **Consecuencia operativa que el abogado debe conocer, y que se vuelve regla:** *todo documento que se sube al expediente es inmediatamente visible para el cliente.* No existe zona intermedia ni borrador oculto. Lo que el abogado no quiera mostrar, **no se sube**: va como nota interna. Esta frase debe aparecer en la interfaz de carga de documentos, no solo en la documentación.

### D-13 — Los catálogos del dominio son administrables por cada despacho
**Decisión:** los cuatro catálogos —estados procesales, tipos de proceso, tipos de documento y tipos de actuación— se entregan con valores iniciales por defecto, y el **Administrador de Despacho** puede añadir, renombrar y desactivar valores dentro de **su** despacho.
**Justificación:** decisión del interesado — *"cada despacho tiene su forma propia de trabajar"*.
**Consecuencias:**
- Los valores propuestos en §2.2–§2.5 del documento 02 dejan de ser una definición del sistema y pasan a ser **semillas por defecto**. Que estén mal elegidos deja de ser un riesgo de análisis: es corregible por configuración.
- Los catálogos pasan a ser **datos del despacho**, no constantes del código. Entran al modelo de datos como entidades, con su pertenencia a un despacho (RN-01).
- Un valor en uso no se elimina, solo se desactiva (RN-06).
- **Excepción que se mantiene:** los estados **Activo** y **Archivado** son obligatorios y no eliminables en todo despacho, porque el RF05 **[P]** exige reportar por ellos literalmente.

### D-14 — Un usuario pertenece a un solo despacho
**Decisión:** confirmada la regla RN-13. Un usuario está vinculado a exactamente un despacho.
**Consecuencia:** una persona que colabore con dos despachos requiere **dos cuentas independientes**. Es el precio de garantizar RN-02 (aislamiento); una cuenta compartida entre despachos sería la vía más directa de fuga de información.

### D-15 — El despacho habilita el acceso del cliente; no hay autorregistro
**Decisión:** confirmada la regla RN-43. El cliente no crea su propia cuenta: el despacho le habilita el acceso al portal.
**Consecuencia:** solo el despacho sabe a quién representa. Un autorregistro abierto permitiría que un tercero reclamara acceso a un expediente ajeno.

### D-16 — Las alertas de términos son configurables en cantidad y anticipación
**Decisión:** el despacho puede definir **cuántas** alertas se emiten por cada término y **con cuánta anticipación** se envían.
**Justificación:** decisión del interesado. La propuesta **[P]** deja el RF04 sin especificar ("fecha de vencimiento y alerta"), a diferencia del RF03, que sí fija 48h, 24h y el día.
**Consecuencias, y una es un riesgo serio:**
- Aparece la entidad **Esquema de alertas**: un conjunto de anticipaciones (por ejemplo 15, 5 y 1 día antes) que se aplica a los términos.
- **⚠ Riesgo R-08 — la configurabilidad permite configurar el fallo.** Si el esquema admite cero alertas, un despacho podría desactivar por completo la vigilancia de sus términos sin darse cuenta, y el sistema cumpliría su configuración mientras el término vence en silencio. Es **R-02 por la puerta de la configuración**.
  **Tratamiento:** se establece un **mínimo obligatorio de una alerta anticipada por término**, no desactivable. La configuración decide *cuántas más* y *cuándo*, nunca *si*.
- **Frontera con el RF03 [P]:** las tres alertas de audiencia (48h, 24h, día) están fijadas literalmente por la propuesta. Se conservan como **obligatorias**; el despacho puede **añadir** alertas adicionales, pero no eliminar esas tres. **[S — a confirmar]**
- **Nivel de configuración: [S — a confirmar]** se propone que el esquema se defina **por despacho** (lo administra el Administrador de Despacho, coherente con D-11 y D-13), con posibilidad de ajustarlo en un término concreto cuando ese término lo amerite.

### D-17 — *Juzgado* pasa a quinto catálogo, administrable por despacho
**Resuelve:** asunto A-04 (abierto en la Fase 5 §3.4).
**Decisión:** *Juzgado* se incorpora como quinto valor de `tipo_catalogo` en la tabla `VALOR_CATALOGO` ya existente. Es **administrable por cada despacho**, igual que los otros cuatro.
**Justificación:** P-RNF02 **[P]** exige buscar por juzgado. Con texto libre, el mismo juzgado se escribiría de formas distintas y la búsqueda devolvería resultados incompletos — un requisito literal de la propuesta quedaría degradado.
**Por qué por despacho y no global — es la parte no obvia:** los juzgados son entidades del mundo real y parecería que corresponden a una lista nacional mantenida por el Administrador de Plataforma. Se descarta: **un directorio nacional de juzgados es una responsabilidad de mantenimiento permanente que nadie pidió, que se desactualiza sola y que convertiría al Administrador de Plataforma en curador de datos jurídicos.** Un despacho litiga ante un puñado de juzgados, no ante todos los del país; su lista se construye sola con el uso. Promover el catálogo a global más adelante es sencillo; lo contrario no.
**Consecuencias:**
- `PROCESO.juzgado` pasa de texto libre a `juzgado_id` con clave foránea a `VALOR_CATALOGO`.
- Cero tablas nuevas y cero migración de datos: aún no hay datos.
- RF-33 amplía su alcance a cinco catálogos; la pantalla ya existe.
- Cierra el riesgo arquitectónico **RA-3**.

### D-18 — Aislamiento: tres controles obligatorios y RLS condicionado
**Resuelve:** el pendiente sobre Row-Level Security de la Fase 6 (ADR-03).
**Decisión:** el aislamiento entre despachos se implementa en cuatro controles, con obligatoriedad distinta:

| Control | Cuándo | Obligatorio |
|---|---|---|
| 1 · Tenant tomado del token, nunca de parámetro del cliente | Sprint 1 | **Sí** |
| 2 · Filtro automático de tenant a nivel de ORM | Sprint 1 | **Sí** |
| 3 · **Pruebas automatizadas de acceso cruzado por módulo** (CA-41.3), como puerta de calidad | Sprint 1 | **Sí** |
| 4 · Row-Level Security en PostgreSQL | Final de Sprint 1 / inicio de Sprint 2 | **Condicionado** |

**Justificación:** RLS es la única capa que sigue protegiendo cuando el código se equivoca, pero tiene un costo real que debe conocerse: **con un pool de conexiones, si una conexión se devuelve al pool con el tenant de la petición anterior pegado, la siguiente lo hereda — y se construye exactamente la fuga que se quería evitar.** Se implementa con `SET LOCAL` dentro de la transacción, que se revierte al terminar; nunca con `SET` a secas.
El control 3 aporta la mayor parte del beneficio a una fracción del costo: atrapa el olvido de filtro **en integración continua**, no en producción, y no depende del pool.
**Condición sobre el control 4:** si el equipo lo hace funcionar limpiamente, se mantiene. Si a mitad del Sprint 2 sigue sin resolverse, **se retira de forma explícita** y se opera con los controles 1 a 3. Lo que no puede ocurrir es que se omita por olvido: eso dejaría ADR-02 apoyada en una premisa que ella misma declaró insuficiente.

### D-19 — Cifras de los RNF adoptadas como línea base, con RNF-11 corregido
**Resuelve:** los supuestos **S-03** y **S-04**, y el riesgo **RA-4**.
**Decisión:** se adoptan las cifras propuestas como **línea base revisable** y se convierten en pruebas automatizadas.
**Justificación:** no hay un despacho real con volumen medido a quien validárselas; el Product Owner es el instructor **[P]**. Esperar una validación que nadie puede dar sería bloquear sin obtener nada. Lo relevante no es que las cifras sean exactas, sino que sean **verificables** — y lo son.
**Corrección a RNF-11:** la tolerancia pasa de **1 hora a 15 minutos**, con planificador ejecutándose cada 5 minutos.
**Motivo de la corrección:** con tolerancia de 1 hora y planificador horario, la alerta de 24 horas podría emitirse a las 23h05 — perdería una hora entera de margen justo en el aviso que más importa, y la de 48h quedaría igual de degradada. El costo de bajar la tolerancia es nulo: es una tarea programada que casi siempre encontrará cero alertas pendientes.
**Sin cambios:** RNF-12 (50 despachos · 500 procesos · < 3 s), RNF-13 (20 MB) y RNF-14 (respaldo diario con restauración probada).

### D-20 — Redistribución de requisitos entre sprints para blindar el Sprint 3
**Resuelve:** el riesgo **R-09**.
**Principio:** **el Sprint 3 es el único irreductible** — contiene el motor de alertas, que es la razón de ser del producto. Todo lo demás puede moverse; eso no.
**Decisión:** se mueven cinco requisitos, **sin cambiar los títulos de los sprints de la propuesta [P]**:

| Requisitos | De → a | Por qué se puede |
|---|---|---|
| RF-02, RF-03 · estado del despacho | 1 → 2 | No hace falta poder desactivar despachos para registrar clientes |
| RF-31 · búsqueda completa | 1 → 4 | Ya estaba repartido "base en 1, refinamiento en 4"; queda entero en el 4 |
| **RF-19, RF-20 · audiencia y calendario** | 3 → 2 | **Movimiento clave.** Registrar una audiencia es un formulario que no depende del motor de alertas |

**Resultado:** Sprint 1: 18 → **13** · Sprint 2: 10 → **15** · Sprint 3: 16 → **13** · Sprint 4: 8 → **9**.
**Razonamiento:** el Sprint 2 se carga, pero es el más seguro — formularios y almacenamiento, sin lógica crítica. **Se carga el sprint barato para descargar el caro.**
**Dos condiciones:**
- Mover RF-19 al Sprint 2 estira su título *("Documentación y actuaciones procesales")*. **Requiere el visto bueno del Product Owner**; no se aplica unilateralmente.
- Si el PO no acepta mover nada, la alternativa es aceptar R-09 y recortar **dentro** del Sprint 3: RF-20 se entrega como lista de próximas audiencias en lugar de calendario completo. **Nada del motor de alertas se recorta.**

### D-21 — Estándares de construcción del código
**Decisión:** el desarrollo se rige por cinco estándares fijados por el interesado:

| # | Estándar | Cómo se aplica |
|---|---|---|
| 1 | **Proyecto real, seguridad en serio** | Sin secretos en el repositorio · contraseñas con hash desde el primer commit · CORS restringido · los tres controles obligatorios de ADR-03 entran en el **primer** incremento, no después |
| 2 | **Software en español** | Interfaz, mensajes de error y validaciones en español |
| 3 | **Código limpio** | Nombres del glosario de la Fase 1 · funciones cortas · sin comentarios que expliquen lo que el nombre ya dice |
| 4 | **Principios SOLID** | Ya preparados desde el diseño: módulos M1–M12 (S), `Pieza` abstracta (O/L), servicios por módulo (I), dominio sin dependencias de infraestructura (D) |
| 5 | **Construcción incremental "como legos"** | Una pieza pequeña y verificable a la vez, justificada contra un RF/RNF/HU ya documentado. **Nunca generar el proyecto completo de un tirón** |

**Idioma del código:** **dominio en español, sufijos técnicos en inglés**.
- Español: `Despacho`, `Expediente`, `TerminoJudicial`, `buscarPorRadicado()`, tabla `proceso`.
- Inglés: `DespachoRepository`, `DespachoService`, `DespachoController`.

**Justificación:** mantiene dentro del código el vocabulario del glosario de la Fase 1 y de las reglas de negocio —lo que preserva la trazabilidad hasta el código— sin separarse de las convenciones de Spring.

**Consecuencia sobre el orden de construcción:** el **aislamiento multi-tenant no puede postergarse**. Añadirlo después obligaría a reescribir todo lo construido antes; por eso entra en el primer bloque junto con despachos, usuarios y autenticación.

### D-22 — Control de versiones con Git desde el inicio
**Decisión:** el proyecto se versiona con Git desde antes de la primera línea de código, con `.gitignore` que excluye secretos, dependencias y artefactos de compilación.
**Justificación:** cada incremento queda como un commit revisable y reversible. Además es la barrera que impide que un archivo con credenciales entre al repositorio por descuido — coherente con el estándar 1 de D-21.
**⚠ Nota de entorno:** el proyecto vive dentro de una carpeta sincronizada por OneDrive. La sincronización puede interferir con archivos de compilación y con el directorio `.git`. El `.gitignore` excluye `target/` y `node_modules/`, que es donde ese conflicto se produce con más frecuencia.

**Estado del entorno a 2026-08-20:** Node 24.16, PostgreSQL 18.4 y Git 2.55 disponibles. **JDK y Maven no instalados** — requisito previo para el stack de D-02. Se resuelve con una sola instalación (JDK), ya que el proyecto usará Maven Wrapper.

### D-23 — Rigor de seguridad diferenciado: local relajado, VPS estricto
**Decisión del interesado:** durante el desarrollo **en local** no se exige rigor pleno en el manejo de credenciales. **En el despliegue al VPS sí**, sin excepción.

**Justificación:** la base local no contiene datos reales de ningún despacho ni expediente sometido a reserva. Exigir el mismo rigor ahí frenaría el desarrollo sin reducir ningún riesgo real. El riesgo aparece cuando el sistema aloja información de clientes verdaderos.

**Consecuencia — y es la razón de registrar esto:** un control relajado "temporalmente" solo es aceptable si existe el momento exacto en que deja de estarlo y alguien lo verifica. Sin esa lista, "lo arreglamos en producción" se convierte en "nunca se arregló".

#### Controles diferidos al despliegue — verificación obligatoria antes de exponer el VPS

| # | Control | Estado en local | Obligatorio en VPS |
|---|---|---|---|
| 1 | Credenciales por **variable de entorno**, nunca en archivo | `application-local.properties` con la clave escrita | **Sí.** El archivo local no se copia al VPS |
| 2 | Clave del rol de base de datos **robusta y única** | Clave corta de desarrollo | **Sí.** Generada, no elegida a mano |
| 3 | **TLS** en todo el tráfico (RNF-06) | HTTP plano en localhost | **Sí**, incluido el portal del cliente |
| 4 | PostgreSQL **no expuesto a internet** | Escucha en localhost | **Sí.** Solo accesible desde la aplicación |
| 5 | **CORS** con orígenes explícitos | Permisivo para desarrollo | **Sí** |
| 6 | Cifrado de documentos en reposo (RNF-04) | ✅ Implementado: AES-256-GCM (`AlmacenDocumentosCifrado`) | **Sí, la clave por variable de entorno** (`SGPJ_DOCUMENTOS_CLAVE`). Sin ella la aplicación no arranca. Es el control 1 aplicado a la clave de cifrado |
| 7 | Respaldo diario **con restauración probada** (RNF-14) | No aplica | **Sí** |
| 8 | Rol `sgpj_app` sin privilegios administrativos | ✅ Ya aplicado desde el inicio | Se mantiene |
| 9 | Contraseñas de usuario con hash y salt (RNF-05) | Se aplica igual desde el inicio | **Sí** |

Los controles 8 y 9 **no se relajan ni siquiera en local**: el 8 porque un rol con privilegios anularía Row-Level Security más adelante sin que nadie lo note (ADR-03), y el 9 porque el hash de contraseñas es código de la aplicación, no configuración de entorno — escribirlo mal en local significa escribirlo mal en producción.

#### ⚠ Impacto en la arquitectura que debe revisarse

La existencia de un **VPS único** como destino de despliegue es información nueva: la Fase 6 asumía "infraestructura en la nube" genérica con nodos separados. Un VPS único cambia tres cosas y **debe reflejarse en la vista de despliegue (VP-5) antes de desplegar**:

- **ADR-05** — el "almacén de objetos" para documentos pasa a ser, con toda probabilidad, el sistema de archivos del propio VPS. El cifrado en reposo y el respaldo hay que resolverlos de otra forma.
- **RA-1** — el punto único de fallo se agrava: aplicación, base de datos y documentos comparten una sola máquina. Si el VPS cae, **la vigilancia de términos se detiene por completo**.
- **RNF-14** — el respaldo no puede quedarse en el mismo VPS. Un respaldo que muere con la máquina que respalda no es un respaldo.

Estos tres puntos se resuelven en un incremento de arquitectura previo al despliegue, no durante él.

---

### D-24 — Las contraseñas deben poder cambiarse: un hueco que la ingeniería de requisitos no vio

**Contexto.** Al construir la pantalla de acceso de clientes se comprobó que el backend expone `POST /clientes/{id}/acceso-portal` recibiendo **correo y contraseña**: es el despacho quien fija la clave del cliente. Buscando cómo se cambia después, se encontró que **no existe ningún endpoint de cambio ni de restablecimiento de contraseña en todo el sistema** — ni para clientes, ni para abogados, ni para administradores.

**Cómo se coló.** Las cuatro fases de requisitos derivaron cada RF de una RN, y cada RN de la propuesta o de una decisión. La propuesta habla de autenticar (P-RNF03) y de habilitar el acceso del cliente, pero **nunca menciona cambiar una contraseña**, así que no nació ninguna regla que lo exigiera. No fue un descuido de redacción: fue un hueco en el material de partida, y ninguna de las fases posteriores estaba mirando hacia ahí.

Lo detectó la construcción del frontend, y solo porque hubo que escribir en pantalla qué le ocurre al cliente después de recibir su clave. **Redactar la interfaz obligó a decir la verdad sobre el sistema**, y la verdad no se sostenía.

**Consecuencias del hueco, tal como estaba:**

- El despacho conoce la contraseña de cada uno de sus clientes, **para siempre**.
- Una credencial filtrada no se puede sustituir: la única salida sería desactivar la cuenta y crear otra, perdiendo su historial (RF-38).
- Quien olvide su contraseña no tiene ninguna vía de vuelta. El enlace «¿La olvidó?» del ingreso no lleva a ninguna parte porque no hay adónde llevar.

**Decisión.** Se añaden **RN-53** y **RN-54**, **RF-39** (cambiar la propia contraseña, exigiendo la actual) y **RF-40** (restablecer la de un usuario del despacho, sin conocer la anterior), con **HU-43** y **HU-44**. Se marcan **Sprint 1** porque pertenecen a M2 —la base de seguridad—, aunque se implementen ahora: el sprint dice a qué bloque pertenece el requisito, no cuándo se descubrió.

**Por qué se documenta antes de programar.** Es la misma regla que se sigue desde la Fase 1: primero existe el requisito, después el código. Implementarlo primero y documentarlo después habría dejado dos requisitos sin RN de la que derivar, y la cadena de trazabilidad —que es lo que hace revisable este proyecto— tendría un eslabón fabricado a posteriori.

**Lo que NO se decide aquí.** No se añade recuperación autónoma por correo («olvidé mi contraseña» sin intervención humana). Exigiría enlaces de un solo uso con caducidad y un canal de correo fiable, y el envío real acaba de implementarse. Queda **fuera de alcance declarado**: el camino de vuelta es que el administrador del despacho restablezca la clave (RF-40), que además es más difícil de suplantar en un despacho pequeño donde todos se conocen.

---

### D-25 — La medición de rendimiento descubre que RNF-11 no se cumple con el volumen objetivo

**Contexto.** Se midió el sistema contra el volumen que fija RNF-12 —50 despachos, 500 procesos por despacho, 50 piezas por expediente— sobre una base desechable de 405 MB con 25.000 procesos, 1.250.000 piezas y 174.999 alertas. Los guiones y el resultado completo están en `backend/src/test/resources/rendimiento/`.

**RNF-12 se cumple con holgura.** Las 16 consultas habituales devuelven datos y ninguna pasa de **584 ms** en su peor tiempo, sobre un límite de 3.000 ms. Se reportó el peor tiempo y no la media, y se marcó `VACIA` toda consulta que devolviera cero filas: en la primera pasada, tres de doce devolvían vacío y salían con tiempos excelentes que no medían nada.

**RNF-11 no se cumple.** Medir el barrido obligó a separar dos cosas que se confunden:

- **La consulta no es el problema.** `EXPLAIN (ANALYZE, BUFFERS)` sobre la consulta real del motor da **0,348 ms**: usa `ix_alerta_pendientes`, no recorre las 175.000 filas. Un barrido completo por HTTP tardó 141 ms el peor de tres, drenando exactamente 300 alertas.
- **El techo del motor sí lo es.** `MotorAlertas.TAMANO_LOTE = 100` cada 5 minutos fija un máximo de **1.200 alertas/hora** que ninguna optimización de consulta mueve.

Y las alertas no llegan repartidas: `Termino.fechaObjetivo()` devuelve la fecha de vencimiento a las `23:59`, una fecha **sin hora**. Todos los términos que vencen el mismo día disparan sus avisos en el mismo instante, y encima coinciden los de 15, 5 y 1 día de anticipación procedentes de días de vencimiento distintos. Medido: **2.499 alertas en un mismo instante**, 2.706 en el peor día. Drenar ese pico exige 25 barridos, es decir **125 minutos** frente a los **15 de tolerancia**. La última alerta del pico saldría con más de dos horas de retraso, justo sobre el aviso de 24 h de un término, que es la razón de ser del producto.

**Por qué no lo detectó ninguna prueba.** Las pruebas del motor verifican que una alerta sale, que no se reenvía y que se registra el retraso (RNF-11 se comprueba **por alerta**). Ninguna comprueba qué ocurre cuando **miles vencen a la vez**, porque el incumplimiento no está en el comportamiento de una alerta sino en el caudal del conjunto. Es un defecto que solo aparece con volumen, y por eso lo encontró la medición y no la suite.

**Decisión.** Se registra el incumplimiento como defecto abierto **A-05** y **no se corrige aquí**, porque las tres salidas posibles no son equivalentes y la elección no es de implementación:

1. **Subir el lote** a 833 como mínimo. No lo limita la base (0,35 ms por lote de 100) sino el emisor, que envía en serie por SMTP. **El envío real por SMTP no está medido** —los 141 ms son con el emisor en modo registro— y es el número que falta antes de elegir esta vía.
2. **Bajar el intervalo** a menos de 36 segundos con el lote actual. Multiplica los barridos en vacío el resto del día.
3. **Repartir el instante de aviso**, para que el pico se aplane solo. Es la más barata de las tres, pero **cambia una regla de negocio**: hoy RN-19 fija la anticipación en días, no en momentos, y decidir a qué hora sale un aviso es del Product Owner, no del desarrollador.

**Lo que sí queda cerrado.** RNF-12 se da por verificado con evidencia reproducible. La base de medición es desechable y se borra al terminar: nunca se mide contra la base de desarrollo.

---

### D-26 — El envío en paralelo es la salida de A-05, y agrupar el lote no lo era

**Contexto.** D-25 dejó tres salidas para RNF-11 y dijo que no se podía elegir sin medir el envío real por SMTP. Se midió (`RendimientoSmtpTest`, resultados completos en `backend/src/test/resources/rendimiento/RESULTADOS.md`).

**Cómo se midió, y por qué así.** Contra GreenMail detrás de un proxy TCP que inyecta latencia, **no contra un proveedor real**. Un proveedor habría dado el número de ese proveedor, ese día, desde esa red; y habría exigido mandar dos mil correos de prueba desde un dominio nuevo, que es la forma más rápida de acabar en una lista negra y que las alertas de verdad dejen de llegar. Lo que se midió en su lugar es **cuántos viajes de red hay por alerta**, que es propiedad del código y no del proveedor: con ese número, el tiempo con cualquier proveedor es una multiplicación.

**El resultado corrigió la hipótesis de partida.** La suposición era que reutilizar la conexión resolvería el problema. No: son **14 viajes de red por alerta** enviando de una en una y **10 por alerta** agrupando el lote. Reutilizar la conexión ahorra solo **4 de 14** —el saludo y la despedida—, porque los otros diez son del protocolo: `MAIL FROM`, `RCPT TO`, `DATA` y el punto final son un viaje cada uno **por mensaje**. Agrupar mejora 1,4×, no el doble, y 1,4× no alcanza: a 100 ms de latencia el pico seguiría tardando 26 minutos.

Enviar en paralelo sí: **6,6 minutos con 4 conexiones y 3,5 con 8**, a 100 ms. Los viajes de red no bajan —1.016 tramos con 4 conexiones frente a 1.004 con una—, simplemente dejan de esperarse en fila.

**La cifra que decide.** Con el código de hoy, **cualquier proveedor a más de 50 ms de latencia incumple RNF-11**. De Neiva a un proveedor en Estados Unidos son 80–120 ms.

**Decisión.** La salida de A-05 es **enviar en paralelo**, no subir el lote. Se descarta la segunda vía de D-25 (bajar el intervalo), que no ataca el problema: el cuello no es la frecuencia del barrido sino el caudal de cada uno.

**Lo que sigue sin decidirse, y es del Product Owner.** Ocho conexiones despachando 2.499 correos en 3,5 minutos son **~12 por segundo**. Un servicio transaccional lo admite; el SMTP de una cuenta de Gmail no, ni de lejos. **El proveedor hay que elegirlo con ese número delante**, porque si limita a menos, el paralelismo no sirve de nada y habría que volver a la tercera vía de D-25 —repartir el instante de aviso, que cambia RN-19—.

**Lo que NO se implementa aquí, y por qué.** `MotorAlertas.ejecutarBarrido()` envía dentro de **una sola transacción**. Enviar en paralelo desde ahí no es cambiar un bucle por un pool: la transacción y sus bloqueos quedarían abiertos esperando a la red, y las entidades de Hibernate no son seguras entre hilos. Separar el envío de la transacción es el trabajo real, y es justo donde puede reaparecer la emisión duplicada que ADR-04 evita hoy con `SKIP LOCKED`. Se hace como cambio propio, con sus pruebas, no de rebote en una medición.

**Nota sobre la prueba.** La primera versión exigía que agrupar fuera **el doble** de rápido. Falló, y estuvo bien que fallara: la cifra era una suposición, no un requisito. Ahora comprueba lo estructural —cuántas conexiones abre cada estrategia— y la dirección de la mejora, y deja las cifras al informe. Una prueba que afirma una suposición solo comprueba que se sigue suponiendo lo mismo.

---

### D-27 — A-05 se cierra por la opción B, que dejó de ser la arriesgada

**Contexto.** D-26 dejó dos salidas para el incumplimiento de RNF-11 y descartó implícitamente la de enviar en paralelo por su coste: *«el motor envía dentro de una sola transacción, y paralelizar desde ahí no es cambiar un bucle por un pool —los bloqueos quedarían abiertos esperando a la red y las entidades de Hibernate no son seguras entre hilos—»*.

**Qué cambió.** Al corregir **H-6** —un barrido interrumpido reenviaba lo ya enviado— el motor dejó de ser una sola transacción: **cada alerta abre la suya, carga su propia entidad y la confirma sola**. El obstáculo que hacía peligroso el paralelismo desapareció como efecto colateral de arreglar otra cosa. Se comprobó antes de afirmarlo: ni `EnvioDeUnaAlerta` ni la redacción ni el emisor guardan estado por hilo.

**Decisión.** Se cierra A-05 por la **opción B**: enviar en paralelo, con lote y número de conexiones configurables.

| | Antes | Ahora |
|---|---:|---:|
| Alertas por barrido | 100 fijo | **3.000** (`SGPJ_ALERTAS_LOTE`) |
| Conexiones de correo | 1, en serie | **4** (`SGPJ_ALERTAS_CONEXIONES`) |
| El pico de 2.499 tarda | **125 min** | **un barrido** |

**Por qué esta y no la opción A.** No porque sea mejor —la de repartir la hora del aviso sigue teniendo a su favor que arregla además un problema de producto, el aviso que llega a medianoche— sino porque **no exige cambiar ninguna regla de negocio**, y por tanto no bloquea. La opción A sigue disponible y sigue mereciendo la pena: son compatibles.

**Lo que esto obliga a decidir de todos modos.** Cuatro conexiones despachando el lote son unos **6 envíos por segundo**. Un servicio transaccional lo admite; el SMTP de una cuenta de Gmail no. **El proveedor de correo hay que elegirlo con esa cifra delante**, y si limita por debajo hay que bajar `SGPJ_ALERTAS_CONEXIONES` y volver a medir. Es el mismo condicionante que ya señalaba D-26, y no desaparece.

**Verificado por mutación, no solo por pasar.** `PicoDeAlertasTest` pasa a `@Tag("integracion")` y con el lote antiguo de 100 vuelve a fallar diciendo «25 barridos, 2 h 05 min tarde» — exactamente la cifra que midió D-25. La prueba comprueba dos cosas distintas: que la muestra sale en un barrido (que el motor respeta su configuración) y que **el pico real cabe en la tolerancia**. Sin la segunda, un lote suficiente para la muestra dejaría pasar la prueba incumpliendo con el volumen de verdad.

---

### D-28 — El radicado se normaliza y se avisa si no tiene forma de radicado

**Contexto.** El radicado no se validaba de ninguna forma: obligatorio, máximo 50 caracteres y único por despacho (RN-17). Nada más. Al revisarlo aparecieron **dos problemas distintos**, y el segundo es el grave.

**Problema 1 — el formato no se comprueba.** Se acepta `ABC-123` como radicado. Coherente con **RN-36** (el sistema no interpreta plazos ni derecho), pero comprobar un *formato* no es interpretar derecho: es lo mismo que se hace con un correo o un NIT.

**Problema 2 — la unicidad se puede burlar sin querer.** Verificado contra el sistema corriendo: el mismo radicado escrito `41001 31 03 001 2026 09999 00` y `41001310300120260999900` creó **dos procesos distintos** en el mismo despacho, cada uno con su expediente. RN-17 no lo impidió porque el índice único compara la cadena tal como se tecleó.

**Por qué el segundo importa más.** No es un problema estético. El despacho acaba con el mismo caso duplicado, y a partir de ahí **sus términos y audiencias quedan repartidos entre dos expedientes**: el abogado registra el término en uno, consulta el otro y no lo ve. Es una forma silenciosa de perder vigilancia, que es exactamente lo que **R-02** castiga.

**De dónde salió.** De una pregunta del analista sobre la integración con la Rama Judicial, no de una prueba. Ninguna prueba lo detectó porque todas usan un radicado y comprueban que no se repita **idéntico**.

**El argumento que lo decidió, y es del analista.** Su observación: *el radicado guardado es el que el sistema usaría para consultar la Rama Judicial (RF-35)*. De ahí se sigue que **la Rama Judicial no puede validar el radicado, porque la consulta va indexada por él** — no se puede usar la llave para verificar la llave. Y si el número está mal, pasa una de dos cosas:

- **No devuelve nada** → el abogado concluye que su proceso no tiene movimiento, cuando lo que falla es el número. Fallo silencioso.
- **Devuelve otro proceso real** → un radicado son 23 dígitos asignados de forma densa, así que un dígito mal tecleado cae con facilidad sobre un proceso existente, y el sistema mostraría **actuaciones de un tercero dentro del expediente del cliente**. Eso es **R-04**.

**Decisión.**

1. **Se normaliza para comparar, se conserva para mostrar.** El radicado se guarda tal como lo escribió el abogado —es su dato— pero la unicidad de RN-17 pasa a evaluarse sobre su forma normalizada (solo dígitos). Dos grafías del mismo número dejan de ser dos procesos.
2. **Se avisa, no se bloquea,** cuando el radicado no tiene la forma de uno colombiano (23 dígitos). Bloquear sería invadir lo que RN-36 reserva al abogado: hay tutelas y procesos antiguos con otros formatos, y **quien sabe cuál es el suyo es él**.
3. **Cuando exista RF-35**, al consultar la Rama Judicial se comparará el juzgado que responde con el registrado, y se avisará si no coinciden. Es lo que atrapa el dígito que cayó sobre un proceso real de otro despacho.

**Lo que NO se decide aquí.** No se rechaza ningún radicado por su formato, ni se corrige automáticamente lo que el abogado escribió. El sistema vigila lo que él registró; esa frontera no se mueve.

**Un fallo de la primera implementación, que conviene dejar escrito.** La regla de normalizar se escribió como «quitar todo lo que no sea dígito», y es correcta **solo para lo que es un radicado**. Aplicada a cualquier texto destruye información: `RAD-ff5c40e8A` y `RAD-ff5c40e8B` —dos procesos distintos— quedaban en `5408` los dos, y RN-17a los declaraba el mismo.

Lo destaparon **nueve pruebas de integración**, no la prueba negativa que se escribió para eso: esa solo usaba radicados de 23 dígitos, donde la regla mala también funciona. La regla final tiene dos caminos —si al quitar la puntuación quedan los 23 dígitos, esa es la forma normalizada; si no, se conserva todo y solo se ignoran espacios y mayúsculas— y ahora sí hay una prueba con el caso que falló.

---

## 5. Supuestos vigentes [S]

Se trabaja con ellos hasta que se validen. Cada uno indica cuándo debe cerrarse.

### 5.1 Supuestos vigentes

**Ninguno.** Los cinco supuestos abiertos durante el proyecto quedaron cerrados.

### 5.2 Supuestos ya cerrados

| ID | Supuesto original | Desenlace |
|---|---|---|
| S-01 | Suscripción por despacho escalada por número de abogados | **Anulado.** La monetización sale del sistema por completo → **D-06** |
| S-02 | Existen tres perfiles: administrador de despacho, abogado y cliente | **Confirmado y precisado.** Los roles internos son acumulables → **D-07** |
| S-05 | Honorarios y facturación fuera de alcance | **Confirmado** → **D-08** |
| S-03 | Escala objetivo sin cifras confirmadas | **Cuantificado y adoptado** como línea base → **D-19** (RNF-12, RNF-13) |
| S-04 | Conservación y respaldo | **Cuantificado y adoptado** como línea base → **D-19** (RNF-14, RNF-15) |

---

## 5.bis Asuntos abiertos para la Fase 2 [A]

No son vacíos de la propuesta ni supuestos: son **preguntas nuevas que nacieron de las decisiones D-06, D-07 y D-09**. Cada decisión cerró un vacío y abrió una consecuencia que exige una regla de negocio. Se resuelven al abrir la Fase 2, no se inventan.

| ID | Asunto | Nace de | Estado |
|---|---|---|---|
| **A-01** | ¿El Administrador de Despacho puede leer el contenido de todos los expedientes, o su rol es solo administrativo? | D-07 | **Resuelto** → **D-11** (hace ambas cosas) |
| **A-02** | Cuando un despacho pasa a inactivo: ¿alertas?, ¿acceso del abogado?, ¿acceso del cliente?, ¿datos? | D-06 | **Resuelto** → **D-10** (bloqueo total, sin borrado) |
| **A-03** | ¿El cliente ve todos los documentos y actuaciones, o pieza por pieza? | D-09 | **Resuelto** → **D-12** (ve todos) |

| **A-04** | `PROCESO.juzgado` como texto libre degrada la búsqueda que exige P-RNF02 | Modelo de datos (Fase 5) | **Resuelto** → **D-17** (quinto catálogo, por despacho) |

| **A-05** | Con el volumen objetivo, **2.499 alertas vencen en el mismo instante** y el motor drenaba 100 cada 5 minutos: el pico tardaba 125 minutos frente a los 15 que tolera RNF-11. | Medición de rendimiento (D-25) | **Resuelto** → **D-27** (envío en paralelo, lote 3.000). Queda elegir proveedor de correo que admita ~6 envíos/segundo |

**Los cinco asuntos quedan cerrados.** RNF-11 y RNF-12 quedan verificados con evidencia reproducible. Lo único pendiente de A-05 no es técnico: elegir un proveedor de correo que admita el caudal medido (D-27).

---

## 6. Fuera de alcance declarado

No se construye, aunque sea tentador o aunque el dominio jurídico lo sugiera:

- Litigio en línea, radicación de demandas o presentación de memoriales ante el juzgado.
- Firma electrónica o digital de documentos.
- Honorarios, facturación al cliente, nómina o contabilidad del despacho **[D-08]**. *(Los **horarios** —fecha y hora de audiencias— sí están dentro; son la base de las alertas.)*
- Suscripciones, precios, pasarelas de pago y cobro de la plataforma **[D-06]**. El sistema solo marca el despacho como activo o inactivo.
- Cálculo automático del vencimiento de un término a partir de normas procesales. **El sistema controla y alerta sobre la fecha que el abogado registra; no interpreta la ley ni computa plazos por sí mismo.** Esta frontera es central y se convertirá en regla de negocio.
- Asesoría jurídica automatizada o cualquier sugerencia de contenido legal.
- Aplicación móvil nativa. La propuesta dice **sistema web**.

---

## 7. Ruta de trabajo acordada

```
Fase 1  Idea y Definición de Negocio          ← en curso
Fase 2  Reglas de Negocio (RN)
Fase 3  Requisitos Funcionales (RF) y No Funcionales (RNF)
Fase 4  Historias de Usuario + Criterios de Aceptación (con RF/RNF asociados)
Fase 5  Diagramas: casos de uso, funcional, modelo de datos, flujo del sistema,
        flujos individuales, clases, despliegue, componentes, secuencia, actividad
Fase 6  Arquitectura del Sistema bajo ISO/IEC/IEEE 42010
```

Cada fase se valida antes de abrir la siguiente. Cada artefacto de una fase referencia el artefacto de la fase anterior que le dio origen.

---

## 8. Bitácora de cambios

| Fecha | Cambio | Motivo |
|---|---|---|
| 2026-08-20 | Creación del registro. Decisiones D-01 a D-04. Supuestos S-01 a S-05. | Cierre de vacíos bloqueantes para iniciar Fase 1. |
| 2026-08-20 | Decisiones **D-05 a D-09**. Cierre de V-05, V-09, V-10, V-11 y cierre parcial de V-06. Anulación de S-01, S-02 y S-05. Apertura de los asuntos **A-01, A-02 y A-03**. | Respuestas del interesado a las 5 preguntas de cierre de la Fase 1. **Fase 1 cerrada.** |
| 2026-08-20 | Decisiones **D-10, D-11 y D-12**. Cierre de A-01, A-02 y A-03. Cierre completo de V-06. | Respuestas del interesado a los tres asuntos. **Apertura de la Fase 2 (Reglas de Negocio).** |
| 2026-08-20 | Decisiones **D-13 a D-16**. Apertura del riesgo **R-08** (configurabilidad de las alertas) con su tratamiento. | Respuestas del interesado a las 5 preguntas de cierre de la Fase 2. **Fase 2 cerrada**, salvo dos confirmaciones menores dentro de D-16. |
| 2026-08-20 | **Fase 3** — 36 RF y 16 RNF. Cierre cuantificado de **S-03** y **S-04** (pendiente de validación de cifras). Apertura del riesgo **R-09** (concentración de los Sprints 1 y 3). | Derivación de los requisitos a partir de las 55 reglas de negocio. |
| 2026-08-20 | **Fase 4** — 42 historias de usuario con criterios de aceptación. La verificación inversa detectó dos reglas huérfanas (**RN-50** y **RN-51**), que obligaron a añadir **RF-37** y **RF-38** a la Fase 3 (ahora 38 RF, 54 requisitos). | La fase siguiente audita a la anterior: RN-51 es parte del tratamiento del riesgo crítico R-02 y se habría quedado sin llegar al código. |
| 2026-08-20 | **Fase 5** — 15 diagramas en 10 categorías. Apertura del asunto **A-04** (`PROCESO.juzgado` como texto libre degrada la búsqueda que exige P-RNF02). | Modelado de datos. |
| 2026-08-20 | **Fase 6** — Descripción Arquitectónica ISO/IEC/IEEE 42010: 8 interesados, 12 preocupaciones, 7 puntos de vista, 8 decisiones arquitectónicas (ADR-01 a ADR-08), 6 riesgos arquitectónicos. Cierre del pendiente de la Fase 5 sobre emisión duplicada de alertas (**ADR-04**). | **Ingeniería de requerimientos y arquitectura completadas.** |
| 2026-08-20 | Decisiones **D-17 a D-20**. Cierre de **A-04**, de los supuestos **S-03** y **S-04**, y de los riesgos **RA-3**, **RA-4** y **R-09**. Corrección de RNF-11 (tolerancia 1 h → 15 min). | Cierre de los cuatro pendientes que quedaban tras la Fase 6. **No queda ningún asunto abierto.** |
| 2026-08-21 | Decisión **D-24**. Se añaden **RN-53** y **RN-54**, **RF-39** y **RF-40**, **HU-43** y **HU-44**: el sistema no tenía forma de cambiar ni restablecer una contraseña. Ahora **40 RF, 56 requisitos** y **44 historias**. Corregida la cifra de origen de §5.2, que llevaba desactualizada desde la Fase 4 (decía «23 de los 52»; el recuento real era 26 de 54, y con D-24 pasa a 28 de 56). | Lo detectó la **construcción del frontend**: escribir en pantalla qué le ocurre al cliente tras recibir su clave obligó a decir la verdad sobre el sistema, y la verdad no se sostenía. La propuesta nunca mencionó cambiar una contraseña, así que ninguna regla lo exigía. |
| 2026-08-21 | Medición de rendimiento contra el volumen objetivo. **RNF-12 verificado** (16 consultas, peor tiempo 584 ms sobre un límite de 3.000). Decisión **D-25**: **RNF-11 INCUMPLE** —el pico de 2.499 alertas en un mismo instante tarda 125 minutos en drenarse frente a los 15 de tolerancia—. Se abre el asunto **A-05**. | El defecto no está en la consulta (0,35 ms, usa índice) sino en el caudal del motor: 100 alertas cada 5 minutos. Ninguna prueba lo detectó porque todas comprueban RNF-11 **por alerta**, y el incumplimiento solo existe en el conjunto. |
| 2026-08-21 | Decisión **D-26**. Medido el envío real por SMTP (`mvnw test -Prendimiento`): la salida de A-05 es **enviar en paralelo** —6,6 min con 4 conexiones frente a 36,8 de hoy—, no agrupar el lote. | La hipótesis de D-25 era que reutilizar la conexión bastaría. La medición dijo que no: ahorra 4 de 14 viajes de red por alerta, porque los otros 10 son del protocolo y van por mensaje. **Con el código de hoy, cualquier proveedor a más de 50 ms incumple RNF-11**, y de Neiva a Estados Unidos son 80-120. |
| 2026-08-21 | Corregida la fila 6 de la lista de controles de **D-23**: decía «Pendiente» y el cifrado en reposo lleva implementado desde el Sprint 3 (AES-256-GCM). | Una lista de seguridad desactualizada es peor que no tenerla: invita a gastar el esfuerzo del despliegue en algo ya hecho, y a confiar en que el resto de la lista está al día cuando esta fila demostraba que no. |
| 2026-08-21 | Cerrado el pendiente de diagramas declarado en **D-24**: **CU-28** (cambiar mi contraseña) y **CU-29** (restablecer la de un usuario) en el diagrama de casos de uso, y **F26** en el funcional. | Al cerrarlo se vio que el pendiente estaba **declarado a medias**: decía que faltaban en el diagrama de casos de uso, y también faltaban en el funcional. Se documenta que CU-29 **no lo alcanza el Administrador de Plataforma** (RN-10): restablecer la clave de un abogado le daría acceso efectivo a expedientes ajenos, que es lo que prohíbe RN-02. |
| 2026-08-22 | Decisión **D-27**: **A-05 queda resuelto** por envío en paralelo (lote 3.000, 4 conexiones). El pico de 2.499 alertas pasa de 125 minutos a un solo barrido. | La opción B dejó de ser la arriesgada al corregir **H-6**: el motor ya no es una sola transacción, así que paralelizar dejó de exigir compartir sesión de Hibernate entre hilos. El obstáculo desapareció arreglando otra cosa. |
| 2026-08-23 | Decisión **D-28**: el radicado se **normaliza para comparar** y se **avisa** si no tiene forma de radicado colombiano. | Verificado que el mismo radicado con y sin espacios creaba **dos procesos distintos** en el mismo despacho, cada uno con su expediente: los términos del mismo caso quedaban repartidos entre los dos. Ninguna prueba lo vio porque todas comprueban que no se repita **idéntico**. Salió de una pregunta del analista sobre RF-35. |
| 2026-08-23 | **Barrido de consistencia de la documentación.** Corregidas siete cifras desfasadas: reglas (58 → **60**), historias (42 → **44**) y decisiones (20 → **28**) en los documentos 02, 04, 05 y 06. Añadidos al modelo de datos la columna `radicado_normalizado` (D-28) y a **ADR-04** la revisión que le hicieron **D-27** y **D-28** durante la construcción. | El documento 06 se **contradecía a sí mismo**: decía 58 reglas en el alcance y 56 en la cadena de cierre. Es el mismo defecto que ya obligó a corregir «23 de los 52» y la fila 6 de D-23: una cifra que no cuadra hace dudar de todo lo demás, y la arquitectura que se sustenta debe ser la que corre, no la que se diseñó. |
