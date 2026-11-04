package com.sable;

import com.google.gson.JsonObject;

/** The request bodies that are not an activity. Plain gson, so they test without the client. */
final class Bodies
{
	private Bodies()
	{
	}

	/** "This key's player is logged in as this name." Nothing else rides along. */
	static JsonObject hello(String rsn)
	{
		JsonObject body = new JsonObject();
		body.addProperty("type", "hello");
		body.addProperty("rsn", rsn);

		return body;
	}
}
