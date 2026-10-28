$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Push-Location $root
try {
    if ((Get-FileHash .\database\setup.sql).Hash -ne (Get-FileHash .\server\src\main\resources\schema.sql).Hash) {
        throw 'Schema resource khong dong bo database/setup.sql; cap nhat ca hai truoc khi dong goi.'
    }
    & java -version
    if ($LASTEXITCODE -ne 0) { throw 'Can cai JDK 21 va them Java vao PATH.' }
    & .\server\mvnw.cmd -f .\pom.xml clean install
    if ($LASTEXITCODE -ne 0) { throw 'Build/test that bai; chua dong goi.' }
    & .\server\mvnw.cmd -f .\server\pom.xml dependency:copy-dependencies '-DincludeScope=runtime'
    if ($LASTEXITCODE -ne 0) { throw 'Khong chuan bi duoc JDBC dependencies.' }
    $dist = Join-Path $root 'dist'
    New-Item -ItemType Directory -Force $dist, (Join-Path $dist 'lib'), (Join-Path $dist 'database') | Out-Null
    Copy-Item .\server\target\server-1.0-SNAPSHOT.jar (Join-Path $dist 'server.jar') -Force
    Copy-Item .\client\target\client-1.0-SNAPSHOT.jar (Join-Path $dist 'client.jar') -Force
    Copy-Item .\admin\target\admin-1.0-SNAPSHOT.jar (Join-Path $dist 'admin.jar') -Force
    Copy-Item .\shared\target\shared-1.0-SNAPSHOT.jar (Join-Path $dist 'shared.jar') -Force
    # dist/lib is generated output: remove JARs no longer in the runtime dependency set.
    $runtimeJars = @(Get-ChildItem -LiteralPath .\server\target\dependency -Filter '*.jar' -File | ForEach-Object { $_.Name })
    $runtimeLib = [System.IO.Path]::GetFullPath((Join-Path $dist 'lib'))
    foreach ($previousJar in Get-ChildItem -LiteralPath $runtimeLib -Filter '*.jar' -File) {
        if ($previousJar.Name -notin $runtimeJars) {
            $previousPath = [System.IO.Path]::GetFullPath($previousJar.FullName)
            if ([System.IO.Path]::GetDirectoryName($previousPath) -ne $runtimeLib) { throw 'Duong dan runtime JAR khong hop le.' }
            Remove-Item -LiteralPath $previousPath -Force
        }
    }
    Copy-Item .\server\target\dependency\*.jar (Join-Path $dist 'lib') -Force
    Copy-Item .\database\setup.sql (Join-Path $dist 'database') -Force
    Copy-Item .\scripts\Start-Server.ps1,.\scripts\Start-Client.cmd,.\scripts\Start-Server.cmd,.\scripts\Start-Admin.cmd $dist -Force
    Copy-Item .\README.md,.\Game_Rules.md $dist -Force
    Write-Host 'BUILD OK. Mo Start-Server.cmd / Start-Client.cmd / Start-Admin.cmd de su dung.'
} finally { Pop-Location }
