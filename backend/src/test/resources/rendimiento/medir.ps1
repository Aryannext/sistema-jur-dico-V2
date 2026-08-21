# Mide RNF-12 contra el volumen objetivo.
#
#   50 despachos - 500 procesos por despacho - 50 piezas por expediente
#
# DOS REGLAS QUE HACEN QUE ESTO MIDA ALGO:
#
# 1. Se reporta el PEOR tiempo, no la media. RNF-12 dice "deben responder
#    en menos de 3 segundos", no "de media": una consulta que tarda 6 s
#    una de cada diez veces incumple, y la media lo escondería.
#
# 2. Una consulta que devuelve VACIO no se reporta como aprobada, se
#    marca VACIA. En la primera pasada, tres de las doce consultas
#    devolvían cero filas -por un identificador de catálogo equivocado y
#    por audiencias sin sembrar- y salían con tiempos excelentes que no
#    significaban nada. Un 11 ms sobre cero filas no dice si el sistema
#    aguanta el volumen.
#
# Los identificadores de catálogo se CONSULTAN, no se escriben a mano:
# son de cada despacho (D-13) y adivinarlos fue exactamente el error de
# la primera pasada.

param(
    [string]$Base = "http://localhost:8081",
    [string]$Correo = "abogado1.d1@prueba.co",
    [string]$Clave = "clave-local-desarrollo",
    [int]$Repeticiones = 6
)

$ErrorActionPreference = "Stop"

# --- Entrar ---------------------------------------------------
$csrf = Invoke-WebRequest -Uri "$Base/api/autenticacion/csrf" -SessionVariable ses -UseBasicParsing
$tok = ($csrf.Content | ConvertFrom-Json).token
$cuerpo = '{"correo":"' + $Correo + '","contrasena":"' + $Clave + '"}'
Invoke-WebRequest -Uri "$Base/api/autenticacion/entrar" -Method Post -Body $cuerpo `
    -ContentType "application/json; charset=utf-8" `
    -Headers @{"X-XSRF-TOKEN" = $tok} -WebSession $ses -UseBasicParsing | Out-Null

function Json($ruta) {
    $bytes = (Invoke-WebRequest "$Base$ruta" -WebSession $ses -UseBasicParsing).RawContentStream.ToArray()
    return [System.Text.Encoding]::UTF8.GetString($bytes) | ConvertFrom-Json
}

# --- Identificadores reales de ESTE despacho ------------------
$estados = Json "/api/catalogos/ESTADO_PROCESAL/activos"
$tipos   = Json "/api/catalogos/TIPO_PROCESO/activos"
$juzgados = Json "/api/catalogos/JUZGADO/activos"

$estadoActivo = ($estados | Where-Object { $_.nombre -eq "Activo" }).id
$tipoCivil    = ($tipos   | Where-Object { $_.nombre -eq "Civil" }).id
$juzgadoUno   = $juzgados[0].id

$procesos = Json "/api/procesos?estadoId=$estadoActivo"
$procesoId = $procesos[0].id

Write-Output "Despacho del medidor: catálogos resueltos -> estado=$estadoActivo tipo=$tipoCivil juzgado=$juzgadoUno"
Write-Output "Proceso de muestra: $procesoId"

# --- Las consultas habituales ---------------------------------
# "Habituales" segun RNF-12: las que un abogado hace todos los días.
$consultas = @(
    @{ nombre = "Panel de vencimientos";           url = "/api/vencimientos" },
    @{ nombre = "Listar procesos (sin filtro)";    url = "/api/procesos" },
    @{ nombre = "Buscar radicado, fragmento ancho"; url = "/api/procesos?radicado=41001" },
    @{ nombre = "Buscar radicado, fragmento corto"; url = "/api/procesos?radicado=0012" },
    @{ nombre = "Buscar por estado";               url = "/api/procesos?estadoId=$estadoActivo" },
    @{ nombre = "Buscar combinando 3 filtros";     url = "/api/procesos?radicado=41001&estadoId=$estadoActivo&tipoProcesoId=$tipoCivil" },
    @{ nombre = "Buscar por juzgado";              url = "/api/procesos?juzgadoId=$juzgadoUno" },
    @{ nombre = "Expediente completo (50 piezas)"; url = "/api/procesos/$procesoId/expediente" },
    @{ nombre = "Reporte resumen";                 url = "/api/reportes/resumen" },
    @{ nombre = "Reporte por estado";              url = "/api/reportes/procesos-por-estado" },
    @{ nombre = "Reporte por tipo";                url = "/api/reportes/procesos-por-tipo" },
    @{ nombre = "Carga por abogado";               url = "/api/reportes/carga-por-abogado" },
    @{ nombre = "Calendario de audiencias";        url = "/api/calendario" },
    @{ nombre = "Clientes del despacho";           url = "/api/clientes" },
    @{ nombre = "Catálogo de juzgados";            url = "/api/catalogos/JUZGADO" },
    @{ nombre = "Historial de alertas";            url = "/api/alertas/programadas" }
)

$LIMITE_MS = 3000
$resultados = @()

foreach ($c in $consultas) {
    $tiempos = @()
    $calentamiento = 0
    $bytes = 0
    $filas = 0

    for ($i = 0; $i -le $Repeticiones; $i++) {
        $reloj = [System.Diagnostics.Stopwatch]::StartNew()
        $r = Invoke-WebRequest "$Base$($c.url)" -WebSession $ses -UseBasicParsing
        $reloj.Stop()

        if ($i -eq 0) {
            $calentamiento = [math]::Round($reloj.Elapsed.TotalMilliseconds)
            $bytes = $r.RawContentLength
            # Cuantas filas trajo de verdad. Es lo que distingue una
            # consulta rapida de una consulta que no encontro nada.
            $texto = [System.Text.Encoding]::UTF8.GetString($r.RawContentStream.ToArray())
            $filas = ([regex]::Matches($texto, '"id"\s*:')).Count
            if ($filas -eq 0 -and $texto.Length -gt 40) { $filas = 1 }  # objeto suelto, como el resumen
        } else {
            $tiempos += $reloj.Elapsed.TotalMilliseconds
        }
    }

    $peor = [math]::Round(($tiempos | Measure-Object -Maximum).Maximum)
    $mediana = [math]::Round(($tiempos | Sort-Object)[[math]::Floor($tiempos.Count / 2)])

    $veredicto = if ($filas -eq 0) { "VACIA" }
                 elseif ($peor -lt $LIMITE_MS) { "si" }
                 else { "NO" }

    $resultados += [PSCustomObject]@{
        Consulta      = $c.nombre
        Filas         = $filas
        Calentamiento = $calentamiento
        Mediana       = $mediana
        Peor          = $peor
        Cumple        = $veredicto
        KB            = [math]::Round($bytes / 1024, 1)
    }
}

Write-Output ""
Write-Output "RNF-12 - limite: menos de $LIMITE_MS ms. Tiempos en milisegundos."
Write-Output "$Repeticiones mediciones por consulta; el calentamiento se reporta aparte y no cuenta."
Write-Output ""
$resultados | Format-Table -AutoSize

$vacias   = @($resultados | Where-Object { $_.Cumple -eq "VACIA" })
$incumplen = @($resultados | Where-Object { $_.Cumple -eq "NO" })

Write-Output ""
if ($vacias.Count -gt 0) {
    Write-Output "AVISO: $($vacias.Count) consulta(s) devolvieron CERO filas. Su tiempo no mide nada:"
    $vacias | ForEach-Object { Write-Output "  - $($_.Consulta)" }
    Write-Output ""
}
if ($incumplen.Count -eq 0 -and $vacias.Count -eq 0) {
    Write-Output "RESULTADO: las $($resultados.Count) consultas devuelven datos y cumplen RNF-12 en su PEOR tiempo."
} elseif ($incumplen.Count -gt 0) {
    Write-Output "RESULTADO: $($incumplen.Count) consulta(s) INCUMPLEN RNF-12:"
    $incumplen | ForEach-Object { Write-Output "  - $($_.Consulta): $($_.Peor) ms sobre $($_.Filas) fila(s)" }
}
