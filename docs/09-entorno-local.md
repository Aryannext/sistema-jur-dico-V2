# Cómo levantar el sistema y entrar

> ⚠ **Todo lo de este documento es SOLO para desarrollo local.** Las
> contraseñas están escritas aquí a propósito, y eso es aceptable
> únicamente porque la base local no contiene datos reales de ningún
> despacho — es la decisión **D-23**. Nada de este archivo se copia al
> VPS: ver la lista de controles de D-23 antes de desplegar.

---

## 1. Levantar las dos mitades

Hacen falta las dos: el frontend solo sirve las pantallas, y sin backend
el ingreso falla aunque la contraseña sea correcta.

**Backend** (Java 21, puerto 8081) — desde `backend/`:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

**Frontend** (Angular, puerto 4200) — desde `frontend/`:

```bash
npm start
```

Se entra por **http://localhost:4200**, nunca por el 8081. El 4200
reenvía `/api` al backend mediante `proxy.conf.json`, que está enganchado
en `angular.json` (`serve.options.proxyConfig`). Abrir el 8081
directamente muestra la API, no la aplicación.

---

## 2. Con qué usuario entrar

Estas son las credenciales **verificadas contra el sistema corriendo**,
no de memoria.

| Correo | Contraseña | Rol | Sirve para ver |
|---|---|---|---|
| `admin@iuris.co` | `clave-local-desarrollo` | Administrador de Plataforma | Alta y estado de despachos (RF-01 a RF-03) |
| `admin.cat@despacho.co` | `clave-cat-12345` | Admin. de Despacho **+ Abogado** | **El más completo.** Procesos, expedientes, términos, catálogos, reportes, usuarios |
| `admin.uno@despacho.co` | `clave-uno-12345` | Admin. de Despacho | Un despacho distinto, para comprobar el aislamiento (RN-02) |
| `admin.dos@despacho.co` | `clave-dos-12345` | Admin. de Despacho | Un tercer despacho |
| *(ver nota abajo)* | — | Cliente | El portal del cliente (M9) |

**Para ver el sistema por primera vez, entrá con `admin.cat@despacho.co`.**
Es el único que tiene los dos roles internos a la vez —el caso del
abogado independiente de **RN-08**— así que ve todas las pantallas sin
tener que cambiar de cuenta.

### El acceso de cliente al portal

**No hay una credencial de cliente fija en esta tabla, y es a propósito.** El
acceso al portal lo habilita el despacho desde la ficha del cliente
(**Clientes → un cliente → habilitar acceso**), y al volver a habilitarlo se
crea un usuario nuevo y **el anterior queda desactivado**. Cualquier correo que
se anotara aquí caducaría la próxima vez que alguien reemitiera un acceso — que
es exactamente lo que ocurrió con `ana.portal@correo.co`, que hoy está inactivo.

Para entrar como cliente: habilite el acceso de un cliente desde su ficha,
anote las credenciales que el sistema muestra **una sola vez**, y entre con
ellas. Si ya hay uno habilitado y no recuerda la clave, un Administrador de
Despacho puede restablecerla desde **Usuarios y roles** (RF-40).

### Dos cuentas que no están en la tabla

- **`carlos@melo.co`** — su contraseña no está documentada y no se
  adivinó. Es Administrador de su despacho y no hay otro, así que **no
  se puede restablecer desde la aplicación**: RF-40 exige un
  administrador *del mismo despacho*, y el Administrador de Plataforma
  no alcanza a los usuarios de un despacho por **RN-10**. Habría que
  tocarla en la base directamente.
- **`beto@melo.co`**, **`mruiz.portal@correo.co`** y **`ana.portal@correo.co`**
  están **inactivos**: los dos primeros a propósito, para comprobar que un
  usuario desactivado no puede entrar (RF-38); el tercero porque su acceso al
  portal se reemitió y el anterior se desactivó, que es el comportamiento
  correcto.

### Por qué existe este documento

Estas cuentas se crearon por API durante el desarrollo, y su contraseña
**no quedó escrita en ninguna parte**. El resultado previsible fue no
poder entrar al propio sistema. Un entorno de desarrollo en el que hay
que adivinar las credenciales es un entorno roto, aunque el código
funcione.

---

## 3. Cambiar la contraseña desde dentro

Desde **Mi cuenta** en el menú lateral, con cualquiera de las cuentas de
arriba (RF-39). Si la cambiás, **actualizá la tabla de este documento** —
si no, el problema que motivó este archivo vuelve tal cual.

Un Administrador de Despacho también puede restablecer la de cualquiera
de su despacho, desde **Usuarios** (RF-40), sin conocer la anterior. Lo
que **no existe en ninguna parte del sistema es consultar una contraseña
existente**: es la regla **RN-54**, y no es un descuido de la interfaz.

---

## 4. Si el ingreso falla

| Síntoma | Causa habitual |
|---|---|
| La pantalla carga pero el ingreso no responde | El backend no está levantado. El 4200 sirve las pantallas por su cuenta |
| «Credenciales inválidas» con una clave de la tabla | La contraseña se cambió desde *Mi cuenta* y la tabla quedó vieja |
| Entra y sale de inmediato | El usuario está inactivo, o su despacho lo está (RF-03) |
| Error de red en `/api/...` | Se está abriendo el **8081** en vez del **4200** |

---

## 5. La base de datos

`iuris_sgpj` en PostgreSQL local, rol `sgpj_app`. Flyway aplica las
migraciones al arrancar; no hay que crear tablas a mano.

**El rol `sgpj_app` no tiene privilegios administrativos, y eso no se
relaja ni siquiera en local** — es el control 8 de D-23. Un rol con
privilegios anularía Row-Level Security más adelante sin que nadie lo
note (ADR-03). Para las tareas que sí requieren superusuario —crear una
base, por ejemplo— se usa `postgres` explícitamente.
