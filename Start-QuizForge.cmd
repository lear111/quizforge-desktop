@echo off
setlocal
title QuizForge
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\Start-QuizForge.ps1" %*
set "quizforgeLaunchExit=%ERRORLEVEL%"
if not "%quizforgeLaunchExit%"=="0" (
    echo.
    echo QuizForge could not start. See the error above.
    pause
)
exit /b %quizforgeLaunchExit%
