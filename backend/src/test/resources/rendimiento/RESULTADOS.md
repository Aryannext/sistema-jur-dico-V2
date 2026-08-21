# Medición de rendimiento — RNF-12 y RNF-11

Fecha: 2026-08-21 · Base `sgpj_rendimiento` (desechable, separada de la de
desarrollo) · PostgreSQL 18.4 · portátil de desarrollo, no el VPS.

## Volumen sembrado

El que fija RNF-12: **50 despachos, 500 procesos por despacho, 50 piezas
por expediente**.

| | |
|---|---|
| Despachos | 50 |
| Usuarios | 151 |
| Clientes | 6.000 |
| Procesos | 25.000 |
| Expedientes | 25.000 |
| Piezas | 1.250.000 (1.000.000 documentos) |
| Términos | 50.000 (13.344 vencidos) |
| Audiencias | 8.333 |
| Alertas | 174.999 |
| Tamaño en disco | 405 MB |

Dos reglas hacen que esto mida algo:

1. Se reporta el **peor** tiempo, no la media. RNF-12 dice «deben responder
   en menos de 3 segundos», no «de media».
2. Una consulta que devuelve cero filas se marca `VACIA`, no «aprobada».
   En la primera pasada tres de las doce consultas devolvían vacío —por un
   identificador de catálogo equivocado y por audiencias sin sembrar— y
   salían con tiempos excelentes que no significaban nada.

## RNF-12 — CUMPLE

Límite: 3.000 ms. Seis mediciones por consulta; el calentamiento se
reporta aparte y no cuenta. Tiempos en milisegundos.

| Consulta | Filas | Calent. | Mediana | Peor | Cumple |
|---|---:|---:|---:|---:|---|
| Panel de vencimientos | 664 | 140 | 132 | 134 | sí |
| Listar procesos (sin filtro) | 3000 | 150 | 144 | 149 | sí |
| Buscar radicado, fragmento ancho | 3000 | 146 | 147 | 153 | sí |
| Buscar radicado, fragmento corto | 300 | 25 | 23 | 25 | sí |
| Buscar por estado | 2100 | 110 | 103 | 107 | sí |
| Buscar combinando 3 filtros | 294 | 23 | 25 | 26 | sí |
| Buscar por juzgado | 246 | 22 | 23 | 28 | sí |
| Expediente completo (50 piezas) | 50 | 23 | 17 | 17 | sí |
| Reporte resumen | 4 | 57 | 102 | 129 | sí |
| Reporte por estado | 4 | 9 | 9 | 10 | sí |
| Reporte por tipo | 7 | 9 | 9 | 11 | sí |
| Carga por abogado | 3 | 8 | 9 | 11 | sí |
| Calendario de audiencias | 30 | 36 | 46 | 55 | sí |
| Clientes del despacho | 120 | 21 | 19 | 19 | sí |
| Catálogo de juzgados | 12 | 8 | 8 | 8 | sí |
| Historial de alertas | 2190 | 794 | 527 | 584 | sí |

Las 16 devuelven datos y ninguna pasa de 584 ms en su peor tiempo. El
margen sobre el límite es de más de 5×.

## RNF-11 — **INCUMPLE**

RNF-11 exige que la alerta salga dentro de los 15 minutos de su momento.
Medirlo obligó a separar dos cosas que se confunden fácil:

### La consulta del barrido no es el problema

`EXPLAIN (ANALYZE, BUFFERS)` sobre la consulta real del motor
(`estado='PROGRAMADA' AND programada_para <= now() ORDER BY
programada_para LIMIT 100 FOR UPDATE SKIP LOCKED`):

```
Limit  (actual time=0.027..0.328 rows=100 loops=1)
  ->  LockRows
        ->  Index Scan using ix_alerta_pendientes on alerta
              Index Cond: (estado = 'PROGRAMADA' AND programada_para <= now())
Execution Time: 0.348 ms
```

**0,348 ms.** Usa el índice; no recorre las 175.000 filas. Y el barrido
completo por HTTP, con el emisor en modo registro: 141 ms el peor de
tres, drenando exactamente 300 alertas (100 por barrido, como debe).

### El problema es el techo del motor

`MotorAlertas.TAMANO_LOTE = 100` y `sgpj.alertas.intervalo-ms = 300000`
(5 minutos). Eso fija un techo que ninguna optimización de consulta
mueve:

```
100 alertas / 5 min  =  1.200 alertas/hora
```

Y las alertas **no llegan repartidas, llegan de golpe**.
`Termino.fechaObjetivo()` devuelve `fechaVencimiento` a las
`FIN_DE_LA_JORNADA = 23:59`. Es una fecha sin hora: todos los términos
que vencen el mismo día disparan sus avisos en el **mismo instante**, y
además coinciden los de 15, 5 y 1 día de anticipación de días de
vencimiento distintos.

Medido sobre el volumen objetivo:

| | |
|---|---|
| Pico de alertas en un mismo instante | **2.499** |
| Peor día | 2.706 |
| Barridos necesarios para drenar el pico | 25 |
| Tiempo para drenar el pico | **125 minutos** |
| Tolerancia RNF-11 | 15 minutos |

**La última alerta del pico saldría con más de dos horas de retraso.**
Es un incumplimiento de 8×, y cae justo sobre el requisito que sostiene
la razón de ser del producto: el aviso de 24 h de un término.

### Lo que haría falta

Para caber en 15 minutos hay 3 barridos, luego el lote tendría que ser
de **833 como mínimo** —o el intervalo bajar a menos de 36 segundos con
el lote actual—. Ninguna de las dos es gratis:

- Un lote grande no lo limita la base (0,35 ms por lote de 100) sino el
  **emisor de correo**, que envía en serie. Los 141 ms medidos son con
  el emisor en modo registro, no con SMTP real: **el envío real por SMTP
  no está medido** y es el número que falta.
- Bajar el intervalo multiplica los barridos en vacío el resto del día,
  cuando no hay nada pendiente.

Una tercera vía, más barata que las dos: **repartir el instante**. Si el
momento de aviso no fuera 23:59 exacto para todos, el pico se aplanaría
solo. Pero eso cambia una regla de negocio y no es una decisión de
implementación.

Queda como defecto abierto, no corregido en esta medición.

## A-05 — cuánto cuesta el envío real por SMTP

Medido con `mvnw test -Prendimiento` (`RendimientoSmtpTest`). Contra
GreenMail detrás de un proxy TCP que inyecta latencia
(`RedConRetardo`), **no contra un proveedor real**: un proveedor daría
el número de ese proveedor, ese día, desde esa red, y además implicaría
mandar dos mil correos de prueba desde un dominio nuevo, que es la forma
más rápida de acabar en una lista negra y que las alertas de verdad
dejen de llegar.

Lo que se mide en su lugar es **cuántos viajes de red hay por alerta**,
que es propiedad del código y no del proveedor. Con ese número, el
tiempo con cualquier proveedor es una multiplicación.

### El coste por alerta

Lote de 100 alertas. La columna **tramos** son viajes de red relevados
por el proxy; es el dato que no depende de la latencia.

| Latencia | Estrategia | Tiempo | Conexiones | Tramos | Por alerta |
|---:|---|---:|---:|---:|---:|
| 0 ms | una conexión por alerta | 1.042 ms | 100 | 1.443 | 10,4 ms |
| 0 ms | una conexión por lote | 470 ms | 1 | 1.005 | 4,7 ms |
| 20 ms | una conexión por alerta | 25.618 ms | 100 | 1.400 | 256,2 ms |
| 20 ms | una conexión por lote | 17.886 ms | 1 | 1.004 | 178,9 ms |
| 50 ms | una conexión por alerta | 39.056 ms | 100 | 1.400 | 390,6 ms |
| 50 ms | una conexión por lote | 28.987 ms | 1 | 1.004 | 289,9 ms |
| 50 ms | **4 conexiones a la vez** | 8.829 ms | 4 | 1.016 | 88,3 ms |
| 50 ms | **8 conexiones a la vez** | 4.702 ms | 8 | 1.032 | 47,0 ms |
| 100 ms | una conexión por alerta | 88.286 ms | 100 | 1.400 | 882,9 ms |
| 100 ms | una conexión por lote | 62.763 ms | 1 | 1.004 | 627,6 ms |
| 100 ms | **4 conexiones a la vez** | 15.922 ms | 4 | 1.016 | 159,2 ms |
| 100 ms | **8 conexiones a la vez** | 8.492 ms | 8 | 1.032 | 84,9 ms |

### El hallazgo: agrupar no es la respuesta

**14 tramos por alerta** enviando de una en una; **10 por alerta**
agrupando el lote en una sola conexión. Reutilizar la conexión ahorra
solo **4 de 14** —el saludo y la despedida— porque los otros diez son
del protocolo: `MAIL FROM`, `RCPT TO`, `DATA` y el punto final son un
viaje cada uno **por mensaje**, y ningún lote los ahorra.

Por eso agrupar mejora 1,4× y no el doble. Y 1,4× no alcanza: a 100 ms
el pico de 2.499 alertas seguiría tardando 26 minutos.

En paralelo los tramos **no bajan** (1.016 con 4 conexiones, 1.032 con
8): son los mismos viajes de red. Lo que cambia es que dejan de
esperarse en fila.

### Qué pasa con el pico de 2.499 alertas

Tolerancia RNF-11: 15 minutos.

| Latencia | Estrategia | El pico tardaría | |
|---:|---|---:|---|
| 20 ms | una conexión por alerta | 10,7 min | cabe |
| 50 ms | una conexión por alerta | 16,3 min | **NO cabe** |
| 50 ms | una conexión por lote | 12,1 min | cabe, sin margen |
| 50 ms | 4 conexiones a la vez | 3,7 min | cabe |
| 50 ms | 8 conexiones a la vez | 2,0 min | cabe |
| 100 ms | una conexión por alerta | 36,8 min | **NO cabe** |
| 100 ms | una conexión por lote | 26,1 min | **NO cabe** |
| 100 ms | 4 conexiones a la vez | 6,6 min | cabe |
| 100 ms | 8 conexiones a la vez | 3,5 min | cabe |

Con el código de hoy, **cualquier proveedor a más de 50 ms incumple
RNF-11**. Neiva a un proveedor en Estados Unidos son del orden de 80–120
ms.

### Tres advertencias sobre estos números

1. **El proxy no hace TLS ni autenticación.** Un proveedor real añade
   handshake y `AUTH` a **cada conexión**. Ese coste castiga sobre todo
   a la estrategia de hoy, que abre 100 conexiones por lote: los tiempos
   de la primera fila son un **suelo**, no una estimación. Con 4 u 8
   conexiones el coste extra es despreciable porque se paga 4 u 8 veces,
   no 2.499.
2. **Los proveedores limitan el caudal.** 8 conexiones despachando 2.499
   correos en 3,5 minutos son ~12 por segundo. Un servicio transaccional
   lo admite; el SMTP de una cuenta de Gmail no, ni de lejos. **El
   proveedor hay que elegirlo con este número en la mano**, y si el
   elegido limita a menos, el paralelismo no sirve de nada.
3. **Hay variación entre ejecuciones.** «Una conexión por alerta» a 50 ms
   dio 47,2 s en una pasada y 39,1 s en otra. Los órdenes de magnitud son
   sólidos; las cifras concretas, ±20 %.

### Lo que esto NO resuelve

`MotorAlertas.ejecutarBarrido()` envía **dentro de una sola
transacción**. Enviar en paralelo desde ahí no es cambiar un bucle por
un pool: la transacción y sus bloqueos quedarían abiertos mientras
esperan a la red, y las entidades de Hibernate no son seguras entre
hilos. Separar el envío de la transacción es el trabajo real, y es
donde puede aparecer emisión duplicada — justo lo que ADR-04 evita hoy
con `SKIP LOCKED`.

## Cómo repetirla

```
createdb -U postgres -O sgpj_app sgpj_rendimiento
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/sgpj_rendimiento ./mvnw spring-boot:run
psql -U sgpj_app -d sgpj_rendimiento -f sembrar-volumen-objetivo.sql
psql -U sgpj_app -d sgpj_rendimiento -f completar-terminos.sql
psql -U sgpj_app -d sgpj_rendimiento -f sembrar-audiencias.sql
psql -U sgpj_app -d sgpj_rendimiento -f sembrar-alertas.sql
psql -U sgpj_app -d sgpj_rendimiento -f habilitar-medidor.sql
powershell -File medir.ps1
psql -U sgpj_app -d sgpj_rendimiento -f medir-barrido.sql
powershell -File medir-barrido.ps1
dropdb -U postgres sgpj_rendimiento
```

Y la de A-05, que no necesita base de datos ni volumen:

```
./mvnw test -Prendimiento
```

La base es **desechable** y se borra al terminar: nunca se mide contra la
base de desarrollo.
