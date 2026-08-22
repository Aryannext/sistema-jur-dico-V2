B=http://localhost:8081
ok(){ echo "  [CUMPLE]    $1"; }; no(){ echo "  [NO CUMPLE] $1"; }; rev(){ echo "  [REVISAR]   $1"; }
tok(){ grep XSRF-TOKEN "$1"|awk '{print $7}'; }
get(){ curl -s -b "$1" "$B$2"; }
put(){ curl -s -b "$1" -X PUT "$B$2" -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $(tok $1)" -d "$3"; }
post(){ curl -s -b "$1" -X POST "$B$2" -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $(tok $1)" -d "$3"; }
CAT=/tmp/cat.txt; CAN=/tmp/can.txt

echo "=== CA-14.1 - el cambio de estado queda registrado y se refleja en reportes ==="
ANTES=$(get $CAT /api/reportes/procesos-por-estado)
PID=499
EST_ARCH=$(get $CAT /api/catalogos/ESTADO_PROCESAL/activos | tr '}' '\n' | grep -i 'archivado' | grep -o '"id":[0-9]*' | cut -d: -f2)
put $CAT /api/procesos/$PID/estado "{\"estadoProcesalId\":$EST_ARCH}" > /dev/null
DESPUES=$(get $CAT /api/reportes/procesos-por-estado)
NUEVO=$(get $CAT /api/procesos/$PID | grep -o '"estadoProcesal":{[^}]*}')
echo "  estado del proceso ahora: $NUEVO"
echo "  reporte antes  : $ANTES"
echo "  reporte despues: $DESPUES"
[ "$ANTES" != "$DESPUES" ] && ok "CA-14.1 el cambio se refleja en el reporte por estado" || no "CA-14.1 el reporte no cambio"

echo
echo "=== CA-11.3 - el destinatario de la alerta es el ABOGADO RESPONSABLE ==="
# Devolver el proceso a Activo para poder vigilarlo (RN-20)
EST_ACT=$(get $CAT /api/catalogos/ESTADO_PROCESAL/activos | tr '}' '\n' | grep -i '"nombre":"Activo"' | grep -o '"id":[0-9]*' | cut -d: -f2)
put $CAT /api/procesos/$PID/estado "{\"estadoProcesalId\":$EST_ACT}" > /dev/null
RESP=$(get $CAT /api/procesos/$PID | grep -o '"abogadoResponsable":{[^}]*}')
T=$(post $CAT /api/procesos/$PID/terminos '{"descripcion":"Termino para CA-11.3","fechaVencimiento":"2026-10-15"}')
TID=$(echo "$T" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
AL=$(get $CAT /api/alertas/de-evento/$TID)
echo "  responsable del proceso: $RESP"
echo "  alertas del termino    : $(echo $AL | head -c 200)"
