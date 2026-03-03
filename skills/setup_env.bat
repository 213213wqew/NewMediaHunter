@echo off
echo [*] Installing Python Agent Dependencies...
py -m pip install playwright
py -m playwright install chromium
echo [*] Done. You can now use the Python Agents in the Publish Center.
pause
