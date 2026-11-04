package com.sable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Each line below is built from the structure of the pattern it is meant to match. */
public class ChatParserTest
{
	@Test
	public void killCountOfABoss()
	{
		ChatParser.Kill k = ChatParser.kill("Your Zulrah kill count is: <col=ff0000>1,234</col>.");

		assertNotNull(k);
		assertEquals("Zulrah", k.boss);
		assertEquals(1234, k.kc);
	}

	@Test
	public void raidCountsKeepTheirMode()
	{
		assertEquals("Theatre of Blood", ChatParser.kill("Your completed Theatre of Blood count is: <col=ff0000>42</col>.").boss);
		assertEquals("Theatre of Blood Hard Mode",
			ChatParser.kill("Your completed Theatre of Blood: Hard Mode count is: <col=ff0000>7</col>.").boss);
		assertEquals("Chambers of Xeric Challenge Mode",
			ChatParser.kill("Your completed Chambers of Xeric: Challenge Mode count is: <col=ff0000>3</col>.").boss);
		assertEquals(3, ChatParser.kill("Your completed Tombs of Amascut count is: <col=ff0000>3</col>.").kc);
	}

	@Test
	public void lapsHarvestsAndTicketsAreNotFights()
	{
		assertNull(ChatParser.kill("Your Rooftop Agility lap count is: <col=ff0000>12</col>."));
		assertNull(ChatParser.kill("Your Herbiboar harvest count is: <col=ff0000>12</col>."));
		assertNull(ChatParser.kill("Your Gnome Restaurant Total Ticket count is: <col=ff0000>12</col>."));
	}

	@Test
	public void aNewPersonalBest()
	{
		ChatParser.Time t = ChatParser.time("Fight duration: <col=ff0000>1:23.40</col> (new personal best)");

		assertNotNull(t);
		assertEquals(83_400, t.timeMs);
		assertTrue(t.isNew);
		assertTrue(t.precise);
		assertEquals(-1, t.teamSize);
	}

	@Test
	public void anOldBestOnAnOrdinaryKill()
	{
		ChatParser.Time t = ChatParser.time("Fight duration: <col=ff0000>1:30.60</col>. Personal best: <col=ff0000>1:23.40</col>");

		assertNotNull(t);
		assertEquals(83_400, t.timeMs);
		assertFalse(t.isNew);
		assertTrue(t.precise);
	}

	@Test
	public void aTimeWithoutHundredthsIsNotPrecise()
	{
		ChatParser.Time t = ChatParser.time("Fight duration: <col=ff0000>1:23</col> (new personal best)");

		assertNotNull(t);
		assertEquals(83_000, t.timeMs);
		assertFalse(t.precise);
	}

	@Test
	public void anHourLongTime()
	{
		assertEquals(3_723_400, ChatParser.toMillis("1:02:03.40"));
	}

	@Test
	public void aRaidsNewBestCarriesItsTeamSize()
	{
		ChatParser.Time t = ChatParser.time("<col=ef20ff>Congratulations - your raid is complete!</col><br>Team size: "
			+ "<col=ff0000>3 players</col> Duration:</col> <col=ff0000>17:42.60</col> (new personal best)</col>");

		assertNotNull(t);
		assertEquals(1_062_600, t.timeMs);
		assertTrue(t.isNew);
		assertEquals(3, t.teamSize);
	}

	@Test
	public void aRaidsOldBest()
	{
		ChatParser.Time t = ChatParser.time("<col=ef20ff>Congratulations - your raid is complete!</col><br>Team size: "
			+ "<col=ff0000>Solo</col> Duration:</col> <col=ff0000>18:00.00</col> Personal best: </col><col=ff0000>16:00.00</col>");

		assertNotNull(t);
		assertEquals(960_000, t.timeMs);
		assertFalse(t.isNew);
		assertEquals(1, t.teamSize);
	}

	@Test
	public void teamSizeLabels()
	{
		assertEquals(1, ChatParser.teamSize("Solo"));
		assertEquals(8, ChatParser.teamSize("8+ players"));
		assertEquals(11, ChatParser.teamSize("11-15 players"));
		assertEquals(-1, ChatParser.teamSize(null));
	}

	@Test
	public void collectionLogAndPets()
	{
		assertEquals("Abyssal whip", ChatParser.collectionLog("New item added to your collection log: Abyssal whip"));
		assertNull(ChatParser.collectionLog("You found something."));
		assertTrue(ChatParser.pet("You have a funny feeling like you're being followed."));
		assertTrue(ChatParser.pet("You feel something weird sneaking into your backpack."));
		assertFalse(ChatParser.pet("You have a funny feeling about this."));
	}

	@Test
	public void diariesAndCombatTasks()
	{
		ChatParser.Named d = ChatParser.diary("Congratulations! You have completed all of the hard tasks in the Karamja area.");

		assertNotNull(d);
		assertEquals("Hard", d.tier);
		assertEquals("Karamja", d.what.replaceAll("\\.$", ""));

		ChatParser.Named c = ChatParser.combatTask("Congratulations, you've completed an elite combat task: Perfect Zulrah (5 points).");

		assertNotNull(c);
		assertEquals("Elite", c.tier);
		assertEquals("Perfect Zulrah", c.what);
	}

	@Test
	public void aReworkedLineIsNoticedButNotMatched()
	{
		String reworded = "Fight length: <col=ff0000>1:23.40</col> (a new personal best)";

		assertNull(ChatParser.time(reworded));
		assertTrue(ChatParser.looksLikeKillLine(reworded));

		// The ones that do match look like kill lines too, which is why the plugin only logs when nothing took the line.
		assertTrue(ChatParser.looksLikeKillLine("Your Zulrah kill count is: 5."));

		assertFalse(ChatParser.looksLikeKillLine("You have a funny feeling like you're being followed."));
		assertFalse(ChatParser.looksLikeKillLine("Your Rooftop Agility lap count is: 12."));
	}

	@Test
	public void questTitles()
	{
		assertEquals("Monkey Madness II", ChatParser.quest("You have completed Monkey Madness II!"));
	}
}
