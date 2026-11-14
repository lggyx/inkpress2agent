@echo off
cd /d D:\inkpress2agent\distributor
set "INKPRESS_PASSWORD=EvoDemo2026!"
node --input-type=module -e "import './node_modules/tsx/dist/cli.mjs';" 2>nul
node_modules\.bin\tsx src\cli.ts login --email "evo-demo@local.dev" --password "%INKPRESS_PASSWORD%" --server "http://117.72.154.28" --name "inkpress-distributor"
