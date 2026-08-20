# Fase 4 — Historias de Usuario y Criterios de Aceptación

**Proyecto:** Iuris / SGPJ — Sistema de Gestión de Procesos Jurídicos
**Deriva de:** [`03-requisitos-funcionales-y-no-funcionales.md`](03-requisitos-funcionales-y-no-funcionales.md) · [`02-reglas-de-negocio.md`](02-reglas-de-negocio.md) · [`00-decisiones-y-trazabilidad.md`](00-decisiones-y-trazabilidad.md)
**Versión:** 1.1 · **Fecha:** 2026-08-20 · **Estado: CERRADA** — decisiones D-17 a D-20 incorporadas

---

## 1. Método

### 1.1 De dónde nace cada historia

Cada historia lleva su **trazabilidad completa**, que es el propósito de haber construido las fases en este orden:

```
Propuesta [P]  →  Decisión [D]  →  Regla RN  →  Requisito RF/RNF  →  Historia HU
```

Leyendo la línea de trazabilidad de cualquier historia se puede reconstruir por qué existe hasta llegar al PDF original o a una decisión registrada. **Ninguna historia aparece sin requisito que la respalde.**

### 1.2 Convención para las historias del sistema

Cuatro historias (HU-24 a HU-27, HU-30) describen comportamiento que **ningún usuario invoca**: el motor de alertas actúa solo. Se redactan desde el **beneficiario**, no desde quien pulsa el botón:

> *"Como abogado, quiero que el sistema me avise… "* — el abogado no ejecuta la acción, pero es quien recibe el valor.

Escribirlas como *"Como sistema, quiero…"* sería un error: un sistema no quiere nada, y la historia perdería el beneficio que justifica construirla.

### 1.3 Criterios de aceptación negativos

Los requisitos innegociables (§5.3 de la Fase 3) generan criterios que verifican que algo **no ocurre**: que un despacho no ve datos de otro, que una nota no llega al cliente, que una alerta no se pierde. Están marcados con **⛔**.

Un criterio negativo es más difícil de probar que uno positivo y por eso se suele omitir — precisamente por eso se escriben explícitamente aquí. **Los cinco fallos que destruyen este producto solo se detectan con criterios negativos.**

### 1.4 Épicas

| Épica | Nombre | Módulos | HU | Sprint |
|---|---|---|---|---|
| **EP1** | Plataforma y despachos | M1 | HU-01 → HU-03 | 1 · 2 |
| **EP2** | Seguridad y acceso | M2 | HU-04 → HU-08 | 1 · 2 · 4 |
| **EP3** | Clientes, procesos y expedientes | M3, M4 | HU-09 → HU-14 | 1 |
| **EP4** | Expediente digital | M5 | HU-15 → HU-19 | 2 |
| **EP5** | Audiencias y términos | M6, M7 | HU-20 → HU-24 | 2 · 3 |
| **EP6** | Motor de alertas ★ | M8 | HU-25 → HU-31 | 3 |
| **EP7** | Portal del cliente | M9 | HU-32 → HU-34 | 4 |
| **EP8** | Búsqueda y reportes | M10 | HU-35 → HU-36 | 4 |
| **EP9** | Administración del despacho | M11 | HU-37 → HU-38 | 2 · 3 |
| **EP10** | Integración Rama Judicial | M12 | HU-39 → HU-40 | posterior |
| **EP11** | Garantías transversales | — | HU-41 → HU-42 | 1 · 2 |

**42 historias.** *(Los sprints reflejan la redistribución **D-20**; ver §13.3.)*

---

## 2. EP1 · Plataforma y despachos

#### HU-01 · Registrar un despacho en la plataforma
> **Como** Administrador de Plataforma, **quiero** registrar un despacho con su primer usuario administrador, **para** que un consultorio nuevo pueda empezar a trabajar en Iuris.

**Criterios de aceptación**
1. **CA-01.1** — *Dado* que soy Administrador de Plataforma, *cuando* registro un despacho con sus datos de identificación, *entonces* el despacho queda creado en estado **activo**.
2. **CA-01.2** — *Dado* que registro un despacho, *cuando* se completa el alta, *entonces* se crea su **primer usuario Administrador de Despacho**, sin el cual el despacho no podría operar.
3. **CA-01.3** — ⛔ *Dado* el formulario de alta, *cuando* lo reviso, *entonces* **no** contiene campos de plan, precio ni facturación: la monetización ocurre fuera del sistema.

**Trazabilidad:** RF-01 · RN-01, RN-13 · **[D-01] [D-06]** — *EP1 · Sprint 1 · Alta*

#### HU-02 · Activar o desactivar un despacho
> **Como** Administrador de Plataforma, **quiero** cambiar el estado de un despacho entre activo e inactivo, **para** reflejar en el sistema el resultado de una gestión comercial que ocurre por fuera.

**Criterios de aceptación**
1. **CA-02.1** — *Dado* un despacho activo, *cuando* lo marco como inactivo, *entonces* el cambio surte efecto de inmediato para todos sus usuarios.
2. **CA-02.2** — *Dado* un despacho inactivo, *cuando* lo reactivo, *entonces* sus usuarios recuperan el acceso y **todos sus datos están exactamente como quedaron**.
3. **CA-02.3** — ⛔ *Dado* cualquier despacho, *cuando* busco la opción de eliminarlo, *entonces* **no existe**: desactivar no es eliminar.

**Trazabilidad:** RF-02 · RNF-15 · RN-03, RN-05, RN-52 · **[D-06] [D-10]** — *EP1 · Sprint 1 · Alta*

#### HU-03 · Bloqueo de un despacho inactivo
> **Como** Administrador de Plataforma, **quiero** que ningún usuario de un despacho inactivo pueda operar, **para** que la inactividad tenga un efecto real y verificable.

**Criterios de aceptación**
1. **CA-03.1** — *Dado* un despacho inactivo, *cuando* cualquiera de sus usuarios —administrador, abogado **o cliente**— intenta cualquier operación, *entonces* se le impide y **se le informa que su despacho está inactivo**, no un error genérico.
2. **CA-03.2** — ⛔ *Dado* un despacho inactivo, *cuando* se revisa su información, *entonces* **ningún dato ha sido alterado ni eliminado**.
3. **CA-03.3** — *Dado* el código del sistema, *cuando* se audita la verificación de estado, *entonces* está implementada en **un único punto de control transversal**, no repetida por módulo.

**Trazabilidad:** RF-03 · RNF-02, RNF-15 · RN-04, RN-05, RN-52 · **[D-10]** — *EP1 · Sprint 1 · Alta*

---

## 3. EP2 · Seguridad y acceso

#### HU-04 · Iniciar sesión
> **Como** usuario del sistema, **quiero** autenticarme con mis credenciales, **para** acceder únicamente a lo que me corresponde.

**Criterios de aceptación**
1. **CA-04.1** — *Dado* que introduzco credenciales válidas, *cuando* inicio sesión, *entonces* accedo a las funciones de mis roles.
2. **CA-04.2** — ⛔ *Dado* que no he iniciado sesión, *cuando* intento acceder a cualquier dato del sistema, *entonces* se me deniega. **No existe ninguna área con datos accesible sin autenticar**, portal del cliente incluido.
3. **CA-04.3** — ⛔ *Dado* el almacén de datos, *cuando* se consulta cualquier registro de usuario, *entonces* **la contraseña no es legible**: está almacenada con hash y salt.
4. **CA-04.4** — *Dado* cualquier comunicación con el servidor, *cuando* se inspecciona, *entonces* viaja cifrada.

**Trazabilidad:** RF-04 · RNF-05, RNF-06 · RN-40 · **[P]** P-RNF03 — *EP2 · Sprint 1 · Alta*

#### HU-05 · Gestionar los usuarios del despacho
> **Como** Administrador de Despacho, **quiero** crear usuarios de mi despacho y asignarles roles, **para** que cada persona tenga las facultades que le corresponden.

**Criterios de aceptación**
1. **CA-05.1** — *Dado* que soy Administrador de Despacho, *cuando* creo un usuario, *entonces* queda vinculado **a mi despacho** y puedo asignarle uno o varios roles.
2. **CA-05.2** — *Dado* que asigno roles, *cuando* selecciono Administrador de Despacho **y** Abogado a la vez, *entonces* el sistema lo acepta — es el caso del abogado independiente.
3. **CA-05.3** — ⛔ *Dado* que soy Administrador de Despacho, *cuando* intento gestionar un usuario de otro despacho, *entonces* se me deniega.
4. **CA-05.4** — *Dado* un usuario existente, *cuando* consulto su despacho, *entonces* pertenece **a uno solo**.

**Trazabilidad:** RF-05 · RNF-03 · RN-07, RN-08, RN-09, RN-13 · **[D-07] [D-11] [D-14]** — *EP2 · Sprint 1 · Alta*

#### HU-06 · Operar con dos roles a la vez
> **Como** abogado independiente que además administra su propio despacho, **quiero** ejercer ambos roles con una sola cuenta, **para** no tener que entrar y salir con dos usuarios distintos.

**Criterios de aceptación**
1. **CA-06.1** — *Dado* que tengo los roles Administrador de Despacho y Abogado, *cuando* inicio sesión, *entonces* accedo a **la unión** de las funciones de ambos, sin cambiar de cuenta.
2. **CA-06.2** — *Dado* que tengo dos roles, *cuando* el sistema evalúa un permiso, *entonces* lo calcula por unión y **no** por un rol principal.
3. **CA-06.3** — *Dado* que se me retira uno de los roles, *cuando* vuelvo a operar, *entonces* conservo exactamente los permisos del rol restante.

**Trazabilidad:** RF-06 · RNF-03 · RN-08 · **[D-07]** — *EP2 · Sprint 1 · Alta*

#### HU-07 · Habilitar el acceso de un cliente al portal
> **Como** abogado, **quiero** habilitarle a mi cliente el acceso al portal, **para** que pueda consultar su caso sin llamarme.

**Criterios de aceptación**
1. **CA-07.1** — *Dado* un cliente registrado en mi despacho, *cuando* le habilito el acceso, *entonces* recibe sus credenciales y puede entrar al portal.
2. **CA-07.2** — ⛔ *Dado* el sistema, *cuando* una persona intenta crearse una cuenta de cliente por su cuenta, *entonces* **no existe autorregistro**: solo el despacho habilita accesos.
3. **CA-07.3** — *Dado* un cliente con acceso habilitado, *cuando* el despacho se lo revoca, *entonces* deja de poder entrar, **sin que se borre su información**.

**Trazabilidad:** RF-07 · RN-11, RN-43 · **[P]** P-RNF03 · **[D-15]** — *EP2 · Sprint 4 · Alta*

#### HU-08 · Auditar los accesos a expedientes
> **Como** Administrador de Despacho, **quiero** que quede registro de quién consultó cada expediente y cuándo, **para** poder responder si se cuestiona el manejo de información reservada.

**Criterios de aceptación**
1. **CA-08.1** — *Dado* que un usuario **consulta** el contenido de un expediente, *cuando* reviso la bitácora, *entonces* aparece quién, qué expediente y cuándo. **La lectura se audita, no solo la modificación.**
2. **CA-08.2** — ⛔ *Dado* cualquier rol, incluido el Administrador de Despacho, *cuando* intenta modificar o borrar un asiento de la bitácora, *entonces* **no puede desde la aplicación**. Una bitácora que el auditado puede editar no sirve como evidencia.

**Trazabilidad:** RF-08 · RNF-07 · RN-12 · **[D-11]** — *EP2 · Sprint 2 · Media*

---

## 4. EP3 · Clientes, procesos y expedientes

#### HU-09 · Registrar un cliente
> **Como** abogado, **quiero** registrar a mi cliente con sus datos y el tipo de proceso, **para** dejar de tenerlo solo en una carpeta física.

**Criterios de aceptación**
1. **CA-09.1** — *Dado* que registro un cliente con sus datos personales y tipo de proceso, *cuando* guardo, *entonces* queda asociado **a mi despacho**.
2. **CA-09.2** — *Dado* un cliente registrado, *cuando* lo consulto o modifico, *entonces* puedo hacerlo sin perder su historial.
3. **CA-09.3** — ⛔ *Dado* un cliente de otro despacho, *cuando* intento consultarlo, *entonces* no aparece ni siquiera en resultados de búsqueda.

**Trazabilidad:** RF-09 · RNF-01 · RN-01, RN-14 · **[P]** P-RF01 — *EP3 · Sprint 1 · Alta*

#### HU-10 · Llevar varios procesos de un mismo cliente
> **Como** abogado, **quiero** asociar varios procesos a un mismo cliente, **para** reflejar que un cliente puede tener más de un caso conmigo.

**Criterios de aceptación**
1. **CA-10.1** — *Dado* un cliente, *cuando* le creo un segundo proceso, *entonces* ambos quedan asociados a él y los veo juntos en su ficha.
2. **CA-10.2** — *Dado* un proceso, *cuando* consulto su titular, *entonces* tiene **exactamente uno** — sin titular único el portal no sabría a quién mostrarlo.

**Trazabilidad:** RF-10 · RN-15 · **[P]** P-RF01 — *EP3 · Sprint 1 · Alta*

#### HU-11 · Crear un proceso
> **Como** abogado, **quiero** crear un proceso con sus datos de identificación judicial, **para** tener el caso registrado y poder encontrarlo después.

**Criterios de aceptación**
1. **CA-11.1** — *Dado* que creo un proceso, *cuando* guardo, *entonces* son obligatorios **radicado, juzgado, tipo de proceso, estado procesal, cliente titular y abogado responsable**.
2. **CA-11.4** — *Dado* el campo juzgado, *cuando* lo diligencio, *entonces* **lo selecciono del catálogo de mi despacho**, no lo escribo libre. Si no existe, lo añado al catálogo — así la búsqueda por juzgado de P-RNF02 devuelve resultados completos.
2. **CA-11.2** — *Dado* que omito cualquiera de esos seis, *cuando* intento guardar, *entonces* el sistema me lo impide e indica cuál falta.
3. **CA-11.3** — *Dado* un proceso creado, *cuando* se emita una alerta suya, *entonces* el destinatario será el **abogado responsable** registrado aquí.

**Trazabilidad:** RF-11 · RN-16, RN-31, RN-44 · **[P]** P-RF01, P-RNF02 · **[D-17]** — *EP3 · Sprint 1 · Alta*

#### HU-12 · Evitar radicados duplicados
> **Como** abogado, **quiero** que el sistema me impida registrar dos veces el mismo radicado, **para** no terminar con expedientes duplicados del mismo caso.

**Criterios de aceptación**
1. **CA-12.1** — *Dado* un radicado ya registrado en mi despacho, *cuando* intento crear otro proceso con el mismo, *entonces* el sistema lo rechaza y me muestra el proceso existente.
2. **CA-12.2** — *Dado* un radicado registrado en **otro** despacho, *cuando* lo registro en el mío, *entonces* el sistema **lo permite**: la unicidad es por despacho, porque dos despachos pueden llevar el mismo proceso representando a partes distintas.

**Trazabilidad:** RF-12 · RN-17 · **[P]** P-RNF02 — *EP3 · Sprint 1 · Alta*

#### HU-13 · Expediente digital creado automáticamente
> **Como** abogado, **quiero** que el expediente se cree solo al crear el proceso, **para** tener siempre dónde guardar los documentos sin un paso extra.

**Criterios de aceptación**
1. **CA-13.1** — *Dado* que creo un proceso, *cuando* se guarda, *entonces* su expediente digital **ya existe**, sin que yo haga nada más.
2. **CA-13.2** — ⛔ *Dado* cualquier proceso del sistema, *cuando* se verifica, *entonces* **no existe ninguno sin expediente**.

**Trazabilidad:** RF-13 · RN-18 · **[P]** P-RF02 — *EP3 · Sprint 1 · Alta*

#### HU-14 · Cambiar el estado procesal y archivar
> **Como** abogado, **quiero** cambiar el estado de un proceso y archivarlo cuando termina, **para** distinguir lo que sigo trabajando de lo que ya cerré.

**Criterios de aceptación**
1. **CA-14.1** — *Dado* un proceso, *cuando* cambio su estado procesal, *entonces* queda registrado y se refleja en los reportes.
2. **CA-14.2** — *Dado* un proceso que termino, *cuando* lo archivo, *entonces* pasa a estado **Archivado** y **deja de generar alertas**.
3. **CA-14.3** — ⛔ *Dado* cualquier proceso, *cuando* busco cómo eliminarlo, *entonces* **no existe la opción**: archivar es un cambio de estado, no un borrado. El histórico es el respaldo del despacho.

**Trazabilidad:** RF-14 · RN-19, RN-20 · **[P]** P-RF05 — *EP3 · Sprint 1 · Alta*

---

## 5. EP4 · Expediente digital

#### HU-15 · Cargar documentos al expediente
> **Como** abogado, **quiero** cargar los documentos del caso al expediente digital, **para** no depender de la carpeta física y encontrarlos cuando los necesito.

**Criterios de aceptación**
1. **CA-15.1** — *Dado* un expediente, *cuando* cargo un documento y lo clasifico por tipo, *entonces* queda disponible para consulta y descarga.
2. **CA-15.2** — ⛔ *Dado* un documento cargado, *cuando* se inspecciona su almacenamiento, *entonces* **está cifrado**, no legible en claro.
3. **CA-15.3** — *Dado* un archivo de hasta 20 MB, *cuando* lo cargo, *entonces* el sistema lo acepta.

**Trazabilidad:** RF-15 · RNF-04, RNF-13 · RN-21, RN-22 · **[P]** P-RF02, P-RNF01 — *EP4 · Sprint 2 · Alta*

#### HU-16 · Saber que lo que cargo lo verá mi cliente
> **Como** abogado, **quiero** que el sistema me avise al cargar un documento de que mi cliente lo verá de inmediato, **para** no exponer por descuido algo que no quería mostrarle.

**Criterios de aceptación**
1. **CA-16.1** — *Dado* que voy a cargar un documento, *cuando* estoy en la pantalla de carga, *entonces* la advertencia de visibilidad inmediata **está visible ahí**, no en un manual ni en un aviso posterior.
2. **CA-16.2** — *Dado* que cargo un documento, *cuando* mi cliente entra al portal, *entonces* **ya lo ve**: no hay estado intermedio ni borrador oculto.
3. **CA-16.3** — *Dado* que no quiero mostrar algo, *cuando* busco la alternativa, *entonces* el sistema me ofrece registrarlo como **nota interna**.

**Trazabilidad:** RF-16 · RN-25, RN-26 · **[D-12]** — *EP4 · Sprint 2 · Alta*

#### HU-17 · Registrar actuaciones del proceso
> **Como** abogado, **quiero** registrar lo que va ocurriendo en el proceso con su fecha, **para** tener el historial del caso y saber desde cuándo corren los términos.

**Criterios de aceptación**
1. **CA-17.1** — *Dado* un proceso, *cuando* registro una actuación, *entonces* son obligatorios **fecha y tipo de actuación**.
2. **CA-17.2** — *Dado* un expediente con varias actuaciones, *cuando* lo consulto, *entonces* las veo en **orden cronológico**.

**Trazabilidad:** RF-17 · RN-23 · **[P]** P-RF02 — *EP4 · Sprint 2 · Alta*

#### HU-18 · Tomar notas internas
> **Como** abogado, **quiero** anotar observaciones internas sobre el caso, **para** registrar mi estrategia sin que la vea el cliente.

**Criterios de aceptación**
1. **CA-18.1** — *Dado* un expediente, *cuando* registro una nota, *entonces* queda visible para los usuarios de mi despacho.
2. **CA-18.2** — ⛔ *Dado* una nota interna, *cuando* el cliente titular entra al portal, *entonces* **no la ve, en ninguna pantalla, en ninguna circunstancia**.

**Trazabilidad:** RF-18 · RN-24 · **[D-09] [R-06]** — *EP4 · Sprint 2 · Alta*

#### HU-19 · Saber quién registró cada pieza
> **Como** Administrador de Despacho, **quiero** ver quién creó cada documento, actuación o nota y cuándo, **para** que el expediente sirva como respaldo demostrable de nuestra gestión.

**Criterios de aceptación**
1. **CA-19.1** — *Dado* cualquier pieza del expediente, *cuando* la consulto, *entonces* muestra **autor y fecha de creación**.
2. **CA-19.2** — *Dado* que registro una pieza, *cuando* se guarda, *entonces* el autor se toma del usuario autenticado: **no es un campo que se escriba a mano**.
3. **CA-19.3** — ⛔ *Dado* una pieza errónea, *cuando* busco eliminarla, *entonces* **no existe la opción**: se corrige registrando otra que la rectifica.

**Trazabilidad:** RF-38 · RN-27, RN-50 · **[P]** P-RF02 — *EP4 · Sprint 2 · Media*

---

## 6. EP5 · Audiencias y términos

#### HU-20 · Registrar una audiencia
> **Como** abogado, **quiero** registrar la fecha y hora de una audiencia, **para** que el sistema me avise antes y no vuelva a olvidarla.

**Criterios de aceptación**
1. **CA-20.1** — *Dado* un proceso, *cuando* registro una audiencia, *entonces* **fecha y hora son obligatorias**. Sin hora no puede calcularse el instante de las alertas de 48h y 24h.
2. **CA-20.2** — *Dado* el formulario de audiencia, *cuando* lo completo, *entonces* se resuelve en **una sola pantalla y no más de 5 campos obligatorios**.
3. **CA-20.3** — *Dado* que registro la audiencia, *cuando* se guarda, *entonces* sus alertas quedan programadas automáticamente.

**Trazabilidad:** RF-19 · RNF-16 · RN-28 · **[P]** P-RF03 · **[R-05]** — *EP5 · Sprint 3 · Alta*

> **Sobre CA-20.2:** el límite de 5 campos no es cosmético. Si registrar cuesta más que anotar en la agenda de papel, el abogado no lo usa, el sistema queda desactualizado y **sus alertas dejan de ser fiables**. Es tratamiento del riesgo de adopción R-05.

#### HU-21 · Ver el calendario de audiencias
> **Como** abogado, **quiero** ver mis audiencias en un calendario, **para** tener a la vista lo que viene sin depender solo del correo.

**Criterios de aceptación**
1. **CA-21.1** — *Dado* que tengo audiencias registradas, *cuando* abro el calendario, *entonces* las veo ubicadas en su fecha.
2. **CA-21.2** — *Dado* el calendario, *cuando* selecciono una audiencia, *entonces* accedo a su proceso y expediente.
3. **CA-21.3** — *Dado* el calendario, *cuando* se evalúa su función, *entonces* es **respaldo visual, no sustituto de la alerta**: la alerta sigue emitiéndose aunque el abogado nunca abra el calendario.

**Trazabilidad:** RF-20 · RN-28 · **[P]** P-RF03 — *EP5 · Sprint 3 · Alta*

#### HU-22 · Registrar un término judicial
> **Como** abogado, **quiero** registrar un término con su fecha de vencimiento, **para** que el sistema lo vigile por mí.

**Criterios de aceptación**
1. **CA-22.1** — *Dado* un proceso, *cuando* registro un término, *entonces* indico **yo** la fecha de vencimiento.
2. **CA-22.2** — ⛔ *Dado* el formulario de término, *cuando* lo reviso, *entonces* el sistema **no calcula ni sugiere** la fecha a partir de normas procesales. El cómputo del plazo es responsabilidad mía; el recordatorio es del sistema.
3. **CA-22.3** — *Dado* el formulario, *cuando* lo completo, *entonces* se resuelve en una pantalla y no más de 5 campos obligatorios.
4. **CA-22.4** — *Dado* que guardo el término, *entonces* queda **al menos una alerta anticipada** programada.

**Trazabilidad:** RF-21 · RNF-16 · RN-35, RN-36 · **[P]** P-RF04 — *EP5 · Sprint 3 · Crítica*

#### HU-23 · Marcar un término como cumplido
> **Como** abogado, **quiero** marcar el estado de un término, **para** dejar de recibir avisos de algo que ya atendí.

**Criterios de aceptación**
1. **CA-23.1** — *Dado* un término, *cuando* cambio su estado entre *pendiente*, *cumplido* y *vencido*, *entonces* queda registrado.
2. **CA-23.2** — *Dado* un término marcado **cumplido**, *cuando* llega el momento de una alerta pendiente suya, *entonces* **no se emite**.

**Trazabilidad:** RF-22 · RN-38, RN-39 · **[P]** P-RF04 · **[R-05]** — *EP5 · Sprint 3 · Alta*

#### HU-24 · Ver los vencimientos próximos al entrar
> **Como** abogado, **quiero** ver al iniciar sesión qué términos vencen pronto y cuáles ya vencieron, **para** tener un respaldo si un correo no me llegó.

**Criterios de aceptación**
1. **CA-24.1** — *Dado* que inicio sesión, *cuando* accedo al panel, *entonces* veo los términos **próximos a vencer** y los **vencidos** de mis procesos.
2. **CA-24.2** — *Dado* que una alerta por correo falló, *cuando* entro al sistema, *entonces* **el vencimiento sigue estando visible aquí**. Es la segunda vía de defensa contra el riesgo R-02.

**Trazabilidad:** RF-23 · RN-37, RN-38 · **[P]** P-RF04 · **[R-02]** — *EP5 · Sprint 3 · Alta*

---

## 7. EP6 · Motor de alertas ★

> Esta épica es la que justifica el proyecto. Es la única cuyo comportamiento **no lo invoca ningún usuario**.

#### HU-25 · Recibir alertas sin pedirlas
> **Como** abogado, **quiero** que el sistema me avise solo, **para** dejar de ser yo quien tiene que acordarse de revisar.

**Criterios de aceptación**
1. **CA-25.1** — *Dado* que existe una audiencia o término próximo, *cuando* llega su momento programado, *entonces* la alerta se emite **sin que ningún usuario la solicite**.
2. **CA-25.2** — *Dado* una alerta emitida, *cuando* se verifica el destinatario, *entonces* es el **abogado responsable** del proceso, por **correo electrónico**.
3. **CA-25.3** — *Dado* una alerta, *cuando* se verifica su contenido, *entonces* identifica el proceso, el radicado, el cliente y la fecha del evento — suficiente para actuar sin entrar al sistema.
4. **CA-25.4** — *Dado* su momento programado, *cuando* se mide el envío real, *entonces* ocurre dentro de **15 minutos** de tolerancia, con el planificador corriendo cada 5 minutos. Con una tolerancia de una hora, la alerta de 24h podría salir a las 23h05 y perder una hora de margen.

**Trazabilidad:** RF-24 · RNF-11 · RN-30, RN-31, RN-32 · **[P]** P-RF03, P-RF04 · **[D-03]** — *EP6 · Sprint 3 · Crítica*

#### HU-26 · Tres alertas por cada audiencia
> **Como** abogado, **quiero** recibir tres avisos escalonados antes de cada audiencia, **para** tener tiempo de prepararme y no solo de asistir.

**Criterios de aceptación**
1. **CA-26.1** — *Dado* una audiencia de un proceso activo, *cuando* transcurre el tiempo, *entonces* recibo alertas a **48 horas**, **24 horas** y el **día de la audiencia**.
2. **CA-26.2** — ⛔ *Dado* la configuración del despacho, *cuando* intento desactivar cualquiera de esas tres, *entonces* **no puedo**: están fijadas por la propuesta y son piso, no configuración.
3. **CA-26.3** — *Dado* que quiero más avisos, *cuando* configuro el despacho, *entonces* puedo **añadir** alertas adicionales.
4. **CA-26.4** — ⛔ *Dado* un momento programado, *cuando* el servicio de alertas se reinicia durante la ventana de envío, *entonces* la alerta se emite **una sola vez**: ni duplicada ni omitida.

**Trazabilidad:** RF-25 · RNF-10 · RN-29 · **[P]** P-RF03 · **[D-16]** — *EP6 · Sprint 3 · Crítica*

#### HU-27 · Alertas de término a mi medida
> **Como** Administrador de Despacho, **quiero** definir cuántos avisos recibimos por cada término y con cuánta anticipación, **para** ajustarlos a como trabaja mi despacho.

**Criterios de aceptación**
1. **CA-27.1** — *Dado* que configuro el esquema de alertas, *cuando* defino por ejemplo 15, 5 y 1 día antes, *entonces* todos los términos nuevos usan ese esquema.
2. **CA-27.2** — ⛔ *Dado* el esquema de alertas, *cuando* intento dejarlo en **cero alertas**, *entonces* **el sistema lo rechaza**. La configuración decide *cuántas más* y *cuándo*, **nunca *si***.
3. **CA-27.3** — *Dado* un término que lo amerita, *cuando* lo requiero, *entonces* puedo ajustar su esquema individualmente sin cambiar el del despacho.

**Trazabilidad:** RF-26, RF-34 · RN-37, RN-37a, RN-37b · **[D-16] [R-08]** — *EP6 · Sprint 3 · Crítica*

> **CA-27.2 es la barrera contra R-08.** Sin ella, un despacho podría apagar su propia vigilancia sin advertirlo y el sistema obedecería mientras el plazo vence en silencio.

#### HU-28 · No recibir alertas que no sirven
> **Como** abogado, **quiero** dejar de recibir avisos de casos cerrados o cosas ya hechas, **para** seguir tomándome en serio los avisos que sí importan.

**Criterios de aceptación**
1. **CA-28.1** — ⛔ *Dado* un proceso **archivado**, *cuando* llega la fecha de una audiencia o término suyo, *entonces* **no se emite alerta**.
2. **CA-28.2** — ⛔ *Dado* un término **cumplido**, *cuando* llega su momento de alerta, *entonces* **no se emite**.

**Trazabilidad:** RF-27 · RN-20, RN-39 · **[P]** P-RF04 · **[R-05]** — *EP6 · Sprint 3 · Alta*

#### HU-29 · Que ninguna alerta se pierda en silencio
> **Como** abogado, **quiero** enterarme si un aviso no pudo enviarse, **para** no seguir confiando en un sistema que dejó de avisarme.

**Criterios de aceptación**
1. **CA-29.1** — *Dado* que el envío de una alerta falla, *cuando* el sistema lo detecta, *entonces* **reintenta**.
2. **CA-29.2** — ⛔ *Dado* que los reintentos se agotan, *cuando* entro al sistema, *entonces* la alerta aparece **marcada como fallida**. **Nunca desaparece sin dejar rastro.**
3. **CA-29.3** — *Dado* el servicio de correo caído, *cuando* se prueba el sistema, *entonces* se cumplen CA-29.1 y CA-29.2. **Es una prueba obligatoria, no opcional.**

**Trazabilidad:** RNF-08 · RN-34 · **[R-02]** — *EP6 · Sprint 3 · Crítica*

> **Es la historia más importante del proyecto.** Una alerta que no se envía es peor que no tener sistema, porque el abogado ya dejó de vigilar manualmente confiando en él.

#### HU-30 · Poder demostrar que el sistema avisó
> **Como** Administrador de Despacho, **quiero** consultar el historial de alertas enviadas, **para** poder demostrar que el despacho fue advertido si alguien lo cuestiona.

**Criterios de aceptación**
1. **CA-30.1** — *Dado* una alerta emitida, *cuando* consulto el historial, *entonces* veo **fecha, destinatario y resultado del envío**.
2. **CA-30.2** — *Dado* un término vencido, *cuando* reviso su historial, *entonces* puedo determinar si el sistema avisó y cuándo.

**Trazabilidad:** RNF-09 · RN-33 · **[R-02]** — *EP6 · Sprint 3 · Alta*

#### HU-31 · Saber cuándo el sistema deja de vigilar
> **Como** abogado de un despacho que pasa a inactivo, **quiero** recibir un aviso final, **para** saber con certeza desde qué momento vuelvo a ser yo quien vigila los plazos.

**Criterios de aceptación**
1. **CA-31.1** — *Dado* que mi despacho pasa a inactivo, *cuando* se aplica el cambio, *entonces* recibo **un correo** informando que la vigilancia de audiencias y términos queda suspendida.
2. **CA-31.2** — *Dado* ese aviso, *cuando* se verifica su naturaleza, *entonces* es una **notificación de corte**, no acceso a la plataforma: es compatible con el bloqueo total de HU-03.

**Trazabilidad:** RF-37 · RN-51 · **[D-10] [R-07]** — *EP6 · Sprint 3 · Alta*

---

## 8. EP7 · Portal del cliente

#### HU-32 · Consultar mi caso sin llamar al abogado
> **Como** cliente, **quiero** entrar al portal y ver cómo va mi proceso, **para** no tener que llamar al despacho cada vez que quiero saber algo.

**Criterios de aceptación**
1. **CA-32.1** — *Dado* que tengo acceso habilitado, *cuando* entro al portal, *entonces* veo **mis procesos** con su estado.
2. **CA-32.2** — ⛔ *Dado* el portal, *cuando* intento crear, modificar o eliminar algo, *entonces* **no puedo**: mi acceso es solo lectura.
3. **CA-32.3** — ⛔ *Dado* un proceso de otro cliente, incluso del mismo despacho, *cuando* intento acceder manipulando el identificador en la dirección, *entonces* se me deniega.

**Trazabilidad:** RF-28 · RNF-01 · RN-11, RN-40, RN-41 · **[P]** P-RNF03 — *EP7 · Sprint 4 · Alta*

#### HU-33 · Ver el detalle de mi expediente
> **Como** cliente, **quiero** ver los documentos, actuaciones y audiencias de mi caso, **para** entender qué ha pasado y qué viene.

**Criterios de aceptación**
1. **CA-33.1** — *Dado* mi expediente, *cuando* lo consulto, *entonces* veo **datos del proceso, estado procesal, actuaciones, documentos y audiencias programadas**.
2. **CA-33.2** — *Dado* mi expediente, *cuando* reviso documentos y actuaciones, *entonces* veo **todos**, sin selección pieza por pieza.
3. **CA-33.3** — *Dado* un documento cargado por mi abogado hace un momento, *cuando* entro al portal, *entonces* **ya está disponible**.

**Trazabilidad:** RF-29 · RN-25, RN-42 · **[D-12]** — *EP7 · Sprint 4 · Alta*

#### HU-34 · Las notas del abogado no son para mí
> **Como** abogado, **quiero** tener la certeza de que mi cliente jamás verá mis notas internas, **para** poder registrar mi estrategia con libertad.

**Criterios de aceptación**
1. **CA-34.1** — ⛔ *Dado* un expediente con notas internas, *cuando* el cliente titular recorre **todas** las pantallas del portal, *entonces* **ninguna nota aparece**.
2. **CA-34.2** — ⛔ *Dado* el portal, *cuando* se consultan directamente sus servicios de datos, *entonces* **no devuelven notas** en ninguna respuesta. La restricción está en los datos, no solo en la pantalla.

**Trazabilidad:** RF-30 · RN-24, RN-42 · **[D-09] [R-06]** — *EP7 · Sprint 4 · Crítica*

> **CA-34.2 existe porque ocultar en la interfaz no es ocultar.** Si el servicio devuelve la nota y la pantalla no la pinta, la información ya salió del despacho.

---

## 9. EP8 · Búsqueda y reportes

#### HU-35 · Encontrar un proceso rápido
> **Como** abogado, **quiero** buscar un proceso por radicado, cliente, juzgado o tipo, **para** encontrarlo en segundos en vez de revisar carpeta por carpeta.

**Criterios de aceptación**
1. **CA-35.1** — *Dado* el buscador, *cuando* busco por **radicado, cliente, juzgado o tipo de proceso**, *entonces* obtengo los procesos que coinciden.
2. **CA-35.2** — *Dado* el buscador, *cuando* combino varios criterios, *entonces* el resultado los aplica en conjunto.
3. **CA-35.3** — ⛔ *Dado* cualquier búsqueda, *cuando* reviso los resultados, *entonces* **solo contienen procesos de mi despacho**. La búsqueda es la vía más fácil de fugar datos si se olvida el filtro.
4. **CA-35.4** — *Dado* el volumen objetivo, *cuando* ejecuto una búsqueda, *entonces* responde en **menos de 3 segundos**.

**Trazabilidad:** RF-31 · RNF-01, RNF-12 · RN-44, RN-45 · **[P]** P-RNF02 — *EP8 · Sprint 1 (base) · 4 (refinamiento) · Alta*

#### HU-36 · Ver cómo está el despacho
> **Como** Administrador de Despacho, **quiero** un reporte de mis procesos por estado, **para** saber cuántos casos activos llevamos realmente.

**Criterios de aceptación**
1. **CA-36.1** — *Dado* el módulo de reportes, *cuando* lo consulto, *entonces* obtengo procesos **activos**, **archivados** y **por estado procesal**.
2. **CA-36.2** — ⛔ *Dado* cualquier reporte, *cuando* reviso su contenido, *entonces* **solo incluye datos de mi despacho**.

**Trazabilidad:** RF-32 · RNF-01 · RN-45, RN-46, RN-06a · **[P]** P-RF05 — *EP8 · Sprint 4 · Alta*

---

## 10. EP9 · Administración del despacho

#### HU-37 · Ajustar los catálogos a mi forma de trabajar
> **Como** Administrador de Despacho, **quiero** ajustar las listas de tipos y estados que usamos, **para** que el sistema hable como habla mi despacho.

**Criterios de aceptación**
1. **CA-37.1** — *Dado* los catálogos de mi despacho, *cuando* los administro, *entonces* puedo **añadir, renombrar y desactivar** valores de los **cinco** catálogos: estados procesales, tipos de proceso, tipos de documento, tipos de actuación y **juzgados**.
2. **CA-37.5** — *Dado* el catálogo de **juzgados**, *cuando* mi despacho empieza, *entonces* está **vacío** y lo construyo con los juzgados ante los que efectivamente litigo. No hay directorio nacional que mantener.
2. **CA-37.2** — ⛔ *Dado* un valor **en uso**, *cuando* intento eliminarlo, *entonces* **no puedo**: solo desactivarlo para nuevos registros. Eliminarlo dejaría registros sin clasificación válida.
3. **CA-37.3** — ⛔ *Dado* los estados **Activo** y **Archivado**, *cuando* intento desactivarlos, *entonces* **no puedo**: los reportes de la propuesta dependen de ellos.
4. **CA-37.4** — ⛔ *Dado* que modifico mis catálogos, *cuando* se revisa otro despacho, *entonces* **los suyos no cambiaron**.

**Trazabilidad:** RF-33 · RN-06, RN-06a, RN-06b · **[D-13] [D-17]** — *EP9 · Sprint 2 · Media*

#### HU-38 · Configurar el esquema de alertas del despacho
> **Como** Administrador de Despacho, **quiero** definir el esquema de alertas que usamos por defecto, **para** no tener que configurarlo término por término.

**Criterios de aceptación**
1. **CA-38.1** — *Dado* la configuración del despacho, *cuando* defino el esquema, *entonces* se aplica por defecto a los términos nuevos.
2. **CA-38.2** — ⛔ *Dado* el esquema, *cuando* intento guardarlo con cero alertas, *entonces* **el sistema lo rechaza**.
3. **CA-38.3** — *Dado* que cambio el esquema, *cuando* se revisan los términos **ya existentes**, *entonces* conservan sus alertas ya programadas: un cambio de configuración no puede desprogramar una vigilancia en curso.

**Trazabilidad:** RF-34 · RN-37a, RN-37b · **[D-16] [R-08]** — *EP9 · Sprint 3 · Alta*

---

## 11. EP10 · Integración Rama Judicial *(ampliación fuera de la propuesta)*

#### HU-39 · Traer las actuaciones publicadas
> **Como** abogado, **quiero** consultar por radicado las actuaciones publicadas del proceso, **para** no tener que revisarlas una por una en el sitio de la Rama Judicial.

**Criterios de aceptación**
1. **CA-39.1** — *Dado* un proceso con radicado, *cuando* solicito la consulta, *entonces* el sistema muestra las actuaciones publicadas del servicio externo.
2. **CA-39.2** — ⛔ *Dado* la información traída, *cuando* se muestra, *entonces* está **siempre identificada como no oficial y de apoyo al seguimiento**. No sustituye mi verificación.

**Trazabilidad:** RF-35 · RN-47, RN-48 · **[D-04]** — *EP10 · posterior · Media*

#### HU-40 · Seguir trabajando aunque el servicio externo falle
> **Como** abogado, **quiero** que el sistema funcione igual si la Rama Judicial no responde, **para** que mi trabajo no dependa de un servicio que no controlo.

**Criterios de aceptación**
1. **CA-40.1** — *Dado* el servicio externo caído, *cuando* uso el sistema, *entonces* **todas** las funciones de la propuesta siguen operando con normalidad.
2. **CA-40.2** — *Dado* el servicio caído, *cuando* necesito registrar una actuación, *entonces* puedo hacerlo **manualmente**.
3. **CA-40.3** — ⛔ *Dado* el servicio caído, *cuando* se verifica el motor de alertas, *entonces* **sigue emitiendo alertas con normalidad**: el núcleo no depende de la integración.

**Trazabilidad:** RF-36 · RN-49 · **[D-04] [R-01]** — *EP10 · posterior · Alta*

---

## 12. EP11 · Garantías transversales

> Estas dos historias no pertenecen a un módulo: son garantías que atraviesan todo el sistema. Se escriben como historias porque **deben probarse explícitamente**, y lo que no tiene historia no entra al tablero.

#### HU-41 · Un despacho nunca ve datos de otro
> **Como** abogado, **quiero** la certeza de que ningún otro despacho puede ver mis expedientes, **para** poder confiarle a Iuris información sometida a reserva profesional.

**Criterios de aceptación**
1. **CA-41.1** — ⛔ *Dado* dos despachos con datos, *cuando* un usuario del despacho A intenta acceder a datos del B por **interfaz, búsqueda, reporte o servicio de datos**, *entonces* se le deniega en todos los casos.
2. **CA-41.2** — ⛔ *Dado* un identificador válido de un recurso del despacho B, *cuando* un usuario de A lo usa directamente en la dirección o en una llamada al servicio, *entonces* se le **deniega el acceso** — no se le devuelve un resultado vacío ambiguo.
3. **CA-41.3** — *Dado* cada módulo que expone datos, *cuando* se prueba, *entonces* existe **una prueba de acceso cruzado para ese módulo**. La prueba no se hace una vez: se hace en cada módulo.

**Trazabilidad:** RNF-01 · RN-01, RN-02, RN-45 · **[D-01] [R-04]** — *EP11 · Sprint 1 · Crítica*

#### HU-42 · El estado del despacho se verifica siempre
> **Como** Administrador de Plataforma, **quiero** que la verificación del estado del despacho sea imposible de saltarse, **para** que la desactivación signifique algo de verdad.

**Criterios de aceptación**
1. **CA-42.1** — ⛔ *Dado* un despacho inactivo, *cuando* se prueba **una funcionalidad de cada módulo**, *entonces* todas quedan bloqueadas.
2. **CA-42.2** — *Dado* el código, *cuando* se audita, *entonces* la verificación está en **un único punto de control**. Repartirla por funcionalidad garantiza que alguna se olvide.

**Trazabilidad:** RNF-02 · RN-04 · **[D-10]** — *EP11 · Sprint 1 · Alta*

---

## 13. Trazabilidad y verificación de cobertura

### 13.1 Cobertura de requisitos — verificación de que ninguno quedó sin historia

| Requisito | Historia | | Requisito | Historia |
|---|---|---|---|---|
| RF-01 | HU-01 | | RF-20 | HU-21 |
| RF-02 | HU-02 | | RF-21 | HU-22 |
| RF-03 | HU-03, HU-42 | | RF-22 | HU-23 |
| RF-04 | HU-04 | | RF-23 | HU-24 |
| RF-05 | HU-05 | | RF-24 | HU-25 |
| RF-06 | HU-06 | | RF-25 | HU-26 |
| RF-07 | HU-07 | | RF-26 | HU-27 |
| RF-08 | HU-08 | | RF-27 | HU-28 |
| RF-09 | HU-09 | | RF-28 | HU-32 |
| RF-10 | HU-10 | | RF-29 | HU-33 |
| RF-11 | HU-11 | | RF-30 | HU-34 |
| RF-12 | HU-12 | | RF-31 | HU-35 |
| RF-13 | HU-13 | | RF-32 | HU-36 |
| RF-14 | HU-14 | | RF-33 | HU-37 |
| RF-15 | HU-15 | | RF-34 | HU-27, HU-38 |
| RF-16 | HU-16 | | RF-35 | HU-39 |
| RF-17 | HU-17 | | RF-36 | HU-40 |
| RF-18 | HU-18 | | RF-37 | HU-31 |
| RF-19 | HU-20 | | RF-38 | HU-19 |

**38 de 38 RF cubiertos.**

| RNF | Historia | | RNF | Historia |
|---|---|---|---|---|
| RNF-01 | HU-09, HU-32, HU-35, HU-36, **HU-41** | | RNF-09 | HU-30 |
| RNF-02 | HU-03, **HU-42** | | RNF-10 | HU-26 |
| RNF-03 | HU-05, HU-06 | | RNF-11 | HU-25 |
| RNF-04 | HU-15 | | RNF-12 | HU-35 |
| RNF-05 | HU-04 | | RNF-13 | HU-15 |
| RNF-06 | HU-04 | | RNF-14 | *(operación — sin HU)* |
| RNF-07 | HU-08 | | RNF-15 | HU-02, HU-03 |
| RNF-08 | **HU-29** | | RNF-16 | HU-20, HU-22 |

**15 de 16 RNF cubiertos por historias.** RNF-14 (respaldo diario con restauración probada) es un requisito **de operación, no de producto**: no hay usuario que lo ejecute en la aplicación. Se verifica en el procedimiento de despliegue de la Fase 6, no en el tablero.

### 13.2 Las historias que no se negocian

Si hubiera que recortar el alcance del sprint, estas seis quedan fuera de la conversación:

| Historia | Qué protege |
|---|---|
| **HU-29** — ninguna alerta se pierde en silencio | La razón de ser del sistema |
| **HU-41** — un despacho nunca ve datos de otro | La reserva profesional |
| **HU-27** — el esquema nunca baja a cero alertas | Impide que la configuración apague la vigilancia |
| **HU-22** — el sistema no calcula plazos | La frontera de responsabilidad profesional |
| **HU-34** — las notas nunca llegan al cliente | La relación abogado-cliente |
| **HU-26** — tres alertas por audiencia | Enunciado literal de la propuesta |

### 13.3 Distribución por sprint

**Distribución tras la redistribución [D-20]:**

| Sprint **[P]** | Historias | Total |
|---|---|---|
| **1** | HU-01, HU-04 → HU-06, HU-09 → HU-14, HU-41 | **11** |
| **2** | HU-02, HU-03, HU-08, HU-15 → HU-21, HU-37, HU-42 | **13** |
| **3** | HU-22 → HU-31, HU-38 | **11** |
| **4** | HU-07, HU-32 → HU-36 | **6** |
| *posterior* | HU-39, HU-40 | **2** |

**Antes de D-20** los Sprints 1 y 3 concentraban 28 de las 42 historias, y el 3 contenía cuatro de las seis innegociables. Tras mover HU-02, HU-03, HU-20, HU-21 y HU-42 hacia el Sprint 2, y HU-35 al 4, la concentración baja a **22 de 42** y el Sprint 3 queda dedicado al motor de alertas y los términos — lo irreductible.

El Sprint 2 pasa a ser el más cargado, y es el correcto para estarlo: son formularios y almacenamiento, sin lógica crítica.

---

## 14. Cierre de la fase

### Hallazgo de esta fase

Antes de escribir las historias se hizo la **verificación inversa**: recorrer las reglas de negocio buscando cuáles no podían convertirse en ninguna historia. Aparecieron **dos huecos en la Fase 3**:

| Regla huérfana | Faltaba | Se añadió |
|---|---|---|
| **RN-51** — aviso de corte al desactivar | Ningún RF lo recogía | **RF-37** → HU-31 |
| **RN-50** — autoría y fecha de cada pieza | Ningún RF lo recogía | **RF-38** → HU-19 |

Es el valor real de encadenar las fases: **la fase siguiente audita a la anterior**. Sin la verificación inversa, RN-51 —que es parte del tratamiento del riesgo crítico R-02— se habría quedado escrita en un documento sin llegar nunca al código.

### Pendientes — cerrados

1. **Las cifras de la Fase 3** se adoptaron como línea base (**D-19**), con RNF-11 corregido de 1 hora a 15 minutos. Incorporadas en CA-15.3, CA-25.4 y CA-35.4.
2. **R-09** tratado con la redistribución **D-20**, sujeta al visto bueno del Product Owner.

### Qué habilita la Fase 5

La cadena está completa:

```
Propuesta [P] → 16 Decisiones [D] → 56 Reglas RN → 54 Requisitos RF/RNF → 42 Historias HU
```

Los diagramas ya no parten de cero:

| Diagrama | De dónde sale |
|---|---|
| **Casos de uso** | Actores de la Fase 1 + las 42 historias. El *Sistema* como actor temporal aparece en EP6. |
| **Modelo de datos** | Glosario (Fase 1) + reglas de estructura G1–G4. La multi-tenencia obliga a que **toda** entidad cuelgue del Despacho. |
| **Clases** | Módulos M1–M12 + la relación usuario–roles **de uno a muchos** (RN-08). |
| **Componentes** | Los 12 módulos, con M8 y M12 desacoplados. |
| **Despliegue** | Angular + Spring Boot + BD relacional + almacén cifrado **[D-02]**. |
| **Secuencia** | Los flujos críticos: emisión de alerta (HU-25), acceso del cliente (HU-32), consulta externa (HU-39). |
| **Actividad y flujos** | Los cinco procesos de negocio PN-1 a PN-5 de la Fase 1. |
