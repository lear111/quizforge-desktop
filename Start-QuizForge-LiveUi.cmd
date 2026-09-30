@echo off
setlocal
title QuizForge Live UI
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Start-QuizForge.ps1" -LiveUi
set "quizforgeLaunchExit=%ERRORLEVEL%"
if not "%quizforgeLaunchExit%"=="0" (
    echo.
    echo QuizForge Live UI could not start. See the error above.
    pause
)
exit /b %quizforgeLaunchExit%
