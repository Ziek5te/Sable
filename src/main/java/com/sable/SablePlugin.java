package com.sable;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.IconID;
import net.runelite.api.ItemComposition;
import net.runelite.api.Player;
import net.runelite.api.clan.ClanMember;
import net.runelite.api.clan.ClanSettings;
import net.runelite.api.clan.ClanTitle;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Reports what happens in the game to the clan's Sable bot, the moment it happens,
 * and shows the character's account overview and the clan's side of it in a side panel.
 *
 * Drops, collection log, pets, diaries, combat tasks, quests, kill counts and
 * personal bests. No screenshots, nothing uploaded, nothing read but the game's
 * own messages and the loot it hands out.
 */
@Slf4j
@PluginDescriptor(
	name = "Sable",
	description = "Reports your drops, collection log, diaries, combat tasks, quests, kill counts and personal bests to the clan's Sable bot",
	tags = {"clan", "discord", "sable", "drops", "pb", "kill count"}
)
public class SablePlugin extends Plugin
{
	// Below this a drop is not worth a message; the bot applies the clan's own, higher, threshold.
	private static final long MIN_DROP_VALUE = 100_000;

	// How close in game ticks the two lines of one kill are taken to be.
	private static final int SAME_KILL_TICKS = 2;

	// How close a pet message and its collection log line are taken to be.
	private static final int PET_TICKS = 3;

	// How often, in game ticks, the panel's figures and the clan rank are read from the client.
	private static final int READ_TICKS = 10;

	// How often Discord is asked for new chat, and how rarely when the clan has the relay off.
	private static final int CHAT_POLL_SECONDS = 6;
	private static final long CHAT_OFF_RECHECK_MS = 60_000;

	// At most this many clan chat lines a minute go to Discord.
	private static final int CHAT_LINES_PER_MINUTE = 20;

	// Config key of each test button, and the kind of sample card it asks the bot for.
	private static final Map<String, String> TEST_BUTTONS = Map.of(
		"testDrop", "drop",
		"testClog", "clog",
		"testPet", "pet",
		"testDiary", "diary",
		"testCombat", "combat",
		"testQuest", "quest",
		"testKc", "kc",
		"testPb", "pb"
	);

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private SpriteManager spriteManager;

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private SableConfig config;

	@Inject
	private SableSender sender;

	@Inject
	private ItemManager itemManager;

	// Pictures for the panel, made once. A game sprite arrives on the next client tick, a rank picture when it has downloaded.
	private final Map<String, Icon> imageIcons = new ConcurrentHashMap<>();
	private final java.util.Set<String> imagesAsked = ConcurrentHashMap.newKeySet();
	private final Map<Integer, Icon> itemIcons = new ConcurrentHashMap<>();
	private final java.util.Set<Integer> itemsAsked = ConcurrentHashMap.newKeySet();
	private final Map<Integer, Icon> spriteIcons = new ConcurrentHashMap<>();
	private final java.util.Set<Integer> spritesAsked = ConcurrentHashMap.newKeySet();
	private final AtomicInteger iconsLoaded = new AtomicInteger();

	private volatile ClanInfo clan;

	// Until when a rise in clan points is announced in chat, set when a drop is reported.
	private volatile long pointsWatchUntil;

	// Item names found to their ids, and the ones already asked about.
	private final java.util.Map<String, Integer> itemIds = new java.util.concurrent.ConcurrentHashMap<>();
	private final java.util.Set<String> itemNamesAsked = java.util.concurrent.ConcurrentHashMap.newKeySet();

	// Every item the game knows by name, built once when the Grand Exchange search comes up empty (pets and other untradeable drops).
	private final java.util.Map<String, Integer> itemIndex = new java.util.concurrent.ConcurrentHashMap<>();
	private final java.util.Set<String> itemNamesWaiting = java.util.concurrent.ConcurrentHashMap.newKeySet();
	private final java.util.concurrent.atomic.AtomicBoolean itemIndexStarted = new java.util.concurrent.atomic.AtomicBoolean();
	private volatile boolean itemIndexDone;

	// The roster last taken in by the bot, and when, so an unchanged clan is not sent again.
	private volatile String rosterSent;
	private volatile long rosterSentAt;
	private static final long ROSTER_REPEAT_MS = 3 * 60 * 60_000L;

	// The chat picture address already being used, so it is fetched once.
	private volatile String chatIconUrl;


	// The character that is playing, and its clan rank as the game shows it. Null when it is in no clan.
	private volatile String currentName;
	private volatile String liveRank;

	private SablePanel panel;
	private NavigationButton navButton;
	private final List<ScheduledFuture<?>> jobs = new ArrayList<>();

	private int tick;

	// Clan chat lines sent to Discord in the last minute, so a busy chat cannot flood the bot.
	private final java.util.Deque<Long> chatSent = new java.util.ArrayDeque<>();

	// The newest Discord message already shown, and whether the clan has the relay on at all.
	private volatile long chatSince = -1;
	private volatile boolean chatRelayOn = true;
	private volatile long chatCheckedAt;

	// Whether this login has already told the bot who is playing.
	private boolean helloSent;

	// The kill the last count line named, waiting for its time line.
	private ChatParser.Kill lastKill;
	private int lastKillTick = -100;

	// A time line that arrived before its count line (raids print the time first).
	private ChatParser.Time pendingTime;
	private int pendingTimeTick = -100;

	private int lastPetTick = -100;

	// A collection log line is held a moment: a pet's name only arrives there.
	private final List<PendingLog> pendingLogs = new ArrayList<>();

	@Provides
	SableConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(SableConfig.class);
	}

	@Override
	protected void startUp()
	{
		sender.resetWarnings();
		helloSent = false;

		panel = new SablePanel(new SablePanel.Source()
		{
			@Override
			public boolean connected()
			{
				return sender.connected();
			}

			@Override
			public ClanInfo clan()
			{
				return clan;
			}

			@Override
			public String liveRank()
			{
				return liveRank;
			}

			@Override
			public AccountView.Icons icons()
			{
				return icons;
			}

			@Override
			public long version()
			{
				return iconsLoaded.get();
			}

			@Override
			public void refreshClan()
			{
				sender.fetchClan(currentName, SablePlugin.this::applyClan);
			}

			@Override
			public void switchLadder(String to, java.util.function.Consumer<String> done)
			{
				sender.switchLadder(to, done);
			}
		});

		navButton = NavigationButton.builder()
			.tooltip("Sable")
			.icon(icon())
			.priority(8)
			.panel(panel)
			.build();

		if (config.showPanel())
		{
			clientToolbar.addNavigation(navButton);
		}

		sender.prepareDiscordIcon();

		jobs.add(executor.scheduleWithFixedDelay(sender::ping, 2, 60, TimeUnit.SECONDS));
		jobs.add(executor.scheduleWithFixedDelay(() -> sender.fetchClan(currentName, this::applyClan), 5, 300, TimeUnit.SECONDS));
		jobs.add(executor.scheduleWithFixedDelay(() -> panel.refresh(false), 3, 3, TimeUnit.SECONDS));
		jobs.add(executor.scheduleWithFixedDelay(this::pollDiscordChat, 8, CHAT_POLL_SECONDS, TimeUnit.SECONDS));
		jobs.add(executor.scheduleWithFixedDelay(this::syncRoster, 45, 120, TimeUnit.SECONDS));
	}

	@Override
	protected void shutDown()
	{
		for (ScheduledFuture<?> job : jobs)
		{
			job.cancel(false);
		}

		jobs.clear();
		clientToolbar.removeNavigation(navButton);
		navButton = null;
		panel = null;
		lastKill = null;
		pendingTime = null;
		pendingLogs.clear();
	}

	private final AccountView.Icons icons = new AccountView.Icons()
	{
		@Override
		public Icon image(String url)
		{
			// Only the clan's emoji, from Discord's own host, whatever the bot says.
			if (url == null || !url.startsWith("https://cdn.discordapp.com/emojis/"))
			{
				return null;
			}

			Icon icon = imageIcons.get(url);

			if (icon == null && imagesAsked.add(url))
			{
				okHttpClient.newCall(new Request.Builder().url(url).build()).enqueue(new Callback()
				{
					@Override
					public void onFailure(Call call, java.io.IOException e)
					{
						log.debug("Sable: could not fetch a rank picture: {}", e.getMessage());
					}

					@Override
					public void onResponse(Call call, Response response)
					{
						try (Response r = response)
						{
							if (r.isSuccessful() && r.body() != null && r.body().contentLength() < 200_000)
							{
								BufferedImage image = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(r.body().bytes()));

								if (image != null)
								{
									imageIcons.put(url, scaled(image, 16));
									iconsLoaded.incrementAndGet();
								}
							}
						}
						catch (java.io.IOException | RuntimeException e)
						{
							log.debug("Sable: could not read a rank picture: {}", e.getMessage());
						}
					}
				});
			}

			return icon;
		}

		@Override
		public Icon itemNamed(String name)
		{
			if (name == null)
			{
				return null;
			}

			Integer id = itemIds.get(name.toLowerCase());

			if (id == null && itemNamesAsked.add(name.toLowerCase()))
			{
				// Looked up off the panel's thread; the picture shows from the next redraw.
				executor.submit(() ->
				{
					List<net.runelite.http.api.item.ItemPrice> found = itemManager.search(name);

					for (net.runelite.http.api.item.ItemPrice p : found)
					{
						if (p.getName().equalsIgnoreCase(name))
						{
							itemIds.put(name.toLowerCase(), p.getId());
							iconsLoaded.incrementAndGet();

							return;
						}
					}

					// Not on the Grand Exchange list (a pet, say): look it up by name among every item the game has.
					itemNamesWaiting.add(name.toLowerCase());
					indexItems();
				});
			}

			return id == null ? null : item(id);
		}

		@Override
		public Icon item(int id)
		{
			Icon icon = itemIcons.get(id);

			if (icon == null && itemsAsked.add(id))
			{
				// Asked for once; the picture is shown from the next redraw after it arrives.
				AsyncBufferedImage image = itemManager.getImage(id);
				image.onLoaded(() ->
				{
					itemIcons.put(id, scaled(image, 16));
					iconsLoaded.incrementAndGet();
				});
			}

			return icon;
		}

		@Override
		public Icon sprite(int id)
		{
			Icon icon = spriteIcons.get(id);

			if (icon == null && spritesAsked.add(id))
			{
				// Sprites are read on the client thread; the panel shows it from the next redraw.
				clientThread.invoke(() ->
				{
					BufferedImage image = spriteManager.getSprite(id, 0);

					if (image != null)
					{
						spriteIcons.put(id, scaled(image, 16));
						iconsLoaded.incrementAndGet();
					}
					else
					{
						spritesAsked.remove(id);
					}
				});
			}

			return icon;
		}
	};

	/** What the bot says about the member: kept, shown, and the Discord server's icon fetched for the game chat when it is new. */
	private void applyClan(ClanInfo info)
	{
		ClanInfo before = clan;
		clan = info;

		/*
		  A drop was just reported and the clan's points have gone up: say so in the player's own chat.

		  The total only, not the month. Half points show as half until the question is answered.
		*/
		if (before != null && pointsWatchUntil > System.currentTimeMillis() && info.points > before.points)
		{
			pointsWatchUntil = 0;
			boolean half = info.pending.size() > before.pending.size();
			sender.say("Sable: +" + (info.points - before.points) + " clan points \u00B7 " + info.points + " total" + (half ? " (half until you say how you ran it, see the Sable panel)" : ""));
		}

		if (panel != null)
		{
			panel.refresh(false);
		}

		String url = info.chatIcon;

		// Only a Discord emoji or server icon, from Discord's own host, whatever the bot says.
		if (url == null || !(url.startsWith("https://cdn.discordapp.com/emojis/") || url.startsWith("https://cdn.discordapp.com/icons/")) || url.equals(chatIconUrl))
		{
			return;
		}

		chatIconUrl = url;

		okHttpClient.newCall(new Request.Builder().url(url).build()).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, java.io.IOException e)
			{
				chatIconUrl = null;
				log.debug("Sable: could not fetch the chat icon: {}", e.getMessage());
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response r = response)
				{
					if (r.isSuccessful() && r.body() != null && r.body().contentLength() < 500_000)
					{
						BufferedImage image = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(r.body().bytes()));

						if (image != null)
						{
							// Chat pictures are about this tall.
							sender.setDiscordIcon(ImageUtil.resizeImage(image, 13, 13));
						}
					}
				}
				catch (java.io.IOException | RuntimeException e)
				{
					log.debug("Sable: could not read the chat icon: {}", e.getMessage());
				}
			}
		});
	}

	/** The amount a drop must be worth to be reported: what the clan announces from, once the bot has said. */
	private long minDrop()
	{
		ClanInfo info = clan;

		return info == null ? MIN_DROP_VALUE : Math.max(1, info.dropMin);
	}

	/** A picture shrunk to fit a small square, keeping its shape. */
	private static Icon scaled(BufferedImage image, int max)
	{
		int w = image.getWidth();
		int h = image.getHeight();
		double f = Math.min(1.0, max / (double) Math.max(w, h));

		if (f >= 1.0)
		{
			return new ImageIcon(image);
		}

		return new ImageIcon(ImageUtil.resizeImage(image, Math.max(1, (int) Math.round(w * f)), Math.max(1, (int) Math.round(h * f))));
	}

	/** The clan rank of the character that is playing, from the game's own clan list. */
	private void updateLiveRank()
	{
		Player me = client.getLocalPlayer();
		ClanSettings clanSettings = client.getClanSettings();

		if (me == null || me.getName() == null || clanSettings == null)
		{
			liveRank = null;

			return;
		}

		ClanMember member = clanSettings.findMember(Text.sanitize(me.getName()));

		if (member == null)
		{
			liveRank = null;

			return;
		}

		ClanTitle title = clanSettings.titleForRank(member.getRank());
		liveRank = title != null ? title.getName() : member.getRank().toString();
	}

	/**
	 * Reads every item's name once, a slice per game tick so the game never stalls, then gives the pictures that were waiting on it.
	 *
	 * The Grand Exchange search only knows tradeable items, so a pet or a reward item had no picture until this.
	 * Noted and placeholder copies are skipped; the lowest number for a name wins.
	 */
	private void indexItems()
	{
		if (itemIndexDone)
		{
			resolveWaitingItems();

			return;
		}

		if (!itemIndexStarted.compareAndSet(false, true))
		{
			return;
		}

		int[] next = {0};

		clientThread.invokeLater(() ->
		{
			int total = client.getItemCount();
			int end = Math.min(total, next[0] + 2500);

			for (int id = next[0]; id < end; id++)
			{
				ItemComposition item = itemManager.getItemComposition(id);

				if (item == null || item.getPlaceholderTemplateId() != -1 || item.getNote() != -1)
				{
					continue;
				}

				String name = item.getName();

				if (name != null && !name.equalsIgnoreCase("null"))
				{
					itemIndex.putIfAbsent(name.toLowerCase(), id);
				}
			}

			next[0] = end;

			if (end < total)
			{
				return false;
			}

			itemIndexDone = true;
			resolveWaitingItems();

			return true;
		});
	}

	private void resolveWaitingItems()
	{
		for (String name : itemNamesWaiting)
		{
			Integer id = itemIndex.get(name);

			if (id != null && itemIds.putIfAbsent(name, id) == null)
			{
				iconsLoaded.incrementAndGet();
			}
		}

		itemNamesWaiting.clear();
	}

	/** The clan's gold S for the side bar; a drawn one only if the picture cannot be read. */
	private static BufferedImage icon()
	{
		try
		{
			return ImageUtil.loadImageResource(SablePlugin.class, "/com/sable/nav.png");
		}
		catch (RuntimeException e)
		{
			return drawnIcon();
		}
	}

	/** A small gold disc with an S, drawn here so there is always something to show. */
	private static BufferedImage drawnIcon()
	{
		BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();

		try
		{
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			g.setColor(new Color(214, 170, 60));
			g.fillOval(1, 1, 30, 30);
			g.setColor(new Color(40, 30, 10));
			g.setFont(g.getFont().deriveFont(java.awt.Font.BOLD, 22f));
			g.drawString("S", 9, 24);
		}
		finally
		{
			g.dispose();
		}

		return image;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			sender.resetWarnings();
		}
		else if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			// A world hop passes through HOPPING, not here, so this is a real new login.
			helloSent = false;
			liveRank = null;
			chatSince = -1;
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (SableConfig.GROUP.equals(event.getGroup()) && "showPanel".equals(event.getKey()) && navButton != null)
		{
			if ("true".equals(event.getNewValue()))
			{
				clientToolbar.addNavigation(navButton);
			}
			else
			{
				clientToolbar.removeNavigation(navButton);
			}

			return;
		}

		String kind = TEST_BUTTONS.get(event.getKey());

		// A test is a tick box that puts itself back: only the tick sends.
		if (!SableConfig.GROUP.equals(event.getGroup()) || kind == null || !"true".equals(event.getNewValue()))
		{
			return;
		}

		configManager.setConfiguration(SableConfig.GROUP, event.getKey(), false);

		Player me = client.getLocalPlayer();

		if (me == null || me.getName() == null)
		{
			sender.say("Sable test: log in to your character first.");

			return;
		}

		sender.test(Text.sanitize(me.getName()), kind);
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		tick++;

		if (tick % READ_TICKS == 0)
		{
			updateLiveRank();
		}

		// The name is not there yet on the login event itself, so the check-in waits for a tick that has it.
		if (!helloSent && client.getLocalPlayer() != null && client.getLocalPlayer().getName() != null)
		{
			helloSent = true;
			String name = Text.sanitize(client.getLocalPlayer().getName());
			currentName = name;
			sender.hello(name);
			sender.fetchClan(name, this::applyClan);
			updateLiveRank();
		}

		// A collection log line is a pet when the pet message came just before or just after it.
		pendingLogs.removeIf(p ->
		{
			if (tick - p.tick < PET_TICKS)
			{
				return false;
			}

			boolean pet = Math.abs(p.tick - lastPetTick) <= PET_TICKS;

			if (pet ? config.pets() : config.collectionLog())
			{
				sendItem(pet ? "valuable_drop" : "new_item_obtained", p.name, 0, 0, 0);
			}

			return true;
		});
	}

	private static final java.util.regex.Pattern IMG = java.util.regex.Pattern.compile("<img=(\\d+)>");

	/** The account type icon in front of a name in chat: ironman, hardcore, group ironman and so on. Null when there is none. */
	private static String accountKind(String rawName)
	{
		java.util.regex.Matcher m = IMG.matcher(rawName == null ? "" : rawName);

		while (m.find())
		{
			int id = Integer.parseInt(m.group(1));

			if (id == IconID.IRONMAN.getIndex())
			{
				return "ironman";
			}
			else if (id == IconID.ULTIMATE_IRONMAN.getIndex())
			{
				return "ultimate";
			}
			else if (id == IconID.HARDCORE_IRONMAN.getIndex())
			{
				return "hardcore";
			}
			else if (id == IconID.GROUP_IRONMAN.getIndex())
			{
				return "group";
			}
			else if (id == IconID.HARDCORE_GROUP_IRONMAN.getIndex())
			{
				return "hardcore_group";
			}
			else if (id == IconID.UNRANKED_GROUP_IRONMAN.getIndex())
			{
				return "unranked_group";
			}
		}

		return null;
	}

	/** A line of the clan chat, for the clan's Discord, when the member has switched that on. */
	private void onClanChat(ChatMessage event)
	{
		if (!config.clanChat() || event.getName() == null || event.getSender() == null)
		{
			return;
		}

		String who = Text.sanitize(Text.removeTags(event.getName()));
		String text = Text.removeTags(event.getMessage());
		Player me = client.getLocalPlayer();

		if (who.isEmpty() || text.isEmpty() || me == null || me.getName() == null)
		{
			return;
		}

		long now = System.currentTimeMillis();

		while (!chatSent.isEmpty() && now - chatSent.peekFirst() > 60_000)
		{
			chatSent.pollFirst();
		}

		if (chatSent.size() >= CHAT_LINES_PER_MINUTE)
		{
			return;
		}

		chatSent.addLast(now);
		sender.postChat(Text.sanitize(me.getName()), who, event.getSender(), text, accountKind(event.getName()));
	}

	/**
	 * Staff only: the clan's member list, sent to the bot when it has changed.
	 *
	 * The bot makes the Wise Old Man group match it. Sent again every few hours
	 * even when nothing moved, so a change the bot refused is not stuck for good.
	 */
	private void syncRoster()
	{
		ClanInfo info = clan;

		if (info == null || !info.staff || client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		clientThread.invoke(() ->
		{
			ClanSettings clanSettings = client.getClanSettings();

			if (clanSettings == null || clanSettings.getName() == null || clanSettings.getMembers().isEmpty())
			{
				return;
			}

			List<String[]> members = new ArrayList<>();

			for (ClanMember m : clanSettings.getMembers())
			{
				ClanTitle title = clanSettings.titleForRank(m.getRank());
				members.add(new String[] {Text.sanitize(m.getName()), title != null ? title.getName() : ""});
			}

			members.sort((a, b) -> a[0].compareToIgnoreCase(b[0]));

			StringBuilder fingerprint = new StringBuilder(clanSettings.getName());

			for (String[] m : members)
			{
				fingerprint.append('|').append(m[0]).append(':').append(m[1]);
			}

			String hash = fingerprint.toString();
			long now = System.currentTimeMillis();

			if (hash.equals(rosterSent) && now - rosterSentAt < ROSTER_REPEAT_MS)
			{
				return;
			}

			sender.postRoster(clanSettings.getName(), members, result ->
			{
				// Any answer but "slow down" or none at all counts as sent; a refused roster is tried again in a few hours, not every two minutes.
				if (result != null && !"slow-down".equals(result))
				{
					rosterSent = hash;
					rosterSentAt = System.currentTimeMillis();
				}
			});
		});
	}

	/** What was written in the clan's Discord chat channel, shown in this player's own game chat. */
	private void pollDiscordChat()
	{
		if (!config.showDiscordChat())
		{
			return;
		}

		// With the relay off in the clan there is nothing to fetch, so look again only now and then.
		if (!chatRelayOn && System.currentTimeMillis() - chatCheckedAt < CHAT_OFF_RECHECK_MS)
		{
			return;
		}

		chatCheckedAt = System.currentTimeMillis();

		sender.pollChat(chatSince, json ->
		{
			chatRelayOn = json.has("enabled") && json.get("enabled").getAsBoolean();

			if (json.has("last"))
			{
				long last = json.get("last").getAsLong();

				// The first look only learns where things stand; nothing old is shown.
				if (chatSince >= 0 && json.has("messages"))
				{
					for (com.google.gson.JsonElement el : json.getAsJsonArray("messages"))
					{
						JsonObject m = el.getAsJsonObject();
						sender.discordLine(m.get("name").getAsString(), m.get("text").getAsString());
					}
				}

				chatSince = last;
			}
		});
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		ChatMessageType type = event.getType();

		if (type == ChatMessageType.CLAN_CHAT)
		{
			onClanChat(event);

			return;
		}

		// Only what the game says about itself; what other players type is never read.
		if (type != ChatMessageType.GAMEMESSAGE && type != ChatMessageType.SPAM && type != ChatMessageType.MESBOX)
		{
			return;
		}

		String message = event.getMessage();
		String plain = Text.removeTags(message);

		if (ChatParser.pet(plain))
		{
			lastPetTick = tick;

			return;
		}

		String item = ChatParser.collectionLog(plain);

		if (item != null)
		{
			pendingLogs.add(new PendingLog(item, tick));

			return;
		}

		ChatParser.Named diary = ChatParser.diary(plain);

		if (diary != null)
		{
			if (config.diaries())
			{
				JsonObject enriched = new JsonObject();
				enriched.addProperty("areaName", diary.what);
				enriched.addProperty("tierName", diary.tier);
				send("achievement_diary_tier_completed", new JsonObject(), enriched, false);
			}

			return;
		}

		ChatParser.Named task = ChatParser.combatTask(plain);

		if (task != null)
		{
			if (config.combatAchievements())
			{
				JsonObject enriched = new JsonObject();
				enriched.addProperty("taskName", task.what);
				enriched.addProperty("tierName", task.tier);
				send("combat_achievement_task_completed", new JsonObject(), enriched, false);
			}

			return;
		}

		ChatParser.Kill kill = ChatParser.kill(message);

		if (kill != null)
		{
			onKill(kill);

			return;
		}

		ChatParser.Time time = ChatParser.time(message);

		if (time != null)
		{
			onTime(time);

			return;
		}

		// Kill counts and bests have no second source to fall back on, so a line
		// that looks like one and matched nothing is written to the debug log.
		// Nothing is sent. If the game rewords a line, this is where it shows.
		if (ChatParser.looksLikeKillLine(plain))
		{
			log.debug("Sable: a kill or time line matched no pattern: {}", plain);
		}
	}

	private void onKill(ChatParser.Kill kill)
	{
		lastKill = kill;
		lastKillTick = tick;

		if (config.killsAndBests())
		{
			JsonObject data = new JsonObject();
			data.addProperty("boss", kill.boss);
			data.addProperty("kc", kill.kc);
			send("kill_count", data, new JsonObject(), false);
		}

		// A raid prints its time before its count.
		if (pendingTime != null && tick - pendingTimeTick <= SAME_KILL_TICKS)
		{
			sendTime(kill, pendingTime);
		}

		pendingTime = null;
	}

	private void onTime(ChatParser.Time time)
	{
		if (lastKill != null && tick - lastKillTick <= SAME_KILL_TICKS)
		{
			sendTime(lastKill, time);

			return;
		}

		pendingTime = time;
		pendingTimeTick = tick;
	}

	private void sendTime(ChatParser.Kill kill, ChatParser.Time time)
	{
		if (!config.killsAndBests())
		{
			return;
		}

		JsonObject data = new JsonObject();
		data.addProperty("boss", kill.boss);
		data.addProperty("kc", kill.kc);
		data.addProperty("timeMs", time.timeMs);
		data.addProperty("teamSize", time.teamSize > 0 ? time.teamSize : teamSize());
		data.addProperty("isNew", time.isNew);
		data.addProperty("precise", time.precise);
		// With who was there, so the clan's record can name the team.
		send("personal_best", data, new JsonObject(), true);
	}

	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		// Loot taken from other players is not a drop.
		if (event.getType() != null && "PLAYER".equals(event.getType().name()))
		{
			return;
		}

		if (!config.drops())
		{
			return;
		}

		// One entry per item, however many stacks the kill gave.
		Map<Integer, Integer> quantities = new LinkedHashMap<>();

		for (ItemStack stack : event.getItems())
		{
			quantities.merge(stack.getId(), stack.getQuantity(), Integer::sum);
		}

		for (Map.Entry<Integer, Integer> e : quantities.entrySet())
		{
			int id = e.getKey();
			int quantity = e.getValue();
			long value = (long) itemManager.getItemPrice(id) * quantity;

			if (value < minDrop())
			{
				continue;
			}

			ItemComposition composition = itemManager.getItemComposition(id);

			sendItem("valuable_drop", composition.getName(), id, quantity, value);
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() != InterfaceID.QUESTSCROLL || !config.quests())
		{
			return;
		}

		// A tick later, once the scroll has its text.
		clientThread.invokeLater(() ->
		{
			Widget title = client.getWidget(InterfaceID.Questscroll.QUEST_TITLE);

			if (title == null || title.getText() == null)
			{
				return;
			}

			String quest = ChatParser.quest(Text.removeTags(title.getText()));

			if (quest != null)
			{
				JsonObject enriched = new JsonObject();
				enriched.addProperty("questName", quest);
				send("quest_completed", new JsonObject(), enriched, false);
			}
		});
	}

	/** A drop, a collection log slot or a pet: the three that can carry who else was there. */
	private void sendItem(String type, String name, int itemId, int quantity, long value)
	{
		JsonObject data = new JsonObject();

		if (itemId > 0)
		{
			data.addProperty("itemId", itemId);
		}

		if (quantity > 0)
		{
			data.addProperty("quantity", quantity);
		}

		// A pet has no price; the bot pays those at a flat rate.
		data.addProperty("value", value);

		JsonObject enriched = new JsonObject();
		enriched.addProperty("itemName", name);

		send(type, data, enriched, true);

		// The bot awards a few seconds after the report, so look for the new total then, and once more in case it was slow.
		pointsWatchUntil = System.currentTimeMillis() + 60_000;
		executor.schedule(() -> sender.fetchClan(currentName, this::applyClan), 9, TimeUnit.SECONDS);
		executor.schedule(() -> sender.fetchClan(currentName, this::applyClan), 25, TimeUnit.SECONDS);
	}

	private void send(String type, JsonObject data, JsonObject enriched, boolean withGroup)
	{
		Player me = client.getLocalPlayer();

		if (me == null || me.getName() == null)
		{
			return;
		}

		JsonObject activity = new JsonObject();
		activity.addProperty("type", type);
		activity.addProperty("createdAt", Instant.now().toString());
		activity.add("data", data);
		activity.add("enriched", enriched);

		sender.send(Text.sanitize(me.getName()), activity, withGroup ? group() : null);
	}

	/** Who else is here, for a group boss unique: only meaningful inside an instance. */
	private JsonObject group()
	{
		JsonObject group = new JsonObject();
		boolean raid = tobTeamSize() > 0 || toaTeamSize() > 0;

		group.addProperty("instanced", client.getTopLevelWorldView().isInstance());
		group.addProperty("raid", raid);

		JsonArray others = new JsonArray();
		int strangers = 0;

		for (Player p : othersHere())
		{
			// Only the names of clan members leave the client. Anyone else is counted, never named.
			if (inClan(p.getName()))
			{
				JsonObject o = new JsonObject();
				o.addProperty("name", Text.sanitize(p.getName()));
				o.addProperty("clan", true);
				others.add(o);
			}
			else
			{
				strangers++;
			}
		}

		group.add("others", others);
		group.addProperty("strangers", strangers);

		return group;
	}

	private List<Player> othersHere()
	{
		List<Player> others = new ArrayList<>();
		Player me = client.getLocalPlayer();

		for (Player p : client.getTopLevelWorldView().players())
		{
			if (p != null && p != me && p.getName() != null)
			{
				others.add(p);
			}
		}

		return others;
	}

	private boolean inClan(String name)
	{
		ClanSettings clanSettings = client.getClanSettings();

		return clanSettings != null && clanSettings.findMember(Text.sanitize(name)) != null;
	}

	/** The party in a raid, else everyone in the instance with you, else just you. */
	private int teamSize()
	{
		int raid = Math.max(tobTeamSize(), toaTeamSize());

		if (raid > 0)
		{
			return raid;
		}

		return client.getTopLevelWorldView().isInstance() ? 1 + othersHere().size() : 1;
	}

	// The same party varbits RuneLite's own chat commands count.
	private static final int[] TOB_PARTY = {
		VarbitID.TOB_CLIENT_P0, VarbitID.TOB_CLIENT_P1, VarbitID.TOB_CLIENT_P2, VarbitID.TOB_CLIENT_P3, VarbitID.TOB_CLIENT_P4,
	};

	private static final int[] TOA_PARTY = {
		VarbitID.TOA_CLIENT_P0, VarbitID.TOA_CLIENT_P1, VarbitID.TOA_CLIENT_P2, VarbitID.TOA_CLIENT_P3,
		VarbitID.TOA_CLIENT_P4, VarbitID.TOA_CLIENT_P5, VarbitID.TOA_CLIENT_P6, VarbitID.TOA_CLIENT_P7,
	};

	private int tobTeamSize()
	{
		return count(TOB_PARTY);
	}

	private int toaTeamSize()
	{
		return count(TOA_PARTY);
	}

	private int count(int[] varbits)
	{
		int n = 0;

		for (int v : varbits)
		{
			n += Math.min(client.getVarbitValue(v), 1);
		}

		return n;
	}

	private static final class PendingLog
	{
		final String name;
		final int tick;

		PendingLog(String name, int tick)
		{
			this.name = name;
			this.tick = tick;
		}
	}
}
