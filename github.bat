@echo off
chcp 65001 >nul
title Subir Projeto Caixa Forte para GitHub
color 0A

echo ============================================
echo     📦 Enviando projeto para o GitHub
echo     Repositorio: luzistartrec/mytwoapp
echo ============================================
echo.

:: Caminho atual
cd /d "%~dp0"

:: Configurar nome e email (caso ainda nao tenha feito)
git config --global user.name "luziamorais"
git config --global user.email "lusalujo@gmail.com"

:: Inicializa e prepara commit
git init
git add .
git commit -m "Atualizacao automatica - Caixa Forte"

:: Define branch principal
git branch -M main

:: Remove e adiciona o remoto (garante que está certo)
git remote remove origin >nul 2>&1
git remote add origin https://github.com/luzistartrec/mytwoapp.git

:: Envia para o GitHub
echo.
echo 🔄 Enviando arquivos para o GitHub...
git push -u origin main

if %errorlevel% neq 0 (
    echo.
    echo ❌ Ocorreu um erro ao enviar. Verifique sua conexao ou login no GitHub.
) else (
    echo.
    echo ✅ Projeto Caixa Forte enviado com sucesso!
    echo 🌐 Verifique em: https://github.com/luzistartrec/mytwoapp
)

echo.
pause
