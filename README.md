# MoveCore
An all-in-one Minecraft teleportation and movement plugin featuring Homes, Warps, Spawn, TPA, RTP, Back, and NoVoidFall, with LuckPerms support, cooldowns, teleport logs, and MySQL/MariaDB or JSON storage.

## Build

Requires Java 21 or newer and Paper 1.21.x.

```bash
./mvnw clean package
```

On Windows:

```bat
mvnw.cmd clean package
```

The shaded plugin is generated in `target/MoveCore-1.0.0.jar`. Copy it to the server `plugins/` directory, start the server once, then edit `plugins/MoveCore/config.yml`.

## Storage

The default backend is JSON (`plugins/MoveCore/data.json`). Set `storage.type` to `mysql` or `mariadb` and configure the JDBC URL and credentials to use MySQL/MariaDB. Data is stored in a configurable table prefix.

## Permissions

Homes and player-warps use `movecore.homes.<grade>` and `movecore.pwarps.<grade>` for LuckPerms grade limits. Administrative operations are split into `movecore.admin.homes.view`, `movecore.admin.homes.teleport`, `movecore.admin.homes.delete`, and `movecore.admin.warps`. The complete message catalog and all operational settings are in `config.yml`.
