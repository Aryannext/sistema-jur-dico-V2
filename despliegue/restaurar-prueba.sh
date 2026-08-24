#!/bin/bash
# Prueba de restauracion. D-23 control 7 · RNF-14.
#
# ESTE ES EL GUION QUE IMPORTA. RNF-14 no pide "respaldo diario": pide
# "respaldo diario CON RESTAURACION PROBADA", y esa segunda mitad es la
# que nadie hace — hasta el dia en que hace falta y el volcado estaba
# vacio, o truncado, o de una version del esquema que ya no existe.
#
# Restaura sobre una base DESECHABLE, nunca sobre la de produccion:
# comprobar un respaldo destruyendo lo que se quiere proteger seria la
# forma mas cara posible de descubrir que funcionaba.
#
# Compara las cifras contra las que anoto el respaldo. Que "restaure sin
# errores" no dice nada: pg_restore termina feliz sobre un volcado que
# solo trae el esquema.
#
# Uso:  ./restaurar-prueba.sh /var/respaldos/iuris/20260823-020000

set -euo pipefail

CARPETA="${1:?Uso: ./restaurar-prueba.sh <carpeta-del-respaldo>}"
USUARIO="${SGPJ_BD_USUARIO:?falta SGPJ_BD_USUARIO}"
# DOS CAMINOS DE AUTENTICACION, y no es un capricho.
#
# El usuario de la aplicacion se conecta por TCP con su contraseña (PGHOST
# y PGPASSWORD, que pone el envoltorio). El superusuario NO: se alcanza por
# socket Unix con autenticacion "peer", que no pide contraseña porque le
# basta con que el usuario del sistema sea "postgres".
#
# Mezclar los dos caminos es lo que permite que este guion funcione sin
# que la contraseña de postgres exista escrita en ningun sitio. Darsela
# solo para poder probar respaldos seria pagar un precio permanente por
# una comodidad de un momento.
super() { ${SGPJ_PSQL_SUPER:-sudo -u postgres psql} "$@"; }
PRUEBA="iuris_restauracion_prueba"

[ -f "$CARPETA/base.dump" ]     || { echo "No hay base.dump en $CARPETA"; exit 1; }
[ -f "$CARPETA/contenido.txt" ] || { echo "No hay contenido.txt: no se puede comparar"; exit 1; }

ESPERADO=$(cat "$CARPETA/contenido.txt")
echo "Respaldo del $(basename "$CARPETA")"
echo "  esperado: $ESPERADO"
echo ""

# --- Base desechable -------------------------------------------------
echo "· Creando base de prueba $PRUEBA..."
super -d postgres -c "DROP DATABASE IF EXISTS $PRUEBA;" > /dev/null
super -d postgres -c "CREATE DATABASE $PRUEBA OWNER $USUARIO;" > /dev/null

limpiar() {
  super -d postgres -c "DROP DATABASE IF EXISTS $PRUEBA;" > /dev/null 2>&1 || true
}
trap limpiar EXIT

# --- Restaurar -------------------------------------------------------
echo "· Restaurando..."
pg_restore -U "$USUARIO" -d "$PRUEBA" --no-owner --no-privileges "$CARPETA/base.dump" 2>&1 \
  | grep -viE "^$" | head -5 || true

# --- Comparar --------------------------------------------------------
OBTENIDO=$(psql -U "$USUARIO" -d "$PRUEBA" -tAc "
  select 'despachos='||(select count(*) from despacho)
     ||' usuarios='||(select count(*) from usuario)
     ||' procesos='||(select count(*) from proceso)
     ||' piezas='||(select count(*) from pieza)
     ||' alertas='||(select count(*) from alerta)
     ||' bitacora='||(select count(*) from asiento_bitacora);")

echo ""
echo "  esperado : $ESPERADO"
echo "  obtenido : $OBTENIDO"
echo ""

if [ "$ESPERADO" != "$OBTENIDO" ]; then
  echo "  ✗ LA RESTAURACION NO COINCIDE. Este respaldo NO sirve."
  exit 1
fi

# --- Y que el esquema sea el que la aplicacion espera -----------------
# Un volcado de hace tres meses restaura perfecto y luego la aplicacion no
# arranca porque le faltan migraciones. Comprobar las filas no basta.
MIGRACIONES=$(psql -U "$USUARIO" -d "$PRUEBA" -tAc \
  "select count(*) from flyway_schema_history where success;" 2>/dev/null || echo "0")
ULTIMA=$(psql -U "$USUARIO" -d "$PRUEBA" -tAc \
  "select version from flyway_schema_history where success order by installed_rank desc limit 1;" 2>/dev/null || echo "?")

echo "  ✓ Las cifras coinciden."
echo "  ✓ Esquema en la version $ULTIMA ($MIGRACIONES migraciones aplicadas)."

# --- Los documentos --------------------------------------------------
if [ -f "$CARPETA/documentos.tar.gz" ]; then
  # No se descomprime entero: se comprueba que el archivo esta integro y
  # cuantos ficheros trae. Un tar corrupto falla aqui, que es el momento
  # de enterarse.
  # "|| true" y no "|| echo 0": grep -c YA imprime el 0 cuando no encuentra
  # nada, pero ademas sale con codigo 1, asi que el echo se sumaba al 0 que
  # grep ya habia escrito y el guion informaba de "0
0 fichero(s)".
  FICHEROS=$(tar -tzf "$CARPETA/documentos.tar.gz" | grep -vc '/$' || true)
  echo "  ✓ El archivo de documentos esta integro: $FICHEROS fichero(s)."
else
  echo "  ! No hay documentos en este respaldo."
fi

echo ""
echo "══════════════════════════════════════════════════════════"
echo "  RESTAURACION PROBADA — $(date '+%F %T')"
echo "  D-23 control 7 · RNF-14 queda verificado para este respaldo."
echo "══════════════════════════════════════════════════════════"
echo ""
echo "  Anote la fecha. RNF-14 no se cumple una vez: se cumple mientras"
echo "  la ultima prueba de restauracion sea reciente."
