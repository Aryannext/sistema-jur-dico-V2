# Fase 7 — Convenciones de Código

**Proyecto:** Iuris / SGPJ
**Deriva de:** **[D-21]** y **[D-22]** · [`06-arquitectura-ieee-42010.md`](06-arquitectura-ieee-42010.md)
**Versión:** 1.0 · **Fecha:** 2026-08-20

> Este documento existe para que *"código limpio y SOLID"* sea **verificable** en una revisión, y no una intención. Cada regla se puede comprobar mirando el código.

---

## 1. Idioma del código **[D-21]**

**Dominio en español, sufijos técnicos en inglés.**

| Elemento | Idioma | Ejemplo |
|---|---|---|
| Clases de dominio | Español | `Despacho`, `Expediente`, `TerminoJudicial`, `EsquemaAlerta` |
| Atributos y métodos de dominio | Español | `fechaVencimiento`, `estaArchivado()`, `marcarCumplido()` |
| Tablas y columnas | Español, `snake_case` | `proceso`, `fecha_vencimiento`, `despacho_id` |
| Sufijos técnicos | Inglés | `DespachoRepository`, `ProcesoService`, `ClienteController` |
| Paquetes | Español | `co.iuris.sgpj.dominio.expediente` |
| Mensajes al usuario | **Español siempre** | `"El radicado ya está registrado en su despacho"` |
| Pruebas | Español descriptivo | `unDespachoNoPuedeVerProcesosDeOtroDespacho()` |

**Por qué esta mezcla y no otra:** el glosario de la Fase 1 y las reglas de negocio están en español. Traducir el dominio al inglés rompería la trazabilidad — nadie podría leer `RN-17` y encontrar la clase que la implementa. Los sufijos técnicos siguen la convención de Spring porque no pertenecen al dominio: son andamiaje.

**Regla práctica:** *si la palabra aparece en el glosario de la Fase 1, va en español.*

---

## 2. Estructura de paquetes

Organización **por dominio, no por capa técnica**. Un paquete `controllers/` con 30 controladores dentro no dice nada sobre el sistema; `expediente/` sí.

```
co.iuris.sgpj
├── comun/                    Utilidades transversales, excepciones base
├── seguridad/                M2 · autenticación, roles, contexto de tenant
│   ├── dominio/
│   ├── aplicacion/
│   └── infraestructura/
├── despacho/                 M1 · despachos y su estado
├── cliente/                  M3
├── proceso/                  M4 · procesos y expedientes
├── expediente/               M5 · documentos, actuaciones, notas
├── vigilancia/               M6 M7 · audiencias y términos
├── alertas/                  M8 · motor de alertas ★
├── consulta/                 M10 · búsqueda y reportes
├── portal/                   M9 · portal del cliente
├── administracion/           M11 · catálogos y esquema de alertas
└── integracion/              M12 · Rama Judicial (desacoplado)
```

Cada módulo repite internamente tres capas:

| Capa | Contiene | **Nunca** contiene |
|---|---|---|
| `dominio/` | Entidades, reglas de negocio, objetos de valor | Anotaciones de framework web, SQL, llamadas HTTP |
| `aplicacion/` | Servicios que orquestan casos de uso | Reglas de negocio |
| `infraestructura/` | Repositorios, controladores, adaptadores | Reglas de negocio |

**Esta separación es la letra D de SOLID** (inversión de dependencias) y la que atiende **C-10**: el dominio se prueba sin levantar la aplicación.

---

## 3. SOLID — dónde vive cada principio

No como teoría, sino como comprobación concreta sobre este código:

| Principio | Cómo se verifica en Iuris |
|---|---|
| **S** · Responsabilidad única | Un servicio pertenece a un módulo. Si `ProcesoService` empieza a enviar correos, la responsabilidad se rompió |
| **O** · Abierto/cerrado | Añadir una cuarta clase de `Pieza` **no debe obligar a tocar el portal**. Si hay que tocarlo, el diseño falló |
| **L** · Sustitución de Liskov | Cualquier `Pieza` puede tratarse como `Pieza`; el portal no pregunta de qué tipo es, llama a `esVisibleParaCliente()` |
| **I** · Segregación de interfaces | Ningún servicio expone métodos que sus consumidores no usan. Sin un `SistemaService` con 40 métodos |
| **D** · Inversión de dependencias | El dominio **no depende del comportamiento** de la infraestructura: no llama a repositorios, HTTP, correo ni servicios externos. Ver la precisión de §3.1 sobre las anotaciones JPA |

**Prueba objetiva del principio D:** si una clase de `dominio/` importa `org.springframework.web`, un repositorio, un cliente HTTP o el adaptador de correo, está mal. Se verifica con una búsqueda de texto.

### 3.1 Precisión sobre JPA — corrección de una regla mal formulada

La primera versión de este documento decía que el dominio *"no importa nada de `jakarta.persistence`"*. **Era una regla mal formulada y se corrige aquí**, porque llevaba a una conclusión equivocada.

Mantenerla al pie de la letra obligaría a duplicar cada entidad —una de dominio y otra de persistencia— más un mapeador entre ambas. Para 4 sprints y una sola persona, eso es sobreingeniería: triplica el código sin reducir ningún riesgo real.

**La distinción correcta es entre metadatos y comportamiento:**

| Permitido en el dominio | Prohibido en el dominio |
|---|---|
| Anotaciones **declarativas**: `@Entity`, `@Column`, `@Enumerated` | Llamar a un repositorio |
| Anotaciones de validación: `@NotBlank`, `@Email` | Hacer una petición HTTP |
| | Enviar un correo |
| | Depender de `HttpServletRequest` o de un DTO de la API |

Una anotación dice *cómo se guarda* esta clase; no la hace **depender** de que exista una base de datos. La entidad se sigue construyendo con `new`, sus reglas se prueban sin levantar Spring, y ahí está lo que el principio D protege de verdad.

**Lo que no se relaja:** el dominio no puede tener lógica de negocio anémica. Si `Despacho` es solo campos con `get` y `set`, y las reglas viven en el servicio, el principio S se rompió aunque el D se cumpla.

---

## 4. Reglas de código limpio

1. **Nombres del glosario.** Si el negocio lo llama *radicado*, la variable se llama `radicado` — no `numeroExpedienteJudicial` ni `codigo`.
2. **Sin comentarios que repitan el código.** `// incrementa el contador` sobre `contador++` es ruido. Los comentarios explican **por qué**, nunca **qué**.
   - *Sí se comenta:* `// RN-37b: el esquema nunca puede quedar en cero, ver riesgo R-08`
3. **Funciones cortas, un nivel de abstracción.** Si un método mezcla validar, calcular y guardar, se parte.
4. **Sin números mágicos.** `48` y `24` son constantes con nombre: `HORAS_PRIMERA_ALERTA_AUDIENCIA`.
5. **Excepciones con significado de dominio.** `RadicadoDuplicadoException`, no `RuntimeException("error")`.
6. **Sin `null` como valor de negocio.** Se usa `Optional` en las consultas que pueden no encontrar nada.
7. **Una regla de negocio, un solo lugar.** Si RN-24 aparece en el servicio y también en el controlador, una de las dos se desactualizará.

---

## 5. Seguridad — lo que nunca se hace **[D-21 · estándar 1]**

Es un proyecto real. Estas prohibiciones no admiten excepción "temporal":

| Nunca | Siempre en su lugar |
|---|---|
| Credenciales, claves o cadenas de conexión en el código o en el repositorio | Variables de entorno; `.env.example` documenta los nombres, no los valores |
| Contraseñas en texto plano o cifrado reversible | Hash con salt (RNF-05) |
| `despacho_id` recibido desde el cliente | **Siempre del token** de sesión (ADR-03 control 1) |
| Devolver lista vacía ante un acceso no autorizado | **Denegar explícitamente** (CA-41.2) — el vacío es ambiguo |
| Filtrar datos sensibles solo en el frontend | Filtrar en el **servicio**, antes de serializar (CA-34.2) |
| `CORS *` | Orígenes explícitos por entorno |
| Mensajes de error con detalles internos | Mensaje claro en español al usuario, detalle técnico solo en el registro |
| Descartar un fallo de envío de alerta | Reintentar y dejarlo **visible** (RNF-08) |

---

## 6. Mensajes al usuario

Español, en segunda persona, y **útiles**: dicen qué pasó y qué hacer.

| ❌ Evitar | ✅ Usar |
|---|---|
| `Error 500` | `No pudimos guardar el proceso. Intente de nuevo.` |
| `Constraint violation: uk_radicado` | `Ese radicado ya está registrado en su despacho.` |
| `Unauthorized` | `Su despacho está inactivo. Comuníquese con el administrador.` |
| `Invalid input` | `La audiencia necesita fecha y hora.` |

---

## 7. Definición de terminado por incremento **[D-21 · estándar 5]**

Un "lego" no está terminado hasta cumplir **las cinco**:

1. Cumple el RF/RNF/HU que lo justifica — **y ese código está citado en el commit**.
2. Tiene pruebas, **incluidas las negativas** si toca una regla crítica.
3. No introduce ninguna prohibición de §5.
4. Los mensajes al usuario están en español.
5. El dominio no depende de infraestructura.

**Sin excepción para el punto 2.** Los cinco fallos que destruyen este producto solo se detectan con pruebas negativas — probar que algo **no** ocurre.

### 7.1 Cómo se ejecutan las pruebas

| Orden | Qué corre | Necesita |
|---|---|---|
| `mvnw test` | El dominio. Es la compilación por defecto y **debe estar siempre en verde** | Nada |
| `mvnw test -Pintegracion` | Además, todo lo que toca la base | PostgreSQL |
| `mvnw test -Prendimiento` | Las mediciones. Inyectan latencia y tardan minutos | Nada |
| `mvnw test -Pdefectos` | Las que demuestran **defectos abiertos**. ⚠ **Fallan a propósito** | PostgreSQL |

### 7.2 El perfil `defectos`, y por qué existe

Una prueba etiquetada `defecto-abierto` **no está rota: está haciendo su
trabajo.** Reproduce un defecto conocido y documentado, y falla hasta el día en
que se corrige.

Vive fuera de toda compilación normal —ni la de por defecto ni la de
integración— porque una suite con un rojo permanente deja de servir para
detectar rojos nuevos: al segundo día nadie mira cuál de los dos falló.

**Es la misma lógica de D-23 aplicada a los defectos.** Allí se aceptó relajar
controles de seguridad en local *solo porque existe una lista que dice cuándo
dejan de estar relajados y alguien la verifica*. Un defecto conocido se acepta
igual: solo si existe algo que demuestre que sigue ahí y que avise el día que
deje de estarlo. Sin eso, «ya lo arreglaremos» se convierte en «nunca se
arregló».

**Al cerrar el defecto, la prueba no se borra**: se le cambia la etiqueta a
`integracion` y pasa a ser el guardián de que no vuelva.

Hoy hay una: `PicoDeAlertasTest` (**A-05**, RNF-11).

---

## 8. Convención de commits

```
<tipo>(<módulo>): <qué hace, en imperativo>

<por qué, y contra qué documento se justifica>

Implementa: RF-05, RNF-03 · HU-05, HU-06
```

Tipos: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`.

**La línea `Implementa:` no es decorativa.** Es lo que mantiene la trazabilidad *hasta el código*: permite responder "¿dónde se implementó RN-24?" con un `git log --grep`.
