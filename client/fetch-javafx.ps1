# Downloads the JavaFX jars into lib (only needed once)
$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"
$version = "21.0.13"
$files = [ordered]@{
    "base" = "7a9a3dd9e7ab445ae751f50d9feddd550056ef5c267faf145512686a58f61651"
    "graphics" = "42d59a74a1d0fcfae03ecd440db185e76f08b7a65dfc870a0a7eb5bad6d8f2d7"
    "controls" = "2fbf566741cba2de8dd886209cd8ba497995b51d2371441fc4d17e0a22ba6e03"
}

$lib = Join-Path $PSScriptRoot "lib"
New-Item -ItemType Directory -Force $lib | Out-Null
# needed for https on older PowerShell
[Net.ServicePointManager]::SecurityProtocol = `
    [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12

foreach ($module in $files.Keys) {
    $name = "javafx-$module-$version-win.jar"
    $path = Join-Path $lib $name
    if ((Test-Path $path) -and (Get-FileHash $path -Algorithm SHA256).Hash -eq $files[$module]) {
        continue
    }
    Write-Host "Downloading $name..."
    $url = "https://repo1.maven.org/maven2/org/openjfx/javafx-$module/$version/$name"
    $partial = "$path.part"
    Invoke-WebRequest -Uri $url -OutFile $partial -UseBasicParsing
    if ((Get-FileHash $partial -Algorithm SHA256).Hash -ne $files[$module]) {
        Remove-Item $partial
        throw "$name does not match its expected hash, so it was not kept"
    }
    Move-Item -Force $partial $path
}
