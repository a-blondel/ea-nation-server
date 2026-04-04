# EA Nation Server

An emulator of the EA Nation server based on the Aries protocol.

This project is a continuation of the [mohh-master-server](https://github.com/a-blondel/mohh-master-server), as the
scope of the project has been extended to support more games.

## Discord

[![Discord Banner](https://discordapp.com/api/guilds/1092099223375323236/widget.png?style=banner3)](https://discord.gg/fwrQHHxrQQ)

It is used to :

- Talk about the games
- Share technical knowledge
- Regroup the community and organize events

Fell free to join !

## Supported games

**Status legend:**

- ✅ Full support
- ⚠️ Partial support (playable with missing features)
- ❌ Not playable yet

### PC

| Game                             | Status |
|----------------------------------|--------|
| Need for Speed: Most Wanted 2005 | ⚠️     |

### PS2

| Game                                          | Status |
|-----------------------------------------------|--------|
| Arena Football                                | ❌      |
| Arena Football Road To Glory                  | ❌      |
| Burnout 3 - Takedown                          | ❌      |
| Burnout Revenge                               | ❌      |
| FIFA Soccer 2004                              | ❌      |
| FIFA 06, 07, 08                               | ⚠️     |
| FIFA World Cup Germany 2006                   | ⚠️     |
| Fight Night Round 2                           | ❌      |
| Fight Night Round 3                           | ❌      |
| James Bond 007: Everything or Nothing         | ❌      |
| Madden NFL 2004, 05, 06, 07, 08, 09, 10       | ❌      |
| Marvel Nemesis: Rise of the Imperfects        | ⚠️     |
| MVP Baseball 2004, 05                         | ❌      |
| MVP 06, 07 NCAA Baseball                      | ❌      |
| NASCAR 04, 05, 06, 07, 08                     | ❌      |
| NASCAR 09                                     | ⚠️     |
| NBA Live 2004, 05, 06, 07                     | ❌      |
| NBA Live 08                                   | ⚠️     |
| NBA Street 2                                  | ❌      |
| NCAA Football 2004, 05, 06, 07, 08, 09, 10    | ❌      |
| NCAA March Madness 2004, 05                   | ❌      |
| NCAA March Madness 06, 07                     | ⚠️     |
| Need for Speed: Underground                   | ❌      |
| Need for Speed: Underground 2                 | ⚠️     |
| Need for Speed: Most Wanted 2005 (Alpha)      | ⚠️     |
| NFL Head Coach                                | ❌      |
| NFL Street (2004), 2, 3                       | ❌      |
| NHL 2004, 05                                  | ❌      |
| NHL 06, 07, 08                                | ⚠️     |
| SSX 3                                         | ❌      |
| The Lord of the Rings: The Return of the King | ❌      |
| The Sims: Bustin' Out                         | ❌      |
| Tiger Woods PGA Tour 2004, 05, 06, 07         | ❌      |
| Tiger Woods PGA Tour 08                       | ❌      |
| UEFA Champions League 2004-2005               | ❌      |
| UEFA Champions League 2006-2007               | ⚠️     |

### PSP

| Game                              | Status |
|-----------------------------------|--------|
| FIFA 07, 08, 09, 10               | ⚠️     |
| FIFA World Cup Germany 2006       | ⚠️     |
| FIFA World Cup South Africa 2010  | ⚠️     |
| Fight Night Round 3               | ⚠️     |
| MADDEN 07, 08, 09, 10             | ⚠️     |
| Medal of Honor: Heroes            | ✅      |
| Medal of Honor: Heroes 2          | ❌      |
| NBA Live 06, 07, 08*              | ⚠️     |
| NCAA 07*                          | ⚠️     |
| Need for Speed: Most Wanted 5-1-0 | ✅      |
| Need for Speed Carbon: OTC        | ✅      |
| Need for Speed: ProStreet         | ✅      |
| Need for Speed: Undercover        | ✅      |
| NHL 07                            | ⚠️     |
| Tiger Woods PGA Tour 07, 08, 10   | ⚠️     |
| UEFA Champions League 2006-2007   | ⚠️     |

\* Only the US version has online support

### Wii

| Game                     | Status |
|--------------------------|--------|
| Medal of Honor: Heroes 2 | ❌      |

Notes:

- **The EU version of NFS MW 5-1-0 requires
  an [SSL bypass patch](https://github.com/a-blondel/ea-nation-patches/tree/main/PSP/Xdelta/NFS-Most-Wanted) to
  connect, as the game's port is already used by NFS Undercover**
- **The only supported PS2 version of NFS MW 2005 is the `Alpha 124` (a.k.a. `Sep 20, 2005 prototype`), as the online
  feature was cut from the Beta & Release on PS2**
- **The PS2 version of NFS MW 2005 requires a DNAS patch. Either
  use [DNAS-net Patcher](https://www.psx-place.com/threads/dnas-net-patcher.22813/) or the
  provided [Xdelta patch](https://github.com/a-blondel/ea-nation-patches/tree/main/PS2/Xdelta/NFS-Most-Wanted)**
- All the other PS2 games requires a DNAS patch unless they are supported by DNASrep.
  See [pnach](https://github.com/a-blondel/ea-nation-patches/tree/main/PS2/pnach) folder for your specific game
- Partial support means that some features are not implemented yet, like:
    - Leaderboards
    - Roster download
    - Interactive league

## Development Status

You can follow the progress on the [project board](https://github.com/users/a-blondel/projects/2/views/1)

## Contribute (for developers)

Everything to know is in the [Wiki](https://github.com/a-blondel/ea-nation-server/wiki)  
It contains :

- Development requirements
- How to run the server
- Project architecture
- Database description
- How to add game servers dynamically
- Technical knowledge about EA Nation server (TCP packets)

## Credits

In addition to the [contributors](https://github.com/a-blondel/ea-nation-server/graphs/contributors) of this project and
the [contributors of mohh-master-server](https://github.com/a-blondel/mohh-master-server/graphs/contributors), the
following projects were inspiring in the development of this project:

- EA SSL certificate vulnerability
    - https://github.com/Aim4kill/Bug_OldProtoSSL (analysis)
    - https://github.com/valters-tomsons/arcadia (implementation)
- Nintendo WFC server emulator
    - https://github.com/barronwaffles/dwc_network_server_emulator
- Related EA server emulators with more or less similar TCP packets
    - https://github.com/HarpyWar/nfsuserver
    - https://github.com/VTSTech/VTSTech-SRVEmu
    - https://github.com/nleiten/ea-server-emu-startpoint
    - https://gitlab.com/gh0stl1ne/eaps

# EA Nation Discord Bot

## Features

- Discord bot activity
    - Current connected player count
    - Current in game player count
    - Current DNS IP Address
- Scoreboards
    - Sends an image of the scoreboard every time a game ends
- Events
    - Sends a message when players connect/disconnect and join/leave games

<img src="resources/images/bot-activity.png" alt="bot activity" /><br/>
*Discord bot activity*

<img src="resources/images/mohh-scoreboard.png" alt="MoHH scoreboard" /><br/>
*MoHH Scoreboard*

<img src="resources/images/nfs-scoreboard.png" alt="NFS scoreboard" /><br/>
*NFS Scoreboard*

<img src="resources/images/nhl-scoreboard.png" alt="NHL scoreboard" /><br/>
*NHL Scoreboard*
