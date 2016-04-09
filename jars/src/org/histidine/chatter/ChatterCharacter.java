package org.histidine.chatter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.histidine.chatter.ChatterLine.MessageType;

public class ChatterCharacter {

	public String name;
	public List<String> personalities = new ArrayList<>();
	public List<String> gender = new ArrayList<>();
	public float chance = 1;
	public float talkativeness = 1;
	public final Map<MessageType, List<ChatterLine>> lines = new HashMap<>();

}
