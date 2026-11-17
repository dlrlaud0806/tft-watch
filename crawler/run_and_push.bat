@echo off
chcp 65001 > nul
set PYTHONUTF8=1
REM Crawl lolchess.gg meta -> docs/decks.json -> git push (served by GitHub Pages)
cd /d "%~dp0"
if exist ".venv\Scripts\python.exe" (
  ".venv\Scripts\python.exe" crawl.py
) else (
  python crawl.py
)
if errorlevel 1 (
  echo [run] crawl failed - skip push
  exit /b 1
)
cd ..
git add docs/decks.json
git diff --cached --quiet && (echo [run] no changes & exit /b 0)
git commit -m "update decks"
git push
