/*
 * Copyright (c) 2018, Tomas Slusny <slusnucky@gmail.com>
 * Copyright (c) 2021, Jonathan Rousseau <https://github.com/JoRouss>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.party;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Keybind;

@ConfigGroup(PartyConfig.GROUP)
public interface PartyConfig extends Config
{
	String GROUP = "party";

	@ConfigItem(
		keyName = "pings",
		name = "Pings",
		description = "Enables party pings",
		position = 1
	)
	default boolean pings()
	{
		return true;
	}

	@ConfigItem(
		keyName = "sounds",
		name = "Sound on ping",
		description = "Enables sound notification on party ping",
		position = 2
	)
	default boolean sounds()
	{
		return true;
	}

	@ConfigItem(
		keyName = "recolorNames",
		name = "Recolor names",
		description = "Recolor party members names based on unique color hash",
		position = 3
	)
	default boolean recolorNames()
	{
		return true;
	}

	@ConfigItem(
		keyName = "pingHotkey",
		name = "Ping hotkey",
		description = "Key to hold to send a tile ping",
		position = 4
	)
	default Keybind pingHotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		keyName = "previousPartyId",
		name = "",
		description = "",
		hidden = true
	)
	default String previousPartyId()
	{
		return "";
	}

	@ConfigItem(
		keyName = "previousPartyId",
		name = "",
		description = "",
		hidden = true
	)
	void setPreviousPartyId(String id);
}
