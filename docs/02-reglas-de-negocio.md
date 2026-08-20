# Fase 2 — Reglas de Negocio (RN)

**Proyecto:** Iuris / SGPJ — Sistema de Gestión de Procesos Jurídicos
**Fuente base:** `24_propuesta.pdf` + [`00-decisiones-y-trazabilidad.md`](00-decisiones-y-trazabilidad.md) + [`01-idea-y-definicion-de-negocio.md`](01-idea-y-definicion-de-negocio.md)
**Versión:** 1.2 · **Fecha:** 2026-08-20 · **Estado: CERRADA** — 56 reglas, decisiones D-13 a D-17 incorporadas

> Marcas de origen: **[P]** propuesta · **[D-nn]** decisión registrada · **[S]** supuesto por validar · **[R-nn]** riesgo que la regla trata.

---

## 1. Qué es y qué no es una regla de negocio aquí

Una **regla de negocio** es una restricción del **dominio jurídico y del negocio** que el sistema debe respetar. Es verdad *antes* de que exista el software y seguiría siendo verdad si el sistema se construyera en otra tecnología.

| Es regla de negocio | No es regla de negocio |
|---|---|
| Un cliente solo puede ver su propio expediente | La sesión expira a los 30 minutos *(→ RNF)* |
| Toda audiencia genera tres alertas anticipadas | Las alertas se envían con un job programado *(→ diseño)* |
| El sistema no calcula plazos legales | La pantalla muestra un calendario mensual *(→ RF/UI)* |

**Por qué importa la distinción:** las reglas de negocio de esta fase son la **fuente** de los RF y RNF de la Fase 3. Si una regla se cuela como requisito o al revés, se pierde la trazabilidad que estamos construyendo.

**Criterio de redacción aplicado:** cada regla es *verificable* (se puede decir si se cumple o no), *atómica* (una sola restricción) y *justificada* (dice por qué existe). Una regla que no se puede violar no es una regla.

---

## 2. Catálogos del dominio

Cuatro catálogos quedaron pendientes de la Fase 1; un quinto (**Juzgados**) se añadió después por **D-17**. Antes de listarlos, la decisión de diseño que los atraviesa:

### 2.1 Los catálogos son administrables, no fijos **[D-13 — confirmado]**

Los catálogos se entregan con **valores iniciales por defecto** —salvo Juzgados, que empieza vacío— y el **Administrador de Despacho** puede añadir, renombrar y desactivar valores en su propio despacho.

> **Consecuencia sobre las listas de §2.2 a §2.5:** al ser administrables, esos valores son **semillas por defecto**, no una definición del sistema. Si un despacho no usa una categoría, la desactiva y crea la suya. Por eso dejan de necesitar validación jurídica previa: el error es corregible por configuración, no por reingeniería.

**Por qué lo propongo así, y es importante:** ninguna de estas listas está fijada por la propuesta, y fijarlas en código nos obligaría a *inventar* una clasificación jurídica y a imponérsela a todos los despachos. Un despacho de familia y uno penal no clasifican igual sus actuaciones. Hacerlos administrables resuelve tres cosas a la vez:

1. **No inventamos derecho.** El despacho pone los nombres que usa en su práctica real.
2. **Reduce el riesgo de adopción R-05.** Si el abogado no encuentra su categoría, abandona el sistema.
3. **Aprovecha D-11.** El Administrador de Despacho ya tiene competencia sobre la configuración del despacho; esto le da contenido concreto a ese rol.

**Restricción que impone:** un valor de catálogo **en uso no puede eliminarse**, solo desactivarse (→ RN-06). De lo contrario se romperían procesos ya registrados.

**Excepción — el catálogo de estados procesales no es libre:** el RF05 **[P]** exige reportar procesos *activos* y *archivados*. Esos dos estados son **obligatorios y no eliminables** en todos los despachos. Los demás son ampliables.

### 2.2 Estados procesales **[S — propuesta para su corrección]**

| Valor | Significado operativo | ¿Obligatorio? |
|---|---|---|
| **Activo** | El proceso está en curso y el despacho lo gestiona. Es vigilado por el sistema. | **Sí** — exigido por RF05 **[P]** |
| **Suspendido** | El proceso está temporalmente detenido. Sigue existiendo, no se vigila con la misma urgencia. | No |
| **Terminado** | El proceso concluyó, pero aún no se archiva. | No |
| **Archivado** | El proceso está cerrado y guardado. No se vigila. | **Sí** — exigido por RF05 **[P]** |

*Los estados* Activo *y* Archivado *salen literalmente de la propuesta. Los otros dos son propuesta mía para cubrir la transición entre ambos; corrígelos si en la práctica del despacho se llaman de otro modo.*

### 2.3 Tipos de proceso **[S — propuesta para su corrección]**

Valores iniciales por rama del derecho: **Civil · Penal · Laboral · Familia · Administrativo · Comercial · Otro**.

*Origen: RF01 **[P]** exige registrar el tipo de proceso, y RNF02 **[P]** lo exige como criterio de búsqueda, pero la propuesta no enumera los valores. Esta lista es una clasificación general; el despacho la ajusta a su práctica.*

### 2.4 Tipos de documento **[S — propuesta para su corrección]**

Valores iniciales: **Demanda · Contestación · Poder · Prueba o anexo · Providencia o auto · Sentencia · Memorial · Otro**.

*Origen: RF02 **[P]** exige documentos adjuntos, sin clasificarlos.*

### 2.5 Tipos de actuación **[S — propuesta para su corrección]**

Valores iniciales: **Auto · Traslado · Notificación · Audiencia · Recurso · Fallo o sentencia · Otro**.

*Origen: RF02 **[P]** exige registrar actuaciones, sin clasificarlas. La importancia de este catálogo es alta: la actuación suele ser el hecho que da origen a un término (§6).*

### 2.6 Juzgados **[D-17]**

**Quinto catálogo, administrable por despacho.** Sin valores iniciales: cada despacho registra los juzgados ante los que efectivamente litiga.

*Origen: P-RNF02 **[P]** exige buscar procesos por juzgado. Con texto libre, el mismo juzgado se escribiría de formas distintas —"Juzgado 1 Civil", "J. 1° Civil del Circuito"— y la búsqueda devolvería resultados incompletos, degradando un requisito literal de la propuesta.*

**Por qué por despacho y no una lista nacional:** un directorio nacional de juzgados sería una responsabilidad de mantenimiento permanente que nadie pidió y que se desactualiza sola. Un despacho litiga ante un puñado de juzgados; su lista se construye sola con el uso. Ver **D-17**.

---

## 3. Reglas de negocio

### G1 · Tenencia y estado del despacho

| Código | Regla | Por qué existe | Origen |
|---|---|---|---|
| **RN-01** | Todo dato del sistema (usuario, cliente, proceso, expediente, documento, actuación, nota, audiencia, término) pertenece a **exactamente un despacho**. No existe información sin despacho. | Es la base de la multi-tenencia. Un dato huérfano no podría aislarse. | **[D-01]** |
| **RN-02** | Ningún usuario puede acceder, por ningún medio, a datos de un despacho distinto del suyo. | Los expedientes están sometidos a reserva profesional. Una fuga entre despachos es el fallo más grave del sistema. | **[D-01] [R-04]** |
| **RN-03** | Un despacho está en estado **activo** o **inactivo**. No hay otros estados. | La monetización ocurre fuera del sistema; esta marca es su única huella dentro. | **[D-06]** |
| **RN-04** | Si un despacho está **inactivo**, **ninguno** de sus usuarios —administrador, abogados **ni clientes**— puede realizar operación alguna en la plataforma. | Decisión de negocio: la inactividad bloquea el servicio completo. | **[D-10]** |
| **RN-05** | La desactivación de un despacho **no elimina ni altera ninguno de sus datos**. La reactivación restituye el acceso al estado exacto en que quedó. | Protege al despacho que olvidó pagar o regulariza después. Desactivar no es eliminar. | **[D-10]** |
| **RN-06** | Un valor de catálogo que ya esté en uso **no puede eliminarse**; solo desactivarse para nuevos registros. | Eliminarlo dejaría procesos, documentos o actuaciones sin clasificación válida. | **[D-13]** |
| **RN-06a** | Cada despacho administra **sus propios catálogos**, independientes de los de otros despachos. Los estados **Activo** y **Archivado** son obligatorios y no eliminables en todos. | Cada despacho clasifica según su práctica; pero el RF05 **[P]** exige reportar por *activos* y *archivados* literalmente, así que esos dos son piso común. | **[D-13] [P]** RF05 |
| **RN-06b** | Los catálogos administrables son **cinco**: estados procesales, tipos de proceso, tipos de documento, tipos de actuación y **juzgados**. | El juzgado es criterio de búsqueda obligatorio de P-RNF02; como texto libre la búsqueda quedaría incompleta. | **[D-17] [P]** P-RNF02 |

### G2 · Identidad, roles y acceso

| Código | Regla | Por qué existe | Origen |
|---|---|---|---|
| **RN-07** | Los roles del sistema son: **Administrador de Plataforma**, **Administrador de Despacho**, **Abogado** y **Cliente**. | Cierra el conjunto de perfiles. | **[P] [D-01] [D-07]** |
| **RN-08** | Un usuario **puede tener varios roles simultáneamente dentro de su despacho**. Sus permisos son la **unión** de los de sus roles. | El abogado independiente es Administrador de Despacho **y** Abogado a la vez. Un rol único haría imposible ese caso, que la propuesta nombra explícitamente. | **[D-07] [P]** |
| **RN-09** | El **Administrador de Despacho** gestiona usuarios, configuración y catálogos de su despacho **y** accede al contenido de todos sus expedientes. | Decisión de negocio. | **[D-11]** |
| **RN-10** | El **Administrador de Plataforma** gestiona el alta y el estado de los despachos, y **nunca** accede al contenido de ningún expediente. | Opera la plataforma, no ejerce la abogacía. El contenido está bajo reserva profesional. | **[D-01] [R-04]** |
| **RN-11** | El **Cliente** tiene acceso de **solo lectura**, y únicamente sobre su propio expediente. No crea, modifica ni elimina nada. | El portal informa; no permite intervenir en el proceso. | **[P]** RNF03 |
| **RN-12** | Todo acceso al **contenido de un expediente** queda registrado en bitácora de auditoría: quién, qué expediente y cuándo. | Como el Administrador de Despacho puede no ser abogado, el acceso amplio debe ser al menos **verificable**. | **[D-11]** |
| **RN-13** | Un usuario pertenece a **un solo despacho**. | Una misma persona que colabore con dos despachos requiere dos cuentas: los datos no pueden mezclarse (RN-02). | **[D-01] [D-14]** |

### G3 · Clientes y procesos

| Código | Regla | Por qué existe | Origen |
|---|---|---|---|
| **RN-14** | Un **cliente** debe registrarse con sus datos personales y quedar asociado a un tipo de proceso. | Enunciado literal del RF01. | **[P]** RF01 |
| **RN-15** | Un cliente puede tener **varios procesos**; un proceso pertenece a **un solo cliente titular**. | Un mismo cliente puede litigar varios casos; el expediente necesita un titular inequívoco para el portal (RN-11). | **[P]** RF01, RF02 |
| **RN-16** | Todo proceso debe tener **radicado**, **juzgado** (valor del catálogo, no texto libre), **tipo de proceso** y **estado procesal**. | Son los cuatro criterios de búsqueda que exige el RNF02 más el eje de los reportes del RF05. Si alguno falta, esas funciones se rompen. | **[P]** RNF02, RF05 · **[D-17]** |
| **RN-17** | El **radicado** identifica al proceso y es **único dentro del despacho**. | Es el identificador de negocio y el criterio de búsqueda principal. Duplicarlo haría ambiguo el resultado. *Nota: la unicidad es por despacho, no global — dos despachos podrían llevar el mismo proceso para partes distintas.* | **[P]** RNF02 |
| **RN-18** | Un proceso tiene **exactamente un expediente digital**, creado junto con él. | Relación uno a uno. Un proceso sin expediente no tendría dónde guardar nada. | **[P]** RF02 |
| **RN-19** | Un proceso **no se elimina**: se lleva a estado **Archivado**. | El histórico del despacho es su respaldo ante una reclamación disciplinaria. Borrar destruiría la prueba de la gestión. | **[P]** RF05, §2.2 |
| **RN-20** | Un proceso **Archivado** no genera alertas de audiencias ni de términos. | Un proceso cerrado no tiene vencimientos que vigilar. Alertar sobre él sería ruido que erosiona la confianza en las alertas reales. | **[P]** RF05 |

### G4 · Expediente: documentos, actuaciones y notas

| Código | Regla | Por qué existe | Origen |
|---|---|---|---|
| **RN-21** | El expediente digital contiene tres tipos de pieza: **documentos**, **actuaciones** y **notas**. | Enunciado literal del RF02. | **[P]** RF02 |
| **RN-22** | Todo **documento** se almacena **cifrado**. | Enunciado literal del RNF01. Contiene información bajo reserva. | **[P]** RNF01 |
| **RN-23** | Toda **actuación** debe tener **fecha** y **tipo de actuación**. | Sin fecha no hay historial ni punto de partida para un término. | **[P]** RF02, §2.5 |
| **RN-24** | Las **notas** son de uso exclusivamente interno del despacho y **nunca** son visibles para el cliente, en ninguna circunstancia. | Contienen estrategia y valoraciones del abogado. Exponerlas dañaría la relación profesional. | **[D-09] [R-06]** |
| **RN-25** | **Todo documento y toda actuación del expediente son visibles para el cliente titular**, sin excepción ni selección individual. | Decisión de negocio: transparencia total salvo las notas. Simplifica el modelo: la visibilidad depende del **tipo de pieza**, no de una marca por pieza. | **[D-12]** |
| **RN-26** | Como consecuencia de RN-25, **un documento cargado en el expediente queda visible para el cliente de inmediato**. No existe borrador oculto ni zona intermedia. Lo que no deba mostrarse, no se carga: se registra como nota. | Es el efecto colateral de la transparencia total y **debe advertirse al abogado en el momento de cargar**, no solo en la documentación. | **[D-12]** |
| **RN-27** | Las piezas del expediente **no se eliminan**. Una pieza errónea se corrige registrando una nueva que la rectifica. | Mismo motivo que RN-19: el expediente es el respaldo del despacho. | **[P]** RF02, RN-19 |

### G5 · Audiencias y alertas ★ *núcleo del sistema*

| Código | Regla | Por qué existe | Origen |
|---|---|---|---|
| **RN-28** | Una **audiencia** debe registrarse con **fecha y hora**. La hora es obligatoria. | Sin hora es imposible calcular el punto exacto de las alertas de 48h y 24h que exige el RF03. | **[P]** RF03, **[D-08]** |
| **RN-29** | Toda audiencia de un proceso activo genera **como mínimo tres alertas obligatorias**: a **48 horas**, a **24 horas** y el **día de la audiencia**. Estas tres **no pueden desactivarse**; el despacho puede **añadir** alertas adicionales. | Los tres momentos están fijados literalmente por el RF03 **[P]**, así que son piso, no configuración. La posibilidad de añadir viene de D-16. | **[P]** RF03, **[D-16]** |
| **RN-30** | Las alertas se emiten **automáticamente**, sin que ningún usuario las solicite. | Es la esencia del sistema: si dependieran de una acción humana, el olvido volvería a ser posible y el producto perdería su razón de ser. | **[P]** RF03, RF04 |
| **RN-31** | El **destinatario** de la alerta es el **abogado responsable** del proceso. | La propuesta dice "alertas automáticas de vencimiento **para el abogado**". Él es quien debe actuar. | **[P]** |
| **RN-32** | El **canal** de la alerta es el **correo electrónico**. | Decisión de negocio. | **[D-03]** |
| **RN-33** | Toda alerta emitida **queda registrada** con su fecha, destinatario y resultado del envío. | Si no hay registro, no se puede demostrar que el sistema avisó. Ante una reclamación, ese registro es la defensa del despacho — y la del producto. | **[R-02]** |
| **RN-34** | Si el envío de una alerta **falla**, el sistema debe **reintentar** y dejar el fallo visible dentro del sistema. Una alerta fallida nunca se descarta en silencio. | Es la regla que trata el riesgo que puede destruir el producto: el abogado dejó de vigilar confiando en el sistema. Una alerta perdida en silencio es peor que no tener sistema. | **[R-02]** |

### G6 · Términos judiciales

| Código | Regla | Por qué existe | Origen |
|---|---|---|---|
| **RN-35** | Un **término judicial** se registra con una **fecha de vencimiento** indicada **por el abogado**. | Enunciado literal del RF04. | **[P]** RF04 |
| **RN-36** | **El sistema no calcula ni interpreta plazos legales.** Vigila la fecha que el abogado registró. El cómputo del plazo es responsabilidad del abogado; el recordatorio es responsabilidad del sistema. | **Regla de frontera legal.** Calcular plazos convertiría al sistema en asesor jurídico y le trasladaría responsabilidad profesional. | §3.4 del doc. 01 |
| **RN-37** | Todo término de un proceso activo genera **al menos una alerta anticipada**, emitida **antes** de su fecha de vencimiento. | El RF04 exige "fecha de vencimiento y alerta". Alertar el mismo día del vencimiento llegaría tarde: el riesgo es irreversible. | **[P]** RF04 |
| **RN-37a** | El despacho **configura cuántas alertas** se emiten por término y **con cuánta anticipación**, mediante un **esquema de alertas** (p. ej. 15, 5 y 1 día antes). | Decisión de negocio: cada despacho maneja plazos distintos según su práctica. La propuesta dejó el RF04 sin especificar, a diferencia del RF03. | **[D-16]** |
| **RN-37b** | El esquema de alertas **no admite el valor cero**. Siempre queda al menos una alerta anticipada activa por término, y no es desactivable. | ★ **Es la regla que impide que la configurabilidad se convierta en el fallo.** Sin ella, un despacho podría apagar sin darse cuenta toda la vigilancia de sus términos, y el sistema cumpliría su configuración mientras el plazo vence en silencio. La configuración decide *cuántas más* y *cuándo*, **nunca *si***. | **[R-08] [R-02]** |
| **RN-38** | Un término tiene un **estado explícito**: pendiente, cumplido o vencido. | Sin estado no se puede distinguir un término atendido de uno olvidado, y las alertas seguirían sonando sobre algo ya resuelto. | **[P]** RF04 |
| **RN-39** | Un término **cumplido** deja de generar alertas. | Evita el ruido que hace que el abogado empiece a ignorar los avisos — la forma silenciosa en que muere R-02. | **[P]** RF04, **[R-05]** |

### G7 · Portal del cliente

| Código | Regla | Por qué existe | Origen |
|---|---|---|---|
| **RN-40** | El acceso del cliente al portal es **restringido y autenticado**. | Enunciado literal del RNF03. | **[P]** RNF03 |
| **RN-41** | El cliente accede **únicamente al expediente de los procesos en que es titular**. No puede ver los de otros clientes, ni siquiera del mismo despacho. | Es el RNF03 llevado a su consecuencia estricta. | **[P]** RNF03 |
| **RN-42** | El portal muestra: datos del proceso, estado procesal, actuaciones, documentos y audiencias programadas. **No muestra notas** (RN-24). | Delimita exactamente qué es "su expediente" para el cliente. | **[D-12] [D-09]** |
| **RN-43** | Las **credenciales del cliente las habilita el despacho**; **no existe autorregistro libre**. | Solo el despacho sabe a quién representa. Un autorregistro abierto permitiría a un tercero reclamar acceso a un expediente ajeno. | **[P]** RNF03, **[D-15]** |

### G8 · Búsqueda y reportes

| Código | Regla | Por qué existe | Origen |
|---|---|---|---|
| **RN-44** | Todo proceso debe poder localizarse por **radicado**, **cliente**, **juzgado** o **tipo de proceso**. | Enunciado literal del RNF02. | **[P]** RNF02 |
| **RN-45** | Los resultados de búsqueda y los reportes se limitan **siempre** al despacho del usuario. | La búsqueda es la vía más fácil de fugar datos entre despachos si se olvida el filtro (RN-02). | **[D-01] [R-04]** |
| **RN-46** | El sistema genera reportes de procesos **activos**, **archivados** y **por estado procesal**. | Enunciado literal del RF05. Fija los dos estados obligatorios del catálogo (§2.2). | **[P]** RF05 |

### G9 · Integración con la Rama Judicial *(ampliación fuera de la propuesta)*

| Código | Regla | Por qué existe | Origen |
|---|---|---|---|
| **RN-47** | La consulta al servicio externo se realiza **por número de radicado**. | Es el identificador con el que opera el servicio de consulta. | **[D-04]** |
| **RN-48** | La información traída del servicio externo es **de apoyo al seguimiento**, no fuente oficial, y **no sustituye la verificación del abogado**. Debe presentarse siempre identificada como tal. | Presentarla como oficial trasladaría al sistema una responsabilidad que no puede asumir. | **[D-04] [R-02]** |
| **RN-49** | Si el servicio externo no está disponible, **el sistema sigue operando con normalidad** mediante registro manual de actuaciones. Ninguna función de la propuesta (RF01–RF05) puede depender de la integración. | El servicio es externo y no controlable. El núcleo del producto no puede quedar a su merced. | **[D-04] [R-01]** |

### G10 · Conservación y trazabilidad

| Código | Regla | Por qué existe | Origen |
|---|---|---|---|
| **RN-50** | Toda pieza del expediente registra **quién la creó y cuándo**. | Es lo que convierte al expediente digital en respaldo de la gestión del despacho. | **[P]** RF02 |
| **RN-51** | Al pasar un despacho a **inactivo**, el sistema emite un **último aviso por correo** informando que la vigilancia de audiencias y términos queda suspendida. | Es el corte limpio: el abogado sabe con certeza desde cuándo vuelve a vigilar él. Sin ese aviso seguiría confiando en un sistema que ya no le avisa — exactamente el fallo R-02. | **[D-10] [R-07]** |
| **RN-52** | La información del despacho se conserva mientras el despacho exista, activo o inactivo. | Consecuencia directa de RN-05. | **[D-10]** |

---

## 4. Las cinco reglas críticas

De las 56, estas cinco son las que, si se incumplen, **rompen el producto** en vez de degradarlo. Son no negociables en las fases siguientes:

| Regla | Qué protege | Qué pasa si falla |
|---|---|---|
| **RN-02** — aislamiento entre despachos | Reserva profesional de los expedientes | Fuga de información confidencial entre despachos. Daño legal y reputacional irreparable. |
| **RN-34** — ninguna alerta se pierde en silencio | La razón de ser del sistema | El abogado dejó de vigilar confiando en el sistema. Término vencido, riesgo disciplinario — el daño que el producto existía para evitar. |
| **RN-37b** — el esquema de alertas nunca baja a cero | La razón de ser del sistema, frente a la configuración | Un despacho apaga su propia vigilancia sin advertirlo y el sistema obedece. Mismo daño que RN-34, pero autoinfligido. |
| **RN-36** — el sistema no calcula plazos legales | La frontera de responsabilidad profesional | El sistema pasaría a responder por un cómputo jurídico erróneo. |
| **RN-24** — las notas nunca llegan al cliente | La relación profesional abogado–cliente | Exposición de estrategia y valoraciones internas. |

### El riesgo R-02 tiene tres puertas, y cada una tiene su regla

El fallo que destruye el producto —*el abogado dejó de vigilar confiando en el sistema, y el sistema no avisó*— puede entrar por tres caminos distintos. Las reglas los cierran uno a uno:

| Puerta | Qué ocurre | Regla que la cierra |
|---|---|---|
| **Fallo técnico** | El sistema debía avisar y el envío falló | **RN-34** — reintento y fallo visible, nunca descarte silencioso |
| **Configuración** | El despacho configuró cero alertas sin advertirlo | **RN-37b** — el mínimo de una alerta no es desactivable |
| **Cambio de estado** | El despacho pasó a inactivo y el sistema dejó de vigilar | **RN-51** — aviso final de corte, para que el abogado sepa desde cuándo vigila él |

Las tres juntas cubren el riesgo completo. Cerrar solo una o dos deja el hueco abierto por donde el producto falla.

---

## 5. Trazabilidad: cobertura de la propuesta

Verificación de que **ningún enunciado de la propuesta quedó sin reglas** que lo sostengan:

| Enunciado **[P]** | Reglas que lo desarrollan |
|---|---|
| RF01 · Registro de clientes | RN-14, RN-15, RN-16 |
| RF02 · Expediente digital | RN-18, RN-21, RN-22, RN-23, RN-27, RN-50 |
| RF03 · Calendario y alertas 48h/24h/día | RN-28, RN-29, RN-30, RN-31, RN-32, RN-33, RN-34 |
| RF04 · Control de términos | RN-35, RN-36, RN-37, RN-37a, RN-37b, RN-38, RN-39 |
| RF05 · Reportes | RN-19, RN-20, RN-46 |
| RNF01 · Cifrado de documentos | RN-22 |
| RNF02 · Búsqueda | RN-16, RN-17, RN-44, RN-45 |
| RNF03 · Portal restringido del cliente | RN-11, RN-40, RN-41, RN-42, RN-43 |

**Cobertura: 8 de 8.** Ningún enunciado de la propuesta quedó huérfano.

Reglas que **no** provienen de la propuesta, y de dónde vienen:

| Origen | Reglas |
|---|---|
| **[D-01]** multi-tenencia | RN-01, RN-02, RN-10, RN-13, RN-45 |
| **[D-04]** Rama Judicial *(ampliación)* | RN-47, RN-48, RN-49 |
| **[D-06] [D-10]** estado del despacho | RN-03, RN-04, RN-05, RN-51, RN-52 |
| **[D-07] [D-11]** roles | RN-08, RN-09, RN-12 |
| **[D-09] [D-12]** visibilidad del expediente | RN-24, RN-25, RN-26 |
| **[R-02]** confiabilidad de la alerta | RN-33, RN-34 |
| **[R-08]** configurabilidad de las alertas | RN-37b |
| Frontera legal §3.4 | RN-36 |
| **[D-13]** catálogos administrables | RN-06, RN-06a |
| **[D-16]** alertas configurables | RN-29 *(ampliada)*, RN-37a |

---

## 6. Cómo se relacionan actuación, término y alerta

Relación central del dominio, que el modelo de datos de la Fase 5 debe reflejar:

```
   ACTUACIÓN                TÉRMINO                    ALERTA
   (algo ocurrió)           (algo hay que hacer        (aviso anticipado)
                             antes de una fecha)
        │                        │                          │
        │  el abogado            │  el sistema              │
        │  identifica que        │  vigila la fecha         │
        │  nace un término       │  registrada              │
        └───────────────────────►└─────────────────────────►│
              RN-36:                    RN-37:              │
        el cómputo del plazo      al menos una alerta       │
        lo hace el ABOGADO        antes del vencimiento     ▼
                                                      RN-31/32: correo
                                                      al abogado responsable
```

**Lectura de la frontera:** la actuación es el **hecho**; el término es la **consecuencia jurídica** que el abogado deriva de ese hecho; la alerta es el **servicio** que presta el sistema. El sistema no cruza de la columna 1 a la columna 2 — ahí está RN-36.

---

## 7. Cierre de la fase

### Preguntas de cierre — resueltas

| # | Pregunta | Respuesta | Queda como |
|---|---|---|---|
| 1 | Nombres reales de los cuatro catálogos | Se conservan las semillas propuestas; la práctica las corrige por configuración | Resuelta por **D-13** |
| 2 | ¿Catálogos administrables por despacho? | Sí — cada despacho tiene su forma propia de trabajar | **D-13** |
| 3 | ¿Un usuario en un solo despacho? | Sí | **D-14** |
| 4 | ¿El despacho habilita el acceso del cliente? | Sí, sin autorregistro libre | **D-15** |
| 5 | ¿Cuántas alertas de término y con cuánta anticipación? | **Configurables** por el despacho, en cantidad y anticipación | **D-16** → abre **R-08**, tratado por **RN-37b** |

**Fase 2 cerrada.**

### Dos confirmaciones menores dentro de D-16

Ninguna bloquea la Fase 3; ambas se resuelven al escribir los RF de alertas. Se trabaja con la propuesta indicada salvo indicación contraria.

| # | Punto | Propuesta con la que se trabaja |
|---|---|---|
| **C-1** | Las tres alertas de audiencia (48h, 24h, día) están fijadas literalmente por el RF03 **[P]**, pero D-16 hace configurables las alertas. | Las tres se conservan **obligatorias**; el despacho puede **añadir** más, nunca quitarlas. Así la configurabilidad no contradice la propuesta. |
| **C-2** | ¿A qué nivel se define el esquema de alertas? | **Por despacho**, administrado por el Administrador de Despacho (coherente con D-11 y D-13), con posibilidad de ajustarlo en un término concreto cuando lo amerite. |

### Qué habilita la Fase 3

Con estas reglas validadas, los **RF y RNF** se derivan directamente:

- Cada regla de los grupos G3–G8 genera uno o varios **RF**.
- RN-02, RN-22, RN-34, RN-37b y RN-45 generan **RNF críticos** de seguridad y confiabilidad.
- D-13 y D-16 añaden RF de **administración del despacho**: gestión de catálogos y configuración del esquema de alertas. Son funcionalidad nueva que la propuesta no enumeraba.
- Los supuestos técnicos **S-03** (volumen) y **S-04** (retención y respaldo) se cierran ahí.
- Se mantiene el criterio que fijaste: **pocos requisitos, cada uno muy bien definido.**
