package com.sable;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * The one code a member pastes into the settings: the key, a dot, then the bot's address, encoded.
 *
 * It lets the plugin carry no address of its own and the member type none: the clan's bot
 * puts its own address into the code it hands out. Only the key part is ever sent as the
 * credential. A code with no address part, or one that is not an http(s) address, gives
 * an empty address, and then nothing is sent.
 */
final class KeyCode
{
	final String key;
	final String address;

	private KeyCode(String key, String address)
	{
		this.key = key;
		this.address = address;
	}

	/** Splits what was pasted. Never throws; anything unreadable is an empty key and address. */
	static KeyCode parse(String pasted)
	{
		String text = pasted == null ? "" : pasted.trim();
		int dot = text.indexOf('.');

		if (dot < 0)
		{
			return new KeyCode(text, "");
		}

		String key = text.substring(0, dot).trim();
		String address = "";

		try
		{
			String decoded = new String(Base64.getUrlDecoder().decode(text.substring(dot + 1).trim()), StandardCharsets.UTF_8).trim();

			while (decoded.endsWith("/"))
			{
				decoded = decoded.substring(0, decoded.length() - 1);
			}

			if (decoded.toLowerCase().startsWith("http://") || decoded.toLowerCase().startsWith("https://"))
			{
				address = decoded;
			}
		}
		catch (IllegalArgumentException e)
		{
			// Not a code from the clan's bot; no address, so nothing is sent.
		}

		return new KeyCode(key, address);
	}
}
