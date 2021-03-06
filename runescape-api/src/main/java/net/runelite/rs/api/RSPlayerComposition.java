package net.runelite.rs.api;

import net.runelite.api.PlayerComposition;
import net.runelite.mapping.Import;

public interface RSPlayerComposition extends PlayerComposition
{
	@Import("isFemale")
	boolean isFemale();

	@Import("bodyColors")
	int[] getColors();

	@Import("hash")
	long getHash();

	@Import("equipment")
	@Override
	int[] getEquipmentIds();

	@Import("npcTransformId")
	@Override
	void setTransformedNpcId(int id);

	@Import("npcTransformId")
	@Override
	int getTransformedNpcId();

	@Import("setHash")
	@Override
	void setHash();

	@Import("playerEquipmentItems")
	@Override
	RSPlayerEquipmentItem[] getPlayerEquipmentItems();
}
