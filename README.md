# Asailify

Asailify is a discord bot created by whose primary purpose is to catch potential spammers/raiders/scammers. 
The name of the bot is a word play on "Assail". To Assail means to Attack or Assault, though the bot does the opposite of this
and aids to divert "Attacks" or "Assaults".

## How

Whenever a user joins the server, or a 'MemberUpdateEvent' or 'MemberJoinEvent' occurs (Ex: role change, profile change, nickname change, logging in after long period of time)
Asailify will scan the users profile picture using pixel similiarity algorithms against a blacklist and notify staff upon match. 

## Why

Those who have malice intentions tend to be consistent, which makes it easier for bots such as Asailify to automate and detect thus making it easier for staff members.

## Technical Information

- The bot is written in Java, utilizing the Discord4J library. I decided to use this library/language in order to utilize Reactive Streams for asynchronous stream processing.
- The bot is hosted on PebbleHost.
- The bot only requires View Channel, and Send Message permission (Optional role mention to mention roles).
- I plan to add machine learning/neural networks to detect NSFW PFP's in the future.

## Commands

Assume prefix = `~`
Required argument = *

| ID | Command | Description | Usage |
|--|------|-------------|-------|
| 1 | `~avatar {User*}` | Returns a link of target user avatar/profile picture. | `~avatar 127932752744939521`, `~avatar @Gvistic` |
| 2 | `~blacklist add {URL*} [Type]` | Adds an image to the blacklist. | `~blacklist add https://cdn.discordapp.com/....png Gvistic`, `~blacklist add https://cdn.discordapp.com/....webp?size=1024` |
| 3 | `~blacklist remove {URL*}/[Type*]` | Removes image(s) from the blacklist. | `~blacklist remove https://cdn.discordapp.com/avatars/....png`, `~blacklist remove Gvistic` |
| 4 | `~blacklist search {URL}/[Type]` | Returns complete list if no arguments provided. Or true/false if URL is only provided, or count of tokens if type is provided | `~blacklist search https://cdn.discordapp.com/....png`, `~blacklist search`, `~blacklist search Gvistic` |
| 5 | `~comamnds` | Returns list of commands. | `~commands` |
| 6 | `~command info [Command]` | Returns information about particular command. | `~command info avatar`, `~command info "blacklist search"`, `~command info 3` |
| 7 | `~ignore add {User}` | Adds a user to the ignore list, users on the ignore list will not be notified if match is found | `~ignore add 127932752744939521`, `~ignore add @Gvistic` |
| 8 | `~ignore remove {User*}` | Removes a user from the ignore list. | `~ignore remove 127932752744939521`, `~ignore remove @Gvistic` |
| 9 | `~ignore search {User}` | Returns complete list if no arguments provided. Or true/false if user is only provided. | `~ignore search`, `~ignore search @Gvistic` |
| 10 | `~notify add {Channel*/*User*/Role*} [Snowflake]` | Adds a channel/user/role to the notification list, whenever a match is made via scan or event the bot will notify the items on this list. | `~notify add channel #mods`, `~notify add user 127932752744939521`, `~notify add role @Staff`
