# MoveCore

<p align="center">
	<img src="logo.png" alt="MoveCore logo" width="180"><br>
	<strong>Teleportation utilities for modern Paper servers</strong><br>
	Homes, warps, spawn, TPA, RTP and more in one lightweight plugin.
</p>

<p align="center">
	<img src="https://img.shields.io/badge/version-1.1.0-2ea44f?style=for-the-badge" alt="Version 1.1.0">
	<img src="https://img.shields.io/badge/Paper-1.21.x-ffffff?style=for-the-badge&logo=minecraft" alt="Paper 1.21.x">
	<img src="https://img.shields.io/badge/Java-21%2B-f89820?style=for-the-badge&logo=openjdk" alt="Java 21 or newer">
</p>

## ✨ Features

- 🏠 Player homes with configurable limits
- 🧭 Admin warps and player warps
- 🏰 Configurable spawn and void-fall protection
- 🤝 TPA requests with lock and unlock controls
- 🎲 Safe random teleportation with blocked worlds
- ↩️ Previous-location support with `/back`
- ⏱️ Warmups, cooldowns and movement cancellation
- 📜 Teleport logs with JSON or MySQL/MariaDB storage
- 🔐 LuckPerms-friendly permission-based limits

## 🚀 Installation

1. Download `target/movecore-1.1.0.jar` from the build output.
2. Copy it into your server's `plugins/` directory.
3. Start the server once to generate the configuration.
4. Edit `plugins/MoveCore/config.yml` as needed.
5. Apply changes in-game with `/movecore reload`.

> Requires **Java 21+** and **Paper 1.21.x**.

## 📖 Commands

| Command | Description |
| --- | --- |
| `/sethome <name>` / `/home [name]` | Create or use a home |
| `/delhome <name>` / `/homes [player]` | Delete or list homes |
| `/setwarp <name>` / `/warp <name>` | Create or use an admin warp |
| `/delwarp <name>` | Delete an admin warp |
| `/setpwarp <name>` / `/pwarp <name>` | Create or use a player warp |
| `/delpwarp <name>` | Delete a player warp |
| `/setspawn` / `/spawn` | Set or use the global spawn |
| `/tpa <player>` / `/tpahere <player>` | Send a teleport request |
| `/tpaccept` / `/tpdeny` | Accept or deny a request |
| `/tpalock <player>` / `/tpaunlock <player>` | Block or allow requests |
| `/rtp` / `/back` | Random teleport or return to the previous location |
| `/movecore reload` | Reload the plugin configuration and services |

## 🔑 Permissions

| Permission | Default | Purpose |
| --- | --- | --- |
| `movecore.admin` | Operator | Reload, set spawn and general administration |
| `movecore.admin.homes.view` | Operator | View another player's homes |
| `movecore.admin.homes.teleport` | Operator | Teleport to another player's home |
| `movecore.admin.homes.delete` | Operator | Delete another player's home |
| `movecore.admin.warps` | Operator | Manage admin warps |
| `movecore.homes.<grade>` | Not set | Apply a LuckPerms home limit |
| `movecore.pwarps.<grade>` | Not set | Apply a LuckPerms player-warp limit |

## 💾 Storage

JSON is enabled by default and stores data in `plugins/MoveCore/data.json`.
To use MySQL or MariaDB, set `storage.type` to `mysql` or `mariadb` and configure the JDBC URL, credentials and table prefix in `config.yml`.

## 🛠️ Build

Requires Java 21 or newer.

```bash
./mvnw clean package
```

On Windows:

```bat
mvnw.cmd clean package
```

The shaded plugin is generated at `target/movecore-1.1.0.jar`.
