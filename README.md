# Sable (RuneLite plugin)

A companion for a clan that runs the Sable Discord bot. It reports a member's drops,
collection log, pets, diaries, combat tasks, quests, kill counts and personal bests to
the clan's bot as they happen, and shows the member's clan points, rank progress,
recent points and every clan rank in a side panel. No screenshots and no uploads.

It only works with a clan whose bot gives out an **address** and a **key**. Nothing is sent
until both are entered under **Connection** in the plugin's settings. The data goes to
that clan's own server, not to RuneLite or any third party.

## What is sent

- **Events** (`POST /plugin/v1/event`): the character name and the event itself. For a group boss drop
  or a personal best, also the names of the clan members in your raid or instance, so the clan's
  points and record can name the team, and how many other players were there (a number, never their names). Always on.
- **Check-in:** on each login, which character is playing, so the bot can confirm who owns the account.
- **Clan chat** (`Relay Clan Chat To Discord`, on by default, can be switched off): each clan chat line you
  see, who said it, what they said and the account type icon beside their name. The plugin never writes into
  the clan chat. `Show Discord Replies` shows the Discord channel as lines in your own game chat only.
- **Clan member list, staff keys only:** the clan's member list (names, ranks, join dates) so the clan's
  Wise Old Man group can follow the game. Nobody else's plugin sends it.

It receives (`GET /plugin/v1/me?rsn=`, `/chat`, `/ping`): your clan points and rank progress, your last few
point awards, the clan's ranks and rank pictures, and the links the clan has set. A key only answers for the
accounts linked to its owner: logged in as another character, the panel shows only which accounts the key belongs to.

The only other address it contacts is Discord's image server (`cdn.discordapp.com`), once, for the clan's rank pictures.
The panel's links open Discord pages in your browser.

## Settings

- **Connection:** Server Address and Sable Key (from the clan's Discord), and whether the side panel icon shows.
- **Reports:** a switch for each kind of event. Drops, collection log, pets, diaries, combat tasks and quests are also
  reported by RuneProfile a few minutes later if the member has it. Kill counts and personal bests have no other
  source: off means they are never announced.
- **Clan Chat:** the two chat settings above.
- **Staff Tools:** staff can post a sample card, marked TEST, in the clan's channel. Nothing is saved and no points are given.

## The side panel

- **Connected / Disconnected** at the top. A key that belongs to another character shows Disconnected and which accounts it is for.
- **Needs attention:** group boss drops waiting for you to say how you ran them, with a link to the Discord message.
- **This character:** the clan rank of the character that is logged in, read from the game.
- **Your clan profile:** clan points and PvM and Loyalty progress. These belong to the member, not the character, and count every
  linked account. The ladder is the rank held in game; a **Change ladder** button asks staff to set the other one. The bot never sets a rank.
- **Recent points:** the last five awards. Rest the pointer on one to see where it came from; click one to open its Discord post.
- **Links:** the clan's Discord invite, events, announcements and leaderboard.
- **All ranks:** every rank, with the ones you hold highlighted.

## In the game

Turn on the collection log chat notification in the in-game settings, or new slots are not seen.
RuneLite's Loot Tracker plugin must be on for drops.

## Build

Needs a JDK 11 or newer.

```
gradlew build
```

To try it in a RuneLite client with the plugin loaded:

```
gradlew runClient
```

The chat parsing is in `ChatParser` (`ChatParserTest`); every pattern in it is copied from RuneLite's own
source or from Dink. The panel's layout is in `AccountView` and `Ui` (`AccountViewTest`).

## Not tested without the game

Loot, collection log, pets, diaries, combat tasks, quests, kill counts, personal bests and the group view all
need a logged-in client. Run it with `runClient` and check each once.

## License

BSD 2-Clause, see `LICENSE`.
