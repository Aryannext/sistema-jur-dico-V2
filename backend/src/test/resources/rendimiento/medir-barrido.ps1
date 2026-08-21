# Mide el barrido del motor de alertas. RNF-11 - ADR-04.
#
# RNF-12 mide lo que hace un abogado. Esto mide lo que hace el sistema
# solo, que es lo que sostiene la razon de ser del producto: si el
# barrido se atrasa, la alerta de 24 h sale tarde y el termino se
# vence igual.
#
# Lo que de verdad se mide aqui no es un tiempo, es un TECHO. El motor
# toma 100 alertas por barrido (TAMANO_LOTE) y barre cada 5 minutos:
# por rapido que sea cada barrido, no puede pasar de 1.200 alertas por
# hora. Un barrido de 300 ms sobre un lote de 100 no dice nada si hay
# 16.000 esperando.

param(
    [string]$Base = "http://localhost:8081",
    [string]$Correo = "abogado1.d1@prueba.co",
    [string]$Clave = "clave-local-desarrollo",
    [int]$Barridos = 3
)

$ErrorActionPreference = "Stop"

$csrf = Invoke-WebRequest -Uri "$Base/api/autenticacion/csrf" -SessionVariable ses -UseBasicParsing
$tok = ($csrf.Content | ConvertFrom-Json).token
$cuerpo = '{"correo":"' + $Correo + '","contrasena":"' + $Clave + '"}'
Invoke-WebRequest -Uri "$Base/api/autenticacion/entrar" -Method Post -Body $cuerpo `
    -ContentType "application/json; charset=utf-8" `
    -Headers @{"X-XSRF-TOKEN" = $tok} -WebSession $ses -UseBasicParsing | Out-Null

$LOTE = 100
$INTERVALO_MIN = 5

Write-Output ""
Write-Output "=== Un barrido real, cronometrado ==="
$tiempos = @()
for ($i = 1; $i -le $Barridos; $i++) {
    $reloj = [System.Diagnostics.Stopwatch]::StartNew()
    Invoke-WebRequest "$Base/api/alertas/barrer" -Method Post `
        -Headers @{"X-XSRF-TOKEN" = $tok} -WebSession $ses -UseBasicParsing | Out-Null
    $reloj.Stop()
    $ms = [math]::Round($reloj.Elapsed.TotalMilliseconds)
    $tiempos += $ms
    Write-Output ("  barrido {0}: {1} ms" -f $i, $ms)
}
$peor = ($tiempos | Measure-Object -Maximum).Maximum
Write-Output ""
Write-Output "  peor barrido: $peor ms para un lote de $LOTE alertas"
Write-Output "  tolerancia RNF-11: 15 minutos = 900.000 ms"
