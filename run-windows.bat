@echo off
title Launching RMF Windows Client
cd /d "%~dp0rmf-windows\RMF.Windows"
echo ==============================================
echo  Launching RMF (Renly Management Platform) ...
echo ==============================================
dotnet run
pause
