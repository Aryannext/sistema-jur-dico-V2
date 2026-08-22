#!/bin/bash
B=http://localhost:8081
RAIZ=/c/dev/iuris
PSQL="/c/Program Files/PostgreSQL/18/bin/psql.exe"
export PGPASSWORD=2283
sql(){ "$PSQL" -U sgpj_app -h localhost -d iuris_sgpj -tAc "$1"; }
ok(){ echo "  [CUMPLE]    $1"; }; no(){ echo "  [NO CUMPLE] $1"; }; rev(){ echo "  [REVISAR]   $1"; }
tok(){ grep XSRF-TOKEN "$1" | awk '{print $7}'; }
get(){ curl -s -b "$1" "$B$2"; }
pj(){ curl -s -b "$1" -X POST "$B$2" -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $(tok $1)" -d "$3"; }
puj(){ curl -s -b "$1" -X PUT "$B$2" -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $(tok $1)" -d "$3"; }
entrar(){ rm -f "$3"; curl -s -c "$3" "$B/api/autenticacion/csrf" >/dev/null
  T=$(grep XSRF-TOKEN "$3"|awk '{print $7}')
  curl -s -b "$3" -c "$3" -X POST "$B/api/autenticacion/entrar" -H "Content-Type: application/json" \
    -H "X-XSRF-TOKEN: $T" -d "{\"correo\":\"$1\",\"contrasena\":\"$2\"}" -o /dev/null -w "%{http_code}"; }

CAT=/tmp/cat.txt; CAN=/tmp/can.txt
entrar admin.cat@despacho.co clave-cat-12345 $CAT > /dev/null
entrar admin@cantillo.co nehuilasur-4821 $CAN > /dev/null
PID=499

echo "=== Rehecho: el campo es diasAnticipacion, no dias ==="

# --- CA-26.3 - se pueden AÑADIR avisos --------------------------------
R=$(puj $CAT /api/esquema-alertas '{"diasAnticipacion":[20,15,5,1]}')
echo "$R" | grep -q "20" \
  && ok "CA-26.3 se añadio un cuarto aviso: $(echo $R | grep -o '\"diasAnticipacion\":\[[^]]*\]')" \
  || no "CA-26.3 -> $(echo $R | head -c 180)"

# --- CA-27.1 / CA-38.1 - los terminos NUEVOS usan el esquema nuevo ----
T=$(pj $CAT "/api/procesos/$PID/terminos" '{"descripcion":"CA-27.1 rehecho","fechaVencimiento":"2027-09-01"}')
TID=$(echo "$T" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
DIAS=$(sql "select string_agg((t.fecha_vencimiento - al.programada_para::date)::text, ', '
                              order by (t.fecha_vencimiento - al.programada_para::date) desc)
            from alerta al join termino t on t.id = al.evento_id where al.evento_id=$TID;")
echo "$DIAS" | grep -q "20" \
  && ok "CA-27.1/CA-38.1 el termino nuevo nacio con [$DIAS] dias: usa el esquema recien configurado" \
  || no "CA-27.1 el termino uso [$DIAS], no el esquema nuevo"

# Restaurar
puj $CAT /api/esquema-alertas '{"diasAnticipacion":[15,5,1]}' > /dev/null
echo "  (esquema restaurado a $(get $CAT /api/esquema-alertas | grep -o '\"diasAnticipacion\":\[[^]]*\]'))"

# --- CA-27.3 - esquema por TERMINO, de verdad -------------------------
POR_TERMINO=$(grep -rn "esquema" $RAIZ/backend/src/main/java/co/iuris/sgpj/vigilancia/infraestructura/VigilanciaController.java \
  | grep -iE "terminos/\{|/\{id\}/esquema" | wc -l)
COL=$(sql "select count(*) from information_schema.columns where table_name='termino' and column_name like '%esquema%';")
if [ "$POR_TERMINO" -gt 0 ] || [ "$COL" -gt 0 ]; then
  ok "CA-27.3 existe forma de ajustar el esquema de un termino concreto"
else
  no "CA-27.3 NO existe: el esquema es solo del despacho. Ni endpoint por termino ni columna en la tabla termino"
fi

echo
echo "=== Rehecho: la columna es 'tipo', no 'tipo_catalogo' ==="

# --- CA-37.5 - el catalogo de juzgados nace vacio ---------------------
SEMILLA=$(grep -rn "JUZGADO" $RAIZ/backend/src/main/resources/db/migration/*.sql 2>/dev/null | grep -i "insert" | wc -l)
UNO=$(sql "select count(*) from valor_catalogo where despacho_id=45 and tipo='JUZGADO';")
CAN_N=$(sql "select count(*) from valor_catalogo where despacho_id=709 and tipo='JUZGADO';")
OTROS_TIPOS=$(sql "select count(*) from valor_catalogo where despacho_id=45 and tipo<>'JUZGADO';")
if [ "$SEMILLA" = "0" ] && [ "$UNO" = "0" ]; then
  ok "CA-37.5 ninguna migracion siembra juzgados. El Despacho Uno tiene 0 juzgados y $OTROS_TIPOS valores de otros catalogos: el juzgado es el unico que nace vacio, a proposito"
else
  no "CA-37.5 hay $SEMILLA insercion(es) en migraciones y el Despacho Uno tiene $UNO juzgado(s)"
fi

echo
echo "=== Rehecho: CA-35.3 contra un despacho QUE SI TIENE procesos ==="

# El intento anterior fue hueco: se busco desde el Despacho Uno, que tiene
# CERO procesos. Cero resultados no prueba aislamiento, prueba que no hay
# nada. Se usa Cantillo, que tiene uno propio.
MIOS=$(sql "select count(*) from proceso where despacho_id=709;")
AJENOS=$(sql "select count(*) from proceso where despacho_id=47;")
RAD_AJENO=$(sql "select radicado from proceso where despacho_id=47 and radicado like '11001%' limit 1;")
FRAG="1100"
RES=$(get $CAN "/api/procesos?radicado=$FRAG")
N=$(echo "$RES" | grep -o '"id":' | wc -l)
FUGA=$(echo "$RES" | grep -c "$RAD_AJENO")
DIRECTO=$(sql "select count(*) from proceso where despacho_id=47 and radicado like '$FRAG%';")
if [ "$FUGA" = "0" ] && [ "$DIRECTO" -ge 1 ]; then
  ok "CA-35.3 el fragmento «$FRAG» coincide con $DIRECTO proceso(s) del OTRO despacho y con 0 de los mios: la busqueda devolvio $N y ninguno ajeno"
  echo "              (Cantillo tiene $MIOS proceso(s) propios y el otro despacho $AJENOS: la busqueda tenia algo real que fugar)"
else
  no "CA-35.3 fuga=$FUGA, coincidencias ajenas reales=$DIRECTO"
fi
