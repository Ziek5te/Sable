package com.sable;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Rectangle;
import javax.swing.BorderFactory;
import javax.swing.JOptionPane;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/**
 * The Sable side panel: whether the bot is reachable, then the account
 * overview laid out like the game's own Character Summary, then the clan
 * overview. One page, divided by lines.
 */
final class SablePanel extends PluginPanel
{
	/** Everything the panel shows comes from here, so it holds no state of its own. */
	interface Source
	{
		boolean connected();


		ClanInfo clan();

		/** The clan rank of the character that is logged in, read from the game, or null. */
		String liveRank();

		AccountView.Icons icons();

		/** Goes up whenever there is something new to draw, such as a picture that has arrived. */
		long version();

		/** Asks for a fresh clan answer; the panel redraws when it arrives. */
		void refreshClan();

		/** Asks staff to move the member to the other ladder. The callback gets a line to show. */
		void switchLadder(String to, java.util.function.Consumer<String> done);
	}

	private static final Color BAD = new Color(220, 90, 90);

	/** A panel that is as wide as the scroll pane showing it, so nothing scrolls sideways. */
	private static final class Tracking extends JPanel implements Scrollable
	{
		Tracking()
		{
			super(new BorderLayout());
			setOpaque(false);
		}

		@Override
		public Dimension getPreferredScrollableViewportSize()
		{
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle r, int o, int d)
		{
			return 16;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle r, int o, int d)
		{
			return 64;
		}

		@Override
		public boolean getScrollableTracksViewportWidth()
		{
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight()
		{
			return false;
		}
	}

	private final Source source;
	private final JLabel status = new JLabel();
	private final Tracking body = new Tracking();
	private String lastKey = "";

	// The list of every rank starts closed; the heading opens it.
	private boolean ranksOpen = false;

	// How the last ladder switch went, shown under its button until the next redraw after a new answer.
	private String ladderMessage = "";

	SablePanel(Source source)
	{
		super(false);
		this.source = source;

		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		status.setFont(Ui.bold());
		status.setIconTextGap(6);

		try
		{
			status.setIcon(new javax.swing.ImageIcon(net.runelite.client.util.ImageUtil.loadImageResource(SablePanel.class, "/com/sable/panel.png")));
		}
		catch (RuntimeException e)
		{
			// No picture is fine; the status line reads the same without it.
		}
		status.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));
		add(status, BorderLayout.NORTH);

		JScrollPane scroll = new JScrollPane(body);
		scroll.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 6));
		scroll.setOpaque(false);
		scroll.getViewport().setOpaque(false);
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getVerticalScrollBar().setPreferredSize(new Dimension(8, 0));
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		add(scroll, BorderLayout.CENTER);

		refresh(true);
	}

	/** Redraws if anything changed, or always when forced. Safe to call from any thread. */
	void refresh(boolean force)
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			SwingUtilities.invokeLater(() -> refresh(force));

			return;
		}

		ClanInfo clan = source.clan();
		String liveRank = source.liveRank();

		// A key that belongs to other accounts than the character logged in counts as not connected.
		boolean wrongCharacter = clan != null && clan.mismatch;
		boolean connected = source.connected() && !wrongCharacter;
		status.setText("● " + (connected ? "Connected" : "Disconnected"));
		status.setForeground(connected ? Ui.GOOD : BAD);

		String key = connected + "|" + wrongCharacter + "|" + (wrongCharacter ? clan.accounts : "") + "|" + (clan == null ? "-" : clan.points + "/" + clan.days + "/" + clan.pvmRank + "/" + clan.loyaltyRank + "/" + clan.ranks.size() + "/" + clan.pending.size())
			+ "|" + liveRank + "|" + source.version() + "|" + ladderMessage + "|" + ranksOpen + "|"
			+ (clan == null ? "" : clan.recent.size() + (clan.recent.isEmpty() ? "" : "/" + clan.recent.get(0).at) + "/" + clan.accounts + clan.linkInvite + clan.linkEvents + clan.linkAnnouncements + clan.linkLeaderboard) + "|" + (clan == null ? "" : clan.ladderOn + "/" + clan.ladderPending + "/" + clan.ladderTargets.size());

		if (!force && key.equals(lastKey))
		{
			return;
		}

		lastKey = key;

		AccountView.Icons icons = source.icons();

		// A key the bot does not accept, or no bot to reach: the status and how to fix it, and nothing else to mislead.
		if (!connected)
		{
			JPanel off = Ui.column();
			off.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 10));
			off.add(Ui.separator());
			off.add(Ui.note(wrongCharacter
				? "This key is linked to " + (clan.accounts.isEmpty() ? "other accounts" : String.join(", ", clan.accounts)) + ". Log in as that character, or paste your own key in the plugin's settings."
				: "Not connected to the clan's bot. Check the key in the plugin's settings, or try again in a minute."));

			body.removeAll();
			body.add(off, BorderLayout.NORTH);
			body.revalidate();
			body.repaint();

			return;
		}

		// Connected, a line, the clan overview, a line, every rank.
		JPanel page = Ui.column();
		// As much room on the right, before the scroll bar, as there is on the left.
		page.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 10));
		page.add(Ui.separator());

		if (clan != null && !clan.pending.isEmpty())
		{
			page.add(AccountView.attention(clan, icons, SablePanel::openInDiscord, System.currentTimeMillis()));
			page.add(Ui.separator());
		}

		page.add(AccountView.clan(clan, liveRank, icons, this::askToSwitch, ladderMessage));
		// Right under the last line of the block, not floating below it.
		page.add(Ui.separator(2));

		if (clan != null && !clan.recent.isEmpty())
		{
			page.add(AccountView.recent(clan, icons, System.currentTimeMillis(), SablePanel::openInDiscord));
			page.add(Ui.separator());
		}

		JPanel links = AccountView.links(clan, SablePanel::openInDiscord);

		if (links.getComponentCount() > 0)
		{
			page.add(links);
			page.add(Ui.separator());
		}

		page.add(AccountView.ranks(clan, icons, ranksOpen, () ->
		{
			ranksOpen = !ranksOpen;
			refresh(true);
		}, liveRank));

		body.removeAll();
		body.add(page, BorderLayout.NORTH);
		body.revalidate();
		body.repaint();
	}

	/** Opens a Discord link in the browser, through RuneLite's own link opener. */
	private static void openInDiscord(String url)
	{
		net.runelite.client.util.LinkBrowser.browse(url);
	}

	/** The button: a question first, since it files a card for staff. */
	private void askToSwitch(String to)
	{
		String name = "pvm".equals(to) ? "PvM" : "Loyalty";
		int answer = JOptionPane.showConfirmDialog(
			this,
			"Ask staff to move you to the " + name + " ladder?\nThey set your rank in game, and you are told in Discord.",
			"Switch ladder",
			JOptionPane.YES_NO_OPTION);

		if (answer != JOptionPane.YES_OPTION)
		{
			return;
		}

		ladderMessage = "Asking...";
		refresh(true);

		source.switchLadder(to, line ->
		{
			ladderMessage = line;
			source.refreshClan();
			refresh(true);
		});
	}

	@Override
	public void onActivate()
	{
		source.refreshClan();
		refresh(true);
	}
}
