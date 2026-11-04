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

	@Test
	public void aTestRequestCarriesOnlyTheNameAndTheKind()
	{
		JsonObject body = Bodies.test("Zezima", "pb");

		assertEquals("test", body.get("type").getAsString());
		assertEquals("Zezima", body.get("rsn").getAsString());
		assertEquals("pb", body.get("kind").getAsString());
		assertEquals(3, body.entrySet().size());
	}
}
