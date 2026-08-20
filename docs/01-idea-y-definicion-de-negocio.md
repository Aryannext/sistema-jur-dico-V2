# Fase 1 — Idea y Definición de Negocio

**Proyecto:** Sistema de Gestión de Procesos Jurídicos para Consultorios de Abogados
**Fuente base:** `24_propuesta.pdf` — Propuesta 24, Competencia 220501094
**Versión:** 1.1 · **Fecha:** 2026-08-20 · **Estado: APROBADA** — cerrada con las decisiones D-05 a D-09

> **Convención de trazabilidad.** Cada afirmación se marca con su origen: **[P]** proviene literalmente de la propuesta · **[D]** es una decisión registrada en [`00-decisiones-y-trazabilidad.md`](00-decisiones-y-trazabilidad.md) · **[S]** es un supuesto declarado y aún no validado. No hay afirmaciones sin marca.

---

## 1. Identidad del producto

| Campo | Valor |
|---|---|
| Nombre comercial | **Iuris** **[D-05]** — del latín *ius, iuris*: "del derecho" |
| Nombre técnico | **SGPJ** — Sistema de Gestión de Procesos Jurídicos |
| Uso de cada nombre | *Iuris* en interfaz, portal del cliente y comunicaciones. *SGPJ* en documentación de ingeniería, diagramas y arquitectura. |
| Tipo de producto | Aplicación **web** **[P]**, ofrecida como plataforma multi-despacho en la nube **[D-01]** |
| Sector | Jurídico / Legal **[P]** |
| Mercado inicial | Consultorios jurídicos y abogados independientes de **Neiva** (Huila, Colombia) **[P]** |

---

## 2. El problema

### 2.1 Enunciado del problema **[P]**

Los consultorios jurídicos y abogados independientes de Neiva gestionan sus procesos judiciales con **carpetas físicas, agendas personales y recordatorios manuales**. De ahí se desprenden tres fallas concretas y una consecuencia:

| # | Falla | Consecuencia directa |
|---|---|---|
| P-1 | Las fechas de audiencias **se olvidan** | Inasistencia o preparación deficiente de la audiencia |
| P-2 | Los términos judiciales **vencen por falta de seguimiento** | Pérdida de oportunidad procesal |
| P-3 | La documentación de los expedientes **se desorganiza** | El abogado no encuentra la pieza cuando la necesita |
| → | **Impacto agregado** | Se afecta la calidad del servicio al cliente y se generan **riesgos de sanciones disciplinarias** |

### 2.2 Por qué el problema no se resuelve solo

El análisis del enunciado revela que la causa raíz no es la falta de voluntad del abogado, sino que **la información crítica está dispersa en tres soportes que no se hablan entre sí**:

```
   CARPETA FÍSICA          AGENDA PERSONAL         MEMORIA / NOTAS
   (documentos)            (fechas)                (estado del caso)
        │                       │                        │
        └───────── sin vínculo entre sí ─────────────────┘
                            │
                   El abogado es el único
                   punto de integración
```

Consecuencia analítica: **el abogado actúa como el sistema**. Si él no recuerda, no hay respaldo. Cuanto más casos lleva, más probable es el fallo. El problema escala con el éxito del despacho.

Segunda consecuencia: **el término judicial es un evento de tiempo, no de documento.** Vence solo, sin que nadie lo toque. Un archivador ordenado no evita que un plazo se cumpla; solo un mecanismo que vigile el calendario lo evita. Por eso el núcleo del sistema no es "guardar papeles", es **vigilar el tiempo**.

### 2.3 Naturaleza del riesgo

El riesgo que menciona la propuesta —sanciones disciplinarias **[P]**— tiene una característica que define todo el diseño: **es irreversible**. Un término vencido no se recupera. Esto se traduce en dos exigencias de negocio:

- El sistema debe **avisar antes**, no informar después. Un reporte de términos vencidos llega tarde por definición.
- El sistema debe ser **confiable en la emisión de la alerta**. Una alerta que no se envía es peor que no tener sistema, porque el abogado ya dejó de vigilar manualmente confiando en él.

Esta última frase es la restricción de negocio más importante del proyecto y se convertirá en regla de negocio y en RNF de confiabilidad.

---

## 3. La idea de solución

### 3.1 Enunciado de la idea

> **SGPJ es una plataforma web que reemplaza la carpeta física, la agenda personal y el recordatorio manual del abogado por un expediente digital único por proceso, que vigila por sí mismo las fechas de audiencias y los términos judiciales y avisa al abogado por correo antes de que venzan.**

Traducción de cada falla en capacidad del sistema:

| Falla | Capacidad que la ataca | Origen |
|---|---|---|
| P-1 Audiencias olvidadas | Calendario de audiencias con alerta automática a 48h, 24h y el día del evento | **[P]** RF03 |
| P-2 Términos vencidos | Control de términos con fecha de vencimiento y alerta anticipada | **[P]** RF04 |
| P-3 Documentación desorganizada | Expediente digital por proceso con documentos, actuaciones y notas en un solo lugar | **[P]** RF02 |
| Falta de visión del despacho | Reportes de procesos activos, archivados y por estado procesal | **[P]** RF05 |
| Cliente desinformado | Portal web donde el cliente consulta su propio expediente | **[P]** RNF03 |
| Seguimiento manual de actuaciones | Consulta al servicio de la Rama Judicial por radicado | **[D-04]** *(ampliación fuera de la propuesta)* |

### 3.2 Los tres pilares del producto

```
┌─────────────────────────────────────────────────────────────────┐
│                            S G P J                              │
├──────────────────┬──────────────────┬───────────────────────────┤
│  1. EXPEDIENTE   │   2. VIGILANCIA  │   3. TRANSPARENCIA        │
│     DIGITAL      │      DEL TIEMPO  │      AL CLIENTE           │
├──────────────────┼──────────────────┼───────────────────────────┤
│ Todo el caso en  │ El sistema mira  │ El cliente ve su caso     │
│ un solo lugar:   │ el calendario    │ sin llamar al abogado.    │
│ documentos,      │ por el abogado   │ Solo el suyo, solo lo     │
│ actuaciones,     │ y avisa antes.   │ que el despacho publique. │
│ notas, partes.   │                  │                           │
├──────────────────┼──────────────────┼───────────────────────────┤
│ Resuelve P-3     │ Resuelve P-1,P-2 │ Resuelve el impacto en    │
│                  │ ← núcleo crítico │ calidad de servicio       │
└──────────────────┴──────────────────┴───────────────────────────┘
```

**El pilar 2 es el corazón del producto.** Los pilares 1 y 3 existen en cualquier gestor documental; el pilar 2 es lo que responde a la razón por la que se pide el sistema.

### 3.3 Propuesta de valor

**Para el abogado o consultorio jurídico** que lleva sus procesos en carpetas físicas y agenda personal, **que necesita** no dejar vencer un término ni faltar a una audiencia, **SGPJ es** una plataforma web de gestión de procesos **que** centraliza el expediente y vigila automáticamente los vencimientos, **a diferencia de** la carpeta física y la agenda, **nuestro producto** avisa por sí solo antes de que el plazo se cumpla y le permite al cliente consultar su caso sin interrumpir al abogado.

Valor entregado, por interesado:

| Interesado | Antes | Con SGPJ | Valor |
|---|---|---|---|
| Abogado | Recuerda o revisa carpeta por carpeta | Recibe el aviso antes del vencimiento | Reduce el riesgo disciplinario **[P]** |
| Consultorio | No sabe cuántos casos activos tiene ni en qué estado | Reportes por estado procesal **[P]** RF05 | Visión de la carga real de trabajo |
| Cliente | Llama al abogado para saber cómo va | Consulta su expediente en el portal **[P]** RNF03 | Percepción de servicio profesional |
| Despacho | La información vive con quien lleva el caso | La información vive en el despacho | Continuidad si el abogado se ausenta |

### 3.4 Lo que el sistema NO es — frontera de responsabilidad

Esta frontera protege legalmente al producto y debe respetarse en todas las fases siguientes:

- **No calcula plazos legales.** El sistema controla y alerta sobre la fecha de vencimiento que **el abogado registra**. No interpreta normas procesales ni computa términos por su cuenta. **La responsabilidad del cómputo es del abogado; la del recordatorio es del sistema.**
- **No presta asesoría jurídica** ni sugiere estrategias, escritos o decisiones.
- **No sustituye la consulta oficial.** La información traída de la Rama Judicial **[D-04]** es apoyo al seguimiento, no fuente oficial. El abogado conserva el deber de verificar.
- **No radica ni presenta actuaciones** ante juzgados.
- **No gestiona honorarios, facturación al cliente, ni contabilidad del despacho** **[D-08]**. *(Distíngase de los **horarios**: la fecha y hora de las audiencias sí son responsabilidad central del sistema — son la base del cálculo de las alertas.)*
- **No gestiona la suscripción ni el cobro de la plataforma** **[D-06]**. La monetización ocurre por fuera; el sistema solo refleja si el despacho está activo o inactivo.

---

## 4. Objetivos

### 4.1 Objetivo general **[P]**

> Desarrollar un sistema web de gestión de procesos jurídicos que administre el expediente digital de cada caso, controle fechas de audiencias y términos, gestione la documentación asociada y genere alertas automáticas de vencimiento para el abogado.

### 4.2 Objetivos específicos

Derivados del objetivo general y de los enunciados de la propuesta. Cada uno anticipa el grupo de requisitos que lo realizará:

| ID | Objetivo específico | Origen | Sprint **[P]** |
|---|---|---|---|
| OE-1 | Registrar clientes y crear el expediente digital de cada proceso, con sus datos de identificación procesal. | RF01, RF02 **[P]** | 1 |
| OE-2 | Centralizar en el expediente los documentos, actuaciones y notas del proceso, con almacenamiento cifrado. | RF02, RNF01 **[P]** | 2 |
| OE-3 | Vigilar las fechas de audiencias y los términos judiciales, y emitir alertas automáticas anticipadas por correo. | RF03, RF04 **[P]**, canal por **[D-03]** | 3 |
| OE-4 | Permitir al cliente consultar su propio expediente mediante acceso restringido a un portal web. | RNF03 **[P]** | 4 |
| OE-5 | Generar reportes del estado de los procesos del despacho para apoyar la gestión. | RF05 **[P]** | 4 |
| OE-6 | Localizar cualquier proceso por radicado, cliente, juzgado o tipo de proceso. | RNF02 **[P]** | transversal |
| OE-7 | Operar como plataforma multi-despacho con aislamiento estricto de datos entre despachos. | **[D-01]** | transversal |
| OE-8 | Consultar las actuaciones publicadas de un proceso en la Rama Judicial a partir del radicado. | **[D-04]** — *ampliación* | posterior a 4 |

**Nota de planificación:** OE-8 no cabe en los 4 sprints de la propuesta **[P]**, que ya están asignados a OE-1…OE-5. Se marca como incremento posterior. Ver riesgo R-03 en §9.

---

## 5. Modelo de negocio

> Toda esta sección es **[D-01]** más **[D-06]**. La propuesta no describe modelo de negocio; describe una necesidad y un objetivo. Se documenta porque la decisión de ser plataforma multi-despacho obliga a definirlo.
>
> **Advertencia de lectura, importante:** por **[D-06]** la monetización ocurre **fuera del software**. Los bloques de este lienzo que hablan de ingresos, canales de venta y costos describen **el negocio que rodea al producto**, no funcionalidad a construir. Nada de esta sección genera requisitos, salvo un único atributo: el **estado activo/inactivo del despacho** (§5.2).

### 5.1 Lienzo de modelo de negocio (resumen)

| Bloque | Contenido |
|---|---|
| **Segmento de clientes** | Primario: consultorios jurídicos pequeños y medianos de Neiva. Secundario: abogados independientes (despacho de una sola persona). Usuario final indirecto: el cliente del abogado. **[P]** define el segmento; **[D-01]** lo convierte en multi-tenant. |
| **Propuesta de valor** | La de §3.3: cero términos vencidos por olvido, expediente único, cliente informado. |
| **Canales** | Aplicación web. Venta y soporte directo, **fuera del sistema** **[D-06]**. |
| **Relación con el cliente** | Autoservicio en la operación diaria; acompañamiento en el alta del despacho y migración inicial de expedientes. **[S]** |
| **Fuentes de ingreso** | **Se gestionan íntegramente fuera del software** **[D-06]**. El sistema no conoce planes, precios ni pagos: solo refleja el resultado comercial marcando el despacho como **activo** o **inactivo**. |
| **Recursos clave** | La plataforma; el almacenamiento cifrado de documentos; la confianza del gremio jurídico. |
| **Actividades clave** | Desarrollo y operación de la plataforma; garantía de entrega de las alertas; custodia y confidencialidad de los expedientes. |
| **Socios clave** | Proveedor de nube y almacenamiento; proveedor de envío de correo; Rama Judicial como fuente de consulta **[D-04]** (no es un socio contractual, es una dependencia externa — ver R-01). |
| **Estructura de costos** | Infraestructura y almacenamiento (crece con el volumen documental); envío de correo; desarrollo y soporte. |

### 5.2 La unidad de negocio: el Despacho **[D-01]**

La decisión de multi-tenencia introduce la entidad más importante del modelo de datos futuro:

```
PLATAFORMA SGPJ
│
├── Despacho A (tenant) ──┬── Abogados
│                         ├── Clientes
│                         ├── Procesos ── Expediente ── Documentos / Actuaciones / Notas
│                         ├── Audiencias
│                         └── Términos
│
├── Despacho B (tenant) ──── … totalmente aislado de A
└── …
```

Tres consecuencias que se arrastran a todas las fases siguientes:

1. **Todo dato pertenece a un despacho.** No existe cliente, proceso ni documento "de la plataforma".
2. **El aislamiento entre despachos es un requisito de seguridad crítico**, no una configuración. Que el despacho A vea un expediente del despacho B sería una fuga de información sometida a reserva profesional. Esto será un RNF de máxima prioridad.
3. **Aparece un actor que la propuesta no tiene:** el Administrador de la Plataforma, que gestiona el alta y el estado de los despachos, y que **no** tiene acceso al contenido de los expedientes.

### 5.3 El estado del despacho: la única huella del negocio en el software **[D-06]**

Como la monetización queda fuera, todo el modelo comercial se reduce dentro del sistema a **un atributo con dos valores**:

```
        FUERA DEL SISTEMA                    DENTRO DEL SISTEMA
   ┌───────────────────────────┐        ┌─────────────────────────┐
   │  Contrato, precio, cobro, │  ───►  │  Despacho.estado        │
   │  factura, renovación      │        │    · ACTIVO             │
   │  (gestión comercial)      │        │    · INACTIVO           │
   └───────────────────────────┘        └─────────────────────────┘
      El Administrador de Plataforma cambia el estado manualmente
```

Esta simplificación es una buena decisión de alcance: elimina pasarelas de pago, planes y facturación del proyecto. **Pero abre una pregunta que no puede quedar sin respuesta**, porque toca el riesgo más grave del sistema:

> **¿Qué deja de funcionar cuando un despacho pasa a INACTIVO?**
> Si las alertas se detienen, los términos de ese despacho empezarán a vencer en silencio — exactamente el daño que el sistema existe para evitar (**R-02**). Si no se detienen, el estado no significa nada en la práctica.

Es una **regla de negocio**, no una decisión técnica, y por eso no se resuelve aquí: queda registrada como **asunto A-02** para la Fase 2. Afecta a cuatro comportamientos distintos: emisión de alertas, acceso del abogado, acceso del cliente al portal y conservación de los datos.

---

## 6. Actores e interesados

### 6.1 Actores del sistema

| Actor | Origen | Descripción | Alcance de su acceso |
|---|---|---|---|
| **Abogado** | **[P]** | Lleva los procesos. Registra clientes, expedientes, actuaciones, documentos, audiencias y términos. Es el **destinatario de las alertas** **[P]**. | Los procesos de su despacho que le correspondan |
| **Cliente** | **[P]** RNF03 | Persona a quien el despacho representa. Consulta el estado y la información de **su propio** expediente en el portal. | Solo su expediente, solo lectura |
| **Administrador de Despacho** | **[D-07]** | Rol **distinto** del Abogado. Gestiona los usuarios, la configuración y los catálogos de su despacho. | Su despacho. *(Su alcance sobre el **contenido** de los expedientes queda pendiente → asunto **A-01**.)* |
| **Administrador de Plataforma** | **[D-01]** | Da de alta despachos, gestiona su estado y suscripción, opera la plataforma. | Metadatos de despachos. **Nunca el contenido de los expedientes.** |
| **Sistema (actor temporal)** | **[P]** RF03/RF04 | Actor no humano. Ejecuta la vigilancia periódica de audiencias y términos y dispara las alertas sin intervención humana. | Interno |
| **Servicio Rama Judicial** | **[D-04]** | Sistema externo consultado por radicado. Actor externo, no controlado. | Externo, solo lectura |

### 6.1.1 Los roles son acumulables, y eso cambia el modelo **[D-07]**

Administrador de Despacho y Abogado son roles **distintos**, pero **una misma persona puede tener los dos**. Ese es precisamente el caso del abogado independiente, que la propuesta nombra de forma explícita **[P]**.

```
  CONSULTORIO CON VARIOS ABOGADOS        ABOGADO INDEPENDIENTE
  ───────────────────────────────        ─────────────────────────
   Ana    → [Admin. Despacho]             Carlos → [Admin. Despacho]
   Beto   → [Abogado]                              [Abogado]
   Carmen → [Abogado]                     ↑ una sola persona,
                                            dos roles simultáneos
```

**Consecuencia de modelado, y es de las importantes del proyecto:** la relación entre usuario y rol es **de uno a muchos, no de uno a uno**. Un usuario tiene un *conjunto* de roles dentro de su despacho y sus permisos se evalúan por la **unión** de ellos.

Modelar "un usuario = un rol" parecería más simple, pero **haría imposible el caso del abogado independiente**: obligaría a crear dos cuentas para la misma persona, o a inventar un tercer rol mixto que se desincronizaría de los otros dos. Esta restricción se arrastra al modelo de datos, al diagrama de clases y al diseño de seguridad, así que queda fijada desde ahora.

Corolario operativo: **el alta de un despacho independiente crea un usuario con dos roles, no dos usuarios.**

**Observación de análisis:** el *Sistema* como actor temporal no es un formalismo. Es el actor que ejecuta la funcionalidad que justifica el proyecto: nadie pulsa un botón para que llegue la alerta de las 48 horas. Aparecerá explícitamente en el diagrama de casos de uso (Fase 5).

### 6.2 Interesados no usuarios

| Interesado | Interés |
|---|---|
| Product Owner (instructor) **[P]** | Que el sistema cumpla lo propuesto y sea evaluable |
| Scrum Master / Development Team **[P]** | Entregar los 4 sprints con incrementos funcionales |
| Autoridad disciplinaria del abogado | No es usuario, pero **su existencia es la razón del sistema** **[P]** |

---

## 7. Procesos de negocio que el sistema soporta

Cuatro procesos, encadenados. Se detallarán como flujos en la Fase 5.

**PN-1 · Vinculación del cliente y apertura del proceso** — *Sprint 1* **[P]**
El despacho registra al cliente con sus datos personales y el tipo de proceso **[P] RF01**, y abre el expediente digital identificado por su radicado **[P] RF02**.

**PN-2 · Gestión del expediente a lo largo del caso** — *Sprint 2* **[P]**
A medida que el proceso avanza, el abogado incorpora documentos, registra actuaciones y toma notas dentro del mismo expediente **[P] RF02**. Los documentos se almacenan cifrados **[P] RNF01**.

**PN-3 · Vigilancia de audiencias y términos** — *Sprint 3* **[P]** — ★ proceso crítico
El abogado registra la audiencia o el término con su fecha. El sistema vigila el calendario de forma autónoma y emite las alertas anticipadas por correo **[P] RF03, RF04 · [D-03]**. Es el único proceso que ocurre **sin que ningún usuario lo inicie**.

**PN-4 · Información al cliente y control del despacho** — *Sprint 4* **[P]**
El cliente consulta su expediente en el portal con acceso restringido **[P] RNF03**; el despacho consulta los reportes de procesos activos, archivados y por estado procesal **[P] RF05**.

**PN-5 · Sincronización de actuaciones desde la Rama Judicial** — *incremento posterior* **[D-04]**
El sistema consulta por radicado las actuaciones publicadas y las ofrece al abogado. **Degradable:** si el servicio externo falla, PN-2 sigue funcionando con registro manual.

---

## 8. Glosario del dominio

Términos definidos **por su papel en el sistema**, no como definiciones normativas. Esta es la base semántica que usarán las reglas de negocio, los requisitos y el modelo de datos.

| Término | Definición operativa para el SGPJ |
|---|---|
| **Despacho** | Consultorio jurídico o abogado independiente registrado en la plataforma. Es la unidad de aislamiento de datos. **[D-01]** |
| **Cliente** | Persona natural o jurídica a la que el despacho representa. Puede tener uno o varios procesos. **[P]** |
| **Proceso** | Caso jurídico que el despacho lleva para un cliente. Es la unidad central del sistema. **[P]** |
| **Radicado** | Número que identifica un proceso ante la autoridad judicial. En el SGPJ es el identificador de negocio del proceso y un criterio de búsqueda obligatorio **[P] RNF02**. *(Su formato exacto se especifica en la Fase 3.)* |
| **Expediente digital** | Conjunto completo de la información de un proceso: documentos, actuaciones y notas. Relación **uno a uno con el proceso**. **[P]** |
| **Actuación** | Registro fechado de algo ocurrido en el proceso. Es el historial del caso y suele ser el hecho que da origen a un término. **[P]** |
| **Documento** | Archivo adjunto al expediente. Se guarda cifrado. **[P] RF02, RNF01** |
| **Nota** | Anotación del abogado sobre el proceso. **De uso exclusivamente interno del despacho: nunca visible para el cliente en ninguna circunstancia.** **[P] · [D-09]** |
| **Audiencia** | Diligencia programada en fecha y hora determinadas a la que el abogado debe asistir. Genera alertas a 48h, 24h y el día del evento. **[P] RF03** |
| **Término judicial** | Plazo con fecha de vencimiento dentro del cual el despacho debe realizar una actuación. **El sistema vigila la fecha registrada por el abogado; no la calcula.** **[P] RF04** |
| **Estado procesal** | Situación en la que se encuentra un proceso. Criterio de clasificación en reportes. **[P] RF05.** *(El catálogo de estados se define en la Fase 2.)* |
| **Tipo de proceso** | Clasificación del proceso según su naturaleza. Se registra desde el alta del cliente **[P] RF01** y es criterio de búsqueda **[P] RNF02**. *(El catálogo se define en la Fase 2.)* |
| **Juzgado** | Autoridad judicial que conoce del proceso. Criterio de búsqueda obligatorio, tomado del **catálogo de juzgados del despacho**, no como texto libre. **[P] RNF02** · **[D-17]** |
| **Alerta** | Aviso automático emitido por el sistema, sin intervención humana, anticipándose a una fecha. Se entrega por correo. **[P] · [D-03]** |
| **Portal del cliente** | Área del sistema de acceso restringido donde el cliente consulta únicamente su propio expediente. **[P] RNF03** |
| **Rol** | Conjunto de facultades de un usuario dentro de su despacho. Un usuario **puede tener varios roles a la vez**; sus permisos son la unión de ellos. **[D-07]** |
| **Estado del despacho** | Marca con dos valores —**activo** / **inactivo**— que refleja dentro del sistema el resultado de una gestión comercial que ocurre por fuera. El sistema no conoce planes, precios ni pagos. **[D-06]** |

**Catálogos definidos en la Fase 2:** estados procesales, tipos de proceso, tipos de documento, tipos de actuación y —añadido por **D-17**— juzgados. No se inventaron aquí; se definieron como reglas de negocio con su justificación, y son administrables por cada despacho.

---

## 9. Riesgos de negocio

| ID | Riesgo | Impacto | Tratamiento |
|---|---|---|---|
| **R-01** | El servicio de consulta de la Rama Judicial no está disponible, cambia o no admite el uso previsto. **[D-04]** | Alto sobre PN-5, **nulo sobre el núcleo** | Integración desacoplada y degradable. Ningún RF de la propuesta puede depender de ella. Verificación técnica antes de comprometer requisitos. |
| **R-02** | Una alerta no se envía y el abogado, confiado en el sistema, deja vencer un término. | **Crítico.** Es el fallo que destruye el producto. | Confiabilidad de la alerta como RNF de máxima prioridad: reintento, registro de envío auditable y panel de vencimientos como respaldo visual. |
| **R-03** | El incremento OE-8 **[D-04]** no cabe en los 4 sprints de la propuesta **[P]**. | Medio sobre el cronograma | Se planifica como incremento posterior. Los 4 sprints propuestos no se alteran. |
| **R-04** | Fuga de información entre despachos por fallo de aislamiento. **[D-01]** | **Crítico.** Viola la reserva de la información del cliente. | Aislamiento por tenant como RNF crítico, verificado con pruebas específicas. |
| **R-05** | El abogado no adopta el sistema y sigue usando la carpeta física; el sistema queda desactualizado y sus alertas dejan de ser fiables. | Alto | El registro debe ser más rápido que anotar en la agenda. La usabilidad del alta de audiencias y términos es requisito, no adorno. |
| **R-06** | El cliente ve en el portal información interna del despacho (p. ej. notas del abogado). | Alto | **Tratado por [D-09]:** las notas nunca son visibles para el cliente. Pasa a regla de negocio y a criterio de aceptación explícito de las historias del portal. Queda por definir la visibilidad de documentos y actuaciones → **A-03**. |
| **R-07** | Un despacho marcado **inactivo** deja de recibir alertas y sus términos vencen en silencio. | **Crítico** — es R-02 por otra vía | **Tratado por [D-10] y RN-51:** aviso final de corte por correo, para que el abogado sepa desde cuándo vuelve a vigilar él. |
| **R-08** | El despacho configura **cero alertas** de término sin advertirlo, y el sistema obedece mientras el plazo vence. | **Crítico** — es R-02 por la puerta de la configuración | **Tratado por RN-37b y RF-26:** mínimo de una alerta anticipada no desactivable. La configuración decide *cuántas más*, nunca *si*. |
| **R-09** | Concentración de carga en los **Sprints 1 y 3**, de una semana cada uno **[P]**. El Sprint 1 asume una base (autenticación, despachos, roles) que la propuesta no enumeraba; el Sprint 3 contiene el núcleo del producto. | Medio-alto sobre el cronograma | **Tratado por [D-20]:** se mueven 5 requisitos (RF-02, RF-03, RF-31, RF-19, RF-20) para descargar los Sprints 1 y 3 sin alterar los títulos de la propuesta. Requiere visto bueno del Product Owner. |

---

## 10. Criterios de éxito

Medidos contra la necesidad de la propuesta, no contra la cantidad de funciones entregadas:

| ID | Criterio | Falla que valida |
|---|---|---|
| CE-1 | Ninguna audiencia registrada llega a su fecha sin que se hayan emitido sus tres alertas (48h, 24h, día). | P-1 **[P]** |
| CE-2 | Ningún término registrado vence sin alerta previa emitida. | P-2 **[P]** |
| CE-3 | Todo proceso activo tiene su expediente digital con documentos y actuaciones al día. | P-3 **[P]** |
| CE-4 | Cualquier proceso se localiza por radicado, cliente, juzgado o tipo de proceso. | **[P]** RNF02 |
| CE-5 | El cliente consulta su expediente sin llamar al despacho, y no puede ver ningún otro. | **[P]** RNF03 |
| CE-6 | Ningún despacho accede a datos de otro despacho. | **[D-01]**, R-04 |

**El criterio raíz, del que dependen todos:** *que el abogado deje de ser el mecanismo de vigilancia y pase a ser el destinatario del aviso.*

---

## 11. Cierre de la fase

### Preguntas de cierre — resueltas

| # | Pregunta | Respuesta | Queda como |
|---|---|---|---|
| 1 | Nombre comercial | **Iuris** | **D-05** |
| 2 | Monetización | Fuera del sistema; solo estado activo/inactivo del despacho | **D-06** → abre **A-02** |
| 3 | Honorarios | Fuera de alcance | **D-08** |
| 4 | Roles | Administrador de Despacho ≠ Abogado, pero **acumulables en una persona** | **D-07** → abre **A-01** |
| 5 | Notas internas | Nunca visibles para el cliente | **D-09** → abre **A-03** |

**Fase 1 cerrada.** No quedan preguntas de negocio pendientes.

### Qué se entrega en esta fase

- Problema analizado con sus causas y su carácter irreversible.
- Idea de solución y propuesta de valor.
- Objetivos general y específicos trazados a los enunciados de la propuesta.
- Frontera de responsabilidad del producto.
- Modelo de negocio derivado de la decisión de multi-tenencia.
- Actores, procesos de negocio y glosario del dominio.
- Riesgos y criterios de éxito.

### Qué habilita la siguiente fase

Con este documento aprobado, la **Fase 2 (Reglas de Negocio)** parte de una base cerrada: el glosario da el vocabulario, los procesos de negocio dan los puntos donde se aplican reglas, la frontera de responsabilidad (§3.4) da las restricciones duras, y los riesgos R-02, R-04 y R-06 se convierten directamente en reglas.

Las reglas de negocio que ya se anticipan y se formalizarán en la Fase 2:

- El sistema vigila la fecha registrada por el abogado; no calcula plazos legales. (§3.4)
- Un cliente solo accede a su propio expediente. (**[P]** RNF03)
- Un despacho no accede a datos de otro despacho. (**[D-01]**, R-04)
- Las notas del abogado son de uso interno y no se muestran al cliente. (**[D-09]**, R-06)
- Toda audiencia registrada genera exactamente tres alertas anticipadas. (**[P]** RF03)
- Un usuario puede acumular roles dentro de su despacho; sus permisos son la unión de ellos. (**[D-07]**)

Y los tres asuntos que la Fase 2 debe resolver antes de escribir esas reglas:

| Asunto | Pregunta | Nace de |
|---|---|---|
| **A-01** | ¿El Administrador de Despacho lee el contenido de los expedientes, o su rol es solo administrativo? | D-07 |
| **A-02** | ¿Qué se suspende cuando un despacho pasa a inactivo — alertas, acceso del abogado, acceso del cliente, datos? | D-06 |
| **A-03** | ¿El cliente ve todos los documentos y actuaciones, o la visibilidad se decide pieza por pieza? | D-09 |

Además, la Fase 2 debe cerrar cuatro catálogos del dominio que no se inventan aquí: **estados procesales, tipos de proceso, tipos de documento y tipos de actuación** (§8).
