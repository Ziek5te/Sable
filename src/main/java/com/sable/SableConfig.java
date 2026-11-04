package com.sable;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(SableConfig.GROUP)
public interface SableConfig extends Config
{
	String GROUP = "sable";

	@ConfigSection(
		name = "Connection",
		description = "Where the plugin sends data to, and the key that allows it. See Server Address for exactly what is sent.",
		position = 0
	)
	String connection = "connection";

	@ConfigSection(
		name = "Reports",
		description = "What is sent to the clan's bot. The panel's totals are kept on your own PC either way.",
		position = 10
	)
	String reports = "reports";

	@ConfigSection(
		name = "Clan Chat",
		description = "The clan chat in the clan's Discord, and Discord replies in your game chat",
		position = 18
	)
	String chat = "chat";

	@ConfigSection(
		name = "Staff Tools",
		description = "Post a sample card, marked TEST, in the clan's channel. Staff only. Nothing is saved and no points are given.",
		position = 90,
		closedByDefault = true
	)
	String staff = "staff";

	@ConfigItem(
		keyName = "address",
		name = "Server Address",
		description = "The clan's bot address, from the same message in Discord as your key. Where the plugin sends data: the clan's own Sable bot, at this address, and nowhere else. Nothing is sent until you also enter your key. Then it sends your character name and your in-game events (drops, collection log, pets, diaries, combat tasks, quests, kill counts, personal bests). For group boss drops and personal bests it also sends the names of the clan members in your raid or instance, and only how many other players were there. A staff key also sends the clan's member list (names, ranks, join dates). With Relay Clan Chat on, it sends each clan chat line you see, who said it and what they said. It never writes into the clan chat. Rank pictures are downloaded from Discord's image servers.",
		section = connection,
		position = 0
	)
	default String address()
	{
		return "";
	}

	@ConfigItem(
		keyName = "key",
		name = "Sable Key",
		description = "Run /plugin in the clan's Discord to get yours. Without it nothing is sent.",
		secret = true,
		section = connection,
		position = 1
	)
	default String key()
	{
		return "";
	}

	@ConfigItem(keyName = "drops", name = "Drops", description = "Report valuable drops. Off: only RuneProfile reports them, up to 5 minutes later. With neither, there is no broadcast and no clan points.", section = reports, position = 11)
	default boolean drops()
	{
		return true;
	}

	@ConfigItem(keyName = "collectionLog", name = "Collection Log", description = "Report new collection log slots. Off: only RuneProfile reports them, up to 5 minutes later. With neither, there is no broadcast and no clan points.", section = reports, position = 12)
	default boolean collectionLog()
	{
		return true;
	}

	@ConfigItem(keyName = "pets", name = "Pets", description = "Report pets. Off: only RuneProfile reports them, up to 5 minutes later. With neither, there is no broadcast and no clan points.", section = reports, position = 13)
	default boolean pets()
	{
		return true;
	}

	@ConfigItem(keyName = "diaries", name = "Diaries", description = "Report completed achievement diary tiers. Off: only RuneProfile reports them, up to 5 minutes later. These earn no clan points.", section = reports, position = 14)
	default boolean diaries()
	{
		return true;
	}

	@ConfigItem(keyName = "combatAchievements", name = "Combat Achievements", description = "Report completed combat tasks. Off: only RuneProfile reports them, up to 5 minutes later. These earn no clan points.", section = reports, position = 15)
	default boolean combatAchievements()
	{
		return true;
	}

	@ConfigItem(keyName = "quests", name = "Quests", description = "Report completed quests. Off: only RuneProfile reports them, up to 5 minutes later. These earn no clan points.", section = reports, position = 16)
	default boolean quests()
	{
		return true;
	}

	@ConfigItem(keyName = "killsAndBests", name = "Kill Counts & Personal Bests", description = "Report your kill counts and personal best times. The clan decides how often a kill count is announced. Off: nothing else reports them, so they are never announced and your times never reach the clan board. These earn no clan points.", section = reports, position = 17)
	default boolean killsAndBests()
	{
		return true;
	}

	@ConfigItem(keyName = "clanChat", name = "Relay Clan Chat To Discord", description = "Send each clan chat line you see (who said it and what) to the clan's Discord channel. On by default. Several members seeing the same line is fine: it is posted once. Nothing is ever sent to the clan chat.", section = chat, position = 19)
	default boolean clanChat()
	{
		return true;
	}

	@ConfigItem(keyName = "showDiscordChat", name = "Show Discord Replies", description = "Show what is written in the clan's Discord chat channel as lines in your own game chat. Only you see them; they are not sent to the clan chat.", section = chat, position = 20)
	default boolean showDiscordChat()
	{
		return true;
	}

	@ConfigItem(keyName = "showPanel", name = "Side Panel Icon", description = "Show the Sable icon in the side bar, with your clan points, progress and ranks.", section = connection, position = 2)
	default boolean showPanel()
	{
		return true;
	}

	@ConfigItem(keyName = "testDrop", name = "Test Drop", description = "Tick to post a sample drop card", section = staff, position = 91)
	default boolean testDrop()
	{
		return false;
	}

	@ConfigItem(keyName = "testClog", name = "Test Collection Log", description = "Tick to post a sample collection log card", section = staff, position = 92)
	default boolean testClog()
	{
		return false;
	}

	@ConfigItem(keyName = "testPet", name = "Test Pet", description = "Tick to post a sample pet card", section = staff, position = 93)
	default boolean testPet()
	{
		return false;
	}

	@ConfigItem(keyName = "testDiary", name = "Test Diary", description = "Tick to post a sample diary card", section = staff, position = 94)
	default boolean testDiary()
	{
		return false;
	}

	@ConfigItem(keyName = "testCombat", name = "Test Combat Achievement", description = "Tick to post a sample combat achievement card", section = staff, position = 95)
	default boolean testCombat()
	{
		return false;
	}

	@ConfigItem(keyName = "testQuest", name = "Test Quest", description = "Tick to post a sample quest card", section = staff, position = 96)
	default boolean testQuest()
	{
		return false;
	}

	@ConfigItem(keyName = "testKc", name = "Test Kill Count", description = "Tick to post a sample kill count card", section = staff, position = 97)
	default boolean testKc()
	{
		return false;
	}

	@ConfigItem(keyName = "testPb", name = "Test Personal Best", description = "Tick to post a sample personal best card", section = staff, position = 98)
	default boolean testPb()
	{
		return false;
	}
}
