@echo off
set "HERE=%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
 "$s=(New-Object -ComObject WScript.Shell).CreateShortcut([Environment]::GetFolderPath('Desktop')+'\Ghost Hunter v5.1.lnk');" ^
 "$s.TargetPath='%HERE%GhostHunter.exe';" ^
 "$s.WorkingDirectory='%HERE%';" ^
 "$s.Save()"
echo Done - look for Ghost Hunter v5.1 on your desktop.
pause
