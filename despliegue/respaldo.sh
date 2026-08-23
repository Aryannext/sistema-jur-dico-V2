#!/bin/bash
# Respaldo diario de Iuris. D-23 control 7 · RNF-14.
#
# Respalda LAS DOS COSAS, y esa es la parte que se olvida: la base de datos
# y el almacen de documentos. Un volcado de la base sin los documentos deja
# expedientes que apuntan a ficheros que ya no existen — y no se nota hasta
# que alguien intenta descargar un poder.
#
# Los documentos ya estan cifrados en disco (RNF-04), asi que el respaldo
# los copia tal cual. Sin la clave de cifrado no sirven de nada: GUARDE LA
# CLAVE APARTE Y FUERA DE ESTE SERVIDOR. Un respaldo perfecto de documentos
# cuya clave se perdio con el servidor es un respaldo de ruido.
#
# Uso:  ./respaldo.sh [directorio-destino]
# Cron: 0 2 * * *  /opt/iuris/respaldo.sh >> /var/log/iuris-respaldo.log 2>&1

set -euo pipefail

DESTINO="${1:-/var/respaldos/iuris}"
RETENER_DIAS="${SGPJ_RETENCION_DIAS:-30}"
BD="${SGPJ_BD_NOMBRE:-iuris_sgpj}"
USUARIO="${SGPJ_BD_USUARIO:?falta SGPJ_BD_USUARIO}"
DOCUMENTOS="${SGPJ_DOCUMENTOS_DIR:-/opt/iuris/almacen-documentos}"

MARCA=$(date +%Y%m%d-%H%M%S)
CARPETA="$DESTINO/$MARCA"
mkdir -p "$CARPETA"

echo "[$(date '+%F %T')] Respaldo de Iuris -> $CARPETA"

# --- La base ---------------------------------------------------------
# Formato custom (-Fc), no SQL plano: permite restaurar tablas sueltas y
# se comprime solo. Restaurar el volcado entero cuando lo unico que se
# perdio fue una tabla es tentar a la suerte.
echo "  · base de datos..."
pg_dump -U "$USUARIO" -d "$BD" -Fc -f "$CARPETA/base.dump"

# --- Los documentos --------------------------------------------------
if [ -d "$DOCUMENTOS" ]; then
  echo "  · documentos ($(ls -1 "$DOCUMENTOS" | wc -l) ficheros)..."
  tar -czf "$CARPETA/documentos.tar.gz" -C "$(dirname "$DOCUMENTOS")" "$(basename "$DOCUMENTOS")"
else
  echo "  ! AVISO: no existe $DOCUMENTOS. El respaldo NO incluye documentos."
fi

# --- Que hay dentro --------------------------------------------------
# Se anotan las cifras para poder comparar contra la restauracion. Sin
# esto, "restaure y arranco" no dice si llego todo.
psql -U "$USUARIO" -d "$BD" -tAc "
  select 'despachos='||(select count(*) from despacho)
     ||' usuarios='||(select count(*) from usuario)
     ||' procesos='||(select count(*) from proceso)
     ||' piezas='||(select count(*) from pieza)
     ||' alertas='||(select count(*) from alerta)
     ||' bitacora='||(select count(*) from asiento_bitacora);" > "$CARPETA/contenido.txt"

echo "  · contenido: $(cat "$CARPETA/contenido.txt")"

# --- Limpieza --------------------------------------------------------
find "$DESTINO" -mindepth 1 -maxdepth 1 -type d -mtime +"$RETENER_DIAS" -exec rm -rf {} + 2>/dev/null || true

TAMANO=$(du -sh "$CARPETA" | cut -f1)
echo "[$(date '+%F %T')] Listo: $TAMANO en $CARPETA"
echo ""
echo "  RECUERDE: este respaldo no vale hasta que se haya RESTAURADO una vez."
echo "  Pruébelo con:  ./restaurar-prueba.sh $CARPETA"
