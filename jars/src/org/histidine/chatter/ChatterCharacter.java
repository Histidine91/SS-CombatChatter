package org.histidine.chatter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.histidine.chatter.ChatterLine.MessageType;

public class ChatterCharacter {

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
}
