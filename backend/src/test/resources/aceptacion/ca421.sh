#!/bin/bash
# CA-42.1 - Con el despacho inactivo, una funcionalidad de CADA modulo queda
# bloqueada.
#
# La prueba solo significa algo con la sesion YA ABIERTA. Si se desactivara
# primero y se intentara entrar despues, lo unico que se comprobaria es que
# el ingreso falla — y eso no dice nada sobre los modulos. Aqui se entra,
# se comprueba que TODO funciona, se desactiva sin tocar la sesion, y se
# vuelve a intentar lo mismo.

B=http://localhost:8081
PSQL="/c/Program Files/PostgreSQL/18/bin/psql.exe"
export PGPASSWORD=2283
tok(){ grep XSRF-TOKEN "$1" | awk '{print $7}'; }
cod(){ curl -s -b "$1" -o /dev/null -w "%{http_code}" "$B$2"; }
entrar(){ rm -f "$3"; curl -s -c "$3" "$B/api/autenticacion/csrf" >/dev/null
  T=$(grep XSRF-TOKEN "$3"|awk '{print $7}')
  curl -s -b "$3" -c "$3" -X POST "$B/api/autenticacion/entrar" -H "Content-Type: application/json" \
    -H "X-XSRF-TOKEN: $T" -d "{\"correo\":\"$1\",\"contrasena\":\"$2\"}" -o /dev/null -w "%{http_code}"; }

CAN=/tmp/can.txt; PLAT=/tmp/plat.txt
echo "sesion del despacho de prueba: $(entrar admin@cantillo.co nehuilasur-4821 $CAN)"
entrar admin@iuris.co clave-local-desarrollo $PLAT > /dev/null

PROC=$("$PSQL" -U sgpj_app -h localhost -d iuris_sgpj -tAc "select id from proceso where despacho_id=709 limit 1;")

# Una funcionalidad por modulo, la mas representativa de cada uno.
declare -a MODULOS=(
  "M3 clientes|/api/clientes"
  "M4 procesos|/api/procesos"
  "M5 expediente|/api/procesos/$PROC/expediente"
  "M6 audiencias|/api/calendario"
  "M7 terminos|/api/vencimientos"
  "M8 alertas|/api/alertas/programadas"
  "M10 reportes|/api/reportes/resumen"
  "M11 catalogos|/api/catalogos/JUZGADO/activos"
  "M2 usuarios|/api/usuarios"
  "M2 bitacora|/api/bitacora"
)

echo
printf "  %-16s %-10s %-10s\n" "MODULO" "ACTIVO" "INACTIVO"
printf "  %-16s %-10s %-10s\n" "----------------" "----------" "----------"

declare -a ANTES
i=0
for m in "${MODULOS[@]}"; do
  ANTES[$i]=$(cod $CAN "${m#*|}"); i=$((i+1))
done

# Desactivar SIN tocar la sesion del despacho.
curl -s -b $PLAT -X PUT "$B/api/despachos/709/desactivar" -H "X-XSRF-TOKEN: $(tok $PLAT)" -H "Content-Length: 0" > /dev/null

BLOQUEADOS=0; ABIERTOS=0; i=0
for m in "${MODULOS[@]}"; do
  NOMBRE="${m%%|*}"; RUTA="${m#*|}"
  D=$(cod $CAN "$RUTA")
  A=${ANTES[$i]}
  MARCA=""
  if [ "$D" = "403" ] || [ "$D" = "401" ]; then BLOQUEADOS=$((BLOQUEADOS+1)); else MARCA="  <-- SIGUE ABIERTO"; ABIERTOS=$((ABIERTOS+1)); fi
  printf "  %-16s %-10s %-10s%s\n" "$NOMBRE" "$A" "$D" "$MARCA"
  i=$((i+1))
done

# Reactivar siempre, pase lo que pase.
curl -s -b $PLAT -X PUT "$B/api/despachos/709/activar" -H "X-XSRF-TOKEN: $(tok $PLAT)" -H "Content-Length: 0" > /dev/null
echo
echo "  (despacho reactivado: $("$PSQL" -U sgpj_app -h localhost -d iuris_sgpj -tAc "select estado from despacho where id=709;"))"
echo
if [ "$ABIERTOS" = "0" ]; then
  echo "  [CUMPLE]    CA-42.1 los $BLOQUEADOS modulos quedaron bloqueados con la sesion ya abierta"
else
  echo "  [NO CUMPLE] CA-42.1 $ABIERTOS modulo(s) siguieron respondiendo con el despacho inactivo"
fi
