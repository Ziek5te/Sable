package com.sable;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

// Starts RuneLite with Sable loaded, for trying it in the game: gradlew runClient
public class SablePluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(SablePlugin.class);
		RuneLite.main(args);
	}
}