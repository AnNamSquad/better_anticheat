---
trigger: always_on
---

the current workspace is template, please modify to make a plugin for paper named lovely-detector. I am using geyser, viabackward, viaversion so the version coverage should be in range bedrock(1.21.130,1.21.132, 26.0, 26.1, 26.2, 26.3, 26.10 and for java(1.9.x - 1.21.x). So this plugin should have following function:
- Detect cheats before they strike,runs lightweight checks when players connect, detecting hacked clients and suspicious mods before they even have a chance to cheat on your server.
- Wide client & mod coverage, from Forge and Fabric to Labymod, WorldDownloader, Vape Cracked and more, HackedServer covers a large set of popular hacked clients and mods across many Minecraft versions.
- Alerts and automatic punishments, Automatically alert your staff, log suspicious activity, or run custom punishments when a bad client is detected, so your team can focus on your community instead of chasing cheaters.

Requirements for the plugin start:
- Minecraft Server: 1.18 or newer (Paper , Spigot , or similar)
- Java: 17 or newer
- PacketEvents: Required for Velocity proxy and hybrid servers


Configuration Files Overview:
config.yaml     Main plugin settings
actions.yaml	Define what happens when clients are detected
generic.yaml	Configure client/mod detection rules
lunar.yaml	Lunar Client Apollo integration settings
forge.yaml	Forge/NeoForge mod detection settings
bedrock.yaml	Bedrock Edition player detection via Geyser/Floodgate
languages/vi.yaml	Customizable messages(main is vietnamese