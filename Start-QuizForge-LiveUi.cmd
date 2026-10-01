@echo off
setlocal
call "%~dp0Start-QuizForge.cmd" -LiveUi %*
exit /b %ERRORLEVEL%
