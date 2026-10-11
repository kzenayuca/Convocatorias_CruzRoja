@echo off
call mvnw.cmd spring-boot:run
if errorlevel 1 pause
