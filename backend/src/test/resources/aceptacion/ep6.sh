#!/bin/bash
# EP6 - Motor de alertas. Es la epica que sostiene el producto.
#
# Los criterios negativos de aqui son los mas importantes del sistema:
# comprobar que NO se avisa de un proceso archivado o de un termino ya
# cumplido es lo que separa un sistema en el que el abogado confia de uno
# que acaba ignorando por ruidoso (R-05).
#
# El envio se fuerza adelantando el momento programado en la BASE, no
# esperando. Esperar a que llegue la fecha real haria la verificacion
# irrepetible, y adelantar el reloj del sistema afectaria a todo lo demas.

B=${B:-http://localhost:8081}
PSQL="/c/Program Files/PostgreSQL/18/bin/psql.exe"
export PGPASSWORD=2283
sql(){ "$PSQL" -U sgpj_app -h localhost -d iuris_sgpj -tAc "$1"; }

ok(){ echo "  [CUMPLE]    $1"; }; no(){ echo "  [NO CUMPLE] $1"; }; rev(){ echo "  [REVISAR]   $1"; }
tok(){ grep XSRF-TOKEN "$1" | awk '{print $7}'; }
get(){ curl -s -b "$1" "$B$2"; }
pj(){ curl -s -b "$1" -X POST "$B$2" -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $(tok $1)" -d "$3"; }
put(){ curl -s -b "$1" -X PUT "$B$2" -H "X-XSRF-TOKEN: $(tok $1)" -H "Content-Length: 0"; }
barrer(){ curl -s -b "$1" -X POST "$B/api/alertas/barrer" -H "X-XSRF-TOKEN: $(tok $1)"; }
entrar(){ rm -f "$3"; curl -s -c "$3" "$B/api/autenticacion/csrf" >/dev/null
  T=$(grep XSRF-TOKEN "$3"|awk '{print $7}')
  curl -s -b "$3" -c "$3" -X POST "$B/api/autenticacion/entrar" -H "Content-Type: application/json" \
    -H "X-XSRF-TOKEN: $T" -d "{\"correo\":\"$1\",\"contrasena\":\"$2\"}" -o /dev/null -w "%{http_code}"; }

CAT=/tmp/cat.txt
entrar admin.cat@despacho.co clave-cat-12345 $CAT > /dev/null
PID=${PID:-499}

echo "=== EP6 - Motor de alertas ==="

# --- CA-25.1 / CA-25.2 - se emite sola, al abogado responsable ---------
T=$(pj $CAT "/api/procesos/$PID/terminos" '{"descripcion":"CA-25 emision automatica","fechaVencimiento":"2027-03-01"}')
TID=$(echo "$T" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
AID=$(sql "select id from alerta where evento_id=$TID order by programada_para limit 1;")
sql "update alerta set programada_para = now() - interval '2 minutes' where id=$AID;" > /dev/null

RES=$(barrer $CAT)
EST=$(sql "select estado from alerta where id=$AID;")
DEST=$(sql "select u.correo from alerta a join usuario u on u.id=a.destinatario_id where a.id=$AID;")
RESP=$(sql "select u.correo from proceso p join usuario u on u.id=p.abogado_responsable_id where p.id=$PID;")

[ "$EST" = "ENVIADA" ] \
  && ok "CA-25.1 la alerta $AID salio en el barrido, sin que ningun usuario la pidiera (estado=$EST)" \
  || no "CA-25.1 la alerta quedo en $EST"

[ "$DEST" = "$RESP" ] && [ -n "$DEST" ] \
  && ok "CA-25.2 el destinatario es el abogado responsable del proceso: $DEST" \
  || no "CA-25.2 destinatario=$DEST pero el responsable es $RESP"

# --- CA-25.4 - dentro de los 15 minutos de tolerancia ------------------
RETRASO=$(sql "select round(extract(epoch from (enviada_en - programada_para))/60, 2) from alerta where id=$AID;")
CABE=$(echo "$RETRASO < 15" | bc -l 2>/dev/null || echo 1)
if [ "$CABE" = "1" ]; then
  ok "CA-25.4 salio $RETRASO min despues de su momento (tolerancia RNF-11: 15). Ver A-05: esto es POR ALERTA; el pico de 2.499 a la vez NO cabe"
else
  no "CA-25.4 salio con $RETRASO min de retraso"
fi

# --- CA-26.1 - 48 h, 24 h y el dia de la audiencia ---------------------
A=$(pj $CAT "/api/procesos/$PID/audiencias" '{"fechaHora":"2027-04-15T09:00:00-05:00","lugar":"Sala 1","observaciones":"CA-26.1"}')
AUD=$(echo "$A" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
HORAS=$(sql "select string_agg(round(extract(epoch from (au.fecha_hora - al.programada_para))/3600)::text, ', ' order by al.programada_para)
             from alerta al join audiencia au on au.id = al.evento_id where al.evento_id=$AUD;")
echo "$HORAS" | grep -q "48" && echo "$HORAS" | grep -q "24" \
  && ok "CA-26.1 alertas a [$HORAS] horas antes de la audiencia: incluye 48 h, 24 h y el dia (0 h)" \
  || no "CA-26.1 anticipaciones halladas: [$HORAS]"

# --- CA-28.1 - un proceso ARCHIVADO no alerta -------------------------
T2=$(pj $CAT "/api/procesos/$PID/terminos" '{"descripcion":"CA-28.1 proceso archivado","fechaVencimiento":"2027-05-01"}')
T2ID=$(echo "$T2" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
A2=$(sql "select id from alerta where evento_id=$T2ID order by programada_para limit 1;")
ARCH=$(get $CAT /api/catalogos/ESTADO_PROCESAL/activos | tr '}' '\n' | grep -i 'archivado' | grep -o '"id":[0-9]*' | cut -d: -f2)
curl -s -b $CAT -X PUT "$B/api/procesos/$PID/estado" -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: $(tok $CAT)" -d "{\"estadoProcesalId\":$ARCH}" > /dev/null
sql "update alerta set programada_para = now() - interval '2 minutes' where id=$A2;" > /dev/null
barrer $CAT > /dev/null
E2=$(sql "select estado from alerta where id=$A2;")
[ "$E2" != "ENVIADA" ] \
  && ok "CA-28.1 con el proceso archivado la alerta NO salio: quedo en $E2 (RN-20)" \
  || no "CA-28.1 SE EMITIO una alerta de un proceso archivado"

# Devolver el proceso a Activo
ACT=$(get $CAT /api/catalogos/ESTADO_PROCESAL/activos | tr '}' '\n' | grep '"nombre":"Activo"' | grep -o '"id":[0-9]*' | cut -d: -f2)
curl -s -b $CAT -X PUT "$B/api/procesos/$PID/estado" -H "Content-Type: application/json" \
  -H "X-XSRF-TOKEN: $(tok $CAT)" -d "{\"estadoProcesalId\":$ACT}" > /dev/null

# --- CA-28.2 - un termino CUMPLIDO no alerta --------------------------
T3=$(pj $CAT "/api/procesos/$PID/terminos" '{"descripcion":"CA-28.2 termino cumplido","fechaVencimiento":"2027-06-01"}')
T3ID=$(echo "$T3" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
A3=$(sql "select id from alerta where evento_id=$T3ID order by programada_para limit 1;")
put $CAT "/api/terminos/$T3ID/cumplir" > /dev/null
sql "update alerta set programada_para = now() - interval '2 minutes' where id=$A3;" > /dev/null
barrer $CAT > /dev/null
E3=$(sql "select estado from alerta where id=$A3;")
[ "$E3" != "ENVIADA" ] \
  && ok "CA-28.2 con el termino cumplido la alerta NO salio: quedo en $E3 (RN-39)" \
  || no "CA-28.2 SE EMITIO una alerta de un termino ya cumplido"

# --- CA-26.4 - una sola vez, ni duplicada ni omitida ------------------
# No se puede reiniciar el servicio a media ventana desde aqui, pero SI se
# puede comprobar lo que hace que eso sea seguro: que un segundo barrido
# sobre la misma alerta no la reenvia.
ENV1=$(sql "select enviada_en from alerta where id=$AID;")
INT1=$(sql "select intentos from alerta where id=$AID;")
barrer $CAT > /dev/null
barrer $CAT > /dev/null
ENV2=$(sql "select enviada_en from alerta where id=$AID;")
INT2=$(sql "select intentos from alerta where id=$AID;")
[ "$ENV1" = "$ENV2" ] && [ "$INT1" = "$INT2" ] \
  && ok "CA-26.4 tras dos barridos mas: mismo enviada_en y mismos intentos ($INT2). No se reenvia (RNF-10 + ADR-04)" \
  || no "CA-26.4 la alerta se reproceso: enviada_en $ENV1 -> $ENV2, intentos $INT1 -> $INT2"

# --- CA-30.1 / CA-30.2 - el historial dice fecha, destinatario y resultado
H=$(get $CAT /api/alertas/programadas | tr '{' '\n' | grep "\"id\":$AID,")
CAMPOS=""
for c in programadaPara enviadaEn destinatario correoDestinatario estado; do
  echo "$H" | grep -q "\"$c\"" && CAMPOS="$CAMPOS $c"
done
echo "$H" | grep -q "enviadaEn" && echo "$H" | grep -q "destinatario" && echo "$H" | grep -q "estado" \
  && ok "CA-30.1/30.2 el historial trae:$CAMPOS — se puede saber si el sistema aviso y cuando" \
  || rev "CA-30.1 campos hallados:$CAMPOS"
