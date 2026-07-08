@echo off
setlocal enabledelayedexpansion
REM Stops everything start.bat started: backend, frontend, and infra containers.

cd /d "%~dp0"

echo ============================================
echo  DocuMind AI Portal - stopping full stack
echo ============================================

echo.
echo [1/3] Stopping backend...
set "BACKEND_PID="
if exist "backend\backend-pid.txt" set /p BACKEND_PID=<backend\backend-pid.txt
if not "!BACKEND_PID!"=="" (
    REM /t kills the whole process tree (mvn.cmd -> java), not just the
    REM launcher shell, since Start-Process's PID is the top of that tree.
    taskkill /pid !BACKEND_PID! /t /f >nul 2>nul
    echo       stop requested for backend process !BACKEND_PID!.
) else (
    echo       no backend PID recorded - skipping.
)
del /q "backend\backend-pid.txt" >nul 2>nul

echo.
echo [2/3] Stopping frontend...
set "FRONTEND_PID="
if exist "frontend\frontend-pid.txt" set /p FRONTEND_PID=<frontend\frontend-pid.txt
if not "!FRONTEND_PID!"=="" (
    taskkill /pid !FRONTEND_PID! /t /f >nul 2>nul
    echo       stop requested for frontend process !FRONTEND_PID!.
) else (
    echo       no frontend PID recorded - skipping.
)
del /q "frontend\frontend-pid.txt" >nul 2>nul

echo.
echo [3/3] Stopping infra containers (postgres, minio, clamav)...
pushd infra
docker compose stop postgres minio clamav
popd

echo.
echo ============================================
echo  Stack stopped.
echo ============================================

endlocal
