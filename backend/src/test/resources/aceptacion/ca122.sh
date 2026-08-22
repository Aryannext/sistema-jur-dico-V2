B=http://localhost:8081
tok(){ grep XSRF-TOKEN "$1"|awk '{print $7}'; }
post(){ curl -s -b "$1" -X POST "$B$2" -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $(tok $1)" -d "$3"; }
get(){ curl -s -b "$1" "$B$2"; }
entrar(){ rm -f "$3"; curl -s -c "$3" "$B/api/autenticacion/csrf" >/dev/null
  T=$(grep XSRF-TOKEN "$3"|awk '{print $7}')
  curl -s -b "$3" -c "$3" -X POST "$B/api/autenticacion/entrar" -H "Content-Type: application/json" \
    -H "X-XSRF-TOKEN: $T" -d "{\"correo\":\"$1\",\"contrasena\":\"$2\"}" -o /dev/null -w "%{http_code}"; }

CAN=/tmp/can.txt
entrar admin@cantillo.co nehuilasur-4821 $CAN >/dev/null

# Montar lo minimo en el despacho de prueba
post $CAN /api/catalogos/JUZGADO '{"nombre":"Juzgado 2 Civil del Circuito de Neiva","orden":1}' >/dev/null
CL=$(post $CAN /api/clientes '{"nombre":"Cliente de contraste","documentoIdentidad":"1075000111","telefono":null,"correo":null}' | grep -o '"id":[0-9]*'|head -1|cut -d: -f2)
JU=$(get $CAN /api/catalogos/JUZGADO/activos | grep -o '"id":[0-9]*'|head -1|cut -d: -f2)
TP=$(get $CAN /api/catalogos/TIPO_PROCESO/activos | grep -o '"id":[0-9]*'|head -1|cut -d: -f2)
ES=$(get $CAN /api/catalogos/ESTADO_PROCESAL/activos | grep -o '"id":[0-9]*'|head -1|cut -d: -f2)
# El responsable debe ser ABOGADO (RN-31): raso@cantillo.co
AB=$(get $CAN /api/usuarios | tr '}' '\n' | grep -B0 'raso@cantillo.co' | grep -o '"id":[0-9]*'|head -1|cut -d: -f2)
echo "montado -> juzgado=$JU tipo=$TP estado=$ES cliente=$CL abogado=$AB"

RAD="41001 31 03 001 2026 00777 00"
echo "El radicado \"$RAD\" ya existe en el Despacho Catalogos."
R=$(post $CAN /api/procesos "{\"radicado\":\"$RAD\",\"juzgadoId\":$JU,\"tipoProcesoId\":$TP,\"estadoProcesalId\":$ES,\"clienteTitularId\":$CL,\"abogadoResponsableId\":$AB}")
echo "$R" | grep -q '"id"' \
  && echo "  [CUMPLE]    CA-12.2 el MISMO radicado se acepta en otro despacho: proceso $(echo $R|grep -o '\"id\":[0-9]*'|head -1|cut -d: -f2)" \
  || echo "  [NO CUMPLE] CA-12.2 -> $(echo $R | head -c 200)"
