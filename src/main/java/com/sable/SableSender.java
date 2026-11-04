package com.sable;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.game.ChatIconManager;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Talks to the bot, off the client thread.
 *
 * An event gets three tries with a growing pause, then it is dropped:
 * RuneProfile catches up on whatever the bot misses. Nothing is written to
 * disk and nothing but what is asked for is sent.
 */
@Slf4j
@Singleton
class SableSender
{
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
	private static final int TRIES = 3;
	private static final long[] BACKOFF_SECONDS = {2, 8};

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private Gson gson;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private SableConfig config;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private ChatIconManager chatIconManager;

	// The picture in front of a line from Discord: a chat bubble of our own until the server's icon has downloaded.
	private volatile int discordIcon = -1;

	// Said once per login, so a wrong key is not a line in chat for every drop.
	private volatile boolean warnedAboutKey;

	// Whether the bot answered the last time it was asked. The side panel shows this.
	private volatile boolean connected;

	boolean connected()
	{
		return connected;
	}

	void resetWarnings()
	{
		warnedAboutKey = false;
	}

	// With no address there is nowhere to send to, so it counts as having no key.
	private String key()
	{
		return config.key() == null || address().isEmpty() ? "" : config.key().trim();
	}

	/** The bot's address as typed in the settings, without a trailing slash; empty until it is filled in. */
	private String address()
	{
		String a = config.address() == null ? "" : config.address().trim();

		while (a.endsWith("/"))
		{
			a = a.substring(0, a.length() - 1);
		}

		return a;
	}

	private HttpUrl url(String path)
	{
		String a = address();

		return a.isEmpty() ? null : HttpUrl.parse(a + path);
	}

	private Request.Builder request(String path)
	{
		HttpUrl url = url(path);

		return url == null ? null : new Request.Builder().url(url).header("Authorization", "Bearer " + key());
	}

	/** Queue one event. Never blocks, never throws. */
	void send(String rsn, JsonObject activity, JsonObject group)
	{
		JsonObject body = new JsonObject();
		body.addProperty("rsn", rsn);
		body.add("activity", activity);

		if (group != null)
		{
			body.add("group", group);
		}

		post(body);
	}

	/**
	 * "This key's player is logged in as this name." Carries nothing else, and
	 * is how the clan's verification knows an applicant owns the account.
	 */
	void hello(String rsn)
	{
		post(Bodies.hello(rsn));
	}

	/** Asks whether the bot is there, for the panel's Connected / Disconnected. */
	void ping()
	{
		if (key().isEmpty())
		{
			connected = false;

			return;
		}

		Request.Builder builder = request("/plugin/v1/ping");

		if (builder == null)
		{
			return;
		}

		okHttpClient.newCall(builder.get().build()).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				connected = false;
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response r = response)
				{
					connected = r.isSuccessful();
				}
			}
		});
	}

	/** Clan points, rank progress and the clan's rank pictures, which only the bot knows. Silent on any failure. */
	void fetchClan(String rsn, Consumer<ClanInfo> done)
	{
		if (key().isEmpty())
		{
			return;
		}

		String query = rsn == null || rsn.isEmpty() ? "" : "?rsn=" + java.net.URLEncoder.encode(rsn, java.nio.charset.StandardCharsets.UTF_8);
		Request.Builder builder = request("/plugin/v1/me" + query);

		if (builder == null)
		{
			return;
		}

		okHttpClient.newCall(builder.get().build()).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				connected = false;
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response r = response)
				{
					connected = r.isSuccessful();

					if (r.isSuccessful() && r.body() != null)
					{
						done.accept(ClanInfo.fromJson(gson.fromJson(r.body().string(), JsonObject.class)));
					}
				}
				catch (IOException | RuntimeException e)
				{
					log.debug("Sable: could not read the clan answer: {}", e.getMessage());
				}
			}
		});
	}

	/** Asks staff, through the bot, to move the member to the other ladder, and says in a line how it went. */
	void switchLadder(String to, Consumer<String> done)
	{
		Request.Builder builder = key().isEmpty() ? null : request("/plugin/v1/ladder");

		if (builder == null)
		{
			done.accept("Paste your key in the settings first.");

			return;
		}

		JsonObject body = new JsonObject();
		body.addProperty("to", to);

		okHttpClient.newCall(builder.post(RequestBody.create(JSON, gson.toJson(body))).build()).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				connected = false;
				done.accept("Could not reach the bot.");
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response r = response)
				{
					done.accept(describeLadder(r.code(), r.body() == null ? "" : r.body().string()));
				}
				catch (IOException e)
				{
					done.accept("The bot's answer could not be read.");
				}
			}
		});
	}

	private String describeLadder(int code, String text)
	{
		if (code == 401)
		{
			return "Your key was not accepted.";
		}

		String result = "";
		String rank = "";

		try
		{
			JsonObject answer = gson.fromJson(text, JsonObject.class);
			result = answer.get("result").getAsString();
			rank = answer.has("rank") && !answer.get("rank").isJsonNull() ? answer.get("rank").getAsString() : "";
		}
		catch (RuntimeException e)
		{
			// Not the bot's usual answer; falls through to the generic line.
		}

		switch (result)
		{
			case "filed":
				return "Sent to staff. You become " + rank + " once they set it in game.";
			case "already-pending":
				return "A move is already waiting for staff.";
			case "slow-down":
				return "Try again in a few minutes.";
			case "nothing-to-switch":
				return "There is nothing to switch to.";
			case "not-in-clan":
				return "Your main account is not in the clan.";
			default:
				return "The bot could not file it.";
		}
	}

	/** One line of the clan chat for the clan's Discord channel. Quiet: nothing is said in chat about it. */
	void postChat(String rsn, String who, String clan, String text, String account)
	{
		Request.Builder builder = key().isEmpty() ? null : request("/plugin/v1/event");

		if (builder == null)
		{
			return;
		}

		JsonObject body = new JsonObject();
		body.addProperty("type", "clan_chat");
		body.addProperty("rsn", rsn);
		body.addProperty("sender", who);
		body.addProperty("clan", clan);
		body.addProperty("text", text);

		// Which account type icon stood beside the name (ironman, hardcore, group ironman ...), if any.
		if (account != null)
		{
			body.addProperty("account", account);
		}

		// One try: a line that does not get through is simply not in Discord, and the next one is.
		okHttpClient.newCall(builder.post(RequestBody.create(JSON, gson.toJson(body))).build()).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Sable: could not post a clan chat line: {}", e.getMessage());
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				response.close();
			}
		});
	}

	/**
	 * The clan's member list as the game shows it, for the bot to match the Wise Old Man group to.
	 *
	 * Staff only on the bot's side. Quiet on failure; the next look sends it again.
	 */
	void postRoster(String clan, java.util.List<String[]> members, Consumer<String> done)
	{
		Request.Builder builder = key().isEmpty() ? null : request("/plugin/v1/roster");

		if (builder == null)
		{
			done.accept(null);

			return;
		}

		JsonObject body = new JsonObject();
		body.addProperty("clan", clan);

		com.google.gson.JsonArray list = new com.google.gson.JsonArray();

		for (String[] m : members)
		{
			JsonObject o = new JsonObject();
			o.addProperty("name", m[0]);
			o.addProperty("rank", m[1]);
			list.add(o);
		}

		body.add("members", list);

		okHttpClient.newCall(builder.post(RequestBody.create(JSON, gson.toJson(body))).build()).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Sable: could not send the roster: {}", e.getMessage());
				done.accept(null);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response r = response)
				{
					String text = r.body() != null ? r.body().string() : "";
					JsonObject json = gson.fromJson(text, JsonObject.class);
					done.accept(json != null && json.has("result") ? json.get("result").getAsString() : null);
				}
				catch (IOException | RuntimeException e)
				{
					done.accept(null);
				}
			}
		});
	}

	/** What was written in the clan chat channel in Discord since the last look. Silent on any failure. */
	void pollChat(long since, Consumer<JsonObject> done)
	{
		Request.Builder builder = key().isEmpty() ? null : request("/plugin/v1/chat?since=" + since);

		if (builder == null)
		{
			return;
		}

		okHttpClient.newCall(builder.get().build()).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Sable: could not fetch the chat: {}", e.getMessage());
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response r = response)
				{
					if (r.isSuccessful() && r.body() != null)
					{
						done.accept(gson.fromJson(r.body().string(), JsonObject.class));
					}
				}
				catch (IOException | RuntimeException e)
				{
					log.debug("Sable: could not read the chat: {}", e.getMessage());
				}
			}
		});
	}

	/**
	 * Gets the chat picture ready, before any line needs it.
	 *
	 * Registered when the plugin starts rather than when the first line comes,
	 * so the game has it by the time a line is drawn.
	 */
	void prepareDiscordIcon()
	{
		clientThread.invoke(() ->
		{
			if (discordIcon < 0)
			{
				discordIcon = chatIconManager.reserveChatIcon();
				chatIconManager.updateChatIcon(discordIcon, bubble());
			}
		});
	}

	/** Swaps the picture for the Discord server's own icon, once it has been fetched. */
	void setDiscordIcon(java.awt.image.BufferedImage image)
	{
		clientThread.invoke(() ->
		{
			if (discordIcon >= 0)
			{
				chatIconManager.updateChatIcon(discordIcon, image);
			}
		});
	}

	/** A line from Discord in the player's own game chat. Only they see it; it is never sent to the clan chat. */
	void discordLine(String name, String text)
	{
		String clean = text.replace('<', ' ').replace('>', ' ');
		String who = name.replace('<', ' ').replace('>', ' ');

		clientThread.invoke(() ->
		{
			if (discordIcon < 0)
			{
				discordIcon = chatIconManager.reserveChatIcon();
				chatIconManager.updateChatIcon(discordIcon, bubble());
			}

			// [icon] name: text, the icon between brackets like the clan's own tag.
			chatMessageManager.queue(QueuedMessage.builder()
				.type(ChatMessageType.CONSOLE)
				.runeLiteFormattedMessage(new ChatMessageBuilder()
					.append(ChatColorType.NORMAL)
					.append("[")
					.img(chatIconManager.chatIconIndex(discordIcon))
					.append("] ")
					.append(ChatColorType.HIGHLIGHT)
					.append(who + ": ")
					.append(ChatColorType.NORMAL)
					.append(clean)
					.build())
				.build());
		});
	}

	/** A small blurple speech bubble: a chat symbol of our own, not anybody's logo. */
	static java.awt.image.BufferedImage bubble()
	{
		java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(14, 14, java.awt.image.BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D g = image.createGraphics();

		try
		{
			g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(new java.awt.Color(88, 101, 242));
			g.fillRoundRect(0, 1, 14, 10, 6, 6);
			g.fillPolygon(new int[]{3, 3, 7}, new int[]{9, 13, 9}, 3);
			g.setColor(java.awt.Color.WHITE);
			g.fillOval(3, 5, 2, 2);
			g.fillOval(6, 5, 2, 2);
			g.fillOval(9, 5, 2, 2);
		}
		finally
		{
			g.dispose();
		}

		return image;
	}

	/** A test button: asks the bot for a marked sample card, and says in chat what came of it. */
	void test(String rsn, String kind)
	{
		Request.Builder builder = key().isEmpty() ? null : request("/plugin/v1/event");

		if (builder == null)
		{
			chat("Sable test: paste your key in the settings first.");

			return;
		}

		Request request = builder.post(RequestBody.create(JSON, gson.toJson(Bodies.test(rsn, kind)))).build();

		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				chat("Sable test: could not reach the bot (" + e.getMessage() + ")");
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response r = response)
				{
					String text = r.body() == null ? "" : r.body().string();
					chat(describeTest(r.code(), text));
				}
				catch (IOException e)
				{
					chat("Sable test: the bot's answer could not be read.");
				}
			}
		});
	}

	private String describeTest(int code, String text)
	{
		if (code == 401)
		{
			return "Sable test: your key was not accepted. Run /plugin in Discord for a new one.";
		}

		if (code == 403)
		{
			return "Sable test: this character is not linked to your key.";
		}

		String result = "";

		try
		{
			result = gson.fromJson(text, JsonObject.class).get("result").getAsString();
		}
		catch (RuntimeException e)
		{
			// Not the bot's usual answer; falls through to the generic line.
		}

		switch (result)
		{
			case "posted":
				return "Sable test: posted. Look in the clan's channel for the card marked TEST.";
			case "staff-only":
				return "Sable test: only clan staff can use the test buttons.";
			case "no-channel":
				return "Sable test: no channel is set for that in the bot's /setup.";
			default:
				return "Sable test: the bot answered HTTP " + code + " " + result;
		}
	}

	private void post(JsonObject body)
	{
		Request.Builder builder = key().isEmpty() ? null : request("/plugin/v1/event");

		if (builder == null)
		{
			return;
		}

		attempt(builder.post(RequestBody.create(JSON, gson.toJson(body))).build(), 1);
	}

	private void attempt(Request request, int n)
	{
		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				retry(request, n, e.getMessage());
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response r = response)
				{
					if (r.isSuccessful())
					{
						connected = true;
					}
					else if (r.code() == 401)
					{
						connected = false;
						warnOnce("Sable: your key was not accepted. Run /plugin in Discord for a new one.");
					}
					else if (r.code() == 403)
					{
						// The bot is there and the key is good; this character just is not linked yet.
						connected = true;
						warnOnce("Sable: this character is not linked to your key. Link it in Discord first.");
					}
					else if (r.code() == 429 || r.code() >= 500)
					{
						retry(request, n, "HTTP " + r.code());
					}
					else
					{
						log.debug("Sable: dropped an event, HTTP {}", r.code());
					}
				}
			}
		});
	}

	private void retry(Request request, int n, String why)
	{
		if (n >= TRIES)
		{
			connected = false;
			log.debug("Sable: dropped an event after {} tries: {}", n, why);

			return;
		}

		executor.schedule(() -> attempt(request, n + 1), BACKOFF_SECONDS[Math.min(n - 1, BACKOFF_SECONDS.length - 1)], TimeUnit.SECONDS);
	}

	private void warnOnce(String text)
	{
		if (!warnedAboutKey)
		{
			warnedAboutKey = true;
			chat(text);
		}
	}

	/** A line in the player's own chat. */
	void say(String text)
	{
		chat(text);
	}

	private void chat(String text)
	{
		clientThread.invoke(() -> chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(text)
			.build()));
	}
}
