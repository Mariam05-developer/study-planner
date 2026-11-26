# Compiles the app into build\studyplanner-gui.jar

$ErrorActionPreference = "Stop"

Push-Location $PSScriptRoot
try {
    # sometimes jar isn't on the PATH even though java is
    $jar = (Get-Command jar -ErrorAction SilentlyContinue).Source
    if (-not $jar) {
        $settings = cmd /c "java -XshowSettings:properties -version 2>&1"
        $line = $settings | Where-Object { $_ -match '^\s*java\.home = (.+)$' } | Select-Object -First 1
        if ($line -match '= (.+)$') { $jar = Join-Path $Matches[1].Trim() "bin\jar.exe" }
        if (-not $jar -or -not (Test-Path $jar)) { throw "Cannot find 'jar'. Install a JDK (17 or newer)." }
    }

    & (Join-Path $PSScriptRoot "fetch-javafx.ps1")
    if (Test-Path build) { Remove-Item -Recurse -Force build }
    $sources = Get-ChildItem src, gui -Recurse -Filter *.java | ForEach-Object FullName
    javac --release 17 -encoding utf-8 --module-path lib --add-modules javafx.controls -d build\classes $sources
    if ($LASTEXITCODE -ne 0) { throw "Compilation failed" }
    Copy-Item gui\studyplanner\gui\app.css build\classes\studyplanner\gui
    & $jar --create --file build\studyplanner-gui.jar --main-class studyplanner.gui.GuiApp -C build\classes .
    if ($LASTEXITCODE -ne 0) { throw "Packaging failed" }
    Write-Host "Done"
}
finally {
    Pop-Location
}
