package com.sable;

import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * The panel's two sections: the clan overview, and every rank in the clan under it.
 */
final class AccountView
{
	/** Pictures for the panel. Either may be missing while it loads, and a missing picture is left out. */
	interface Icons
	{
		/** One of the game's own sprites. */
		Icon sprite(int id);

		/** A small picture from an address (the clan's rank emoji). */
		Icon image(String url);

		/** An item's own picture, such as the Collection Log book. */
		Icon item(int id);

		/** The picture of an item found by its name; missing until it has been looked up. */
		Icon itemNamed(String name);
	}

	/** The game's own sprite the points tile uses. Number from RuneLite's SpriteID. */
	static final class Sprites
	{
		static final int CLAN = 2307;

		private Sprites()
		{
		}
	}

	private AccountView()
	{
	}

	private static Icon sprite(Icons icons, int id)
	{
		return icons == null ? null : icons.sprite(id);
	}

	private static String number(long n)
	{
		return String.format(Locale.US, "%,d", n);
	}

	/** Which ladder the main account is on, in words. Empty when the bot has not said. */
	static String ladderText(ClanInfo clan)
	{
		if (clan == null || clan.ladderOn == null)
		{
			return "";
		}

		if (!clan.inClan)
		{
			return "Your main account is not in the clan.";
		}

		switch (clan.ladderOn)
		{
			case "pvm":
				return "Your main account is on the PvM ladder.";
			case "loyalty":
				return "Your main account is on the Loyalty ladder.";
			case "charter":
				return "Charter: outside both ladders.";
			case "manual":
				return "";
			default:
				return "Your main account has no rank in game yet.";
		}
	}

	private static String ladderName(String to)
	{
		return "pvm".equals(to) ? "PvM" : "Loyalty";
	}

	/** A button for each ladder the member could move to, or a line saying a move is already waiting. */
	private static JComponent switcher(ClanInfo c, Consumer<String> onSwitch, String message)
	{
		JPanel p = Ui.column();

		if (c.ladderPending != null)
		{
			p.add(Ui.note("A move to " + c.ladderPending + " is waiting for staff to set it in game."));
		}

		// Only somebody on a ladder, or on Charter, has one to change.
		boolean hasLadder = "pvm".equals(c.ladderOn) || "loyalty".equals(c.ladderOn) || "charter".equals(c.ladderOn);

		// Charter sits outside both ladders, so it always shows both ways in, the one without a rank earned yet greyed out.
		if (onSwitch != null && "charter".equals(c.ladderOn) && c.inClan && c.ladderPending == null)
		{
			for (String to : new String[] {"pvm", "loyalty"})
			{
				ClanInfo.Target target = null;

				for (ClanInfo.Target t : c.ladderTargets)
				{
					if (to.equals(t.to))
					{
						target = t;
					}
				}

				Ui.gap(p, 6);
				JButton b = new JButton("Change to " + ladderName(to) + " ladder");
				b.setFocusable(false);
				b.setAlignmentX(JComponent.LEFT_ALIGNMENT);
				b.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 28));

				if (target == null)
				{
					b.setEnabled(false);
					p.add(b);
					Ui.gap(p, 2);
					p.add(Ui.note("You have not earned a " + ladderName(to) + " rank yet."));
				}
				else
				{
					final String go = target.to;
					b.setToolTipText("Asks staff to move you to the " + ladderName(go) + " ladder as " + target.rankName);
					b.addActionListener(e -> onSwitch.accept(go));
					p.add(b);
					Ui.gap(p, 2);
					p.add(Ui.note("Moves you to the " + ladderName(go) + " ladder as " + target.rankName + ". Staff set it in game; the bot never does."));
				}
			}
		}
		else if (onSwitch != null && hasLadder && c.inClan && c.ladderPending == null)
		{
			if (c.ladderTargets.isEmpty())
			{
				Ui.gap(p, 6);
				JButton none = new JButton("Change ladder");
				none.setFocusable(false);
				none.setEnabled(false);
				none.setAlignmentX(JComponent.LEFT_ALIGNMENT);
				none.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 28));
				p.add(none);
				Ui.gap(p, 2);
				p.add(Ui.note("You have not earned a rank on the other ladder yet."));
			}

			for (ClanInfo.Target t : c.ladderTargets)
			{
				Ui.gap(p, 6);
				// One other ladder is just "Change ladder"; Charter can go either way, so each says where.
				JButton b = new JButton(c.ladderTargets.size() == 1 ? "Change ladder" : "Change ladder to " + ladderName(t.to));
				b.setFocusable(false);
				b.setAlignmentX(JComponent.LEFT_ALIGNMENT);
				b.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, 28));
				b.setToolTipText("Asks staff to move you to the " + ladderName(t.to) + " ladder as " + t.rankName);
				b.addActionListener(e -> onSwitch.accept(t.to));
				p.add(b);
				Ui.gap(p, 2);
				p.add(Ui.note("Moves you to the " + ladderName(t.to) + " ladder as " + t.rankName + ". Staff set it in game; the bot never does."));
			}
		}

		if (message != null && !message.isEmpty())
		{
			Ui.gap(p, 4);
			p.add(Ui.label(message, Ui.small(), Ui.GOLD));
		}

		return p;
	}

	private static JPanel progress(Icons icons, ClanInfo c, String title, String gameRank, long value, long from, long at, String next, long left, String unit)
	{
		JPanel p = Ui.column();
		Icon icon = icons == null ? null : icons.image(c.rankIcon(gameRank));

		if (next == null)
		{
			p.add(new Ui.Progress(title, "Top of the ladder", 1, icon));
		}
		else
		{
			double f = at > from ? (value - from) / (double) (at - from) : 0;
			p.add(new Ui.Progress(title, number(left) + " " + unit + " to " + next, f, icon));
		}

		return p;
	}

	/** Drops waiting on the member, each with its picture and a way to the Discord message that asks. Nothing at all when there are none. */
	static JPanel attention(ClanInfo c, Icons icons, Consumer<String> onOpen, long now)
	{
		JPanel p = Ui.column();

		if (c == null || c.pending.isEmpty())
		{
			return p;
		}

		p.add(Ui.heading("Needs attention"));
		Ui.gap(p, 6);

		for (ClanInfo.Pending d : c.pending)
		{
			p.add(new Ui.Row(d.item, icons == null ? null : icons.itemNamed(d.item)));
			p.add(Ui.note(left(d.until, now) + " to say how you ran it. " + number(d.points) + " points now, " + number(d.full) + " if you were alone or with the clan."));
		}

		if (c.dmUrl != null && onOpen != null)
		{
			Ui.gap(p, 4);
			JButton open = new JButton("Open in Discord");
			open.setAlignmentX(JComponent.LEFT_ALIGNMENT);
			open.addActionListener(e -> onOpen.accept(c.dmUrl));
			p.add(open);
		}

		return p;
	}

	/** How long is left, in the words a person would use. */
	static String left(long until, long now)
	{
		long minutes = Math.max(0, (until - now) / 60_000);

		return minutes >= 60 ? (minutes / 60) + " h left" : minutes + " min left";
	}

	/** Every rank in the clan, open. */
	static JPanel ranks(ClanInfo c, Icons icons)
	{
		return ranks(c, icons, true, null, null);
	}

	static JPanel ranks(ClanInfo c, Icons icons, boolean open, Runnable onToggle)
	{
		return ranks(c, icons, open, onToggle, null);
	}

	/**
	 * Every rank in the clan, one line each: the picture, the name and what earns it.
	 *
	 * The heading opens and closes it, so the panel stays short until somebody wants the list.
	 * The ranks the member holds are picked out in gold.
	 */
	static JPanel ranks(ClanInfo c, Icons icons, boolean open, Runnable onToggle, String liveRank)
	{
		JPanel p = Ui.column();
		p.add(Ui.toggle("All ranks", open, onToggle));

		if (!open)
		{
			return p;
		}

		Ui.gap(p, 6);

		if (c == null || c.ranks.isEmpty())
		{
			p.add(Ui.note("The clan's ranks are not loaded yet."));

			return p;
		}

		String group = null;

		for (ClanInfo.RankRow r : c.ranks)
		{
			if (!r.group.equals(group))
			{
				if (group != null)
				{
					Ui.gap(p, 8);
				}

				p.add(Ui.label(r.group, Ui.small(), Ui.MUTED));
				Ui.gap(p, 2);
				group = r.group;
			}

			// The ranks that are yours: the two ladder ranks, and whatever the game says this character holds (staff, Charter, Echo).
			boolean mine = r.name.equals(c.pvmRank) || r.name.equals(c.loyaltyRank) || (liveRank != null && !r.game.isEmpty() && rankKey(r.game).equals(rankKey(liveRank)) && !isLadder(r.group));

			Ui.Line line = new Ui.Line(r.name, shorten(r.how), icons == null ? null : icons.image(c.rankIcon(r.game)), mine ? Ui.GOLD : Ui.TEXT, Ui.MUTED, false);
			p.add(mine ? line.marked() : line);
		}

		return p;
	}

	private static String rankKey(String rank)
	{
		return rank == null ? "" : rank.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
	}

	private static boolean isLadder(String group)
	{
		return "Loyalty".equals(group) || "PvM".equals(group);
	}

	/** "30 days in the clan" and "500 clan points" as "30 days" and "500 pts", and the longer sentences as a few words, so a rank fits on one line. */
	static String shorten(String how)
	{
		switch (how)
		{
			case "Runs the place.":
				return "Staff";
			case "Here in the first week. Never given again.":
				return "First week only";
			case "A second account. Ranks go on your main.":
				return "Second account";
			case "Won boss of the week.":
				return "Boss of the week";
			case "Won skill of the week.":
				return "Skill of the week";
			default:
				return how.replace(" days in the clan", " days").replace(" clan points", " pts");
		}
	}

	/** The member's last few awards, newest first, each with the points and how long ago. Nothing at all when there are none. */
	static JPanel recent(ClanInfo c, Icons icons, long now)
	{
		return recent(c, icons, now, null);
	}

	/** The same, with each row that has a Discord post opening it when clicked. */
	static JPanel recent(ClanInfo c, Icons icons, long now, Consumer<String> onOpen)
	{
		JPanel p = Ui.column();

		if (c == null || c.recent.isEmpty())
		{
			return p;
		}

		p.add(Ui.heading("Recent points"));
		Ui.gap(p, 4);

		for (ClanInfo.Recent r : c.recent)
		{
			Ui.Line line = new Ui.Line(r.what, (r.points > 0 ? "+" : "") + number(r.points) + "  " + ago(r.at, now), icons == null ? null : icons.itemNamed(r.what), Ui.TEXT, Ui.MUTED, false);
			boolean link = onOpen != null && r.url != null;
			String hint = link ? "Click to open the post in Discord" : null;
			line.setToolTipText(r.detail.isEmpty() ? hint : (hint == null ? r.detail : r.detail + " \u00B7 " + hint));

			if (link)
			{
				line.clickable(() -> onOpen.accept(r.url));
			}

			p.add(line);
		}

		return p;
	}

	/** How long ago, in the words a person would use. */
	static String ago(long at, long now)
	{
		long minutes = Math.max(0, (now - at) / 60_000);

		if (minutes < 60)
		{
			return minutes + "m";
		}

		long hours = minutes / 60;

		return hours < 24 ? hours + "h" : (hours / 24) + "d";
	}

	/** The quick links the clan has set, as buttons. Nothing at all when none is. */
	static JPanel links(ClanInfo c, Consumer<String> onOpen)
	{
		JPanel p = Ui.column();

		if (c == null || onOpen == null)
		{
			return p;
		}

		String[][] all = {
			{"Discord invite", c.linkInvite},
			{"Events", c.linkEvents},
			{"Announcements", c.linkAnnouncements},
			{"Leaderboard", c.linkLeaderboard},
		};

		JPanel grid = new JPanel(new java.awt.GridLayout(0, 2, 4, 4));
		grid.setOpaque(false);
		grid.setAlignmentX(JComponent.LEFT_ALIGNMENT);

		int shown = 0;

		for (String[] link : all)
		{
			if (link[1] == null || link[1].isEmpty())
			{
				continue;
			}

			JButton b = new JButton(link[0]);
			b.setFocusable(false);
			b.setFont(Ui.small());
			b.setMargin(new java.awt.Insets(2, 2, 2, 2));
			b.addActionListener(e -> onOpen.accept(link[1]));
			grid.add(b);
			shown++;
		}

		if (shown == 0)
		{
			return p;
		}

		p.add(Ui.heading("Links"));
		Ui.gap(p, 4);
		p.add(grid);

		return p;
	}

	/** The clan overview: the character's rank in game, then the main account's points and progress. */
	static JPanel clan(ClanInfo c, String liveRank, Icons icons)
	{
		return clan(c, liveRank, icons, null, null);
	}

	/** The same, with the button that moves the member to the other ladder, and the line that says how it went. */
	static JPanel clan(ClanInfo c, String liveRank, Icons icons, Consumer<String> onSwitch, String message)
	{
		JPanel p = Ui.column();
		// Only the rank belongs to the character that is logged in, straight from the game.
		p.add(Ui.heading("This character"));
		Ui.gap(p, 6);

		Icon rankIcon = c == null || icons == null ? null : icons.image(c.rankIcon(liveRank));
		p.add(new Ui.Row(liveRank == null ? "Not in the clan" : liveRank, rankIcon));
		Ui.gap(p, 2);
		p.add(Ui.note("Its rank in the clan. Nothing else here changes with the character you log in as."));

		// Everything else belongs to the member, whichever account they are on.
		p.add(Ui.separator());
		p.add(Ui.heading("Your clan profile"));
		Ui.gap(p, 4);

		if (c == null)
		{
			p.add(Ui.note("Clan points and rank progress are not loaded yet."));

			return p;
		}

		p.add(Ui.note(c.accounts.isEmpty()
			? "These are your points and progress, whichever character you log in as."
			: "These are your points and progress, whichever character you log in as. Linked accounts: " + String.join(", ", c.accounts) + "."));
		Ui.gap(p, 8);

		// The number members look for first: larger, in gold, with the same space above and below.
		p.add(new Ui.Line("Clan points", number(c.points), null, Ui.TEXT, Ui.GOLD, true).big());
		Ui.gap(p, 8);

		p.add(progress(icons, c, "PvM \u00B7 " + (c.pvmRank == null ? "none yet" : c.pvmRank), c.pvmGame, c.points, c.pvmFrom, c.pvmAt, c.pvmNext, c.pvmLeft, "pts"));

		if (c.days != null)
		{
			Ui.gap(p, 6);
			p.add(progress(icons, c, "Loyalty \u00B7 " + (c.loyaltyRank == null ? "none yet" : c.loyaltyRank), c.loyaltyGame, c.days, c.loyaltyFrom, c.loyaltyAt, c.loyaltyNext, c.loyaltyLeft, "days"));
		}

		// The way to the other ladder, and a line only when there is something to say about it.
		String ladder = ladderText(c);

		if (!ladder.isEmpty())
		{
			Ui.gap(p, 8);
			p.add(Ui.note(ladder));
		}

		p.add(switcher(c, onSwitch, message));

		return p;
	}
}
