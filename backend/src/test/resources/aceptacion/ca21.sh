B=http://localhost:8081
ok(){ echo "  [CUMPLE]    $1"; }; no(){ echo "  [NO CUMPLE] $1"; }
tok(){ grep XSRF-TOKEN "$1"|awk '{print $7}'; }
get(){ curl -s -b "$1" "$B$2"; }
pj(){ curl -s -b "$1" -X POST "$B$2" -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $(tok $1)" -d "$3"; }
CAT=/tmp/cat.txt; PID=499

echo "El calendario devuelve por defecto los proximos 30 dias. Se comprueba de dos formas."
echo

# a) Una audiencia DENTRO de la ventana por defecto
CERCA=$(date -d "+10 days" +%Y-%m-%d 2>/dev/null || date -v+10d +%Y-%m-%d)
A=$(pj $CAT /api/procesos/$PID/audiencias "{\"fechaHora\":\"${CERCA}T10:00:00-05:00\",\"lugar\":\"Sala 5\",\"observaciones\":\"CA-21 dentro de la ventana\"}")
AID=$(echo "$A" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
CAL=$(get $CAT /api/calendario)
FILA=$(echo "$CAL" | tr '{' '\n' | grep "\"id\":$AID,")
if [ -n "$FILA" ]; then
  ok "CA-21.1 la audiencia $AID ($CERCA) aparece en el calendario por defecto: $(echo $FILA | grep -o '\"fechaHora\":\"[^\"]*\"')"
  PR=$(echo "$FILA" | grep -o '"procesoId":[0-9]*' | cut -d: -f2)
  RAD=$(echo "$FILA" | grep -o '"radicado":"[^"]*"')
  if [ "$PR" = "$PID" ]; then
    ok "CA-21.2 la fila trae procesoId=$PR y $RAD: desde el calendario se llega a su proceso y expediente"
  else
    no "CA-21.2 la fila no dice a que proceso pertenece: $FILA"
  fi
else
  no "CA-21.1 no aparece ni estando dentro de la ventana"
fi

# b) Y la de noviembre SI aparece si se pide su rango: la ausencia anterior
#    era del rango, no del calendario.
LEJOS=$(get $CAT "/api/calendario?desde=2026-11-01T00:00:00-05:00&hasta=2026-11-30T23:59:59-05:00")
N=$(echo "$LEJOS" | grep -o '"id":' | wc -l)
[ "$N" -ge 1 ] \
  && ok "CA-21.1 (contraste) pidiendo noviembre aparecen $N audiencia(s): la ausencia anterior era del RANGO, no un defecto" \
  || no "CA-21.1 (contraste) noviembre sigue vacio"
