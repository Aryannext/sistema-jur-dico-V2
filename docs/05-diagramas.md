# Fase 5 — Diagramas del Sistema

**Proyecto:** Iuris / SGPJ — Sistema de Gestión de Procesos Jurídicos
**Deriva de:** [`04-historias-de-usuario.md`](04-historias-de-usuario.md) · [`03-requisitos-funcionales-y-no-funcionales.md`](03-requisitos-funcionales-y-no-funcionales.md) · [`02-reglas-de-negocio.md`](02-reglas-de-negocio.md) · [`01-idea-y-definicion-de-negocio.md`](01-idea-y-definicion-de-negocio.md)
**Versión:** 1.1 · **Fecha:** 2026-08-20 · **Estado: CERRADA** — A-04 resuelto por D-17

---

## 0. Cómo leer este documento

Ningún diagrama se dibujó desde cero. Cada uno tiene una **fuente documental** en las fases anteriores:

> **Pendiente declarado (D-24).** Estos diagramas se dibujaron sobre las 42 historias de la Fase 4. Durante la construcción se añadieron **HU-43** y **HU-44** (cambio y restablecimiento de contraseña), que **todavía no están reflejadas** en el diagrama de casos de uso. Se anota aquí en lugar de dejar que el lector lo descubra comparando: un diagrama incompleto que no se sabe incompleto es peor que uno que lo declara.

| Diagrama | Sale de |
|---|---|
| 1 · Casos de uso | Actores (Fase 1 §6) + las 42 historias |
| 2 · Funcional | Los 12 módulos M1–M12 (Fase 3 §2) |
| 3 · Modelo de datos | Glosario (Fase 1 §8) + reglas G1–G4 |
| 4 · Clases | Modelo de datos + reglas de comportamiento G5–G7 |
| 5 · Componentes | Módulos + decisiones de desacoplamiento **[D-04]** |
| 6 · Despliegue | Stack **[D-02]** + RNF de seguridad |
| 7 · Secuencia | Historias críticas HU-25, HU-32, HU-39 |
| 8 · Actividad | Reglas del ciclo de términos G6 |
| 9 · Flujo del sistema | Visión global |
| 10 · Flujos individuales | Procesos de negocio PN-1 a PN-5 (Fase 1 §7) |

**Los diagramas están en formato Mermaid.** Para verlos renderizados: la versión publicada como Artifact, VS Code con extensión Mermaid, o mermaid.live.

---

## 1. Diagrama de Casos de Uso

Los actores son los de la Fase 1 §6. Recuérdese que **el Sistema es un actor temporal**: ejecuta casos de uso que nadie invoca — y son justamente los que justifican el proyecto.

```mermaid
flowchart LR
    AP(["Administrador<br/>de Plataforma"])
    AD(["Administrador<br/>de Despacho"])
    AB(["Abogado"])
    CL(["Cliente"])
    SYS(["SISTEMA<br/>actor temporal"])
    RJ(["Servicio<br/>Rama Judicial"])

    subgraph P1["Gestión de la plataforma"]
        CU01["CU-01 Registrar despacho"]
        CU02["CU-02 Activar / desactivar despacho"]
    end

    subgraph P2["Seguridad y acceso"]
        CU03["CU-03 Autenticarse"]
        CU04["CU-04 Gestionar usuarios y roles"]
        CU05["CU-05 Habilitar acceso de cliente"]
        CU06["CU-06 Consultar bitácora de auditoría"]
    end

    subgraph P3["Gestión de casos"]
        CU07["CU-07 Registrar cliente"]
        CU08["CU-08 Crear proceso"]
        CU09["CU-09 Cambiar estado procesal"]
    end

    subgraph P4["Expediente digital"]
        CU10["CU-10 Cargar documento"]
        CU11["CU-11 Registrar actuación"]
        CU12["CU-12 Registrar nota interna"]
    end

    subgraph P5["Vigilancia del tiempo"]
        CU13["CU-13 Registrar audiencia"]
        CU14["CU-14 Registrar término"]
        CU15["CU-15 Consultar calendario"]
        CU16["CU-16 Consultar panel de vencimientos"]
        CU17["CU-17 Marcar término cumplido"]
    end

    subgraph P6["Alertas automáticas"]
        CU18["CU-18 Evaluar vencimientos"]
        CU19["CU-19 Emitir alerta"]
        CU20["CU-20 Reintentar alerta fallida"]
        CU21["CU-21 Avisar suspensión de vigilancia"]
    end

    subgraph P7["Portal y consulta"]
        CU22["CU-22 Consultar mi expediente"]
        CU23["CU-23 Buscar proceso"]
        CU24["CU-24 Generar reporte"]
    end

    subgraph P8["Administración del despacho"]
        CU25["CU-25 Administrar catálogos"]
        CU26["CU-26 Configurar esquema de alertas"]
    end

    subgraph P9["Integración externa"]
        CU27["CU-27 Consultar actuaciones publicadas"]
    end

    AP --> CU01 & CU02
    AD --> CU04 & CU05 & CU06 & CU25 & CU26 & CU24
    AB --> CU07 & CU08 & CU09 & CU10 & CU11 & CU12
    AB --> CU13 & CU14 & CU15 & CU16 & CU17 & CU23 & CU05 & CU27
    CL --> CU22
    AP --> CU03
    AD --> CU03
    AB --> CU03
    CL --> CU03

    SYS --> CU18 & CU19 & CU20 & CU21
    CU27 -.consulta.-> RJ
    CU18 -.dispara.-> CU19
    CU19 -.si falla.-> CU20

    style SYS fill:#c62828,stroke:#8e0000,color:#fff
    style P6 fill:#fff3e0,stroke:#e65100
    style RJ fill:#eceff1,stroke:#607d8b,stroke-dasharray: 5 5
```

### Lectura del diagrama

**El paquete P6 no tiene ningún actor humano apuntando hacia él.** Es la observación central: los cuatro casos de uso que cumplen la promesa del sistema los ejecuta el propio sistema.

| Caso de uso | Historia | Requisito |
|---|---|---|
| CU-18 Evaluar vencimientos | HU-25 | RF-24 |
| CU-19 Emitir alerta | HU-25, HU-26, HU-27 | RF-24, RF-25, RF-26 |
| CU-20 Reintentar alerta fallida | HU-29 | RNF-08 |
| CU-21 Avisar suspensión de vigilancia | HU-31 | RF-37 |

**El actor Rama Judicial se dibuja con línea discontinua** porque es externo y no controlado: RN-49 exige que su caída no afecte a ningún otro paquete.

---

## 2. Diagrama Funcional (descomposición)

```mermaid
flowchart TD
    S["IURIS / SGPJ<br/>Sistema de Gestión de Procesos Jurídicos"]

    S --> F1["F1 · Administración<br/>de la plataforma"]
    S --> F2["F2 · Gestión<br/>del despacho"]
    S --> F3["F3 · Gestión<br/>de casos"]
    S --> F4["F4 · Vigilancia<br/>del tiempo ★"]
    S --> F5["F5 · Información<br/>y consulta"]

    F1 --> F11["Registro de despachos<br/>M1 · RF-01"]
    F1 --> F12["Estado activo/inactivo<br/>M1 · RF-02, RF-03"]

    F2 --> F21["Usuarios y roles<br/>M2 · RF-05, RF-06"]
    F2 --> F22["Autenticación<br/>M2 · RF-04"]
    F2 --> F23["Auditoría<br/>M2 · RF-08"]
    F2 --> F24["Catálogos<br/>M11 · RF-33"]
    F2 --> F25["Esquema de alertas<br/>M11 · RF-34"]

    F3 --> F31["Clientes<br/>M3 · RF-09, RF-10"]
    F3 --> F32["Procesos<br/>M4 · RF-11, RF-12, RF-14"]
    F3 --> F33["Expediente digital<br/>M4-M5 · RF-13, RF-15, RF-17, RF-18, RF-38"]

    F4 --> F41["Audiencias<br/>M6 · RF-19, RF-20"]
    F4 --> F42["Términos<br/>M7 · RF-21, RF-22, RF-23"]
    F4 --> F43["Motor de alertas<br/>M8 · RF-24 a RF-27, RF-37"]

    F5 --> F51["Búsqueda<br/>M10 · RF-31"]
    F5 --> F52["Reportes<br/>M10 · RF-32"]
    F5 --> F53["Portal del cliente<br/>M9 · RF-28, RF-29, RF-30"]
    F5 --> F54["Consulta Rama Judicial<br/>M12 · RF-35, RF-36"]

    style F4 fill:#fff3e0,stroke:#e65100,stroke-width:3px
    style F43 fill:#ffe0b2,stroke:#e65100,stroke-width:2px
    style F54 stroke-dasharray: 5 5
```

**F4 es la función que justifica el proyecto.** F1, F2, F3 y F5 existen en cualquier gestor documental; F4 es lo que responde a la razón por la que el consultorio pide el sistema.

---

## 3. Modelo de Datos

### 3.1 Principio estructural

Toda entidad cuelga del **Despacho** (RN-01). No es una decoración del diagrama: es la condición que hace posible el aislamiento RN-02.

```mermaid
erDiagram
    DESPACHO ||--o{ USUARIO : "tiene"
    DESPACHO ||--o{ CLIENTE : "tiene"
    DESPACHO ||--o{ PROCESO : "tiene"
    DESPACHO ||--o{ VALOR_CATALOGO : "define"
    DESPACHO ||--|| ESQUEMA_ALERTA : "configura"
    DESPACHO ||--o{ BITACORA_AUDITORIA : "registra"

    USUARIO }o--o{ ROL : "USUARIO_ROL"
    USUARIO ||--o{ PROCESO : "responsable_de"

    CLIENTE ||--o{ PROCESO : "es_titular_de"
    CLIENTE ||--o| USUARIO : "accede_como"

    PROCESO ||--|| EXPEDIENTE : "tiene"
    PROCESO ||--o{ AUDIENCIA : "programa"
    PROCESO ||--o{ TERMINO : "genera"
    PROCESO }o--|| VALOR_CATALOGO : "estado_procesal"
    PROCESO }o--|| VALOR_CATALOGO : "juzgado"
    PROCESO }o--|| VALOR_CATALOGO : "tipo_proceso"

    EXPEDIENTE ||--o{ DOCUMENTO : "contiene"
    EXPEDIENTE ||--o{ ACTUACION : "contiene"
    EXPEDIENTE ||--o{ NOTA : "contiene"

    DOCUMENTO }o--|| VALOR_CATALOGO : "tipo_documento"
    ACTUACION }o--|| VALOR_CATALOGO : "tipo_actuacion"

    AUDIENCIA ||--o{ ALERTA : "programa"
    TERMINO ||--o{ ALERTA : "programa"
    ESQUEMA_ALERTA ||--o{ ITEM_ESQUEMA : "compone"

    DESPACHO {
        bigint id PK
        string nombre
        string nit
        string estado "ACTIVO | INACTIVO"
        datetime fecha_registro
    }
    USUARIO {
        bigint id PK
        bigint despacho_id FK
        string nombre
        string correo
        string password_hash
        boolean activo
    }
    ROL {
        bigint id PK
        string codigo "ADMIN_PLATAFORMA | ADMIN_DESPACHO | ABOGADO | CLIENTE"
    }
    CLIENTE {
        bigint id PK
        bigint despacho_id FK
        string nombre
        string documento_identidad
        string telefono
        string correo
        bigint usuario_portal_id FK
    }
    PROCESO {
        bigint id PK
        bigint despacho_id FK
        string radicado "unico por despacho"
        bigint juzgado_id FK
        bigint tipo_proceso_id FK
        bigint estado_procesal_id FK
        bigint cliente_titular_id FK
        bigint abogado_responsable_id FK
        datetime fecha_creacion
    }
    EXPEDIENTE {
        bigint id PK
        bigint proceso_id FK "1 a 1"
        datetime fecha_apertura
    }
    DOCUMENTO {
        bigint id PK
        bigint expediente_id FK
        bigint tipo_documento_id FK
        string nombre_archivo
        string ruta_cifrada
        bigint tamano_bytes
        bigint creado_por FK
        datetime creado_en
    }
    ACTUACION {
        bigint id PK
        bigint expediente_id FK
        bigint tipo_actuacion_id FK
        date fecha_actuacion
        string descripcion
        string origen "MANUAL | RAMA_JUDICIAL"
        bigint creado_por FK
        datetime creado_en
    }
    NOTA {
        bigint id PK
        bigint expediente_id FK
        string contenido
        bigint creado_por FK
        datetime creado_en
    }
    AUDIENCIA {
        bigint id PK
        bigint proceso_id FK
        datetime fecha_hora "hora obligatoria"
        string lugar
        string observaciones
    }
    TERMINO {
        bigint id PK
        bigint proceso_id FK
        string descripcion
        date fecha_vencimiento "la indica el abogado"
        string estado "PENDIENTE | CUMPLIDO | VENCIDO"
        bigint actuacion_origen_id FK
    }
    ALERTA {
        bigint id PK
        string tipo_evento "AUDIENCIA | TERMINO"
        bigint evento_id
        bigint destinatario_id FK
        datetime programada_para
        datetime enviada_en
        string estado "PROGRAMADA | ENVIADA | FALLIDA"
        int intentos
        string detalle_error
    }
    ESQUEMA_ALERTA {
        bigint id PK
        bigint despacho_id FK
    }
    ITEM_ESQUEMA {
        bigint id PK
        bigint esquema_id FK
        int dias_anticipacion
    }
    VALOR_CATALOGO {
        bigint id PK
        bigint despacho_id FK
        string tipo_catalogo "ESTADO | TIPO_PROCESO | TIPO_DOC | TIPO_ACT | JUZGADO"
        string nombre
        boolean activo
        boolean protegido "Activo y Archivado"
    }
    BITACORA_AUDITORIA {
        bigint id PK
        bigint despacho_id FK
        bigint usuario_id FK
        bigint expediente_id FK
        string accion
        datetime ocurrido_en
    }
```

### 3.2 Las siete decisiones de modelado que hay que justificar

| # | Decisión | Por qué, y qué regla la obliga |
|---|---|---|
| 1 | **`despacho_id` en toda entidad raíz** | Sin la columna no hay forma de filtrar por tenant. **RN-01, RN-02** |
| 2 | **`USUARIO_ROL` como tabla intermedia** (muchos a muchos) | Un `rol_id` dentro de `USUARIO` haría **imposible** el abogado independiente, que necesita dos roles simultáneos. **RN-08** |
| 3 | **`VALOR_CATALOGO` única, con `tipo_catalogo`** | Cinco tablas casi idénticas serían duplicación. Una sola tabla tipificada, con `despacho_id`, cubre los cinco catálogos administrables — incluido **Juzgado**, que se añadió sin crear ninguna tabla nueva. **RN-06a, RN-06b, D-13, D-17** |
| 4 | **`protegido` en `VALOR_CATALOGO`** | Es el mecanismo que impide desactivar *Activo* y *Archivado*, exigidos por P-RF05. **RN-06a** |
| 5 | **`ALERTA` como entidad persistida, no calculada al vuelo** | Sin fila no hay forma de saber si se envió, ni de reintentar, ni de demostrarlo después. **RN-33, RN-34** |
| 6 | **`CLIENTE.usuario_portal_id` opcional** | El cliente existe en el sistema **antes** de tener acceso al portal; el acceso lo habilita el despacho después. **RN-43, D-15** |
| 7 | **`ACTUACION.origen`** | Distingue lo registrado por el abogado de lo traído de la Rama Judicial, que **nunca** puede presentarse como oficial. **RN-48** |

### 3.3 Lo que NO está en el modelo, deliberadamente

| Ausencia | Motivo |
|---|---|
| Tablas de plan, precio, factura, pago | La monetización ocurre fuera del sistema. **D-06** |
| Campo `visible` en `DOCUMENTO` y `ACTUACION` | El cliente ve **todas**; la visibilidad depende del *tipo de pieza*, no de una marca por pieza. **D-12** |
| Campo `eliminado` o borrado físico | Ni procesos ni piezas del expediente se eliminan. **RN-19, RN-27** |
| Campo de honorarios | Fuera de alcance. **D-08** |

### 3.4 Resolución del asunto A-04 — *Juzgado* como quinto catálogo **[D-17]**

**El problema detectado:** `PROCESO.juzgado` estaba modelado como texto libre. Con P-RNF02 **[P]** exigiendo búsqueda por juzgado, el mismo juzgado acabaría escrito de formas distintas —*"Juzgado 1 Civil"*, *"J. 1° Civil del Circuito"*, *"juzgado primero civil"*— y la búsqueda devolvería resultados incompletos. **Un requisito literal de la propuesta habría quedado degradado.**

**La resolución:** `juzgado_id` con clave foránea a `VALOR_CATALOGO`, `tipo_catalogo = 'JUZGADO'`. **Cero tablas nuevas, cero migración** — no hay datos aún — y RF-33 ya tenía la pantalla construida.

**La decisión no obvia fue el ámbito, no la existencia del catálogo.** Los juzgados son entidades del mundo real, compartidas entre despachos: parecería que corresponden a una lista nacional mantenida por el Administrador de Plataforma. Se descartó, y el motivo importa:

> Un directorio nacional de juzgados es una **responsabilidad de mantenimiento permanente** que ningún requisito pidió, que se desactualiza sola, y que convertiría al Administrador de Plataforma en curador de datos jurídicos — un rol que la Fase 1 le negó explícitamente.

Un despacho litiga ante un puñado de juzgados, no ante todos los del país. Su lista se construye sola con el uso. Y si más adelante la duplicación entre despachos llega a doler, **promover el catálogo a global es sencillo; el camino inverso no lo es.**

## 4. Diagrama de Clases

```mermaid
classDiagram
    class Despacho {
        -Long id
        -String nombre
        -EstadoDespacho estado
        +estaActivo() boolean
        +desactivar() void
        +activar() void
    }

    class Usuario {
        -Long id
        -Despacho despacho
        -String correo
        -Set~Rol~ roles
        +tieneRol(codigo) boolean
        +permisos() Set~Permiso~
    }

    class Rol {
        -String codigo
        -Set~Permiso~ permisos
    }

    class Cliente {
        -Long id
        -String nombre
        -Usuario usuarioPortal
        +tieneAccesoPortal() boolean
    }

    class Proceso {
        -Long id
        -String radicado
        -String juzgado
        -ValorCatalogo estadoProcesal
        -Cliente titular
        -Usuario abogadoResponsable
        +estaArchivado() boolean
        +admiteAlertas() boolean
        +archivar() void
    }

    class Expediente {
        -Long id
        -Proceso proceso
        +piezasVisiblesParaCliente() List~Pieza~
    }

    class Pieza {
        <<abstract>>
        -Long id
        -Usuario creadoPor
        -LocalDateTime creadoEn
        +esVisibleParaCliente()* boolean
    }

    class Documento {
        -String rutaCifrada
        -long tamanoBytes
        +esVisibleParaCliente() boolean
    }

    class Actuacion {
        -LocalDate fechaActuacion
        -OrigenActuacion origen
        +esVisibleParaCliente() boolean
        +esOficial() boolean
    }

    class Nota {
        -String contenido
        +esVisibleParaCliente() boolean
    }

    class EventoVigilado {
        <<abstract>>
        -Proceso proceso
        +fechaObjetivo()* LocalDateTime
        +anticipaciones()* List~Duration~
        +requiereVigilancia()* boolean
    }

    class Audiencia {
        -LocalDateTime fechaHora
        +anticipaciones() List~Duration~
    }

    class Termino {
        -LocalDate fechaVencimiento
        -EstadoTermino estado
        +marcarCumplido() void
        +requiereVigilancia() boolean
    }

    class Alerta {
        -EventoVigilado evento
        -Usuario destinatario
        -LocalDateTime programadaPara
        -EstadoAlerta estado
        -int intentos
        +marcarEnviada() void
        +marcarFallida(error) void
        +puedeReintentar() boolean
    }

    class MotorAlertas {
        <<service>>
        +evaluarVencimientos() void
        +programarAlertas(evento) void
        +emitir(alerta) void
        +reintentarFallidas() void
    }

    class EsquemaAlerta {
        -Despacho despacho
        -List~Integer~ diasAnticipacion
        +validar() void
        +esVacio() boolean
    }

    Despacho "1" *-- "*" Usuario
    Despacho "1" *-- "*" Cliente
    Despacho "1" *-- "*" Proceso
    Despacho "1" *-- "1" EsquemaAlerta
    Usuario "*" -- "*" Rol
    Cliente "1" -- "0..1" Usuario
    Cliente "1" *-- "*" Proceso
    Proceso "1" *-- "1" Expediente
    Proceso "1" *-- "*" EventoVigilado
    Expediente "1" *-- "*" Pieza
    Pieza <|-- Documento
    Pieza <|-- Actuacion
    Pieza <|-- Nota
    EventoVigilado <|-- Audiencia
    EventoVigilado <|-- Termino
    EventoVigilado "1" *-- "*" Alerta
    MotorAlertas ..> Alerta : gestiona
    MotorAlertas ..> EsquemaAlerta : consulta
```

### Las tres abstracciones del diseño

**1 · `Pieza` abstracta con `esVisibleParaCliente()`**
Las tres piezas del expediente comparten estructura (autor, fecha) pero difieren en **una sola cosa**: si el cliente las ve. `Documento` y `Actuacion` devuelven `true`; `Nota` devuelve `false`, siempre. Poner la visibilidad como método polimórfico —y no como un `if` repartido por el portal— hace que **RN-24 sea imposible de violar por olvido**: el día que se añada una cuarta pieza, el compilador obligará a decidir su visibilidad.

**2 · `EventoVigilado` abstracta**
Audiencias y términos son cosas distintas del dominio, pero para el motor de alertas son **lo mismo**: algo con una fecha objetivo y un esquema de anticipaciones. Esta abstracción evita duplicar toda la lógica de vigilancia. `Audiencia.anticipaciones()` devuelve las tres fijas de P-RF03; `Termino.anticipaciones()` consulta el esquema configurable de D-16.

**3 · `EsquemaAlerta.validar()`**
La barrera contra R-08 vive **en el modelo de dominio**, no en el formulario. Si estuviera solo en la interfaz, una carga masiva o un cambio por API podría dejar un despacho con cero alertas.

---

## 5. Diagrama de Componentes

```mermaid
flowchart TB
    subgraph FE["Frontend · Angular SPA"]
        UI1["Módulo Administración"]
        UI2["Módulo Casos"]
        UI3["Módulo Vigilancia"]
        UI4["Portal Cliente"]
    end

    subgraph API["Backend · Spring Boot"]
        direction TB
        SEC["★ Filtro de Seguridad<br/>autenticación · roles · TENANT · estado despacho<br/>RNF-01 RNF-02 RNF-03"]

        subgraph SRV["Servicios de dominio"]
            S1["DespachoService<br/>M1"]
            S2["UsuarioService<br/>M2"]
            S3["CasoService<br/>M3 M4"]
            S4["ExpedienteService<br/>M5"]
            S5["VigilanciaService<br/>M6 M7"]
            S6["ConsultaService<br/>M10"]
            S7["AdminDespachoService<br/>M11"]
        end

        subgraph ASY["Procesos autónomos"]
            MOT["★ MotorAlertas<br/>M8 · sin actor humano"]
            SCH["Planificador<br/>tarea programada"]
        end

        INT["ClienteRamaJudicial<br/>M12 · desacoplado"]
        REP["Repositorios JPA"]
    end

    subgraph INFRA["Infraestructura"]
        DB[("PostgreSQL")]
        OBJ[("Almacén de documentos<br/>cifrado · RNF-04")]
        SMTP["Servicio de correo"]
    end

    RJ["Servicio Rama Judicial<br/>externo · no controlado"]

    UI1 & UI2 & UI3 & UI4 --> SEC
    SEC --> S1 & S2 & S3 & S4 & S5 & S6 & S7
    S1 & S2 & S3 & S5 & S6 & S7 --> REP
    S4 --> REP
    S4 --> OBJ
    REP --> DB
    SCH --> MOT
    MOT --> REP
    MOT --> SMTP
    S5 -.programa alertas.-> MOT
    S3 -.consulta opcional.-> INT
    INT -.HTTP · degradable.-> RJ

    style SEC fill:#c62828,stroke:#8e0000,color:#fff
    style MOT fill:#e65100,stroke:#bf360c,color:#fff
    style INT stroke-dasharray: 5 5
    style RJ fill:#eceff1,stroke:#607d8b,stroke-dasharray: 5 5
```

### Las tres decisiones de componentes

**1 · El Filtro de Seguridad es un punto único de paso.**
Toda petición del frontend lo atraviesa antes de llegar a cualquier servicio. Allí se resuelven cuatro cosas juntas: autenticación (RF-04), roles por unión (RNF-03), **tenant del usuario** (RNF-01) y **estado del despacho** (RNF-02).

Está dibujado así porque RNF-02 lo exige literalmente: *"en un único punto de control"*. Si el estado del despacho se verificara servicio por servicio, alguno se olvidaría — y ese olvido sería una funcionalidad operando en un despacho inactivo.

**2 · El Motor de Alertas cuelga del Planificador, no del frontend.**
No hay ninguna flecha desde la interfaz hacia `MotorAlertas`. Es la representación gráfica de RN-30: **ningún usuario invoca las alertas**. Si existiera esa flecha, el sistema habría vuelto a depender de que alguien se acuerde.

**3 · `ClienteRamaJudicial` está aislado con línea discontinua.**
Ningún servicio del núcleo depende de él: solo `CasoService` lo consulta, y de forma opcional. Es RN-49 hecho estructura — la caída del servicio externo no puede propagarse.

---

## 6. Diagrama de Despliegue

Stack: **Angular + Spring Boot + PostgreSQL** **[D-02]**.

```mermaid
flowchart TB
    subgraph CLI["Dispositivos cliente"]
        NAV["Navegador web<br/>abogado · administrador"]
        NAVC["Navegador web<br/>cliente del despacho"]
    end

    subgraph NUBE["Infraestructura en la nube"]
        subgraph N1["Nodo · Servidor web"]
            WEB["Servidor web / CDN<br/>Angular compilado"]
        end

        subgraph N2["Nodo · Servidor de aplicaciones"]
            APP["Spring Boot<br/>API REST + Motor de alertas<br/>JVM"]
        end

        subgraph N3["Nodo · Base de datos"]
            PG[("PostgreSQL<br/>cifrado en reposo")]
        end

        subgraph N4["Nodo · Almacenamiento"]
            S3[("Almacén de objetos<br/>documentos cifrados<br/>RNF-04")]
        end

        subgraph N5["Respaldo"]
            BK[("Respaldo diario<br/>RNF-14")]
        end
    end

    MAIL["Servidor SMTP<br/>servicio externo"]
    RAMA["API Rama Judicial<br/>servicio externo"]

    NAV -->|HTTPS| WEB
    NAVC -->|HTTPS| WEB
    WEB -->|HTTPS / REST| APP
    APP -->|JDBC / TLS| PG
    APP -->|API cifrada| S3
    APP -->|SMTP / TLS| MAIL
    APP -.->|HTTPS · degradable| RAMA
    PG -.->|copia diaria| BK
    S3 -.->|copia diaria| BK

    style APP fill:#e65100,stroke:#bf360c,color:#fff
    style RAMA fill:#eceff1,stroke:#607d8b,stroke-dasharray: 5 5
```

### Notas de despliegue

| Punto | Justificación |
|---|---|
| **Todo el tráfico va por HTTPS/TLS**, incluido el portal del cliente | RNF-06, sin excepciones |
| **Documentos separados de la base de datos**, en almacén de objetos cifrado | RNF-04. Separarlos permite cifrar y respaldar con políticas propias |
| **El Motor de Alertas vive dentro del nodo de aplicación** | Simplifica el despliegue del proyecto. ⚠ Si se escalara a varias instancias, habría que garantizar que la alerta se emite **una sola vez** (RNF-10) mediante bloqueo distribuido. Queda anotado para la Fase 6 |
| **Respaldo diario de BD y del almacén** | RNF-14. Los documentos también se respaldan: respaldar solo la base dejaría expedientes sin sus piezas |
| **La API de la Rama Judicial va con línea discontinua** | Su caída no debe afectar al nodo de aplicación (RN-49) |

---

## 7. Diagramas de Secuencia

### 7.1 Emisión de una alerta ★ — el flujo que justifica el sistema

```mermaid
sequenceDiagram
    autonumber
    participant SCH as Planificador
    participant MOT as MotorAlertas
    participant REP as Repositorio
    participant SMTP as Servicio correo
    participant AB as Abogado

    Note over SCH,MOT: Nadie inicia esto. Se ejecuta solo. RN-30

    SCH->>MOT: evaluarVencimientos()
    MOT->>REP: buscarAlertasProgramadas(ahora)
    REP-->>MOT: lista de alertas

    loop por cada alerta
        MOT->>REP: cargar evento y proceso
        REP-->>MOT: evento + proceso

        alt Proceso archivado o término cumplido
            MOT->>REP: descartar alerta - RF-27
            Note right of MOT: No se emite.<br/>Evita el ruido que erosiona<br/>la confianza. R-05
        else Requiere vigilancia
            MOT->>SMTP: enviar(destinatario, contenido)

            alt Envío correcto
                SMTP-->>MOT: OK
                MOT->>REP: marcarEnviada() - RNF-09
                SMTP->>AB: correo de alerta
            else Envío fallido
                SMTP-->>MOT: error
                MOT->>REP: marcarFallida(intentos+1)
                Note right of MOT: NUNCA se descarta.<br/>RNF-08 · riesgo R-02

                alt Quedan reintentos
                    MOT->>MOT: reprogramar reintento
                else Reintentos agotados
                    MOT->>REP: estado = FALLIDA visible
                    Note right of MOT: Aparece marcada en el<br/>panel del despacho.<br/>HU-29 CA-29.2
                end
            end
        end
    end
```

**El punto crítico es la rama de envío fallido.** Ahí está la diferencia entre un sistema en el que se puede confiar y uno que es peor que no tener nada: la alerta fallida **nunca desaparece**, se reintenta y, si se agota, queda visible dentro del sistema.

### 7.2 Acceso del cliente a su expediente

```mermaid
sequenceDiagram
    autonumber
    participant CL as Cliente
    participant UI as Portal Angular
    participant SEC as Filtro de Seguridad
    participant SRV as ExpedienteService
    participant REP as Repositorio

    CL->>UI: iniciar sesión
    UI->>SEC: POST /auth
    SEC->>REP: validar credenciales (hash)
    REP-->>SEC: usuario + roles + despacho

    alt Despacho INACTIVO
        SEC-->>UI: 403 · despacho inactivo
        UI-->>CL: "Su despacho está inactivo"
        Note over SEC: RF-03 · el bloqueo alcanza<br/>también al cliente
    else Despacho ACTIVO
        SEC-->>UI: token con despacho y roles
        CL->>UI: ver mi expediente
        UI->>SEC: GET /portal/expediente/{id}
        SEC->>SEC: verificar tenant + rol CLIENTE

        alt El expediente no es del cliente
            SEC-->>UI: 403 denegado
            Note over SEC: RN-41 · deniega,<br/>NO devuelve vacío ambiguo
        else Es su expediente
            SEC->>SRV: obtenerParaCliente(id)
            SRV->>REP: cargar piezas
            REP-->>SRV: documentos + actuaciones + notas
            SRV->>SRV: filtrar esVisibleParaCliente()
            Note right of SRV: Las NOTAS se excluyen AQUÍ,<br/>en los datos — no en la pantalla.<br/>RN-24 · HU-34 CA-34.2
            SRV-->>SEC: documentos + actuaciones
            SEC->>REP: registrar en bitácora - RF-08
            SEC-->>UI: expediente sin notas
            UI-->>CL: expediente
        end
    end
```

**El filtrado de notas ocurre en el servicio, no en el portal.** Si el servicio devolviera las notas y Angular simplemente no las pintara, la información ya habría salido del despacho — bastaría abrir las herramientas del navegador. Es CA-34.2 hecho secuencia.

### 7.3 Consulta a la Rama Judicial, con degradación

```mermaid
sequenceDiagram
    autonumber
    participant AB as Abogado
    participant SRV as CasoService
    participant INT as ClienteRamaJudicial
    participant RJ as API Rama Judicial

    AB->>SRV: consultar actuaciones del radicado
    SRV->>INT: consultarPorRadicado(radicado)
    INT->>RJ: GET /procesos/{radicado}

    alt Servicio disponible
        RJ-->>INT: actuaciones publicadas
        INT-->>SRV: lista normalizada
        SRV-->>AB: actuaciones marcadas NO OFICIAL
        Note over AB: RN-48 · apoyo al seguimiento.<br/>No sustituye su verificación
        AB->>SRV: incorporar al expediente
        SRV->>SRV: guardar con origen=RAMA_JUDICIAL
    else Servicio caído o sin respuesta
        RJ--xINT: timeout / error
        INT-->>SRV: no disponible
        SRV-->>AB: "Consulta no disponible. Puede registrar manualmente."
        Note over AB,SRV: RN-49 · el sistema sigue<br/>operando al 100%.<br/>Las alertas no se ven afectadas
    end
```

---

## 8. Diagrama de Actividad — ciclo de vida de un término

```mermaid
flowchart TD
    INI([Inicio]) --> A1["Ocurre una actuación<br/>en el proceso"]
    A1 --> A2["El ABOGADO determina<br/>que nace un término"]
    A2 --> A3{"¿El sistema calcula<br/>la fecha?"}
    A3 -->|NO · RN-36| A4["El abogado registra<br/>la fecha de vencimiento"]

    A4 --> A5["Sistema consulta el<br/>esquema de alertas del despacho"]
    A5 --> A6{"¿El esquema<br/>tiene alertas?"}
    A6 -->|Cero · imposible| A7["RECHAZADO<br/>RN-37b · mínimo 1"]
    A7 --> A5
    A6 -->|Una o más| A8["Programar las alertas<br/>anticipadas"]

    A8 --> A9{"Estado del<br/>término"}
    A9 -->|PENDIENTE| A10{"¿Llegó el momento<br/>de una alerta?"}
    A10 -->|No| A9
    A10 -->|Sí| A11{"¿Proceso archivado?"}
    A11 -->|Sí| A12["No emitir<br/>RF-27"]
    A11 -->|No| A13["Emitir alerta<br/>al abogado responsable"]
    A13 --> A14{"¿Envío<br/>correcto?"}
    A14 -->|Sí| A15["Registrar ENVIADA"]
    A14 -->|No| A16["Reintentar · RNF-08"]
    A16 --> A17{"¿Reintentos<br/>agotados?"}
    A17 -->|No| A13
    A17 -->|Sí| A18["Marcar FALLIDA<br/>y hacerla visible"]

    A15 --> A9
    A18 --> A9
    A12 --> FIN
    A9 -->|El abogado actúa| A19["Marcar CUMPLIDO"]
    A19 --> A20["Cancelar alertas<br/>pendientes · RN-39"]
    A20 --> FIN([Fin])
    A9 -->|Pasó la fecha sin cumplir| A21["Marcar VENCIDO"]
    A21 --> FIN

    style A3 fill:#c62828,stroke:#8e0000,color:#fff
    style A7 fill:#c62828,stroke:#8e0000,color:#fff
    style A18 fill:#e65100,stroke:#bf360c,color:#fff
```

**Los dos nodos rojos son las fronteras del sistema:** A3 marca dónde el sistema **no** cruza (no calcula plazos, RN-36) y A7 marca dónde el sistema **no cede** (no acepta cero alertas, RN-37b). Uno protege la responsabilidad profesional; el otro, la razón de ser del producto.

---

## 9. Flujo General del Sistema

```mermaid
flowchart LR
    subgraph EXT["Fuera del sistema"]
        COM["Gestión comercial<br/>D-06"]
        JUZ["Juzgado /<br/>Rama Judicial"]
    end

    subgraph ALTA["1 · Alta"]
        F1["Registrar despacho"]
        F2["Crear usuarios<br/>y roles"]
        F3["Configurar catálogos<br/>y esquema de alertas"]
    end

    subgraph CASO["2 · Apertura del caso"]
        F4["Registrar cliente"]
        F5["Crear proceso<br/>con radicado"]
        F6["Expediente creado<br/>automáticamente"]
    end

    subgraph GEST["3 · Gestión del caso"]
        F7["Cargar documentos"]
        F8["Registrar actuaciones"]
        F9["Notas internas"]
    end

    subgraph VIG["4 · Vigilancia ★"]
        F10["Registrar audiencias<br/>y términos"]
        F11["El sistema vigila<br/>sin intervención"]
        F12["Alertas por correo<br/>al abogado"]
    end

    subgraph INFO["5 · Información"]
        F13["Portal del cliente"]
        F14["Búsqueda y reportes"]
    end

    COM -->|activo/inactivo| F1
    F1 --> F2 --> F3
    F3 --> F4 --> F5 --> F6
    F6 --> F7 & F8 & F9
    JUZ -.consulta opcional.-> F8
    F8 -->|nace un término| F10
    F10 --> F11 --> F12
    F7 & F8 --> F13
    F5 & F10 --> F14
    F12 -.el abogado actúa.-> F8

    style VIG fill:#fff3e0,stroke:#e65100,stroke-width:3px
    style F11 fill:#e65100,stroke:#bf360c,color:#fff
```

**El ciclo se cierra en la flecha punteada de retorno:** la alerta lleva al abogado a actuar, esa actuación se registra, y de ella puede nacer un nuevo término. **El sistema no es lineal, es un ciclo de vigilancia continua** — y esa es la diferencia con un archivador digital.

---

## 10. Flujos Individuales — procesos de negocio

Corresponden a PN-1 a PN-5 de la Fase 1 §7.

### 10.1 PN-1 · Vinculación del cliente y apertura del proceso *(Sprint 1)*

```mermaid
flowchart TD
    A([Cliente llega al despacho]) --> B["Abogado registra al cliente<br/>datos personales + tipo de proceso<br/>RF-09"]
    B --> C["Crear proceso"]
    C --> D{"¿Radicado ya existe<br/>en este despacho?"}
    D -->|Sí| E["Rechazar y mostrar<br/>el proceso existente · RF-12"]
    E --> C
    D -->|No| F["Validar los 6 campos<br/>obligatorios · RF-11"]
    F --> G{"¿Completos?"}
    G -->|No| H["Indicar cuál falta"]
    H --> F
    G -->|Sí| I["Guardar proceso"]
    I --> J["Crear expediente<br/>automáticamente · RF-13"]
    J --> K([Caso abierto])
```

### 10.2 PN-2 · Gestión del expediente *(Sprint 2)*

```mermaid
flowchart TD
    A([Avanza el proceso]) --> B{"¿Qué registro?"}
    B -->|Documento| C["Mostrar advertencia:<br/>el cliente lo verá de inmediato<br/>RF-16"]
    C --> D{"¿Continuar?"}
    D -->|No| E["Registrar como<br/>nota interna"]
    D -->|Sí| F["Cargar y cifrar · RNF-04"]
    B -->|Actuación| G["Registrar con fecha<br/>y tipo · RF-17"]
    B -->|Nota interna| E
    F --> H["Sellar autor y fecha<br/>RF-38"]
    G --> H
    E --> H
    H --> I{"¿La actuación<br/>genera un término?"}
    I -->|Sí| J["Ir a PN-3"]
    I -->|No| K([Expediente actualizado])
```

### 10.3 PN-3 · Vigilancia de audiencias y términos ★ *(Sprint 3)*

```mermaid
flowchart TD
    A([Evento por vigilar]) --> B{"¿Audiencia<br/>o término?"}
    B -->|Audiencia| C["Registrar fecha Y HORA<br/>obligatorias · RF-19"]
    C --> D["Programar las 3 alertas fijas<br/>48h · 24h · día · RF-25"]
    B -->|Término| E["El ABOGADO indica<br/>la fecha de vencimiento<br/>RF-21 · el sistema no la calcula"]
    E --> F["Aplicar el esquema<br/>del despacho · RF-26"]
    F --> G{"¿Mínimo<br/>una alerta?"}
    G -->|No| H["Rechazar · RN-37b"]
    H --> F
    G -->|Sí| I["Programar alertas"]
    D --> J["VIGILANCIA AUTÓNOMA<br/>sin intervención humana"]
    I --> J
    J --> K["Emitir alertas por correo<br/>al abogado responsable"]
    K --> L{"¿Envío<br/>correcto?"}
    L -->|Sí| M([Abogado avisado])
    L -->|No| N["Reintentar y dejar<br/>visible · RNF-08"]
    N --> M

    style J fill:#e65100,stroke:#bf360c,color:#fff
```

### 10.4 PN-4 · Información al cliente y control del despacho *(Sprint 4)*

```mermaid
flowchart TD
    A([Necesidad de información]) --> B{"¿Quién<br/>consulta?"}
    B -->|Cliente| C["Autenticarse en el portal<br/>acceso habilitado por el despacho<br/>RF-07"]
    C --> D["Verificar que el expediente<br/>es suyo · RN-41"]
    D --> E["Filtrar piezas visibles<br/>excluir NOTAS · RF-30"]
    E --> F["Mostrar proceso, estado,<br/>actuaciones, documentos,<br/>audiencias · RF-29"]
    F --> G["Registrar acceso<br/>en bitácora · RF-08"]
    B -->|Despacho| H{"¿Buscar o<br/>reportar?"}
    H -->|Buscar| I["Radicado · cliente ·<br/>juzgado · tipo<br/>RF-31"]
    H -->|Reportar| J["Activos · archivados ·<br/>por estado procesal<br/>RF-32"]
    I --> K["Filtrar SIEMPRE<br/>por despacho · RN-45"]
    J --> K
    G --> L([Información entregada])
    K --> L
```

### 10.5 PN-5 · Sincronización con la Rama Judicial *(posterior)*

```mermaid
flowchart TD
    A([Abogado quiere revisar]) --> B["Solicitar consulta<br/>por radicado · RF-35"]
    B --> C{"¿Servicio<br/>disponible?"}
    C -->|No| D["Informar y ofrecer<br/>registro manual · RF-36"]
    D --> E["Registrar actuación<br/>manualmente · PN-2"]
    C -->|Sí| F["Traer actuaciones<br/>publicadas"]
    F --> G["Mostrar marcadas<br/>NO OFICIAL · RN-48"]
    G --> H{"¿Incorporar<br/>al expediente?"}
    H -->|No| I([Solo consulta])
    H -->|Sí| J["Guardar con<br/>origen = RAMA_JUDICIAL"]
    J --> K["El abogado verifica<br/>y decide si nace un término"]
    K --> L([Expediente actualizado])

    style C fill:#eceff1,stroke:#607d8b
```

**Obsérvese que ambas ramas de C llevan a un expediente actualizado.** Eso es RN-49 hecho diagrama: el servicio externo caído no bloquea nada, solo cambia el camino.

---

## 11. Verificación de coherencia

Comprobación de que los diagramas no contradicen la documentación previa:

| Regla crítica | Dónde se ve materializada |
|---|---|
| **RN-02** aislamiento | `despacho_id` en el modelo de datos · Filtro de Seguridad en componentes · verificación de tenant en secuencia 7.2 |
| **RN-08** roles acumulables | `USUARIO_ROL` muchos a muchos · `Usuario.permisos()` por unión en clases |
| **RN-24** notas nunca al cliente | `Nota.esVisibleParaCliente() = false` en clases · filtrado en el **servicio** en secuencia 7.2 · PN-4 |
| **RN-30** alertas sin actor humano | Paquete P6 sin actores en casos de uso · sin flecha desde el frontend en componentes · secuencia 7.1 iniciada por el Planificador |
| **RN-34** ninguna alerta en silencio | Entidad `ALERTA` persistida · rama de fallo en 7.1 · nodo A18 en actividad |
| **RN-36** no calcular plazos | Nodo rojo A3 en el diagrama de actividad · PN-3 |
| **RN-37b** mínimo una alerta | `EsquemaAlerta.validar()` en clases · nodo rojo A7 en actividad · PN-3 |
| **RN-49** degradación externa | Línea discontinua en componentes y despliegue · ambas ramas de 7.3 y PN-5 terminan bien |

---

## 12. Cierre de la fase

### Asunto detectado en esta fase — resuelto

| ID | Asunto | Desenlace |
|---|---|---|
| **A-04** | `PROCESO.juzgado` es texto libre, pero P-RNF02 exige buscar por juzgado | **Resuelto → D-17.** Quinto catálogo administrable por despacho. Ver §3.4 |

### Anotación para la Fase 6 — resuelta

El Motor de Alertas se desplegó dentro del nodo de aplicación. Si se escalara a varias instancias, **dos instancias podrían emitir la misma alerta**, violando RNF-10. **Resuelto en ADR-04:** las alertas se toman con `SELECT … FOR UPDATE SKIP LOCKED`, que además reparte el trabajo entre instancias en lugar de dejar una sola trabajando.

### Qué habilita la Fase 6

La arquitectura bajo **ISO/IEC/IEEE 42010** organiza el sistema en **vistas** dirigidas a las preocupaciones de cada interesado. Estos diagramas son la materia prima:

| Vista 42010 | Diagramas que la alimentan |
|---|---|
| Lógica | Clases (§4) · Modelo de datos (§3) |
| Procesos | Secuencia (§7) · Actividad (§8) |
| Desarrollo | Componentes (§5) · Funcional (§2) |
| Física | Despliegue (§6) |
| Escenarios | Casos de uso (§1) · Flujos (§9, §10) |

Y las **preocupaciones de los interesados** ya están identificadas: confidencialidad (RN-02, RN-24), confiabilidad de la alerta (R-02, R-08), frontera de responsabilidad legal (RN-36) y degradación ante servicios externos (RN-49).
