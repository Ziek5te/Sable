package com.sable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;

/** What the bot says about a member that the plugin cannot know itself: clan points, rank progress and the clan's rank pictures. */
final class ClanInfo
{
	long points;
	long monthPoints;
	String pvmRank;
	String pvmGame;
	String pvmNext;
	long pvmLeft;
	long pvmFrom;
	long pvmAt;
	Integer days;
	String loyaltyRank;
	String loyaltyGame;
	String loyaltyNext;
	long loyaltyLeft;
	long loyaltyFrom;
	long loyaltyAt;

	// What the clan announces a drop from. Until the bot says, a guess.
	long dropMin = 500_000;

	// RuneProfile's achievement diary and combat task totals, for when the game's own panel has not been seen. 0 when not known.
	int diariesDone;
	int diariesTotal;
	int tasksDone;
	int tasksTotal;

	/** A ladder the member could move to, and the rank it would put them on. */
	static final class Target
	{
		final String to;
		final String rankName;

		Target(String to, String rankName)
		{
			this.to = to;
			this.rankName = rankName;
		}
	}

	// Which ladder the main account is on (pvm, loyalty, charter, manual or none), whether it is in the clan, and where it could go.
	boolean inClan = true;
	String ladderOn;
	String ladderPending;
	final java.util.List<Target> ladderTargets = new java.util.ArrayList<>();

	// The picture for lines from Discord in game chat: an emoji called discord, or the server's icon, as an address on Discord's image host.
	String chatIcon;

	/** One rank in the clan: the group it is listed under, its name, the game's name for it, and how it is earned. */
	static final class RankRow
	{
		final String group;
		final String name;
		final String game;
		final String how;

		RankRow(String group, String name, String game, String how)
		{
			this.group = group == null ? "" : group;
			this.name = name == null ? "" : name;
			this.game = game;
			this.how = how == null ? "" : how;
		}
	}

	/** A drop that still waits for the member to say how they ran it: what it was, the half paid now, the full amount, and when the question lapses. */
	static final class Pending
	{
		final String item;
		final long points;
		final long full;
		final long until;

		Pending(String item, long points, long full, long until)
		{
			this.item = item == null ? "a drop" : item;
			this.points = points;
			this.full = full;
			this.until = until;
		}
	}

	/** One award from the member's last few: what it was for, the points, and when. */
	static final class Recent
	{
		final String what;
		final long points;
		final long at;
		// Where it came from and its code, shown when the pointer rests on the row.
		final String detail;
		// The Discord post the points came from, or null.
		final String url;

		Recent(String what, long points, long at, String detail, String url)
		{
			this.what = what == null ? "Points" : what;
			this.points = points;
			this.at = at;
			this.detail = detail == null ? "" : detail;
			this.url = url == null || url.isEmpty() ? null : url;
		}
	}

	// The member's last few awards, newest first.
	final java.util.List<Recent> recent = new java.util.ArrayList<>();

	// True when the key belongs to somebody else's accounts: the bot then sends only their names, nothing to show.
	boolean mismatch;

	// The accounts the member has linked, main first. Points and progress count all of them.
	final java.util.List<String> accounts = new java.util.ArrayList<>();

	// Where the quick links go. Any of them may be missing when the clan has not set it.
	String linkInvite;
	String linkEvents;
	String linkAnnouncements;
	String linkLeaderboard;

	// Drops waiting on the member, and the address of the Discord message that asks.
	final java.util.List<Pending> pending = new java.util.ArrayList<>();
	String dmUrl;

	// Every rank, in the order the clan lists them.
	final java.util.List<RankRow> ranks = new java.util.ArrayList<>();

	// Whether the bot counts this key's owner as clan staff, who keep the Wise Old Man group in step with the game.
	boolean staff;

	// Keyed by the game's name for a rank, with only letters and lower case.
	final Map<String, String> rankIcons = new HashMap<>();

	private static String key(String rank)
	{
		return rank == null ? "" : rank.toLowerCase().replaceAll("[^a-z]", "");
	}

	/** The picture address for an in-game rank such as "Owner" or "Squire", or null. */
	String rankIcon(String gameRank)
	{
		return rankIcons.get(key(gameRank));
	}

	/** Reads the bot's /me answer. Missing pieces stay empty rather than failing. */
	static ClanInfo fromJson(JsonObject o)
	{
		ClanInfo c = new ClanInfo();

		if (o == null)
		{
			return c;
		}

		JsonObject points = obj(o, "points");
		c.points = num(points, "total");
		c.monthPoints = num(points, "month");

		long dropMin = num(o, "dropMin");

		if (dropMin > 0)
		{
			c.dropMin = dropMin;
		}

		JsonObject pvm = obj(o, "pvm");
		c.pvmRank = str(pvm, "rank");
		c.pvmGame = str(pvm, "game");
		JsonObject pvmNext = obj(pvm, "next");
		c.pvmNext = str(pvmNext, "name");
		c.pvmLeft = num(pvmNext, "left");
		c.pvmFrom = num(pvm, "from");
		c.pvmAt = num(pvmNext, "at");

		JsonObject loyalty = obj(o, "loyalty");
		c.days = loyalty != null && loyalty.has("days") && !loyalty.get("days").isJsonNull() ? loyalty.get("days").getAsInt() : null;
		c.loyaltyRank = str(loyalty, "rank");
		c.loyaltyGame = str(loyalty, "game");
		JsonObject loyaltyNext = obj(loyalty, "next");
		c.loyaltyNext = str(loyaltyNext, "name");
		c.loyaltyLeft = num(loyaltyNext, "left");
		c.loyaltyFrom = num(loyalty, "from");
		c.loyaltyAt = num(loyaltyNext, "at");

		JsonObject profile = obj(o, "profile");
		JsonObject diaries = obj(profile, "diaries");
		JsonObject tasks = obj(profile, "tasks");
		c.diariesDone = (int) num(diaries, "done");
		c.diariesTotal = (int) num(diaries, "total");
		c.tasksDone = (int) num(tasks, "done");
		c.tasksTotal = (int) num(tasks, "total");

		c.chatIcon = str(o, "chatIcon");
		if (o.has("ranks") && o.get("ranks").isJsonArray())
		{
			for (JsonElement el : o.getAsJsonArray("ranks"))
			{
				JsonObject r = el.getAsJsonObject();
				c.ranks.add(new RankRow(str(r, "group"), str(r, "name"), str(r, "game"), str(r, "how")));
			}
		}

		c.dmUrl = str(o, "dmUrl");

		if (o.has("recent") && o.get("recent").isJsonArray())
		{
			for (JsonElement el : o.getAsJsonArray("recent"))
			{
				JsonObject r = el.getAsJsonObject();
				c.recent.add(new Recent(str(r, "what"), num(r, "points"), num(r, "at"), str(r, "detail"), str(r, "url")));
			}
		}

		c.mismatch = o.has("mismatch") && o.get("mismatch").getAsBoolean();

		if (o.has("accounts") && o.get("accounts").isJsonArray())
		{
			for (JsonElement el : o.getAsJsonArray("accounts"))
			{
				if (el.isJsonPrimitive())
				{
					c.accounts.add(el.getAsString());
				}
			}
		}

		JsonObject links = obj(o, "links");
		c.linkInvite = str(links, "invite");
		c.linkEvents = str(links, "events");
		c.linkAnnouncements = str(links, "announcements");
		c.linkLeaderboard = str(links, "leaderboard");

		if (o.has("pending") && o.get("pending").isJsonArray())
		{
			for (JsonElement el : o.getAsJsonArray("pending"))
			{
				JsonObject p = el.getAsJsonObject();
				c.pending.add(new Pending(str(p, "item"), num(p, "points"), num(p, "full"), num(p, "until")));
			}
		}

		c.staff = o.has("staff") && o.get("staff").getAsBoolean();
		fill(obj(o, "rankIcons"), c.rankIcons);

		JsonObject ladder = obj(o, "ladder");

		if (ladder != null)
		{
			c.inClan = !ladder.has("inClan") || ladder.get("inClan").getAsBoolean();
			c.ladderOn = str(ladder, "on");
			c.ladderPending = str(ladder, "pending");

			if (ladder.has("targets") && ladder.get("targets").isJsonArray())
			{
				for (JsonElement el : ladder.getAsJsonArray("targets"))
				{
					JsonObject t = el.getAsJsonObject();
					c.ladderTargets.add(new Target(str(t, "to"), str(t, "rankName")));
				}
			}
		}

		return c;
	}

	private static void fill(JsonObject from, Map<String, String> into)
	{
		if (from == null)
		{
			return;
		}

		for (Map.Entry<String, JsonElement> e : from.entrySet())
		{
			if (e.getValue().isJsonPrimitive())
			{
				into.put(e.getKey(), e.getValue().getAsString());
			}
		}
	}

	private static JsonObject obj(JsonObject o, String key)
	{
		JsonElement e = o == null ? null : o.get(key);

		return e != null && e.isJsonObject() ? e.getAsJsonObject() : null;
	}

	private static String str(JsonObject o, String key)
	{
		JsonElement e = o == null ? null : o.get(key);

		return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
	}

	private static long num(JsonObject o, String key)
	{
		JsonElement e = o == null ? null : o.get(key);

		return e != null && e.isJsonPrimitive() ? e.getAsLong() : 0;
	}
}
