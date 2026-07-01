# Xiaohongshu MCP Notes

## Current Setup

- Source directory: `C:\Users\Administrator\GitHub\xiaohongshu-mcp`
- Windows binaries: `C:\Users\Administrator\GitHub\xiaohongshu-mcp\bin`
- Login tool: `xiaohongshu-login-windows-amd64.exe`
- MCP server: `xiaohongshu-mcp-windows-amd64.exe`
- MCP endpoint: `http://localhost:18060/mcp`
- Cookie file path after the successful login on 2026-07-01:
  `C:\Users\Administrator\GitHub\xiaohongshu-mcp\bin\cookies.json`

Do not commit or copy the cookie file into this repo. It is a private login token.

## Launch Commands

The bundled Chromium download failed on this Windows machine with `Access is denied`, so use the installed Edge binary.

Login:

```powershell
cd C:\Users\Administrator\GitHub\xiaohongshu-mcp\bin
.\xiaohongshu-login-windows-amd64.exe -bin "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
```

Start the MCP server:

```powershell
cd C:\Users\Administrator\GitHub\xiaohongshu-mcp\bin
$env:COOKIES_PATH = "cookies.json"
.\xiaohongshu-mcp-windows-amd64.exe -bin "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
```

## Usage Policy

Use this MCP only for low-frequency reading and searching public Xiaohongshu content, mainly to research study methods for law master entrance exam planning.

Do not use the account for:

- liking
- favoriting or unfavoriting
- commenting or replying
- following
- publishing text/image/video posts
- bulk scraping or high-frequency browsing

The account is the user's secondary account, but avoiding platform risk is still important.

## Verified State

On 2026-07-01, the local MCP service started successfully and `check_login_status` returned logged in.
