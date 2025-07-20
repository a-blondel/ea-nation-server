# EA Nation Server

An emulator of the EA Nation server.

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

| Game                    | Platform(s) | Region(s) | Status                        |
|-------------------------|-------------|-----------|-------------------------------|
| Medal of Honor Heroes   | PSP         | EU, US    | Full support                  |
| Medal of Honor Heroes 2 | PSP, Wii    | EU, US    | In progress, not playable yet |
| NHL 07                  | PSP         | US        | Full support                  |
| FIFA 07                 | PSP         | US        | Partial support, playable     |

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
