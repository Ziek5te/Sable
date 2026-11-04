package com.sable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.JsonParser;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.Test;

public class AccountViewTest
{
	private static <T> List<T> find(Container c, Class<T> type)
	{
		List<T> out = new ArrayList<>();

		for (Component child : c.getComponents())
		{
			if (type.isInstance(child))
			{
				out.add(type.cast(child));
			}

			if (child instanceof Container)
			{
				out.addAll(find((Container) child, type));
			}
		}

		return out;
	}

	/** The mouse listeners a line has put there itself, not the ones Swing adds for a tooltip. */
	private static List<java.awt.event.MouseListener> clickListeners(Component c)
	{
		List<java.awt.event.MouseListener> out = new ArrayList<>();

		for (java.awt.event.MouseListener l : c.getMouseListeners())
		{
			if (!l.getClass().getName().contains("ToolTipManager"))
			{
				out.add(l);
			}
		}

		return out;
	}

	private static void layoutAll(Container c)
	{
		c.doLayout();

		for (Component child : c.getComponents())
		{
			if (child instanceof Container)
			{
				layoutAll((Container) child);
			}
		}
	}

	private static Icon dot(Color color)
	{
		BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setColor(color);
		g.fillOval(1, 1, 14, 14);
		g.dispose();

		return new ImageIcon(image);
	}

	private static AccountView.Icons testIcons()
	{
		return new AccountView.Icons()
		{
			@Override
			public Icon sprite(int id)
			{
				return dot(new Color(180, 80, 200));
			}

			@Override
			public Icon image(String url)
			{
				return url == null ? null : dot(new Color(60, 180, 120));
			}

			@Override
			public Icon item(int id)
			{
				return dot(new Color(200, 120, 70));
			}

			@Override
			public Icon itemNamed(String name)
			{
				return dot(new Color(200, 120, 70));
			}
		};
	}

	private static ClanInfo clan()
	{
		return ClanInfo.fromJson(new JsonParser().parse(
			"{\"points\":{\"total\":650,\"month\":40},"
				+ "\"pvm\":{\"rank\":\"Bladed\",\"game\":\"Duellist\",\"from\":500,\"next\":{\"name\":\"Tempered\",\"left\":350,\"at\":1000}},"
				+ "\"loyalty\":{\"days\":85,\"rank\":\"Sworn\",\"game\":\"Steel\",\"from\":30,\"next\":{\"name\":\"Stalwart\",\"left\":5,\"at\":90}},"
				+ "\"ladder\":{\"inClan\":true,\"on\":\"loyalty\",\"pending\":null,\"targets\":[{\"to\":\"pvm\",\"rankName\":\"Bladed\"}]},"
				+ "\"ranks\":[{\"group\":\"Loyalty\",\"name\":\"Sworn\",\"game\":\"Steel\",\"how\":\"30 days in the clan\"},{\"group\":\"PvM\",\"name\":\"Bladed\",\"game\":\"Duellist\",\"how\":\"500 clan points\"},{\"group\":\"Won at clan events\",\"name\":\"Echo\",\"game\":\"Priest\",\"how\":\"\"}],"
				+ "\"rankIcons\":{\"duellist\":\"https://cdn.discordapp.com/emojis/1.png?size=32\",\"steel\":\"https://cdn.discordapp.com/emojis/2.png?size=32\"}}").getAsJsonObject());
	}

	private static ClanInfo on(String on)
	{
		return ClanInfo.fromJson(new JsonParser().parse("{\"ladder\":{\"inClan\":true,\"on\":\"" + on + "\",\"targets\":[]}}").getAsJsonObject());
	}

	@Test
	public void theWordsSayWhichLadderTheMainAccountIsOn()
	{
		assertEquals("Your main account is on the PvM ladder.", AccountView.ladderText(on("pvm")));
		assertEquals("Your main account is on the Loyalty ladder.", AccountView.ladderText(on("loyalty")));
		assertEquals("", AccountView.ladderText(on("manual")));
		assertEquals("Charter: outside both ladders.", AccountView.ladderText(on("charter")));
		assertEquals("Your main account has no rank in game yet.", AccountView.ladderText(on("none")));
		assertEquals("Your main account is not in the clan.",
			AccountView.ladderText(ClanInfo.fromJson(new JsonParser().parse("{\"ladder\":{\"inClan\":false,\"on\":\"none\",\"targets\":[]}}").getAsJsonObject())));
		// Until the bot says, nothing is claimed.
		assertEquals("", AccountView.ladderText(null));
		assertEquals("", AccountView.ladderText(ClanInfo.fromJson(new JsonParser().parse("{}").getAsJsonObject())));
	}

	@Test
	public void theSwitchButtonIsThereOnlyWhenThereIsSomewhereToGoAndAsksForThatLadder() throws Exception
	{
		System.setProperty("java.awt.headless", "true");

		String[] asked = {null};
		JPanel with = AccountView.clan(clan(), "Bronze", testIcons(), to -> asked[0] = to, "");
		List<javax.swing.JButton> buttons = find(with, javax.swing.JButton.class);

		assertEquals(1, buttons.size());
		assertEquals("Change ladder", buttons.get(0).getText());
		buttons.get(0).doClick();
		assertEquals("pvm", asked[0]);

		// Nowhere to go: no button.
		assertEquals(0, find(AccountView.clan(on("manual"), "Owner", testIcons(), to -> { }, ""), javax.swing.JButton.class).size());

		// A move already waiting: no button, and a line saying so.
		ClanInfo waiting = ClanInfo.fromJson(new JsonParser().parse("{\"ladder\":{\"inClan\":true,\"on\":\"loyalty\",\"pending\":\"Bladed\",\"targets\":[]}}").getAsJsonObject());
		JPanel pending = AccountView.clan(waiting, "Bronze", testIcons(), to -> { }, "");
		assertEquals(0, find(pending, javax.swing.JButton.class).size());
		assertTrue(find(pending, Ui.Note.class).size() >= 2);

		// On a ladder with nothing earned on the other one: the button is there but cannot be pressed, and a line says why.
		ClanInfo nothing = ClanInfo.fromJson(new JsonParser().parse("{\"ladder\":{\"inClan\":true,\"on\":\"loyalty\",\"pending\":null,\"targets\":[]}}").getAsJsonObject());
		List<javax.swing.JButton> disabled = find(AccountView.clan(nothing, "Bronze", testIcons(), to -> { }, ""), javax.swing.JButton.class);
		assertEquals(1, disabled.size());
		assertEquals(false, disabled.get(0).isEnabled());
		assertEquals("Change ladder", disabled.get(0).getText());

		// Charter can go either way, so each button says where.
		ClanInfo charter = ClanInfo.fromJson(new JsonParser().parse("{\"ladder\":{\"inClan\":true,\"on\":\"charter\",\"pending\":null,\"targets\":[{\"to\":\"pvm\",\"rankName\":\"Bladed\"},{\"to\":\"loyalty\",\"rankName\":\"Sworn\"}]}}").getAsJsonObject());
		List<javax.swing.JButton> both = find(AccountView.clan(charter, "Astral", testIcons(), to -> { }, ""), javax.swing.JButton.class);
		assertEquals(2, both.size());
		assertEquals("Change to PvM ladder", both.get(0).getText());
		assertEquals("Change to Loyalty ladder", both.get(1).getText());
		assertTrue(both.get(0).isEnabled() && both.get(1).isEnabled());

		// A Charter who has earned only one still sees both buttons, the other greyed out.
		ClanInfo charterOne = ClanInfo.fromJson(new JsonParser().parse("{\"ladder\":{\"inClan\":true,\"on\":\"charter\",\"pending\":null,\"targets\":[{\"to\":\"loyalty\",\"rankName\":\"Sworn\"}]}}").getAsJsonObject());
		List<javax.swing.JButton> one = find(AccountView.clan(charterOne, "Astral", testIcons(), to -> { }, ""), javax.swing.JButton.class);
		assertEquals(2, one.size());
		assertEquals("Change to PvM ladder", one.get(0).getText());
		assertEquals(false, one.get(0).isEnabled());
		assertEquals(true, one.get(1).isEnabled());

		// A panel that cannot switch (the picture) shows no button either.
		assertEquals(0, find(AccountView.clan(clan(), "Bronze", testIcons()), javax.swing.JButton.class).size());
	}

	@Test
	public void everyRankIsListedUnderItsGroupWithItsPictureAndHowItIsEarned() throws Exception
	{
		System.setProperty("java.awt.headless", "true");

		JPanel ranks = AccountView.ranks(clan(), testIcons());
		layoutAll(ranks);

		// One line per rank, with no note under it.
		assertEquals(3, find(ranks, Ui.Line.class).size());
		assertEquals(0, find(ranks, Ui.Note.class).size());

		// Closed, only the heading shows; the heading's click opens it.
		boolean[] open = {false};
		JPanel closed = AccountView.ranks(clan(), testIcons(), false, () -> open[0] = true);
		assertEquals(0, find(closed, Ui.Line.class).size());
		JLabel heading = find(closed, JLabel.class).get(0);
		assertTrue(heading.getText().contains("ALL RANKS"));
		for (java.awt.event.MouseListener l : heading.getMouseListeners())
		{
			l.mouseClicked(null);
		}
		assertTrue(open[0]);

		// Before the bot has said, or with nothing loaded, it says so instead of failing.
		assertEquals(0, find(AccountView.ranks(null, null), Ui.Line.class).size());
		assertTrue(find(AccountView.ranks(null, null), Ui.Note.class).size() == 1);
		assertTrue(AccountView.clan(null, null, null).getPreferredSize().height > 20);

		assertEquals("Staff", AccountView.shorten("Runs the place."));
		assertEquals("First week only", AccountView.shorten("Here in the first week. Never given again."));
		assertEquals("Second account", AccountView.shorten("A second account. Ranks go on your main."));
		assertEquals("Boss of the week", AccountView.shorten("Won boss of the week."));

		// Yours stand out: the two ladder ranks, and the character's own game rank for staff, Charter and Echo.
		JPanel mine = AccountView.ranks(clan(), testIcons(), true, null, "Priest");
		layoutAll(mine);
		assertEquals(3, find(mine, Ui.Line.class).size());

		assertEquals("30 days", AccountView.shorten("30 days in the clan"));
		assertEquals("1,000 pts", AccountView.shorten("1,000 clan points"));
	}

	@Test
	public void theProfileNamesEveryLinkedAccountAndTheRankIsTheOnlyThingTiedToTheCharacter() throws Exception
	{
		System.setProperty("java.awt.headless", "true");

		ClanInfo c = ClanInfo.fromJson(new JsonParser().parse("{\"accounts\":[\"Ziek5te\",\"Z 5 S\"],\"points\":{\"total\":5}}").getAsJsonObject());
		assertEquals(2, c.accounts.size());
		assertEquals("Ziek5te", c.accounts.get(0));
		assertEquals(0, ClanInfo.fromJson(new JsonParser().parse("{}").getAsJsonObject()).accounts.size());

		JPanel shown = AccountView.clan(c, "Owner", testIcons());
		layoutAll(shown);

		// Two headings: the character's rank, then the member's own profile.
		List<String> headings = new ArrayList<>();

		for (JLabel l : find(shown, JLabel.class))
		{
			headings.add(l.getText());
		}

		assertTrue(headings.contains("THIS CHARACTER"));
		assertTrue(headings.contains("YOUR CLAN PROFILE"));
		assertEquals(1, find(shown, Ui.Row.class).size());
	}

	@Test
	public void recentPointsAreListedNewestFirstAndTheLinksOnlyWhereTheClanSetOne() throws Exception
	{
		System.setProperty("java.awt.headless", "true");

		long now = 10 * 3_600_000L;
		ClanInfo c = ClanInfo.fromJson(new JsonParser().parse(
			"{\"recent\":[{\"what\":\"Twisted bow\",\"points\":25,\"detail\":\"Drop on Alpha \\u00B7 Code ABC1234\",\"url\":\"https://discord.com/channels/1/2/3\",\"at\":" + (now - 90 * 60_000L) + "},{\"what\":\"Pet\",\"points\":10,\"at\":" + (now - 5 * 60_000L) + "}],"
				+ "\"links\":{\"invite\":\"https://discord.gg/x\",\"events\":null,\"announcements\":\"https://discord.com/channels/1/2\",\"leaderboard\":null}}").getAsJsonObject());

		String[] clicked = {null};
		JPanel recent = AccountView.recent(c, testIcons(), now, url -> clicked[0] = url);
		layoutAll(recent);

		// Only the row that has a post is a link; clicking it hands over the address.
		List<Ui.Line> rows = find(recent, Ui.Line.class);
		assertEquals(1, clickListeners(rows.get(0)).size());
		assertEquals(0, clickListeners(rows.get(1)).size());
		clickListeners(rows.get(0)).get(0).mouseClicked(null);
		assertEquals("https://discord.com/channels/1/2/3", clicked[0]);
		assertTrue(rows.get(0).getToolTipText().contains("Click to open the post in Discord"));

		// Without a way to open it, no row is a link.
		assertEquals(0, clickListeners(find(AccountView.recent(c, testIcons(), now), Ui.Line.class).get(0)).size());
		assertEquals(2, find(recent, Ui.Line.class).size());
		assertEquals("Drop on Alpha · Code ABC1234 · Click to open the post in Discord", find(recent, Ui.Line.class).get(0).getToolTipText());
		assertEquals(null, find(recent, Ui.Line.class).get(1).getToolTipText());
		assertEquals(0, AccountView.recent(clan(), testIcons(), now).getComponentCount());
		assertEquals(0, AccountView.recent(null, null, 0).getComponentCount());

		assertEquals("5m", AccountView.ago(now - 5 * 60_000L, now));
		assertEquals("1h", AccountView.ago(now - 90 * 60_000L, now));
		assertEquals("3d", AccountView.ago(now - 3 * 24 * 3_600_000L, now));

		String[] opened = {null};
		JPanel links = AccountView.links(c, url -> opened[0] = url);
		List<javax.swing.JButton> buttons = find(links, javax.swing.JButton.class);
		assertEquals(2, buttons.size());
		assertEquals("Discord invite", buttons.get(0).getText());
		assertEquals("Announcements", buttons.get(1).getText());
		buttons.get(1).doClick();
		assertEquals("https://discord.com/channels/1/2", opened[0]);

		// Nothing set, nothing shown: not even a heading.
		assertEquals(0, AccountView.links(clan(), url -> { }).getComponentCount());
		assertEquals(0, AccountView.links(null, null).getComponentCount());
	}

	@Test
	public void dropsWaitingOnTheMemberAreListedWithAWayToTheDiscordMessageAndNothingWhenThereAreNone() throws Exception
	{
		System.setProperty("java.awt.headless", "true");

		ClanInfo waiting = ClanInfo.fromJson(new JsonParser().parse(
			"{\"pending\":[{\"item\":\"Twisted bow\",\"points\":12,\"full\":25,\"until\":" + (System.currentTimeMillis() + 20 * 3_600_000L) + "}],"
				+ "\"dmUrl\":\"https://discord.com/channels/@me/1\"}").getAsJsonObject());

		String[] opened = {null};
		JPanel shown = AccountView.attention(waiting, testIcons(), url -> opened[0] = url, System.currentTimeMillis());
		layoutAll(shown);

		assertEquals(1, find(shown, Ui.Row.class).size());
		assertEquals(1, find(shown, Ui.Note.class).size());
		List<javax.swing.JButton> buttons = find(shown, javax.swing.JButton.class);
		assertEquals(1, buttons.size());
		assertEquals("Open in Discord", buttons.get(0).getText());
		buttons.get(0).doClick();
		assertEquals("https://discord.com/channels/@me/1", opened[0]);

		// Nothing waiting, nothing shown: not even a heading.
		assertEquals(0, AccountView.attention(clan(), testIcons(), url -> { }, System.currentTimeMillis()).getComponentCount());
		assertEquals(0, AccountView.attention(null, null, null, 0).getComponentCount());

		assertEquals("20 h left", AccountView.left(20 * 3_600_000L + 5, 0));
		assertEquals("5 min left", AccountView.left(5 * 60_000L, 0));
	}

	@Test
	public void thePanelIsOnePageWithLinesBetweenItsSections() throws Exception
	{
		System.setProperty("java.awt.headless", "true");

		ClanInfo c = clan();

		SablePanel[] holder = new SablePanel[1];
		SwingUtilities.invokeAndWait(() -> holder[0] = new SablePanel(new SablePanel.Source()
		{
			@Override
			public boolean connected()
			{
				return true;
			}

			@Override
			public ClanInfo clan()
			{
				return c;
			}

			@Override
			public String liveRank()
			{
				return "Squire";
			}

			@Override
			public AccountView.Icons icons()
			{
				return testIcons();
			}

			@Override
			public long version()
			{
				return 0;
			}

			@Override
			public void refreshClan()
			{
			}

			@Override
			public void switchLadder(String to, java.util.function.Consumer<String> done)
			{
			}
		}));

		SablePanel panel = holder[0];
		panel.setSize(225, 900);
		layoutAll(panel);

		boolean connected = false;

		for (JLabel l : find(panel, JLabel.class))
		{
			connected |= l.getText() != null && l.getText().contains("Connected");
		}

		assertTrue("the status line says Connected", connected);
		assertEquals("a line under the status, between character and profile, and one before the ranks", 3, count(panel, "separator"));
		// The points line and the character's own rank; the list of ranks starts closed.
		assertEquals(1, find(panel, Ui.Line.class).size());
		assertEquals(1, find(panel, Ui.Row.class).size());
		assertNotNull(panel);

		new File("build").mkdirs();
		BufferedImage image = new BufferedImage(225, 760, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		g.setColor(new Color(40, 40, 40));
		g.fillRect(0, 0, 225, 760);
		panel.setSize(225, 760);
		layoutAll(panel);
		panel.paint(g);
		g.dispose();
		ImageIO.write(image, "png", new File("build/panel.png"));
		Files.size(new File("build/panel.png").toPath());
	}

	@Test
	public void aPanelThatIsNotConnectedShowsOnlyTheStatusAndTheHint() throws Exception
	{
		System.setProperty("java.awt.headless", "true");

		ClanInfo stale = clan();

		SablePanel[] holder = new SablePanel[1];
		SwingUtilities.invokeAndWait(() -> holder[0] = new SablePanel(new SablePanel.Source()
		{
			@Override
			public boolean connected()
			{
				return false;
			}

			@Override
			public ClanInfo clan()
			{
				return stale;
			}

			@Override
			public String liveRank()
			{
				return "Squire";
			}

			@Override
			public AccountView.Icons icons()
			{
				return testIcons();
			}

			@Override
			public long version()
			{
				return 0;
			}

			@Override
			public void refreshClan()
			{
			}

			@Override
			public void switchLadder(String to, java.util.function.Consumer<String> done)
			{
			}
		}));

		SablePanel panel = holder[0];
		panel.setSize(225, 400);
		layoutAll(panel);

		boolean disconnected = false;

		for (JLabel l : find(panel, JLabel.class))
		{
			disconnected |= l.getText() != null && l.getText().contains("Disconnected");
		}

		assertTrue("the status line says Disconnected", disconnected);
		// Nothing left over from an earlier answer: no rank, no points, no ranks list.
		assertEquals(0, find(panel, Ui.Row.class).size());
		assertEquals(0, find(panel, Ui.Line.class).size());
		assertEquals(0, find(panel, Ui.Progress.class).size());
		assertEquals(1, find(panel, Ui.Note.class).size());
	}

	/** Components that are anonymous separators: the ones with no class name of their own and a thin fixed height. */
	private static int count(Container c, String what)
	{
		int n = 0;

		for (Component child : find(c, Component.class))
		{
			if (what.equals("separator") && child.getClass().isAnonymousClass() && child.getPreferredSize().height == 13)
			{
				n++;
			}
		}

		return n;
	}
}
