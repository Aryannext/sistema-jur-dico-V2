#!/bin/bash
# EP7 a EP11 - lo que quedaba, mas los pendientes de EP6.
#
# Tres de estos criterios (CA-41.3, CA-42.1, CA-42.2) no se comprueban
# contra el sistema corriendo sino contra el CODIGO: hablan de como esta
# construido, no de lo que hace. Un recorrido que los ignorara por eso
# dejaria fuera justo las garantias transversales.

B=${B:-http://localhost:8081}
RAIZ=/c/dev/iuris
PSQL="/c/Program Files/PostgreSQL/18/bin/psql.exe"
export PGPASSWORD=2283
sql(){ "$PSQL" -U sgpj_app -h localhost -d iuris_sgpj -tAc "$1"; }

ok(){ echo "  [CUMPLE]    $1"; }; no(){ echo "  [NO CUMPLE] $1"; }; rev(){ echo "  [REVISAR]   $1"; }
tok(){ grep XSRF-TOKEN "$1" | awk '{print $7}'; }
get(){ curl -s -b "$1" "$B$2"; }
cod(){ curl -s -b "$1" -o /dev/null -w "%{http_code}" "$B$2"; }
pj(){ curl -s -b "$1" -X POST "$B$2" -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $(tok $1)" -d "$3"; }
puj(){ curl -s -b "$1" -X PUT "$B$2" -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $(tok $1)" -d "$3"; }
entrar(){ rm -f "$3"; curl -s -c "$3" "$B/api/autenticacion/csrf" >/dev/null
  T=$(grep XSRF-TOKEN "$3"|awk '{print $7}')
  curl -s -b "$3" -c "$3" -X POST "$B/api/autenticacion/entrar" -H "Content-Type: application/json" \
    -H "X-XSRF-TOKEN: $T" -d "{\"correo\":\"$1\",\"contrasena\":\"$2\"}" -o /dev/null -w "%{http_code}"; }

CAT=/tmp/cat.txt; UNO=/tmp/uno.txt
entrar admin.cat@despacho.co clave-cat-12345 $CAT > /dev/null
entrar admin.uno@despacho.co clave-uno-12345 $UNO > /dev/null
PID=${PID:-499}

echo "=== EP3 - lo que quedaba de clientes ==="

# --- CA-09.1 - el cliente queda asociado A MI despacho -----------------
NUEVO=$(pj $CAT /api/clientes '{"nombre":"Cliente CA-09.1","documentoIdentidad":"1075909090","telefono":"3001112233","correo":null}')
CID=$(echo "$NUEVO" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
MIO=$(sql "select d.nombre from cliente c join despacho d on d.id=c.despacho_id where c.id=$CID;")
YO=$(sql "select d.nombre from usuario u join despacho d on d.id=u.despacho_id where u.correo='admin.cat@despacho.co';")
# El despacho NO viaja en la peticion: lo pone el backend desde la sesion.
ENVIADO=$(echo "$NUEVO" | grep -c "despachoId")
[ "$MIO" = "$YO" ] \
  && ok "CA-09.1 el cliente $CID quedo en «$MIO», que es mi despacho, sin que yo lo indicara (ADR-03 control 1)" \
  || no "CA-09.1 quedo en «$MIO» y mi despacho es «$YO»"

# --- CA-09.2 - modificarlo no pierde su historial ----------------------
# El historial de un cliente son sus procesos. Se toma uno que YA tiene
# procesos, se le cambia un dato y se comprueba que sigue teniendolos.
CON_PROC=$(sql "select cliente_titular_id from proceso where despacho_id=47 limit 1;")
ANTES=$(get $CAT "/api/procesos/de-cliente/$CON_PROC" | grep -o '"id":' | wc -l)
ORIG=$(get $CAT "/api/clientes/$CON_PROC")
NOM=$(echo "$ORIG" | grep -o '"nombre":"[^"]*"' | head -1 | cut -d'"' -f4)
puj $CAT "/api/clientes/$CON_PROC" "{\"nombre\":\"$NOM\",\"documentoIdentidad\":null,\"telefono\":\"3009998877\",\"correo\":null}" > /dev/null
DESPUES=$(get $CAT "/api/procesos/de-cliente/$CON_PROC" | grep -o '"id":' | wc -l)
[ "$ANTES" = "$DESPUES" ] && [ "$ANTES" -ge 1 ] \
  && ok "CA-09.2 tras modificar al cliente $CON_PROC conserva sus $DESPUES proceso(s)" \
  || no "CA-09.2 tenia $ANTES proceso(s) y ahora tiene $DESPUES"

echo
echo "=== EP6 - configuracion del esquema ==="

# --- CA-26.3 / CA-27.1 / CA-38.1 - el esquema del despacho ------------
ORIGINAL=$(get $CAT /api/esquema-alertas)
puj $CAT /api/esquema-alertas '{"dias":[20,15,5,1]}' > /dev/null
NUEVO_ESQ=$(get $CAT /api/esquema-alertas)
echo "$NUEVO_ESQ" | grep -q "20" \
  && ok "CA-26.3 se pueden AÑADIR avisos al esquema del despacho: $(echo $NUEVO_ESQ | head -c 90)" \
  || no "CA-26.3 no se guardo el dia añadido: $NUEVO_ESQ"

T=$(pj $CAT "/api/procesos/$PID/terminos" '{"descripcion":"CA-27.1 usa el esquema nuevo","fechaVencimiento":"2027-08-01"}')
TID=$(echo "$T" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
DIAS=$(sql "select string_agg(distinct (t.fecha_vencimiento - al.programada_para::date)::text, ', ' order by (t.fecha_vencimiento - al.programada_para::date)::text)
            from alerta al join termino t on t.id = al.evento_id where al.evento_id=$TID;")
echo "$DIAS" | grep -q "20" \
  && ok "CA-27.1/CA-38.1 el termino NUEVO nacio con el esquema recien configurado: [$DIAS] dias de anticipacion" \
  || no "CA-27.1 el termino no uso el esquema nuevo: [$DIAS]"

# Restaurar el esquema como estaba
DIAS_ORIG=$(echo "$ORIGINAL" | grep -o '"diasAnticipacion":[0-9]*' | cut -d: -f2 | paste -sd, -)
puj $CAT /api/esquema-alertas "{\"dias\":[$DIAS_ORIG]}" > /dev/null
echo "  (esquema restaurado a [$DIAS_ORIG])"

# --- CA-27.3 - ajustar el esquema de UN termino ------------------------
ENDPOINT=$(grep -rn "esquema" $RAIZ/backend/src/main/java/co/iuris/sgpj/vigilancia/infraestructura/VigilanciaController.java 2>/dev/null | grep -i "mapping\|termino" | wc -l)
if [ "$ENDPOINT" -gt 0 ]; then
  ok "CA-27.3 hay endpoint para el esquema por termino"
else
  no "CA-27.3 NO existe forma de ajustar el esquema de un termino concreto: el esquema es solo del despacho"
fi

echo
echo "=== EP8 - busqueda ==="

# --- CA-35.3 - la busqueda solo devuelve procesos de MI despacho -------
RAD_AJENO=$(sql "select radicado from proceso where despacho_id=47 limit 1;")
FRAGMENTO=$(echo "$RAD_AJENO" | cut -c1-5)
DESDE_OTRO=$(get $UNO "/api/procesos?radicado=$(echo $FRAGMENTO | sed 's/ /%20/g')")
FUGA=$(echo "$DESDE_OTRO" | grep -c "$RAD_AJENO")
TOTAL_OTRO=$(echo "$DESDE_OTRO" | grep -o '"id":' | wc -l)
MIOS_OTRO=$(sql "select count(*) from proceso where despacho_id=45;")
[ "$FUGA" = "0" ] \
  && ok "CA-35.3 buscando «$FRAGMENTO» desde otro despacho: 0 apariciones del radicado ajeno ($TOTAL_OTRO resultado(s), y su despacho tiene $MIOS_OTRO proceso(s))" \
  || no "CA-35.3 FUGA: el radicado de otro despacho aparecio $FUGA vez/veces"

# --- CA-35.4 - responde en menos de 3 segundos -------------------------
echo "  CA-35.4 ya medido con el volumen objetivo (RNF-12): la busqueda por radicado"
echo "          respondio en 153 ms sobre 25.000 procesos. Ver RESULTADOS.md."

echo
echo "=== EP9 - catalogos ==="

# --- CA-37.5 - el catalogo de juzgados nace VACIO ----------------------
SEMILLA=$(grep -rn "JUZGADO" $RAIZ/backend/src/main/resources/db/migration/*.sql 2>/dev/null | grep -i "insert" | wc -l)
CANTILLO=$(sql "select count(*) from valor_catalogo where despacho_id=709 and tipo_catalogo='JUZGADO';")
OTROS=$(sql "select count(*) from valor_catalogo where despacho_id=45 and tipo_catalogo='JUZGADO';")
if [ "$SEMILLA" = "0" ] && [ "$OTROS" = "0" ]; then
  ok "CA-37.5 ninguna migracion siembra juzgados, y un despacho que no ha creado ninguno tiene 0 (el de prueba tiene $CANTILLO, los que se le crearon a mano)"
else
  no "CA-37.5 hay $SEMILLA insercion(es) de juzgados en las migraciones y el despacho 45 tiene $OTROS"
fi

echo
echo "=== EP11 - garantias transversales ==="

# --- CA-41.3 - una prueba de acceso cruzado POR MODULO -----------------
echo "  Modulos que exponen datos y su prueba de acceso cruzado:"
FALTAN=0
for m in cliente proceso expediente vigilancia alertas portal catalogo usuario bitacora; do
  N=$(grep -rli "despacho\|cruzad\|ajeno\|otroDespacho" $RAIZ/backend/src/test/java/co/iuris/sgpj/$m/ 2>/dev/null | wc -l)
  C=$(grep -rn "otroDespacho\|OtroDespacho\|de otro despacho\|acceso cruzado" $RAIZ/backend/src/test/java/co/iuris/sgpj/$m/ 2>/dev/null | wc -l)
  if [ "$C" -gt 0 ]; then
    printf "    %-12s %s referencia(s) a acceso cruzado\n" "$m" "$C"
  else
    printf "    %-12s SIN prueba de acceso cruzado\n" "$m"
    FALTAN=$((FALTAN+1))
  fi
done
[ "$FALTAN" = "0" ] \
  && ok "CA-41.3 todos los modulos que exponen datos tienen su prueba de acceso cruzado" \
  || rev "CA-41.3 $FALTAN modulo(s) sin prueba de acceso cruzado propia"

# --- CA-42.2 - la verificacion en UN UNICO punto de control ------------
PUNTOS=$(grep -rln "despachoActual()" $RAIZ/backend/src/main/java/co/iuris/sgpj/seguridad/ 2>/dev/null | wc -l)
DEFINE=$(grep -rn "public Long despachoActual" $RAIZ/backend/src/main/java/co/iuris/sgpj/ -r 2>/dev/null | wc -l)
DESDE_PETICION=$(grep -rn "despachoId" $RAIZ/backend/src/main/java/co/iuris/sgpj/*/infraestructura/*.java 2>/dev/null | grep -iE "RequestParam|PathVariable" | wc -l)
if [ "$DEFINE" = "1" ] && [ "$DESDE_PETICION" = "0" ]; then
  ok "CA-42.2 el despacho se resuelve en UN solo sitio ($DEFINE definicion de despachoActual) y NUNCA llega por la peticion ($DESDE_PETICION parametros despachoId)"
else
  rev "CA-42.2 definiciones de despachoActual: $DEFINE; parametros despachoId en peticiones: $DESDE_PETICION"
fi
