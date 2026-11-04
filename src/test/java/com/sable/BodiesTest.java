package com.sable;

import static org.junit.Assert.assertEquals;

import com.google.gson.JsonObject;
import org.junit.Test;

public class BodiesTest
{
	@Test
	public void aCheckInCarriesOnlyTheName()
	{
		JsonObject body = Bodies.hello("Zezima");

		assertEquals("hello", body.get("type").getAsString());
		assertEquals("Zezima", body.get("rsn").getAsString());
		assertEquals(2, body.entrySet().size());
	}
}
