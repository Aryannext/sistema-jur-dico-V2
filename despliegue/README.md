# Desplegar Iuris

Lo que hay que tener listo antes de exponer el sistema, y en qué orden.

**No es una guía de servidor.** Instalar Java, PostgreSQL o nginx está
documentado mucho mejor en otros sitios. Aquí está solo lo que es de *este*
sistema: qué exige, por qué, y qué pasa si falta.

---

## Antes de empezar: el arranque verifica, no confía

Con `SGPJ_PERFIL=produccion`, la aplicación comprueba al arrancar los controles
de seguridad de **D-23** y **se niega a arrancar** si falta alguno. No hay que
acordarse de nada: si el despliegue está mal, no sube.

Lo que se ve si algo falta:

```
══════════════════════════════════════════════════════════
  NO SE ARRANCA: hay controles de D-23 sin cumplir
══════════════════════════════════════════════════════════
  ✗ Control 2 — la clave de la base tiene 4 caracteres.
                Genérela, no la elija:  openssl rand -base64 24
  ✗ Control 6 — la clave de cifrado es la de desarrollo, que está
                en el repositorio. Genere otra:  openssl rand -base64 32
```

**Cuatro controles no los puede ver la aplicación** y quedan a cargo de quien
despliega. El arranque los lista en vez de callarlos:

- PostgreSQL no escucha en internet
- El certificado TLS del proxy es válido y renueva solo
- Existe respaldo diario
- **La restauración de ese respaldo se ha probado**

---

## El correo

### Por qué hay que contratar algo

El sistema avisa por correo. Ese es el producto. Y **no se puede usar el correo
del despacho** para hacerlo: los avisos salen en tandas —todos los términos que
vencen el mismo día disparan a la vez— y una cuenta normal de Gmail bloquea la
cuenta al tercer intento.

Hace falta un **servicio de correo transaccional**, que es otra cosa: están
hechos para enviar en volumen desde una aplicación.

### Con cuál empezar, y por qué gratis alcanza

El pico de correos **escala con el número de despachos**, y el volumen objetivo
de RNF-12 —50 despachos— es la meta, no el punto de partida:

| Despachos | Correos en el día peor |
|---:|---:|
| 1 | ~54 |
| 5 | ~270 |
| 10 | ~540 |
| 50 *(objetivo RNF-12)* | ~2.700 |

**Brevo tiene 300 correos/día gratis y permanentes**, lo que cubre los primeros
**cuatro o cinco despachos**. Para entonces hay cuatro o cinco despachos
pagando, y el salto a un plan de pago deja de ser una decisión difícil.

Alternativas equivalentes: **Mailjet** (200/día) y **Resend** (3.000/mes).
**SendGrid ya no sirve para esto**: retiró su plan gratuito en 2025.

### Empiece con UNA conexión

Con pocos despachos el pico es pequeño, y una sola conexión lo despacha de
sobra: 270 correos en serie tardan menos de tres minutos, muy dentro de los 15
que tolera **RNF-11**.

```
SGPJ_ALERTAS_CONEXIONES=1
```

Así **no se roza ningún límite por segundo** de ningún proveedor. Cuando el
sistema crezca hay que subirlo —cuatro conexiones son unos 6 envíos por
segundo— y ahí sí conviene mirar qué admite el plan contratado. Ver **D-27**.

### Cómo se sabe que hace falta subirlo

No hay que adivinarlo. Si los avisos empiezan a salir tarde, el propio sistema
lo escribe en el registro:

```
La alerta 1234 salió con 22 minutos de retraso sobre su momento
programado. RNF-11 admite 15.
```

Ese mensaje es la señal para subir `SGPJ_ALERTAS_CONEXIONES` — y para comprobar
antes que el proveedor admita el ritmo.

---

## Las variables de entorno

Ninguna tiene valor por defecto a propósito (**D-23 control 1**): si falta una,
la aplicación no arranca. Una credencial con valor por defecto acaba siendo una
credencial publicada en el repositorio.

```bash
# --- Perfil ---
SGPJ_PERFIL=produccion

# --- Base de datos ---
SGPJ_BD_HOST=localhost
SGPJ_BD_NOMBRE=iuris_sgpj
SGPJ_BD_USUARIO=sgpj_app
SGPJ_BD_CLAVE=            # openssl rand -base64 24   ← generada, no elegida

# --- Documentos (RNF-04) ---
SGPJ_DOCUMENTOS_DIR=/opt/iuris/almacen-documentos
SGPJ_DOCUMENTOS_CLAVE=    # openssl rand -base64 32

# --- Correo ---
SGPJ_CORREO_MODO=smtp
SGPJ_SMTP_HOST=smtp-relay.brevo.com
SGPJ_SMTP_PUERTO=587
SGPJ_SMTP_USUARIO=
SGPJ_SMTP_CLAVE=
SGPJ_CORREO_REMITENTE=alertas@sudominio.co

# --- Alertas ---
SGPJ_ALERTAS_CONEXIONES=1     # subir solo cuando el registro avise de retrasos

# --- Web ---
SGPJ_PUERTO=8080
SGPJ_ORIGENES=                # vacío: el mismo servidor sirve frontend y API
```

> ⚠ **La clave de cifrado de documentos hay que guardarla APARTE y fuera de este
> servidor.** Los documentos del respaldo están cifrados con ella: si se pierde
> junto con la máquina, el respaldo es ruido. Es el único dato del sistema que
> no se puede regenerar.

---

## El respaldo

```bash
# Diario, a las 2 de la mañana
0 2 * * *  /opt/iuris/respaldo.sh >> /var/log/iuris-respaldo.log 2>&1
```

**Y probarlo.** `RNF-14` no pide «respaldo diario»: pide **respaldo diario con
restauración probada**, y esa segunda mitad es la que nadie hace hasta el día en
que hace falta.

```bash
./restaurar-prueba.sh /var/respaldos/iuris/20260823-020000
```

Restaura sobre una base desechable, compara las cifras contra las que anotó el
respaldo y comprueba que el esquema esté al día. Que `pg_restore` termine sin
errores no dice nada: termina feliz sobre un volcado que solo trae el esquema.

**RNF-14 no se cumple una vez.** Se cumple mientras la última prueba de
restauración sea reciente. Conviene repetirla cada tanto y anotar la fecha.

---

## Orden sugerido

1. PostgreSQL, escuchando **solo en localhost** (control 4)
2. Crear la base y el rol `sgpj_app` **sin privilegios administrativos** (control 8)
3. Generar las dos claves y guardarlas donde corresponda
4. Cuenta gratuita en Brevo y verificar el dominio del remitente
5. Levantar la aplicación con `SGPJ_PERFIL=produccion` — **si arranca, los
   controles comprobables pasaron**
6. nginx delante: sirve el frontend compilado y reenvía `/api` al backend
7. Certificado TLS con renovación automática (control 3)
8. Programar el respaldo **y probar la restauración** (control 7)
9. Dar de alta el primer despacho desde la zona de plataforma

Los pasos 1 a 5 se pueden repetir cuantas veces haga falta: mientras algo falte,
la aplicación no sube y dice qué es.
