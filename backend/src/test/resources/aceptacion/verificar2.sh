#!/bin/bash
B=http://localhost:8081
ok(){ echo "  [CUMPLE]    $1"; }; no(){ echo "  [NO CUMPLE] $1"; }; duda(){ echo "  [REVISAR]   $1"; }
entrar(){ rm -f "$3"; curl -s -c "$3" "$B/api/autenticacion/csrf" >/dev/null
  T=$(grep XSRF-TOKEN "$3"|awk '{print $7}')
  curl -s -b "$3" -c "$3" -X POST "$B/api/autenticacion/entrar" -H "Content-Type: application/json" \
    -H "X-XSRF-TOKEN: $T" -d "{\"correo\":\"$1\",\"contrasena\":\"$2\"}" -o /dev/null -w "%{http_code}"; }
tok(){ grep XSRF-TOKEN "$1"|awk '{print $7}'; }
post(){ curl -s -b "$1" -X POST "$B$2" -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $(tok $1)" -d "$3"; }
get(){ curl -s -b "$1" "$B$2"; }

CAT=/tmp/cat.txt; UNO=/tmp/uno.txt
entrar admin.cat@despacho.co clave-cat-12345 $CAT >/dev/null
entrar admin.uno@despacho.co clave-uno-12345 $UNO >/dev/null

echo "=== EP3 - procesos ==="

# CA-11.2 - omitir un campo obligatorio se impide E INDICA CUAL
FALTA=$(post $CAT /api/procesos '{"radicado":"SIN-JUZGADO-001","tipoProcesoId":1,"estadoProcesalId":1,"clienteTitularId":4,"abogadoResponsableId":51}')
echo "$FALTA" | grep -qi "juzgado" \
  && ok "CA-11.2 al faltar el juzgado el sistema lo impide e INDICA cual: $(echo $FALTA | grep -o '\"detail\":\"[^\"]*\"' | head -c 120)" \
  || no "CA-11.2 el mensaje no nombra el campo que falta: $(echo $FALTA | head -c 160)"

# CA-12.2 - el MISMO radicado en OTRO despacho debe PERMITIRSE
RAD="41001 31 03 001 2026 00777 00"
JU=$(get $UNO /api/catalogos/JUZGADO/activos | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
TP=$(get $UNO /api/catalogos/TIPO_PROCESO/activos | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
ES=$(get $UNO /api/catalogos/ESTADO_PROCESAL/activos | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
CL=$(get $UNO /api/clientes | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
AB=$(get $UNO /api/autenticacion/yo | grep -o '"usuarioId":[0-9]*' | cut -d: -f2)
if [ -z "$CL" ]; then
  CL=$(post $UNO /api/clientes '{"nombre":"Cliente CA-12.2","documentoIdentidad":null,"telefono":null,"correo":null}' | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
fi
DUP=$(post $UNO /api/procesos "{\"radicado\":\"$RAD\",\"juzgadoId\":$JU,\"tipoProcesoId\":$TP,\"estadoProcesalId\":$ES,\"clienteTitularId\":$CL,\"abogadoResponsableId\":$AB}")
echo "$DUP" | grep -q '"id"' \
  && ok "CA-12.2 el mismo radicado SI se permite en otro despacho (la unicidad es por despacho, RN-17)" \
  || no "CA-12.2 fue rechazado: $(echo $DUP | head -c 160)"
