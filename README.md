# 🧠 Build Rush

Build Rush is a build memorizing minigame for Minecraft!  
The goal is to memorize a structure and then rebuild it as fast as possible.

**⚠️ Although this mod is functional in its current state, it is still very work-in-progress!**

This is a part of an attempt to create an open source Fabric-based minigames server. (Nucleoid)  
For more information, see [our website](https://nucleoid.xyz).

## Configuration

### Adding a map

Please follow the Nucleoid [tutorial](https://docs.nucleoid.xyz/plasmid/maps/) on how to create a map template.

A Build Rush map template needs the following regions:
- `center_plot`: the center of the map, where the players will be teleported to at first, and where the chosen build may appear at points. (only 1)
- `plot`: the plots where the players will build. There must be as many plots as the max amount of players in the game configuration (see below) at least.

A map can only support one size of builds. The size of the chosen builds is determined by the size of the plots. All plots must be of the same size (width and length) and be 1 block high.

You now need to add game configuration that will use your map.  
Game configurations are stored in the `games` folder of the datapack.

Here is a breakdown of a Build Rush game configuration:

```json5
{
  "type": "build_rush:standard",                   // mandatory, must not be changed 
  "name": {                                        // mandatory
    "translate": "game.build_rush.small.with_map",
    "with": [
      {
        "translate": "map.build_rush.my_map"
      }
    ]
  },
  "icon": "minecraft:diamond",                     // optional, for the game menu on Nucleoid
  "players": {                                     // mandatory
    "min": 1,
    "threshold": 8,
    "max": 16
  },
  "map": {                                         // mandatory
    "template": "build_rush:my_map",               // - mandatory, must reference the map template
    "nametag_offset": 5,                           // - optional, defaults to 10
    "nametag_size": 2.0                            // - optional, defaults to 5.0
  },
  "builds": "#build_rush:generic"                  // mandatory, must reference a list of builds or a tag
}
```

The builds field can contain any builds, even if they are not compatible with the map. Only the compatible ones will get filtered out.

> **Important**
> If you are contributing to this repository, please make sure to follow these instructions:  
> - Your game configuration is in the correct subfolder: `small` is for maps compatible with 5x5 builds, `medium` is for 7x7, and `large` is for 9x9.
> - Your game configuration is listed in the `random` game configuration in the same subfolder.
> - The name of your game configuration is the same as the example above, with the map name changed (and the size in the first string if necessary). It is translatable.
> - Your game configuration has an icon.
> - The build list is large enough, to counter the feeling of repetitiveness. Use the `#build_rush:generic` to include all builds.

### Adding a build

Builds are stored in the `build_rush/builds` folder of the datapack.

```json5
{
  "structure": "build_rush:build/my_build",         // mandatory, must reference a structure file
  "name": {                                         // mandatory
    "translate": "build.my_build"
  },
  "author": {                                       // optional
    "name": "jeb_",                                 // - mandatory
    "uuid": "853c80ef-3c37-49fd-aa49-938b674adae6"  // - optional
  }
}
```

> **Note**
> If a game tries to load any build that is not valid, it will be filtered out and a warning will be sent in the console.

> **Important**
> If you are contributing to this repository, please add your build to the `#build_rush:generic` tag.

## Credits

- [Mineplex](https://www.mineplex.com) - Original minigame
- [Napero](https://github.com/Napero) - Idea of porting to Nucleoid
- [Hugman](https://github.com/Hugman76) - Development

### Maps
- Rock-Candy Mines (by [Hugman](https://github.com/Hugman76))
- Sandcastles (by [Hugman](https://github.com/Hugman76))

### Builds
The builds were made by:
- atlas8_
- moehreag
- Pufferfish
- [Hugman](https://github.com/Hugman76)
- [Napero](https://github.com/Napero)
- sockfriend
- Geefire
- haykam
- Sioul
- valbrimi
- [Jerozgen](https://github.com/Jerozgen)
- WesleyH21

### 🌐 Translations
| Language         | Translators                                                                |
|------------------|----------------------------------------------------------------------------|
| English (base)   | [Hugman](https://github.com/Hugman76)                                      |
| French           | [Hugman](https://github.com/Hugman76), [Voxxin](https://github.com/Voxxin) |
| Swedish          | valbrimi                                                                   |
| Norwegian Bokmål | [comradekingu](https://github.com/comradekingu)                            |
| Russian          | [Jerozgen](https://github.com/Jerozgen)                                    |