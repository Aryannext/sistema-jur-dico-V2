# Propuesta de decisión — A-05

**Para:** Product Owner
**De:** análisis y desarrollo
**Fecha:** 21 de agosto de 2026
**Decide:** una sola pregunta, al final de la §3

---

## 1. Qué pasó

Se midió el sistema contra el volumen que el propio requisito RNF-12 fija como
objetivo: **50 despachos, 500 procesos cada uno, 50 documentos por
expediente**. En cifras reales, una base de 405 MB con 25.000 procesos,
1.250.000 piezas y 175.000 alertas.

**Las consultas del día a día pasaron con holgura.** Las 16 pantallas que un
abogado usa a diario responden en menos de 0,6 segundos, sobre un límite de 3.
RNF-12 queda verificado.

**El envío de alertas no pasó**, y falla justo en lo que sostiene la razón de
ser del producto.

---

## 2. El problema, sin tecnicismos

El sistema revisa cada 5 minutos si hay avisos que mandar, y manda hasta 100
por revisión. Da igual lo rápido que sea la base de datos: **el techo son 1.200
avisos por hora**.

El problema es que los avisos de términos **no llegan repartidos, llegan todos
de golpe**. Un término se registra con **fecha de vencimiento, sin hora**. El
sistema, al no tener hora, usa el final del día: las 23:59. Y como el aviso se
calcula restando días a ese momento, **todos los términos que vencen el mismo
día disparan su aviso en el mismísimo instante**.

Medido sobre el volumen objetivo: **2.499 avisos en un solo instante**. A 100
por cada 5 minutos, el último de esa tanda sale **más de dos horas tarde**.

El requisito RNF-11 tolera 15 minutos.

### Dos cosas que conviene saber

**Las audiencias no tienen este problema.** Una audiencia se registra con fecha
**y hora** —lo exige RN-28—, así que sus avisos de 48 h, 24 h y del día ya
salen repartidos a lo largo de la jornada. Esta propuesta **no toca RN-29**,
que es literal de la propuesta original. Solo afecta a los términos.

**Hay un segundo problema, y es de producto.** El aviso de «vence mañana» se
calcula como *el día del vencimiento a las 23:59, menos un día*. Es decir:
**llega a las 23:59 de la noche anterior**. A medianoche. Para cuando el
abogado abre el correo por la mañana, ese aviso ya está sepultado bajo el
correo del día. El aviso que más importa es el que peor llega.

---

## 3. Qué hay que decidir

La regla **RN-37a** dice hoy que el despacho configura *cuántos* avisos y *con
cuánta anticipación*, en días. **No dice a qué hora salen.** Ese silencio es lo
que el código resolvió por su cuenta poniendo las 23:59.

> ### La pregunta
>
> **¿Puede el aviso de un término llegar dentro de una ventana horaria —por
> ejemplo, entre las 6:00 y las 9:00 de la mañana— en lugar de a un instante
> exacto?**
>
> Y si la respuesta es sí: **¿qué ventana?**

No es una pregunta técnica disfrazada. Cambia lo que el despacho puede
prometerle a un abogado sobre cuándo recibirá su aviso.

---

## 4. Las dos salidas, con sus números

Ambas cumplen RNF-11. Se diferencian en qué cuestan y en qué arriesgan.

### Opción A — repartir la hora del aviso *(recomendada)*

Los avisos de términos dejan de salir todos a las 23:59 y se reparten dentro de
una ventana de mañana. Cada aviso conserva **su propio** momento programado, así
que **RNF-11 se cumple literalmente**: cada uno sale dentro de sus 15 minutos,
no «en promedio».

La ventana y el tamaño del lote se eligen **juntos**: una ventana ancha reparte
los avisos, un lote grande los despacha más rápido, y hace falta que las dos
cosas cuadren. Con la peor latencia realista (100 ms) y el código actual:

| Ventana | Lote | Llegan/min | Salen/min | Espera peor | |
|---|---:|---:|---:|---:|---|
| 6:00–8:00 | 150 | 20,8 | 20,8 | *crece sin tope* | **no cumple** — no da abasto |
| 6:00–8:00 | 250 | 20,8 | 28,8 | 12,4 min | cumple, 2,6 min de margen |
| **6:00–9:00** | **150** | **13,9** | **20,8** | **9,4 min** | **cumple, 5,6 min de margen** |
| 6:00–10:00 | 120 | 10,4 | 17,7 | 8,5 min | cumple, 6,5 min de margen |

La segunda fila enseña por qué hay que elegirlas juntas: misma ventana que la
primera, y cumple solo porque el lote sube.

**Se recomienda la tercera: ventana de 6:00 a 9:00, lote de 150.** Es la que
deja más margen sin pedir una ventana de media mañana.

**Qué cuesta:** cambiar RN-37a para que fije también la hora, y una constante
del código. Nada más.

**Qué gana además:** el abogado recibe el aviso al empezar la jornada, no a
medianoche.

**Qué arriesga:** que el despacho no pueda prometer una hora exacta. Para un
aviso de «vence en 15 días» es irrelevante; para el de «vence mañana» hay que
confirmar que una ventana de mañana es aceptable.

### Opción B — enviar en paralelo

No se toca ninguna regla de negocio. El sistema abre varias conexiones de
correo a la vez en lugar de una detrás de otra. Está medido: de 36,8 minutos a
6,6 con 4 conexiones.

**Qué cuesta:** es cirugía sobre la parte más delicada del sistema. Hoy el
envío ocurre dentro de una única transacción de base de datos; sacarlo de ahí
para poder paralelizarlo es exactamente donde puede reaparecer el **envío
duplicado** que la decisión ADR-04 evita hoy. Un abogado que recibe dos veces
el mismo aviso empieza a desconfiar de todos.

**Qué arriesga además:** los proveedores de correo limitan el caudal. Ocho
conexiones despachando 2.499 correos son **12 por segundo**; un servicio
transaccional lo admite, el correo de una cuenta de Gmail no. Esta opción
**condiciona qué proveedor se puede contratar**.

### Y no son excluyentes

Si más adelante el volumen crece por encima de lo previsto, la opción A no
impide añadir la B. Al revés sí es más difícil: paralelizar primero significa
asumir el riesgo del envío duplicado sin haber probado antes lo barato.

---

## 5. Recomendación

**Opción A**, por tres razones en este orden:

1. **Corrige un problema de producto, no solo de rendimiento.** Un aviso a
   medianoche es un aviso peor, independientemente de cuántos sean.
2. **No arriesga la regla que sostiene el sistema.** No toca la transacción del
   motor ni el mecanismo que impide el envío duplicado.
3. **Es reversible y ampliable.** Si la ventana elegida resulta incómoda, se
   cambia una configuración; si el volumen crece, se añade la opción B encima.

Si la respuesta a la §3 es **no** —el aviso debe salir a una hora exacta—,
entonces la opción B es la única salida, y hay que contratar el proveedor de
correo con el requisito de 12 envíos por segundo sobre la mesa.

---

## 6. Qué cambia en la documentación si se aprueba

Nada de esto se programa antes de que la decisión esté tomada y escrita. Es la
misma regla que rige el proyecto desde la Fase 1: primero el requisito, después
el código.

| Documento | Cambio |
|---|---|
| `02-reglas-de-negocio.md` | **RN-37a** gana la hora del aviso, no solo la anticipación en días. Posible **RN-37c** para la ventana |
| `03-requisitos...md` | RF-26 (esquema de alertas) refleja la ventana configurable |
| `04-historias-de-usuario.md` | La HU del esquema de alertas gana un criterio de aceptación sobre la hora |
| `00-decisiones-y-trazabilidad.md` | **A-05 se cierra** con la decisión del PO |

---

## 7. De dónde salen estas cifras

Todas están medidas, ninguna estimada. Los guiones, el procedimiento y los
resultados completos están en `backend/src/test/resources/rendimiento/`, y se
pueden volver a ejecutar:

```
./mvnw test -Prendimiento
```

Dos salvedades que conviene decir antes de que las pregunte alguien:

- Los tiempos de envío se midieron contra un servidor de correo local con
  latencia inyectada, **no contra un proveedor real**. Fue deliberado: un
  proveedor habría dado su número, ese día, desde esa red — y habría exigido
  mandar dos mil correos de prueba desde un dominio nuevo, que es la vía más
  rápida a una lista negra. Se midió en su lugar cuántos viajes de red cuesta
  cada aviso, que es propiedad del código y no del proveedor.
- Las cifras varían **±20 %** entre ejecuciones. Los órdenes de magnitud son
  sólidos; los decimales, no.

Las cifras de la opción A suponen la peor latencia realista (100 ms, que es lo
que hay de Neiva a un proveedor en Estados Unidos). Con un proveedor más
cercano el margen es mayor.
