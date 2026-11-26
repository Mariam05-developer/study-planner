# Starts the app (builds it first if needed).
# To use an account, start run-server.ps1 first.
$ErrorActionPreference = "Stop"
$client = Join-Path $PSScriptRoot "client"
$app = Join-Path $client "build\studyplanner-gui.jar"
if (-not (Test-Path $app)) {
    & (Join-Path $client "build.ps1")
}
$ErrorActionPreference = "Continue"
java --module-path (Join-Path $client "lib") --add-modules javafx.controls `
    --enable-native-access=javafx.graphics -cp $app studyplanner.gui.GuiApp
exit $LASTEXITCODE
