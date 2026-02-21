package org.histidine.chatter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.histidine.chatter.ChatterLine.MessageType;
import org.jetbrains.annotations.NotNull;

public class ChatterCharacter implements Comparable<ChatterCharacter> {

	public String name;
	public String id;
	public List<String> personalities = new ArrayList<>();
	public List<String> gender = new ArrayList<>();
	public Set<String> categoryTags = new HashSet<>();
	public float chance = 1;
	public float talkativeness = 1;
	public Set<String> allowedFactions = new HashSet<>();
	public boolean allowedForAI;
	public final Map<MessageType, List<ChatterLine>> lines = new HashMap<>();
	public boolean isDefault = false;

	@Override
	public int compareTo(@NotNull ChatterCharacter o) {
		// default characters first
		if (isDefault && !o.isDefault) return -1;
		if (!isDefault && o.isDefault) return 1;

		// compare by ID prefix
		String[] split = id.split("_");
		String[] otherSplit = o.id.split("_");
		String prefix = split[0];
		String otherPrefix = otherSplit[0];
		if (prefix == null && otherPrefix != null) return -1;
		if (prefix != null && otherPrefix == null) return 1;

		if (prefix != null && !prefix.equals(otherPrefix)) {
			return prefix.compareTo(otherPrefix);
		}


		// compare by name
		return name.compareTo(o.name);
	}
}
