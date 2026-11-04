package com.sable;

import static org.junit.Assert.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.Test;

public class KeyCodeTest
{
	private static String code(String key, String address)
	{
		return key + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(address.getBytes(StandardCharsets.UTF_8));
	}

	@Test
	public void theCodeSplitsIntoTheKeyAndTheAddress()
	{
		KeyCode k = KeyCode.parse(code("sbl_abc123", "http://203.0.113.9:5034"));

		assertEquals("sbl_abc123", k.key);
		assertEquals("http://203.0.113.9:5034", k.address);
	}

	@Test
	public void aTrailingSlashAndSpacesAreTidiedAway()
	{
		KeyCode k = KeyCode.parse("  " + code("sbl_abc123", "https://bot.example.test/") + "  ");

		assertEquals("sbl_abc123", k.key);
		assertEquals("https://bot.example.test", k.address);
	}

	@Test
	public void aKeyWithNoAddressPartHasNoAddress()
	{
		KeyCode k = KeyCode.parse("sbl_abc123");

		assertEquals("sbl_abc123", k.key);
		assertEquals("", k.address);
	}

	@Test
	public void anythingThatIsNotAnHttpAddressIsIgnored()
	{
		assertEquals("", KeyCode.parse(code("sbl_x", "javascript:alert(1)")).address);
		assertEquals("", KeyCode.parse(code("sbl_x", "ftp://host")).address);
		assertEquals("", KeyCode.parse("sbl_x.!!!not-base64!!!").address);
	}

	@Test
	public void nothingPastedGivesNothing()
	{
		assertEquals("", KeyCode.parse(null).key);
		assertEquals("", KeyCode.parse("").address);
		assertEquals("", KeyCode.parse("   ").key);
	}
}
