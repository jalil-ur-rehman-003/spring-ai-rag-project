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

REM Load infra\.env (KEY=value per line) into this process's environment so
REM the backend, launched below with mvn as a child process, inherits
REM DB_PASSWORD/JWT_SIGNING_KEY/ANTHROPIC_API_KEY/VOYAGE_API_KEY etc. docker
REM compose reads infra\.env on its own; a plain `mvn spring-boot:run` does
REM not, so without this the backend starts with those variables empty.
REM Values may be wrapped in double quotes (e.g. VOYAGE_API_KEY="pa-...") -
REM those quotes must be stripped, or the literal quote characters end up
REM as part of the key/secret sent to the provider, which then rejects it
REM as invalid (401) even though the underlying value is correct. Batch's
REM own string handling can't reliably strip embedded quote characters, so
REM PowerShell does the parsing and emits a `set "KEY=value"` script that
REM this process then calls to populate its own environment.
powershell -NoProfile -Command ^
    "Get-Content 'infra\.env' | Where-Object { $_ -match '^[^#=]+=' } | ForEach-Object { $k,$v = $_.Split('=',2); $v = $v.Trim(); if ($v.Length -ge 2 -and $v.StartsWith('\"') -and $v.EndsWith('\"')) { $v = $v.Substring(1, $v.Length - 2) }; \"set `\"$k=$v`\"\" } | Set-Content -Encoding ascii 'backend\.env-vars.cmd'"
call "backend\.env-vars.cmd"
del /q "backend\.env-vars.cmd" >nul 2>nul

REM docker-compose.yml maps MINIO_ROOT_USER/PASSWORD -> S3_ACCESS_KEY/
REM S3_SECRET_KEY for its containerized backend service only; a locally-run
REM backend needs the same mapping done here, plus S3_ENDPOINT pointed at
REM localhost (not the "minio" hostname that only resolves inside Docker's
REM network).
if defined MINIO_ROOT_USER set "S3_ACCESS_KEY=%MINIO_ROOT_USER%"
if defined MINIO_ROOT_PASSWORD set "S3_SECRET_KEY=%MINIO_ROOT_PASSWORD%"
set "S3_ENDPOINT=http://localhost:9000"

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
        for /f "delims=" %%S in ('docker inspect -f "{{.State.Health.Status}}" documind-postgres-1 ^<nul 2^>nul') do (
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

REM Locate a usable mvn. Preference order: MVN_CMD env var (if set) -> mvn on
REM PATH -> a well-known local install location (this repo has been run from
REM a machine where Maven wasn't on PATH; see README). Backend startup is
REM skipped (not fatal) if none of these resolve, so the frontend still comes
REM up and you get a clear message instead of the whole script aborting.
set "MVN_EXE="
if defined MVN_CMD set "MVN_EXE=%MVN_CMD%"

if not defined MVN_EXE (
    where mvn >nul 2>nul
    if not errorlevel 1 set "MVN_EXE=mvn"
)

if not defined MVN_EXE (
    for /f "delims=" %%D in ('powershell -NoProfile -Command "(Get-ChildItem -Path \"$env:LOCALAPPDATA\maven\" -Recurse -Filter mvn.cmd -ErrorAction SilentlyContinue | Select-Object -First 1 -ExpandProperty FullName)"') do set "MVN_EXE=%%D"
)

echo.
if defined MVN_EXE goto :start_backend
echo [3/4] Skipping backend - mvn not found on PATH, MVN_CMD not set, and no
echo        local Maven install auto-detected under %%LOCALAPPDATA%%\maven.
echo        Set MVN_CMD to the full path of mvn.cmd and re-run start.bat,
echo        or start the backend manually - cd backend, then mvn spring-boot:run -Dspring-boot.run.profiles=local
goto :start_frontend

:start_backend
echo [3/4] Starting backend - Spring Boot, local profile...
del /q "backend\backend-pid.txt" >nul 2>nul
powershell -NoProfile -Command ^
    "$p = Start-Process -FilePath '%MVN_EXE%' -ArgumentList 'spring-boot:run', '-Dspring-boot.run.profiles=local' -WorkingDirectory 'backend' -WindowStyle Minimized -RedirectStandardOutput 'backend\backend-run.log' -RedirectStandardError 'backend\backend-run.err.log' -PassThru; $p.Id | Out-File -FilePath 'backend\backend-pid.txt' -Encoding ascii -NoNewline"
if exist "backend\backend-pid.txt" (
    set /p BACKEND_PID=<backend\backend-pid.txt
    echo       backend PID: !BACKEND_PID!
) else (
    echo       [WARN] could not capture backend PID - check backend\backend-run.log
)

:start_frontend

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
