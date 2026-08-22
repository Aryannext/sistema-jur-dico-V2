B=http://localhost:8081
tok(){ grep XSRF-TOKEN "$1"|awk '{print $7}'; }
put(){ curl -s -b "$1" -X PUT "$B$2" -H "X-XSRF-TOKEN: $(tok $1)" -H "Content-Length: 0"; }
entrar(){ rm -f "$3"; curl -s -c "$3" "$B/api/autenticacion/csrf" >/dev/null
  T=$(grep XSRF-TOKEN "$3"|awk '{print $7}')
  curl -s -b "$3" -c "$3" -X POST "$B/api/autenticacion/entrar" -H "Content-Type: application/json" \
    -H "X-XSRF-TOKEN: $T" -d "{\"correo\":\"$1\",\"contrasena\":\"$2\"}" -o /dev/null -w "%{http_code}"; }
PLAT=/tmp/plat.txt
entrar admin@iuris.co clave-local-desarrollo $PLAT >/dev/null

export PGPASSWORD=2283
Q="/c/Program Files/PostgreSQL/18/bin/psql.exe -U sgpj_app -h localhost -d iuris_sgpj -tAc"
censo(){ "/c/Program Files/PostgreSQL/18/bin/psql.exe" -U sgpj_app -h localhost -d iuris_sgpj -tAc "
select 'usuarios='||(select count(*) from usuario where despacho_id=709)
   ||' clientes='||(select count(*) from cliente where despacho_id=709)
   ||' procesos='||(select count(*) from proceso where despacho_id=709)
   ||' catalogos='||(select count(*) from valor_catalogo where despacho_id=709)
   ||' piezas='||(select count(*) from pieza p join expediente e on e.id=p.expediente_id
                  join proceso pr on pr.id=e.proceso_id where pr.despacho_id=709);"; }

echo "ANTES de desactivar: $(censo)"
put $PLAT /api/despachos/709/desactivar > /dev/null
echo "DESPUES de desactivar: $(censo)"
echo "  estado: $("/c/Program Files/PostgreSQL/18/bin/psql.exe" -U sgpj_app -h localhost -d iuris_sgpj -tAc "select estado from despacho where id=709;")"
LOGIN=$(curl -s -c /tmp/x.txt "$B/api/autenticacion/csrf" >/dev/null; T=$(grep XSRF-TOKEN /tmp/x.txt|awk '{print $7}')
  curl -s -b /tmp/x.txt -X POST "$B/api/autenticacion/entrar" -H "Content-Type: application/json" -H "X-XSRF-TOKEN: $T" \
    -d '{"correo":"raso@cantillo.co","contrasena":"clave-raso-12345"}' -o /dev/null -w "%{http_code}")
echo "  su abogado intenta entrar: HTTP $LOGIN  (CA-02.1: surte efecto de inmediato)"
put $PLAT /api/despachos/709/activar > /dev/null
echo "REACTIVADO: $(censo)  estado=$("/c/Program Files/PostgreSQL/18/bin/psql.exe" -U sgpj_app -h localhost -d iuris_sgpj -tAc "select estado from despacho where id=709;")"
