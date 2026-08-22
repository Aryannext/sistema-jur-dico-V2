B=http://localhost:8081; CAT=/tmp/cat.txt
get(){ curl -s -b "$CAT" "$B$1"; }
export PGPASSWORD=2283
sql(){ "/c/Program Files/PostgreSQL/18/bin/psql.exe" -U sgpj_app -h localhost -d iuris_sgpj -tAc "$1"; }

EV=$(sql "select evento_id from alerta where id=2473;")
echo "=== CA-30.1/30.2 por API: historial del evento $EV ==="
get "/api/alertas/de-evento/$EV" | tr '{' '\n' | grep -o '"programadaPara":"[^"]*"\|"enviadaEn":[^,]*\|"estado":"[^"]*"\|"correoDestinatario":"[^"]*"' | paste -sd' ' - | fold -w 160
echo ""
echo "=== lo que la PANTALLA de historial puede ver ==="
echo "  fallidas   : $(get /api/alertas/fallidas | grep -o '\"id\":' | wc -l)"
echo "  programadas: $(get /api/alertas/programadas | grep -o '\"id\":' | wc -l)"
echo "  ENVIADAS en el despacho (que NO consulta ninguna pantalla): $(sql "select count(*) from alerta a join evento_vigilado e on e.id=a.evento_id join proceso p on p.id=e.proceso_id where p.despacho_id=47 and a.estado='ENVIADA';")"
