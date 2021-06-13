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
| 1 | `~avatar [User*]` | Returns a link of target user avatar/profile picture. | `~avatar 127932752744939521`, `~avatar @Gvistic` |
| 2 | `~blacklist add [URL*] [Type]` | Adds an image to the blacklist. | `~blacklist add https://cdn.discordapp.com/....png Gvistic`, `~blacklist add https://cdn.discordapp.com/....webp?size=1024` |
| 3 | `~blacklist remove [URL*]/[Type*]` | Removes image(s) from the blacklist. | `~blacklist remove https://cdn.discordapp.com/avatars/....png`, `~blacklist remove Gvistic` |
| 4 | `~blacklist search [URL]/[Type]` | Returns complete list if no arguments provided, or true/false if URL is only provided, or count of tokens if type is provided. | `~blacklist search https://cdn.discordapp.com/....png`, `~blacklist search`, `~blacklist search Gvistic` |
| 5 | `~comamnds` | Returns list of commands. | `~commands` |
| 6 | `~command info [Command]` | Returns information about particular command. | `~command info avatar`, `~command info "blacklist search"`, `~command info 3` |
| 7 | `~ignore add [User]` | Adds a user to the ignore list, users on the ignore list will not be notified if match is found | `~ignore add 127932752744939521`, `~ignore add @Gvistic` |
| 8 | `~ignore remove [User*]` | Removes a user from the ignore list. | `~ignore remove 127932752744939521`, `~ignore remove @Gvistic` |
| 9 | `~ignore search [User]` | Returns complete list if no arguments provided. Or true/false if user is only provided. | `~ignore search`, `~ignore search @Gvistic` |
| 10 | `~notify add [Channel*/*User*/Role*] [Snowflake*]` | Adds a channel/user/role to the notification list, whenever a match is made via scan or event the bot will notify the items on this list. | `~notify add channel #mods`, `~notify add user 127932752744939521`, `~notify add role @Staff`
| 11 | `~notify remove [Snowflake*]` | Removes a item from the notify list. | `~notify remove @Staff`, `~notify remove #mods`, `~notify remove 12345678910` |
| 12 | `~notify search [Snowflake/Type]` | Returns complete list if no arguments provided, or true/false if snowflade is only provided, or count of tokens if type is provided. | `~notify search`, `~notify search channels`, `~notify search @Gvistic` |
| 13 | `~permissions add [CommandID*] [Snowflake*] [AllowHigherRoles]` | Adds a permission to a particular command, if allowhigher is true/1/enabled any role above the given will be permitted to execute the given command. | `~permissions add 1 12345678910`, `~permissions add 4 12345678910 true`, `~permissions add 5 @Gvistic` |
| 14 | `~permissions remove [CommandID*] [Snowflake*]` | Removes a permissions from a particular command. | `~permissions remove 1 @Gvistic`, `~permissions remove 3 12345678910` |
| 15 | `~perms` | Returns a embed containing all commands with corresponding permitted roles. | `~perms` |
| 16 | `~prefix set [String*]` | Updates the bot's prefix | `~prefix set !` |
| 17 | `~scan [All*/User*/Custom*/Legacy*]` | If all argument provided, it will scan the entire guild for any matches from the blacklist. If a user argument provided, it will only scan that user for any matches. If custom provided, you can either provide another argument of an int to set the divisor of the algorithm (higher divisor may increase scan speeds) or provide an image to scan for only that particular image. If legacy provided, a linear styled scan will start. | `~scan all`, `~scan custom https://cdn.discordapp.com/....png`, `~scan custom 100`, `~scan legacy` |
| 18 | `~similarity set` | Sets the similarity index. Whenever a user's avatar is checked, pixel similarity takes place against all images in the blacklist, and stores a similarity index for each image in the blacklist.
If one of those indices is lower than the set index, it notifies as a match.
If there are false positives, lowering the index will help reduce that. | `~similarity set 5` |
| 19 | `~similiarity search` | Returns the current similarity index. | `~similarity search` |
