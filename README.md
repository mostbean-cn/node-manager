# Node Manager

> IntelliJ Platform plugin for managing Node.js versions directly within JetBrains IDEs.

## Features

- **Version Management** — View, install, switch, and uninstall Node.js versions without leaving the IDE
- **Multi-Manager Support** — Works with [nvm](https://github.com/nvm-sh/nvm) (Mac/Linux), [nvm-windows](https://github.com/coreybutler/nvm-windows), and [fnm](https://github.com/Schniz/fnm) (auto-detection)
- **Manager Dashboard** — Monitor manager status, enable/disable nvm, configure fnm shell integration
- **Conflict Detection** — Warning when both managers are actively controlling PATH
- **Status Bar Widget** — Always see the current Node.js version at a glance
- **Search & Filter** — Quickly find versions from the full Node.js release list
- **Mirror Support** — Configurable mirror sources (e.g. npmmirror) for faster downloads in China

## Requirements

- IntelliJ Platform IDE 2024.2+
- At least one of the following version managers installed:
  - [nvm](https://github.com/nvm-sh/nvm) (Mac / Linux)
  - [nvm-windows](https://github.com/coreybutler/nvm-windows) (Windows)
  - [fnm](https://github.com/Schniz/fnm) (Cross-platform)

## Installation

1. Open **Settings → Plugins → Marketplace**
2. Search for **Node Manager**
3. Click **Install** and restart the IDE

## Usage

### Versions Tab

![Versions Tab](docs/images/versions.png)

- View all locally installed Node.js versions
- Click **⊕** to install a new version (searchable list)
- Right-click a version to **switch** or **uninstall**
- Use the **Switch** dropdown to choose which manager to use

### Managers Tab

![Managers Tab](docs/images/managers.png)

- View the status of installed version managers
- **nvm**: Enable/Disable toggle (`nvm on`/`nvm off`)
- **fnm**: Setup/Remove shell integration (PowerShell profile)
- Conflict warning when both managers are active simultaneously

## Third-Party Tools

This plugin **does not bundle, include, or redistribute** any third-party software.
It integrates with version managers already installed on the user's system:

| Tool | License | Repository |
|------|---------|------------|
| [nvm](https://github.com/nvm-sh/nvm) | MIT | github.com/nvm-sh/nvm |
| [nvm-windows](https://github.com/coreybutler/nvm-windows) | MIT | github.com/coreybutler/nvm-windows |
| [fnm](https://github.com/Schniz/fnm) | GPL-3.0 | github.com/Schniz/fnm |
| [Node.js](https://nodejs.org) | MIT | nodejs.org |

All trademarks and product names are the property of their respective owners.
"Node.js" is a trademark of the OpenJS Foundation.

## Privacy

This plugin:

- **Does NOT collect** any user data or telemetry
- **Does NOT send** any information to external servers (except fetching the public Node.js version list from `nodejs.org/dist/index.json` or the configured mirror)
- All version management operations are performed **locally** via the installed tools

## License

MIT License — see [LICENSE](LICENSE) for details.
