@echo off
setlocal enabledelayedexpansion
REM Starts the whole DocuMind stack: Docker infra (Postgres/pgvector, MinIO,
REM ClamAV), the Spring Boot backend, and the Angular frontend dev server.
REM Run stop.bat to tear everything back down.

cd /d "%~dp0"

echo ============================================
echo  DocuMind AI Portal - starting full stack
echo ============================================

if not exist "infra\.env" (
    echo.
    echo [ERROR] infra\.env not found.
    echo Copy infra\.env.example to infra\.env and fill in real values first.
    exit /b 1
)

echo.
echo [1/4] Starting infra containers (postgres, minio, clamav)...
pushd infra
docker compose up -d postgres minio clamav
if errorlevel 1 (
    echo [ERROR] docker compose failed to start infra containers.
    popd
    exit /b 1
)
popd

echo.
echo [2/4] Waiting for Postgres to report healthy...
set "PG_READY="
for /l %%I in (1,1,30) do (
    if not defined PG_READY (
        for /f "delims=" %%S in ('docker inspect -f "{{.State.Health.Status}}" documind-postgres-1 2^>nul') do (
            if "%%S"=="healthy" set "PG_READY=1"
        )
        if not defined PG_READY timeout /t 2 /nobreak >nul
    )
)
if not defined PG_READY (
    echo [WARN] Postgres did not report healthy in time - continuing anyway.
) else (
    echo       Postgres is healthy.
)

REM Locate a usable mvn. Prefers mvn on PATH; falls back to MVN_CMD if set
REM (e.g. set MVN_CMD=C:\path\to\mvn.cmd before calling this script on a
REM machine where Maven isn't on PATH).
set "MVN_EXE=mvn"
where mvn >nul 2>nul
if errorlevel 1 (
    if defined MVN_CMD (
        set "MVN_EXE=%MVN_CMD%"
    ) else (
        echo.
        echo [ERROR] mvn not found on PATH and MVN_CMD is not set.
        echo Either add Maven to PATH, or set MVN_CMD to the full path of mvn.cmd.
        exit /b 1
    )
)

echo.
echo [3/4] Starting backend (Spring Boot, local profile)...
del /q "backend\backend-pid.txt" >nul 2>nul
powershell -NoProfile -Command ^
    "$mvn = '%MVN_EXE%'; if ($mvn -eq 'mvn') { $resolved = (Get-Command mvn.cmd -ErrorAction SilentlyContinue).Source; if ($resolved) { $mvn = $resolved } }; $p = Start-Process -FilePath $mvn -ArgumentList 'spring-boot:run', '-Dspring-boot.run.profiles=local' -WorkingDirectory 'backend' -WindowStyle Minimized -RedirectStandardOutput 'backend\backend-run.log' -RedirectStandardError 'backend\backend-run.err.log' -PassThru; $p.Id | Out-File -FilePath 'backend\backend-pid.txt' -Encoding ascii -NoNewline"
if exist "backend\backend-pid.txt" (
    set /p BACKEND_PID=<backend\backend-pid.txt
    echo       backend PID: !BACKEND_PID!
) else (
    echo       [WARN] could not capture backend PID - check backend\backend-run.log
)

echo.
echo [4/4] Starting frontend (Angular dev server)...
del /q "frontend\frontend-pid.txt" >nul 2>nul
powershell -NoProfile -Command ^
    "$npm = (Get-Command npm.cmd -ErrorAction SilentlyContinue).Source; if (-not $npm) { $npm = 'npm.cmd' }; $p = Start-Process -FilePath $npm -ArgumentList 'start' -WorkingDirectory 'frontend' -WindowStyle Minimized -RedirectStandardOutput 'frontend\frontend-run.log' -RedirectStandardError 'frontend\frontend-run.err.log' -PassThru; $p.Id | Out-File -FilePath 'frontend\frontend-pid.txt' -Encoding ascii -NoNewline"
if exist "frontend\frontend-pid.txt" (
    set /p FRONTEND_PID=<frontend\frontend-pid.txt
    echo       frontend PID: !FRONTEND_PID!
) else (
    echo       [WARN] could not capture frontend PID - check frontend\frontend-run.log
)

echo.
echo ============================================
echo  Stack starting up:
echo    Backend:  http://localhost:8080
echo    Frontend: http://localhost:4200
echo    MinIO console: http://localhost:9001
echo  Logs: backend\backend-run.log, frontend\frontend-run.log
echo  Run stop.bat to shut everything down.
echo ============================================

endlocal
