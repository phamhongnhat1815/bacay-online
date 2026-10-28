param(
    [int]$Port = 0,
    [string]$DbUser,
    [AllowEmptyString()][string]$DbPassword,
    [string]$DbUrl,
    [string]$DbHost,
    [int]$DbPort = 0,
    [string]$DbName
)
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::InputEncoding = [System.Text.Encoding]::UTF8
Set-Location $PSScriptRoot

function Resolve-Text([string]$ExplicitValue, [string]$EnvironmentName, [string]$Fallback) {
    if (-not [string]::IsNullOrWhiteSpace($ExplicitValue)) { return $ExplicitValue }
    $configured = [Environment]::GetEnvironmentVariable($EnvironmentName)
    if (-not [string]::IsNullOrWhiteSpace($configured)) { return $configured }
    return $Fallback
}
if ($Port -eq 0) { $Port = [int](Resolve-Text '' 'BACAY_SERVER_PORT' '8888') }
if ($Port -lt 1 -or $Port -gt 65535) { throw 'Port phai tu 1 den 65535.' }
$env:BACAY_DB_USER = Resolve-Text $DbUser 'BACAY_DB_USER' 'root'
$componentOverride = $PSBoundParameters.ContainsKey('DbHost') -or $PSBoundParameters.ContainsKey('DbPort') -or $PSBoundParameters.ContainsKey('DbName')
if ([string]::IsNullOrWhiteSpace($DbUrl) -and -not $componentOverride) {
    $DbUrl = [Environment]::GetEnvironmentVariable('BACAY_DB_URL')
}
if ([string]::IsNullOrWhiteSpace($DbUrl)) {
    $resolvedHost = Resolve-Text $DbHost 'BACAY_DB_HOST' 'localhost'
    $resolvedName = Resolve-Text $DbName 'BACAY_DB_NAME' 'bacay_online'
    if ($DbPort -eq 0) { $DbPort = [int](Resolve-Text '' 'BACAY_DB_PORT' '3306') }
    if ($resolvedHost -notmatch '^[A-Za-z0-9.\-]+$' -or $resolvedName -notmatch '^[A-Za-z0-9_]+$' -or $DbPort -lt 1 -or $DbPort -gt 65535) {
        throw 'DbHost/DbName/DbPort khong hop le; dung DbUrl neu can tuy bien nang cao.'
    }
    $DbUrl = "jdbc:mysql://${resolvedHost}:${DbPort}/${resolvedName}?connectTimeout=5000&socketTimeout=10000&connectionTimeZone=UTC&allowPublicKeyRetrieval=true"
}
$env:BACAY_DB_URL = $DbUrl
if ($PSBoundParameters.ContainsKey('DbPassword')) {
    $env:BACAY_DB_PASSWORD = $DbPassword
} elseif ($null -eq $env:BACAY_DB_PASSWORD) {
    $env:BACAY_DB_PASSWORD = [Environment]::GetEnvironmentVariable('BACAY_DB_PASSWORD', 'User')
    if ($null -eq $env:BACAY_DB_PASSWORD) {
        $secure = Read-Host 'Mat khau MySQL (co the de trong)' -AsSecureString
        $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
        try { $env:BACAY_DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
        finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
    }
}
if ([string]::IsNullOrWhiteSpace($env:BACAY_DATA_DIR)) {
    $env:BACAY_DATA_DIR = Join-Path $PSScriptRoot 'data/pending-results'
}
& java '-Dfile.encoding=UTF-8' -cp 'server.jar;shared.jar;lib/*' com.game3cay.server.ServerApplication $Port
if ($LASTEXITCODE -ne 0) { throw 'Server chua chay. Kiem tra JDK 21, MySQL, schema va cau hinh TLS trong log.' }
