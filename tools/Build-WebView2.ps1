param([string]$SdkVersion = '1.0.3856.49')
$ErrorActionPreference = 'Stop'
$repository = Split-Path -Parent $PSScriptRoot
$output = Join-Path $repository 'target/webview2-native'
New-Item -ItemType Directory -Path $output -Force | Out-Null
$sdk = Join-Path $output "sdk-$SdkVersion"
if (-not (Test-Path -LiteralPath (Join-Path $sdk 'build/native/include/WebView2.h'))) {
    $archive = Join-Path $output "sdk-$SdkVersion.zip"
    Invoke-WebRequest "https://api.nuget.org/v3-flatcontainer/microsoft.web.webview2/$SdkVersion/microsoft.web.webview2.$SdkVersion.nupkg" -OutFile $archive
    Expand-Archive -LiteralPath $archive -DestinationPath $sdk -Force
}
$vswhere = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio/Installer/vswhere.exe'
$installation = & $vswhere -latest -products '*' -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
if (-not $installation) { throw 'Install Visual Studio C++ Build Tools (x64) before building this native adapter.' }
$vcvars = Join-Path $installation 'VC/Auxiliary/Build/vcvars64.bat'
$source = Join-Path $repository 'quizforge-desktop-app/native/webview2/quizforge_webview2.cpp'
$include = Join-Path $sdk 'build/native/include'
$library = Join-Path $sdk 'build/native/x64/WebView2LoaderStatic.lib'
$dll = Join-Path $output 'quizforge_webview2.dll'
$obj = Join-Path $output 'quizforge_webview2.obj'
$importLibrary = Join-Path $output 'quizforge_webview2.lib'
# Paths are quoted arguments; no user-provided source or command is evaluated.
$build = Join-Path $output 'build.cmd'
@"
@echo off
call "$vcvars" >nul
cl /nologo /std:c++20 /EHsc /O2 /MT /LD /DUNICODE /D_UNICODE /I"$include" "$source" /Fo"$obj" /link "$library" ole32.lib oleaut32.lib user32.lib shlwapi.lib advapi32.lib version.lib /OUT:"$dll" /IMPLIB:"$importLibrary"
"@ | Set-Content -LiteralPath $build -Encoding ascii
& $env:ComSpec /d /c $build
if ($LASTEXITCODE -ne 0) { throw 'WebView2 native adapter build failed.' }
Write-Output "WebView2 adapter: $dll"
