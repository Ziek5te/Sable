package com.sable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the game's own chat lines mean.
 *
 * Every pattern here is RuneLite's: the kill count and personal best ones are
 * ChatCommandsPlugin's, the collection log, pet, combat task and quest ones are
 * ScreenshotPlugin's, and the diary and combat task ones are the same lines
 * Dink matches. They are copied, not reworded, because the only way to be
 * wrong about a game message is to have written it from memory.
 *
 * No RuneLite types in here on purpose, so all of it can be tested with plain
 * strings.
 */
final class ChatParser
{
	private ChatParser()
	{
	}

	// ChatCommandsPlugin
	static final String TEAM_SIZES = "(?<teamsize>\\d+(?:\\+|-\\d+)? players?|Solo)";

	static final Pattern KILLCOUNT = Pattern.compile(
		"Your (?<pre>completion count for |subdued |completed )?(?:<col=[0-9a-f]{6}>)?"
			+ "(?<boss>.+?)(?:</col>)? (?<post>(?:(?:kill|harvest|lap|completion|success|Total Ticket) )?(?:count )?)is: ?"
			+ "(?:<col=[0-9a-f]{6}>|@.+?@)(?<kc>[0-9,]+)</col>");

	private static final String PB_PREFIX =
		"(?i)(?:(?:Fight |Lap |Challenge |Corrupted challenge )?duration:|Subdued in|"
			+ "(?<!total )completion time:|The Fractured Archive: Total completion time:) ";

	static final Pattern NEW_PB = Pattern.compile(
		PB_PREFIX + "(?:<col=[0-9a-f]{6}>|@.+?@)(?<pb>[0-9:]+(?:\\.[0-9]+)?)</col> \\(new personal best\\)");

	static final Pattern KILL_DURATION = Pattern.compile(
		PB_PREFIX + "(?:<col=[0-9a-f]{6}>|@.+?@)[0-9:.]+</col>\\. Personal best: (?:<col=ff0000>|@.+?@)?"
			+ "(?<pb>[0-9:]+(?:\\.[0-9]+)?)");

	static final Pattern RAIDS_PB = Pattern.compile(
		"<col=ef20ff>Congratulations - your raid is complete!</col><br>Team size: <col=ff0000>" + TEAM_SIZES
			+ "</col> Duration:</col> <col=ff0000>(?<pb>[0-9:]+(?:\\.[0-9]+)?)</col> \\(new personal best\\)</col>");

	static final Pattern RAIDS_DURATION = Pattern.compile(
		"<col=ef20ff>Congratulations - your raid is complete!</col><br>Team size: <col=ff0000>" + TEAM_SIZES
			+ "</col> Duration:</col> <col=ff0000>[0-9:.]+</col> Personal best: </col><col=ff0000>"
			+ "(?<pb>[0-9:]+(?:\\.[0-9]+)?)</col>");

	// ChatCommandsPlugin: COLLECTION_LOG_ITEM_PATTERN
	static final Pattern COLLECTION_LOG = Pattern.compile("New item added to your collection log: (.*)");

	// ScreenshotPlugin
	static final String[] PET_MESSAGES = {
		"You have a funny feeling like you're being followed",
		"You feel something weird sneaking into your backpack",
		"You have a funny feeling like you would have been followed",
	};

	static final Pattern QUEST_PATTERN_1 = Pattern.compile(
		".+?ve\\.*? (?<verb>been|rebuilt|.+?ed)? ?(?:the )?'?(?<quest>.+?)'?(?: [Qq]uest)?[!.]?$");

	static final Pattern QUEST_PATTERN_2 = Pattern.compile(
		"'?(?<quest>.+?)'?(?: [Qq]uest)? (?<verb>[a-z]\\w+?ed)?(?: f.*?)?[!.]?$");

	// Dink: DiaryNotifier and CombatTaskNotifier
	static final Pattern DIARY = Pattern.compile(
		"Congratulations! You have completed all of the (?<difficulty>.+) tasks in the (?<area>.+) area");

	static final Pattern COMBAT_TASK = Pattern.compile(
		"Congratulations, you've completed an? (?<tier>\\w+) combat task: (?<task>.+)\\.");

	private static final Pattern TASK_POINTS = Pattern.compile("\\s+\\(\\d+ points?\\)$");

	// Words a kill count or time line always has. Used only to notice a line that
	// looks like one but matched nothing, because Jagex reworded it.
	private static final Pattern LOOKS_LIKE_KILL = Pattern.compile(
		"(?i)(duration|personal best|kill count|completion count|success count|completion time)");

	/** A kill count line: which boss, and the count. */
	static final class Kill
	{
		final String boss;
		final int kc;

		Kill(String boss, int kc)
		{
			this.boss = boss;
			this.kc = kc;
		}
	}

	/** A time line: the best time it names, whether it is new, and the team size if the line says. */
	static final class Time
	{
		final long timeMs;
		final boolean precise;
		final boolean isNew;
		final int teamSize; // -1 when the line does not say

		Time(long timeMs, boolean precise, boolean isNew, int teamSize)
		{
			this.timeMs = timeMs;
			this.precise = precise;
			this.isNew = isNew;
			this.teamSize = teamSize;
		}
	}

	/** A diary or combat task: the tier, and the area or the task. */
	static final class Named
	{
		final String tier;
		final String what;

		Named(String tier, String what)
		{
			this.tier = tier;
			this.what = what;
		}
	}

	/** True for a line that reads like a kill count or time, whether or not a pattern took it. */
	static boolean looksLikeKillLine(String plain)
	{
		return LOOKS_LIKE_KILL.matcher(plain).find();
	}

	static Kill kill(String message)
	{
		Matcher m = KILLCOUNT.matcher(message);

		if (!m.find())
		{
			return null;
		}

		String pre = m.group("pre") == null ? "" : m.group("pre");
		String post = m.group("post") == null ? "" : m.group("post");

		// Laps, harvests and ticket totals are not fights.
		boolean fight = post.contains("kill") || post.contains("completion") || post.contains("success")
			|| pre.startsWith("completed") || pre.startsWith("subdued");

		if (!fight)
		{
			return null;
		}

		try
		{
			return new Kill(boss(m.group("boss")), Integer.parseInt(m.group("kc").replace(",", "")));
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	/**
	 * The boss as the clan reads it.
	 *
	 * Colons are dropped, as RuneLite's own config does, so "Theatre of Blood: Hard Mode"
	 * and "Theatre of Blood Hard Mode" are one name.
	 */
	static String boss(String raw)
	{
		return raw.replace(":", "").replaceAll("\\s+", " ").trim();
	}

	static Time time(String message)
	{
		Matcher m = RAIDS_PB.matcher(message);

		if (m.find())
		{
			return time(m, true, teamSize(m.group("teamsize")));
		}

		m = RAIDS_DURATION.matcher(message);

		if (m.find())
		{
			return time(m, false, teamSize(m.group("teamsize")));
		}

		m = NEW_PB.matcher(message);

		if (m.find())
		{
			return time(m, true, -1);
		}

		m = KILL_DURATION.matcher(message);

		if (m.find())
		{
			return time(m, false, -1);
		}

		return null;
	}

	private static Time time(Matcher m, boolean isNew, int teamSize)
	{
		String pb = m.group("pb");

		return new Time(toMillis(pb), pb.contains("."), isNew, teamSize);
	}

	/** "Solo" is 1; "3 players" is 3; "8+ players" and "11-15 players" are read as their lower number. */
	static int teamSize(String label)
	{
		if (label == null)
		{
			return -1;
		}

		if (label.equalsIgnoreCase("Solo"))
		{
			return 1;
		}

		Matcher m = Pattern.compile("^\\d+").matcher(label);

		return m.find() ? Integer.parseInt(m.group()) : -1;
	}

	/** "17:42.60" or "1:02:03.40" or "59.4", to milliseconds. */
	static long toMillis(String text)
	{
		String[] parts = text.split(":");
		double seconds = 0;

		for (String part : parts)
		{
			seconds = seconds * 60 + Double.parseDouble(part);
		}

		return Math.round(seconds * 1000);
	}

	static String collectionLog(String plain)
	{
		Matcher m = COLLECTION_LOG.matcher(plain);

		return m.find() ? m.group(1).trim() : null;
	}

	static boolean pet(String plain)
	{
		for (String p : PET_MESSAGES)
		{
			if (plain.contains(p))
			{
				return true;
			}
		}

		return false;
	}

	static Named diary(String plain)
	{
		Matcher m = DIARY.matcher(plain);

		return m.find() ? new Named(capital(m.group("difficulty")), m.group("area").trim()) : null;
	}

	static Named combatTask(String plain)
	{
		Matcher m = COMBAT_TASK.matcher(plain);

		if (!m.find())
		{
			return null;
		}

		return new Named(capital(m.group("tier")), TASK_POINTS.matcher(m.group("task")).replaceAll("").trim());
	}

	/** The quest name from the quest scroll's title, which RuneLite reads with these two patterns in turn. */
	static String quest(String plain)
	{
		Matcher m = QUEST_PATTERN_1.matcher(plain);

		if (m.matches())
		{
			return m.group("quest");
		}

		m = QUEST_PATTERN_2.matcher(plain);

		return m.matches() ? m.group("quest") : null;
	}

	private static String capital(String s)
	{
		String t = s.trim().toLowerCase();

		return t.isEmpty() ? t : Character.toUpperCase(t.charAt(0)) + t.substring(1);
	}
}
