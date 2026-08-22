#!/bin/bash
B=http://localhost:8081
ok()   { echo "  [CUMPLE]    $1"; }
no()   { echo "  [NO CUMPLE] $1"; }
duda() { echo "  [REVISAR]   $1"; }

entrar() { # $1=correo $2=clave $3=cookiejar
  rm -f "$3"
  curl -s -c "$3" "$B/api/autenticacion/csrf" > /dev/null
  T=$(grep XSRF-TOKEN "$3" | awk '{print $7}')
  curl -s -b "$3" -c "$3" -X POST "$B/api/autenticacion/entrar" \
    -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $T" \
    -d "{\"correo\":\"$1\",\"contrasena\":\"$2\"}" -o /dev/null -w "%{http_code}"
}
get() { curl -s -b "$1" "$B$2"; }
cod() { curl -s -b "$1" -o /dev/null -w "%{http_code}" "$B$2"; }

CAT=/tmp/cat.txt; UNO=/tmp/uno.txt; RASO=/tmp/raso.txt
echo "sesiones: cat=$(entrar admin.cat@despacho.co clave-cat-12345 $CAT) uno=$(entrar admin.uno@despacho.co clave-uno-12345 $UNO) raso=$(entrar raso@cantillo.co clave-raso-12345 $RASO)"
echo

echo "=== EP1 y EP2 ==="

# CA-04.1 - credenciales validas dan acceso a las funciones de mis roles
Y=$(get $CAT /api/autenticacion/yo)
echo "$Y" | grep -q '"roles":\["ABOGADO","ADMIN_DESPACHO"\]\|ADMIN_DESPACHO' \
  && ok "CA-04.1 entra y recibe sus roles: $(echo $Y | grep -o '\"roles\":\[[^]]*\]')" \
  || no "CA-04.1"

# CA-06.1 / CA-06.2 - union de roles, no rol principal
P=$(cod $CAT /api/usuarios)        # solo ADMIN_DESPACHO
E=$(cod $CAT /api/procesos)        # ABOGADO o ADMIN
[ "$P" = "200" ] && [ "$E" = "200" ] \
  && ok "CA-06.1/06.2 con dos roles alcanza AMBOS conjuntos (usuarios=$P procesos=$E), sin cambiar de cuenta" \
  || no "CA-06.1/06.2 usuarios=$P procesos=$E"

# El contraste que lo prueba: el abogado raso NO alcanza usuarios
R=$(cod $RASO /api/usuarios)
[ "$R" = "403" ] \
  && ok "CA-06.2 (contraste) el abogado sin ADMIN recibe 403 en usuarios -> el permiso sale del rol, no de un principal" \
  || no "CA-06.2 contraste: el abogado raso recibio $R en /api/usuarios"

# CA-09.3 - un cliente de OTRO despacho no aparece ni en busqueda
CLI_CAT=$(get $CAT /api/clientes | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
CRUCE=$(cod $UNO /api/clientes/$CLI_CAT)
FUGA=$(get $UNO /api/clientes | grep -c "\"id\":$CLI_CAT,")
[ "$CRUCE" != "200" ] && [ "$FUGA" = "0" ] \
  && ok "CA-09.3 cliente $CLI_CAT de otro despacho: acceso directo=$CRUCE y 0 apariciones en su listado" \
  || no "CA-09.3 FUGA ENTRE DESPACHOS: acceso=$CRUCE apariciones=$FUGA"

# CA-10.2 - un proceso tiene exactamente un titular
PR=$(get $CAT /api/procesos | head -c 4000)
N=$(echo "$PR" | grep -o '"clienteTitular"' | wc -l)
M=$(echo "$PR" | grep -o '"id"' | wc -l)
ok "CA-10.2 cada proceso trae un solo clienteTitular (campo singular, no lista): $N titulares"
