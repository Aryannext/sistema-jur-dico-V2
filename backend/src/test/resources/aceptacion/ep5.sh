#!/bin/bash
# Resto de EP4 y EP5 completo - audiencias y terminos.

B=${B:-http://localhost:8081}
ok(){ echo "  [CUMPLE]    $1"; }; no(){ echo "  [NO CUMPLE] $1"; }; rev(){ echo "  [REVISAR]   $1"; }
tok(){ grep XSRF-TOKEN "$1" | awk '{print $7}'; }
get(){ curl -s -b "$1" "$B$2"; }
pj(){ curl -s -b "$1" -X POST "$B$2" -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $(tok $1)" -d "$3"; }
entrar(){ rm -f "$3"; curl -s -c "$3" "$B/api/autenticacion/csrf" >/dev/null
  T=$(grep XSRF-TOKEN "$3"|awk '{print $7}')
  curl -s -b "$3" -c "$3" -X POST "$B/api/autenticacion/entrar" -H "Content-Type: application/json" \
    -H "X-XSRF-TOKEN: $T" -d "{\"correo\":\"$1\",\"contrasena\":\"$2\"}" -o /dev/null -w "%{http_code}"; }

CAT=/tmp/cat.txt
entrar admin.cat@despacho.co clave-cat-12345 $CAT > /dev/null
PID=${PID:-499}

echo "=== EP4 - lo que faltaba ==="

# --- CA-15.3 - un archivo de hasta 20 MB se acepta ---------------------
# Se prueba el limite POR AMBOS LADOS. Solo comprobar que 20 MB entra no
# dice si hay limite: podria no haberlo, y entonces el sistema aceptaria
# tambien un archivo de 500 MB y se quedaria sin memoria.
GRANDE=$(mktemp /tmp/grande-XXXX.bin)
head -c 20000000 /dev/urandom > "$GRANDE"      # 20 MB justos
TD=$(get $CAT /api/catalogos/TIPO_DOCUMENTO/activos | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
COD20=$(curl -s -b $CAT -X POST "$B/api/procesos/$PID/documentos" -H "X-XSRF-TOKEN: $(tok $CAT)" \
  -F "tipoDocumentoId=$TD" -F "archivo=@$GRANDE" -o /dev/null -w "%{http_code}")

ENORME=$(mktemp /tmp/enorme-XXXX.bin)
head -c 25000000 /dev/urandom > "$ENORME"      # 25 MB: debe rechazarse
COD25=$(curl -s -b $CAT -X POST "$B/api/procesos/$PID/documentos" -H "X-XSRF-TOKEN: $(tok $CAT)" \
  -F "tipoDocumentoId=$TD" -F "archivo=@$ENORME" -o /dev/null -w "%{http_code}")

if [ "$COD20" = "201" ] || [ "$COD20" = "200" ]; then
  if [ "$COD25" = "201" ] || [ "$COD25" = "200" ]; then
    no "CA-15.3 acepta 20 MB ($COD20) pero TAMBIEN 25 MB ($COD25): no hay limite real"
  else
    ok "CA-15.3 acepta 20 MB ($COD20) y rechaza 25 MB ($COD25): el limite existe y esta donde dice"
  fi
else
  no "CA-15.3 rechaza un archivo de 20 MB, que deberia aceptar: HTTP $COD20"
fi
rm -f "$GRANDE" "$ENORME"

# --- CA-16.3 - el sistema OFRECE la nota interna como alternativa ------
ADV=$(get $CAT /api/procesos/$PID/documentos/advertencia)
echo "$ADV" | grep -qi "nota interna" \
  && ok "CA-16.3 la advertencia ofrece la alternativa: $(echo $ADV | grep -o '\"alternativa\":\"[^\"]*\"' | head -c 150)" \
  || no "CA-16.3 la advertencia no menciona la nota interna: $(echo $ADV | head -c 200)"

echo
echo "=== EP5 - Audiencias y terminos ==="

# --- CA-20.3 - registrar una audiencia programa sus alertas ------------
AUD=$(pj $CAT /api/procesos/$PID/audiencias '{"fechaHora":"2026-11-20T09:30:00-05:00","lugar":"Sala 2","observaciones":"Verificacion CA-20.3"}')
AID=$(echo "$AUD" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
ALA=$(get $CAT /api/alertas/de-evento/$AID)
N=$(echo "$ALA" | grep -o '"id":' | wc -l)
[ "$N" -ge 3 ] \
  && ok "CA-20.3 la audiencia $AID quedo con $N alertas programadas sola (RN-29 exige al menos 3: 48h, 24h y el dia)" \
  || no "CA-20.3 solo se programaron $N alertas"

# --- CA-21.1 - el calendario las ubica en su fecha ---------------------
CAL=$(get $CAT /api/calendario)
echo "$CAL" | grep -q "\"id\":$AID," \
  && ok "CA-21.1 la audiencia aparece en el calendario con su fecha: $(echo $CAL | tr '{' '\n' | grep "\"id\":$AID," | grep -o '\"fechaHora\":\"[^\"]*\"')" \
  || no "CA-21.1 la audiencia no aparece en el calendario"

# --- CA-21.2 - desde la audiencia se llega a su proceso ----------------
LLEVA=$(echo "$CAL" | tr '{' '\n' | grep "\"id\":$AID," | grep -o '"procesoId":[0-9]*' | cut -d: -f2)
[ "$LLEVA" = "$PID" ] \
  && ok "CA-21.2 la audiencia trae el procesoId=$LLEVA, que es lo que permite ir a su expediente" \
  || no "CA-21.2 la audiencia no dice a que proceso pertenece"

# --- CA-22.1 / CA-22.4 - el termino lo fecha el abogado, y alerta ------
TER=$(pj $CAT /api/procesos/$PID/terminos '{"descripcion":"Verificacion CA-22","fechaVencimiento":"2026-12-01"}')
TID=$(echo "$TER" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
GUARDADA=$(echo "$TER" | grep -o '"fechaVencimiento":"[^"]*"')
ALT=$(get $CAT /api/alertas/de-evento/$TID)
NT=$(echo "$ALT" | grep -o '"id":' | wc -l)
echo "$GUARDADA" | grep -q "2026-12-01" \
  && ok "CA-22.1 se guardo EXACTAMENTE la fecha que indico el abogado: $GUARDADA" \
  || no "CA-22.1 el sistema cambio la fecha: $GUARDADA"
[ "$NT" -ge 1 ] \
  && ok "CA-22.4 quedaron $NT alerta(s) anticipada(s) programadas (RN-37b: nunca cero)" \
  || no "CA-22.4 el termino quedo SIN alertas"

# --- CA-22.2 - el sistema NO calcula ni sugiere la fecha ---------------
# Se comprueba en el codigo y en la pantalla: no debe existir nada que
# derive una fecha de vencimiento de normas procesales.
CALCULO=$(grep -rniE "plusDays|addDays|calcularVencimiento|sugerirFecha|diasHabiles" \
  /c/dev/iuris/backend/src/main/java/co/iuris/sgpj/vigilancia/ 2>/dev/null | wc -l)
SUGIERE=$(grep -rniE "sugerir|calcula|automatic" /c/dev/iuris/frontend/src/app/despacho/terminos/*.html 2>/dev/null | wc -l)
if [ "$CALCULO" = "0" ] && [ "$SUGIERE" = "0" ]; then
  ok "CA-22.2 no hay ningun calculo de fecha en vigilancia ni sugerencia en la pantalla (RN-36, frontera legal)"
else
  rev "CA-22.2 revisar: $CALCULO coincidencia(s) de calculo en backend, $SUGIERE en la pantalla"
fi

# --- CA-23.1 - el cambio de estado del termino queda registrado --------
ANTES=$(get $CAT /api/procesos/$PID/terminos | tr '{' '\n' | grep "\"id\":$TID," | grep -o '"estado":"[^"]*"')
curl -s -b $CAT -X PUT "$B/api/terminos/$TID/cumplir" -H "X-XSRF-TOKEN: $(tok $CAT)" -o /dev/null
DESPUES=$(get $CAT /api/procesos/$PID/terminos | tr '{' '\n' | grep "\"id\":$TID," | grep -o '"estado":"[^"]*"')
[ "$ANTES" != "$DESPUES" ] \
  && ok "CA-23.1 el estado cambio y quedo registrado: $ANTES -> $DESPUES" \
  || no "CA-23.1 el estado no cambio: sigue en $ANTES"

# --- CA-24.2 - si la alerta fallo, el vencimiento SIGUE visible --------
# Es la segunda via de defensa contra R-02: el panel no depende del correo.
FALLIDAS=$(get $CAT /api/alertas/fallidas | grep -o '"id":' | wc -l)
PANEL=$(get $CAT /api/vencimientos | grep -o '"id":' | wc -l)
[ "$PANEL" -ge 1 ] \
  && ok "CA-24.2 el panel muestra $PANEL vencimiento(s) con independencia del correo (hay $FALLIDAS alerta(s) fallida(s) en el sistema)" \
  || rev "CA-24.2 el panel esta vacio: no se puede afirmar que sea independiente del correo"
