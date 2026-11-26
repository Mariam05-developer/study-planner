# Starts the backend. API docs are at http://127.0.0.1:8000/docs
param([int]$Port = 8000)

$ErrorActionPreference = "Stop"
$backend = Join-Path $PSScriptRoot "backend"
$python = Join-Path $backend ".venv\Scripts\python.exe"
$marker = Join-Path $backend ".venv\installed.txt"

if (-not (Test-Path $python)) {
    Write-Host "Creating a virtual environment..."
    if (Get-Command py -ErrorAction SilentlyContinue) {
        py -3 -m venv (Join-Path $backend ".venv")
    }
    else {
        python -m venv (Join-Path $backend ".venv")
    }
    if ($LASTEXITCODE -ne 0) { throw "Could not create the virtual environment" }
}

# only create installed.txt if pip worked
if (-not (Test-Path $marker)) {
    Write-Host "Installing dependencies..."
    & $python -m pip install --quiet -r (Join-Path $backend "requirements.txt")
    if ($LASTEXITCODE -ne 0) { throw "Installing dependencies failed" }
    Set-Content -Path $marker -Value "ok"
}

& $python -m uvicorn app.main:create_app --factory --app-dir $backend --host 127.0.0.1 --port $Port
