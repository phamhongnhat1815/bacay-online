param(
    [int]$Port = 8888,
    [string]$DbUser = 'root',
    [string]$DbPassword = $null,
    [string]$DbUrl = 'jdbc:mysql://localhost:3306/bacay_online?connectTimeout=5000&socketTimeout=10000&connectionTimeZone=UTC&allowPublicKeyRetrieval=true'
)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::InputEncoding = [System.Text.Encoding]::UTF8
Set-Location $PSScriptRoot
$env:BACAY_DB_USER = $DbUser
$env:BACAY_DB_URL = $DbUrl
if ($DbPassword) {
    $env:BACAY_DB_PASSWORD = $DbPassword
}
if ($null -eq $env:BACAY_DB_PASSWORD -or $env:BACAY_DB_PASSWORD -eq '') {
    $env:BACAY_DB_PASSWORD = [System.Environment]::GetEnvironmentVariable('BACAY_DB_PASSWORD', 'User')
}
if ($null -eq $env:BACAY_DB_PASSWORD -or $env:BACAY_DB_PASSWORD -eq '') {
    $secure = Read-Host 'Mat khau MySQL (co the de trong)' -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try { $env:BACAY_DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
}
$env:BACAY_DATA_DIR = Join-Path $PSScriptRoot 'data/pending-results'
& java '-Dfile.encoding=UTF-8' -cp 'server.jar;shared.jar;lib/*' com.game3cay.server.ServerApplication $Port
if ($LASTEXITCODE -ne 0) { throw 'Server chua chay. Kiem tra JDK 21, MySQL, schema va mat khau o log phia tren.' }
