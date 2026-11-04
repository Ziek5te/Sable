package com.sable;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;

/**
 * The few things the panel is drawn from: stat tiles like the game's own
 * Character Summary, progress bars, a row for a rank, notes and the lines
 * between sections. Painted by hand so they sit the same at any scale.
 */
final class Ui
{
	static final Color GOLD = new Color(220, 180, 90);
	static final Color TEXT = new Color(235, 235, 235);
	static final Color MUTED = new Color(160, 160, 160);
	static final Color TRACK = new Color(255, 255, 255, 24);
	static final Color CARD = new Color(48, 48, 48);
	static final Color GOOD = new Color(90, 200, 110);
	// The green the game writes its summary figures in.
	static final Color VALUE = new Color(70, 225, 70);
	static final Color LINE = new Color(90, 90, 90);

	private Ui()
	{
	}

	// The system's clean sans-serif rather than the game's pixel font, which was too jagged to read at panel size.
	static Font small()
	{
		return new Font(Font.SANS_SERIF, Font.PLAIN, 11);
	}

	static Font bold()
	{
		return new Font(Font.SANS_SERIF, Font.BOLD, 12);
	}

	private static void quality(Graphics2D g)
	{
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
	}

	/** Text cut to fit a width, with an ellipsis. */
	private static String fit(String text, FontMetrics fm, int width)
	{
		if (fm.stringWidth(text) <= width)
		{
			return text;
		}

		String s = text;

		while (s.length() > 1 && fm.stringWidth(s + "…") > width)
		{
			s = s.substring(0, s.length() - 1);
		}

		return s + "…";
	}

	/** A vertical stack that stretches to the width it is given. */
	static JPanel column()
	{
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setOpaque(false);

		return p;
	}

	static void gap(JPanel p, int px)
	{
		p.add(Box.createVerticalStrut(px));
	}

	static JLabel label(String text, Font font, Color color)
	{
		JLabel l = new JLabel(text);
		l.setFont(font);
		l.setForeground(color);
		l.setAlignmentX(JComponent.LEFT_ALIGNMENT);

		return l;
	}

	/** A section's name in small gold capitals. */
	static JComponent heading(String text)
	{
		return label(text.toUpperCase(), small().deriveFont(Font.BOLD), GOLD);
	}

	/** A thin line across the panel, between its sections. */
	static JComponent separator()
	{
		return separator(6);
	}

	/** The same line, drawn this far from its top; a small number puts it close under the text above it. */
	static JComponent separator(int lineAt)
	{
		return new JComponent()
		{
			{
				setAlignmentX(LEFT_ALIGNMENT);
			}

			@Override
			public Dimension getPreferredSize()
			{
				return new Dimension(10, 13);
			}

			@Override
			public Dimension getMaximumSize()
			{
				return new Dimension(Integer.MAX_VALUE, 13);
			}

			@Override
			protected void paintComponent(Graphics g)
			{
				g.setColor(LINE);
				g.fillRect(0, lineAt, getWidth(), 1);
			}
		};
	}

	/**
	 * Small grey text that wraps to whatever width it is given.
	 *
	 * Drawn by hand: text in a fixed-width web-style label ignores the screen's
	 * scaling and ran past the edge of the panel.
	 */
	static final class Note extends JComponent
	{
		private final String text;

		Note(String text)
		{
			this.text = text;
			setFont(small());
			setForeground(MUTED);
			setOpaque(false);
			setAlignmentX(LEFT_ALIGNMENT);
		}

		private List<String> lines(int width)
		{
			FontMetrics fm = getFontMetrics(getFont());
			List<String> out = new ArrayList<>();
			StringBuilder line = new StringBuilder();

			for (String word : text.split(" "))
			{
				String tryLine = line.length() == 0 ? word : line + " " + word;

				if (line.length() > 0 && fm.stringWidth(tryLine) > width)
				{
					out.add(line.toString());
					line = new StringBuilder(word);
				}
				else
				{
					line = new StringBuilder(tryLine);
				}
			}

			if (line.length() > 0)
			{
				out.add(line.toString());
			}

			return out;
		}

		@Override
		public Dimension getPreferredSize()
		{
			int width = getWidth() > 0 ? getWidth() : 200;

			return new Dimension(10, lines(width).size() * getFontMetrics(getFont()).getHeight() + 4);
		}

		@Override
		public Dimension getMaximumSize()
		{
			return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
		}

		@Override
		public void setBounds(int x, int y, int w, int h)
		{
			boolean widthChanged = w != getWidth();
			super.setBounds(x, y, w, h);

			// The height depends on the width, which is only known once it has been laid out.
			if (widthChanged)
			{
				revalidate();
			}
		}

		@Override
		protected void paintComponent(Graphics g0)
		{
			Graphics2D g = (Graphics2D) g0.create();
			quality(g);
			g.setFont(getFont());
			g.setColor(getForeground());

			int h = g.getFontMetrics().getHeight();
			int y = g.getFontMetrics().getAscent();

			for (String line : lines(Math.max(40, getWidth())))
			{
				g.drawString(line, 0, y);
				y += h;
			}

			g.dispose();
		}
	}

	static JComponent note(String text)
	{
		return new Note(text);
	}

	/**
	 * One figure on a card, like the game's own summary: a picture on the left,
	 * the name of the figure in gold and the figure itself in green below it.
	 */
	static final class Tile extends JComponent
	{
		private final String label;
		private final String value;
		private final Icon icon;

		Tile(String label, String value, Icon icon)
		{
			this.label = label;
			this.value = value;
			this.icon = icon;
			setOpaque(false);
			setAlignmentX(LEFT_ALIGNMENT);
		}

		@Override
		public Dimension getPreferredSize()
		{
			return new Dimension(10, 52);
		}

		@Override
		public Dimension getMaximumSize()
		{
			return new Dimension(Integer.MAX_VALUE, 52);
		}

		/** The label on one line or two, broken at a space, the way the game wraps "Quests Completed:". */
		private List<String> labelLines(FontMetrics fm, int room)
		{
			List<String> lines = new ArrayList<>();

			if (fm.stringWidth(label) <= room)
			{
				lines.add(label);

				return lines;
			}

			String[] words = label.split(" ");
			StringBuilder line = new StringBuilder();

			for (String word : words)
			{
				String attempt = line.length() == 0 ? word : line + " " + word;

				if (line.length() > 0 && fm.stringWidth(attempt) > room && lines.size() < 1)
				{
					lines.add(line.toString());
					line = new StringBuilder(word);
				}
				else
				{
					line = new StringBuilder(attempt);
				}
			}

			lines.add(fit(line.toString(), fm, room));

			return lines;
		}

		@Override
		protected void paintComponent(Graphics g0)
		{
			Graphics2D g = (Graphics2D) g0.create();
			quality(g);
			int w = getWidth();
			int h = getHeight();

			g.setColor(CARD);
			g.fillRoundRect(0, 0, w, h, 8, 8);

			int left = 6;

			if (icon != null)
			{
				icon.paintIcon(this, g, 6, 6);
				left = 6 + icon.getIconWidth() + 2;
			}

			int room = w - left - 6;

			g.setFont(small());
			FontMetrics fm = g.getFontMetrics();
			List<String> lines = labelLines(fm, room);

			// One line of label sits lower, so the figure under it always has the same place.
			int y = lines.size() == 1 ? 20 : 16;
			g.setColor(GOLD);

			for (String line : lines)
			{
				g.drawString(line, left + (room - fm.stringWidth(line)) / 2, y);
				y += 12;
			}

			g.setFont(bold());
			fm = g.getFontMetrics();
			String v = fit(value, fm, room);
			g.setColor(VALUE);
			g.drawString(v, left + (room - fm.stringWidth(v)) / 2, 45);
			g.dispose();
		}
	}

	/** One line: a picture and words on the left, a figure on the right. No box around it unless it is marked. */
	static final class Line extends JComponent
	{
		private final String left;
		private final String right;
		private final Icon icon;
		private final Color leftColor;
		private final Color rightColor;
		private final boolean rightBold;
		private boolean marked;
		private boolean big;
		private boolean hover;

		Line(String left, String right, Icon icon, Color leftColor, Color rightColor, boolean rightBold)
		{
			this.left = left == null ? "" : left;
			this.right = right == null ? "" : right;
			this.icon = icon;
			this.leftColor = leftColor;
			this.rightColor = rightColor;
			this.rightBold = rightBold;
			setOpaque(false);
			setAlignmentX(LEFT_ALIGNMENT);
		}

		/** The one that is yours: a gold tint behind it, a bar on its edge and the name in bold. */
		Line marked()
		{
			marked = true;

			return this;
		}

		/** Makes the line a link: a hand under the pointer, a faint highlight, and the action on a click. */
		Line clickable(Runnable onClick)
		{
			setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
			addMouseListener(new java.awt.event.MouseAdapter()
			{
				@Override
				public void mouseClicked(java.awt.event.MouseEvent e)
				{
					onClick.run();
				}

				@Override
				public void mouseEntered(java.awt.event.MouseEvent e)
				{
					hover = true;
					repaint();
				}

				@Override
				public void mouseExited(java.awt.event.MouseEvent e)
				{
					hover = false;
					repaint();
				}
			});

			return this;
		}

		/** A taller line with a larger figure, for the number the member looks for first. */
		Line big()
		{
			big = true;

			return this;
		}

		private int height()
		{
			return big ? 28 : 20;
		}

		@Override
		public Dimension getPreferredSize()
		{
			return new Dimension(10, height());
		}

		@Override
		public Dimension getMaximumSize()
		{
			return new Dimension(Integer.MAX_VALUE, height());
		}

		@Override
		protected void paintComponent(Graphics g0)
		{
			Graphics2D g = (Graphics2D) g0.create();
			quality(g);
			int w = getWidth();
			int h = getHeight();

			if (hover)
			{
				g.setColor(new Color(255, 255, 255, 22));
				g.fillRoundRect(0, 1, w, h - 2, 6, 6);
			}

			if (marked)
			{
				g.setColor(new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), 40));
				g.fillRoundRect(0, 1, w, h - 2, 6, 6);
				g.setColor(GOLD);
				g.fillRoundRect(0, 3, 3, h - 6, 3, 3);
			}

			int pad = marked ? 8 : 0;
			int x = pad;

			if (icon != null)
			{
				icon.paintIcon(this, g, x, (h - icon.getIconHeight()) / 2);
				x += icon.getIconWidth() + 5;
			}

			Font figure = big ? bold().deriveFont(16f) : (rightBold ? bold() : small());
			g.setFont(figure);
			FontMetrics rm = g.getFontMetrics();

			// The figure never crowds the name: it gets at most just over half the line.
			String shownRight = fit(right, rm, Math.max(30, (int) ((w - x - pad) * 0.55)));
			int rightWidth = shownRight.isEmpty() ? 0 : rm.stringWidth(shownRight);
			int y = (h + rm.getAscent() - rm.getDescent()) / 2;

			g.setColor(rightColor);
			g.drawString(shownRight, w - pad - rightWidth, y);

			g.setFont(marked ? small().deriveFont(Font.BOLD) : (big ? bold() : small()));
			FontMetrics lm = g.getFontMetrics();
			g.setColor(leftColor);
			g.drawString(fit(left, lm, w - x - rightWidth - pad - 8), x, (h + lm.getAscent() - lm.getDescent()) / 2);
			g.dispose();
		}
	}

	/** A section name that opens and closes what is under it, with an arrow showing which. */
	static JComponent toggle(String text, boolean open, Runnable onToggle)
	{
		JLabel l = label((open ? "\u25BE " : "\u25B8 ") + text.toUpperCase(), small().deriveFont(Font.BOLD), GOLD);
		l.setBorder(javax.swing.BorderFactory.createEmptyBorder(2, 0, 2, 0));

		if (onToggle != null)
		{
			l.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
			l.addMouseListener(new java.awt.event.MouseAdapter()
			{
				@Override
				public void mouseClicked(java.awt.event.MouseEvent e)
				{
					onToggle.run();
				}
			});
		}

		return l;
	}

	/** Two tiles side by side. */
	static JPanel pair(JComponent a, JComponent b)
	{
		JPanel row = new JPanel(new java.awt.GridLayout(1, 2, 6, 0));
		row.setOpaque(false);
		row.setAlignmentX(JComponent.LEFT_ALIGNMENT);
		row.add(a);
		row.add(b);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));

		return row;
	}

	/** A bar that fills towards the next rank, with the rank on the left and what is left on the right. */
	static final class Progress extends JComponent
	{
		private final String title;
		private final String note;
		private final double fraction;
		private final Icon icon;

		Progress(String title, String note, double fraction, Icon icon)
		{
			this.title = title;
			this.note = note;
			this.icon = icon;
			this.fraction = Math.max(0, Math.min(1, fraction));
			setOpaque(false);
			setAlignmentX(LEFT_ALIGNMENT);
		}

		@Override
		public Dimension getPreferredSize()
		{
			return new Dimension(10, 30);
		}

		@Override
		public Dimension getMaximumSize()
		{
			return new Dimension(Integer.MAX_VALUE, 30);
		}

		@Override
		protected void paintComponent(Graphics g0)
		{
			Graphics2D g = (Graphics2D) g0.create();
			quality(g);
			int w = getWidth();

			g.setFont(small());
			FontMetrics fm = g.getFontMetrics();
			int noteWidth = fm.stringWidth(note);
			int x = 0;

			if (icon != null)
			{
				icon.paintIcon(this, g, 0, 0);
				x = icon.getIconWidth() + 5;
			}

			g.setColor(MUTED);
			g.drawString(note, Math.max(x, w - noteWidth), 12);

			g.setColor(TEXT);
			g.drawString(fit(title, fm, Math.max(20, w - x - noteWidth - 8)), x, 12);

			g.setColor(TRACK);
			g.fillRoundRect(0, 20, w, 6, 6, 6);
			g.setColor(GOLD);
			g.fillRoundRect(0, 20, Math.max(5, (int) (w * fraction)), 6, 6, 6);
			g.dispose();
		}
	}

	/** One line of text on a card, with a picture before it. */
	static final class Row extends JComponent
	{
		private final String text;
		private final Icon icon;

		Row(String text, Icon icon)
		{
			this.text = text;
			this.icon = icon;
			setOpaque(false);
			setAlignmentX(LEFT_ALIGNMENT);
		}

		@Override
		public Dimension getPreferredSize()
		{
			return new Dimension(10, 24);
		}

		@Override
		public Dimension getMaximumSize()
		{
			return new Dimension(Integer.MAX_VALUE, 24);
		}

		@Override
		protected void paintComponent(Graphics g0)
		{
			Graphics2D g = (Graphics2D) g0.create();
			quality(g);
			int w = getWidth();
			int h = getHeight();

			g.setColor(CARD);
			g.fillRoundRect(0, 0, w, h, 8, 8);

			int x = 8;

			if (icon != null)
			{
				icon.paintIcon(this, g, 8, (h - icon.getIconHeight()) / 2);
				x = 8 + icon.getIconWidth() + 5;
			}

			g.setFont(small());
			FontMetrics fm = g.getFontMetrics();
			g.setColor(TEXT);
			g.drawString(fit(text, fm, w - x - 8), x, (h + fm.getAscent() - fm.getDescent()) / 2);
			g.dispose();
		}
	}

	static JPanel card()
	{
		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		p.setBackground(ColorScheme.DARK_GRAY_COLOR);

		return p;
	}
}
