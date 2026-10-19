param(
    [int]$Port = 8888,
    [string]$DbUser = 'root',
    [string]$DbUrl = 'jdbc:mysql://localhost:3306/bacay_online?connectTimeout=5000&socketTimeout=10000&connectionTimeZone=UTC&allowPublicKeyRetrieval=true'
)
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$env:BACAY_DB_USER = $DbUser
$env:BACAY_DB_URL = $DbUrl
if ($null -eq $env:BACAY_DB_PASSWORD) {
    $secure = Read-Host 'Mat khau MySQL (co the de trong)' -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { $env:BACAY_DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}
$env:BACAY_DATA_DIR = Join-Path $PSScriptRoot 'data/pending-results'
& java '-Dfile.encoding=UTF-8' -cp 'server.jar;shared.jar;lib/*' com.game3cay.server.ServerApplication $Port
if ($LASTEXITCODE -ne 0) { throw 'Server chua chay. Kiem tra JDK 21, MySQL, schema va mat khau o log phia tren.' }
