#!/bin/bash
# EP4 - Expediente digital. Recorrido de criterios de aceptacion.
#
# Se comprueba contra el sistema corriendo, no contra la base: cargar un
# documento por la API es lo que hace de verdad un abogado, y es el unico
# camino que ejercita el cifrado, la clasificacion y la bitacora a la vez.

B=${B:-http://localhost:8081}
ok(){ echo "  [CUMPLE]    $1"; }; no(){ echo "  [NO CUMPLE] $1"; }; rev(){ echo "  [REVISAR]   $1"; }
tok(){ grep XSRF-TOKEN "$1" | awk '{print $7}'; }
get(){ curl -s -b "$1" "$B$2"; }
entrar(){ rm -f "$3"; curl -s -c "$3" "$B/api/autenticacion/csrf" >/dev/null
  T=$(grep XSRF-TOKEN "$3"|awk '{print $7}')
  curl -s -b "$3" -c "$3" -X POST "$B/api/autenticacion/entrar" -H "Content-Type: application/json" \
    -H "X-XSRF-TOKEN: $T" -d "{\"correo\":\"$1\",\"contrasena\":\"$2\"}" -o /dev/null -w "%{http_code}"; }

CAT=/tmp/cat.txt
entrar admin.cat@despacho.co clave-cat-12345 $CAT > /dev/null
PID=${PID:-499}

echo "=== EP4 - Expediente digital ==="

# --- CA-15.1 / CA-15.2 -------------------------------------------------
# Se carga un documento con un texto RECONOCIBLE. Es lo que permite
# comprobar el cifrado sin ambiguedad: si esa frase aparece en el fichero
# del disco, no esta cifrado; si no aparece, si.
FRASE="PODER-ESPECIAL-AMPLIO-Y-SUFICIENTE-$(date +%s)"
TMP=$(mktemp /tmp/poder-XXXX.txt); echo "$FRASE" > "$TMP"

TD=$(get $CAT /api/catalogos/TIPO_DOCUMENTO/activos | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
SUBIDA=$(curl -s -b $CAT -X POST "$B/api/procesos/$PID/documentos" \
  -H "X-XSRF-TOKEN: $(tok $CAT)" -F "tipoDocumentoId=$TD" -F "archivo=@$TMP")
PIEZA=$(echo "$SUBIDA" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)

if [ -z "$PIEZA" ]; then
  no "CA-15.1 no se pudo cargar: $(echo $SUBIDA | head -c 200)"
else
  # Disponible para consulta...
  EN_LISTA=$(get $CAT /api/procesos/$PID/expediente | grep -c "\"id\":$PIEZA,")
  # ...y para descarga, con el contenido intacto
  BAJADA=$(curl -s -b $CAT "$B/api/procesos/$PID/documentos/$PIEZA")
  if [ "$EN_LISTA" -ge 1 ] && echo "$BAJADA" | grep -q "$FRASE"; then
    ok "CA-15.1 el documento queda en el expediente y se descarga con su contenido intacto (pieza $PIEZA)"
  else
    no "CA-15.1 en lista=$EN_LISTA, contenido recuperado=$(echo $BAJADA | head -c 60)"
  fi

  # --- CA-15.2 - cifrado en reposo -------------------------------------
  ALM=$(ls -td /c/dev/iuris/backend/almacen-documentos 2>/dev/null | head -1)
  if [ -d "$ALM" ]; then
    HALLADO=$(grep -rl "$FRASE" "$ALM" 2>/dev/null | wc -l)
    RECIENTE=$(ls -t "$ALM" | head -1)
    if [ "$HALLADO" = "0" ]; then
      ok "CA-15.2 la frase del documento NO aparece en claro en ninguno de los $(ls "$ALM" | wc -l) ficheros del almacen"
      echo "              fichero mas reciente ($RECIENTE), primeros bytes:"
      head -c 48 "$ALM/$RECIENTE" | od -c | head -2 | sed 's/^/              /'
    else
      no "CA-15.2 EL DOCUMENTO ESTA EN CLARO: la frase aparece en $HALLADO fichero(s)"
    fi
  else
    rev "CA-15.2 no se encontro el almacen de documentos en $ALM"
  fi
fi

# --- CA-19.3 - no existe la opcion de eliminar una pieza ---------------
BORRA=$(curl -s -b $CAT -X DELETE "$B/api/procesos/$PID/piezas/$PIEZA" \
  -H "X-XSRF-TOKEN: $(tok $CAT)" -o /dev/null -w "%{http_code}")
BOTON=$(grep -rniE "eliminar|borrar" /c/dev/iuris/frontend/src/app/despacho/expediente/*.html 2>/dev/null | wc -l)
if [ "$BORRA" != "200" ] && [ "$BORRA" != "204" ] && [ "$BOTON" = "0" ]; then
  ok "CA-19.3 no hay endpoint de borrado (DELETE -> $BORRA) NI boton en el expediente: se corrige rectificando"
else
  no "CA-19.3 DELETE devolvio $BORRA y hay $BOTON mencion(es) de eliminar/borrar en la pantalla"
fi

# --- CA-17.1 - fecha y tipo obligatorios en una actuacion --------------
FALTA=$(curl -s -b $CAT -X POST "$B/api/procesos/$PID/actuaciones" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $(tok $CAT)" \
  -d '{"descripcion":"Sin fecha ni tipo"}')
echo "$FALTA" | grep -q '"errores"' \
  && ok "CA-17.1 se rechaza sin fecha ni tipo, y nombra los campos: $(echo $FALTA | grep -o '\"errores\":{[^}]*}')" \
  || no "CA-17.1 -> $(echo $FALTA | head -c 200)"

# --- CA-18.1 - la nota queda visible para el despacho ------------------
NOTA=$(curl -s -b $CAT -X POST "$B/api/procesos/$PID/notas" \
  -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $(tok $CAT)" \
  -d '{"contenido":"Nota interna de verificacion CA-18.1"}')
NID=$(echo "$NOTA" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
VE_DESPACHO=$(get $CAT /api/procesos/$PID/expediente | grep -c "\"id\":$NID,")
[ "$VE_DESPACHO" -ge 1 ] \
  && ok "CA-18.1 la nota $NID aparece en el expediente del despacho" \
  || no "CA-18.1 la nota no aparece"

# --- CA-16.2 / D-12 - lo que ve el cliente -----------------------------
VISTA=$(get $CAT /api/procesos/$PID/expediente/vista-cliente)
VE_DOC=$(echo "$VISTA" | grep -c "\"id\":$PIEZA,")
VE_NOTA=$(echo "$VISTA" | grep -c "\"id\":$NID,")
if [ "$VE_DOC" -ge 1 ] && [ "$VE_NOTA" = "0" ]; then
  ok "CA-16.2 el cliente ve el documento recien cargado SIN paso intermedio, y NO ve la nota interna (D-12)"
else
  no "CA-16.2 documento visible=$VE_DOC (deberia 1), nota visible=$VE_NOTA (deberia 0)"
fi

rm -f "$TMP"
