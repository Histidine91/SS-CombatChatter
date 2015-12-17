package org.histidine.chatter.combat;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.EveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI.ShipTypeHints;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.fleets.FleetFactoryV2;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;
import org.histidine.chatter.utils.GeneralUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.awt.Color;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import org.histidine.chatter.utils.StringHelper;


public class ChatterCombatPlugin implements EveryFrameCombatPlugin {

	public static final String CHARACTERS_DIR = "data/config/chatter/";
	public static final String CHARACTERS_LIST = CHARACTERS_DIR + "characters.csv";
	public static final String CONFIG_FILE = "chatterConfig.json";
	public static Logger log = Global.getLogger(ChatterCombatPlugin.class);
	
	public static final Map<String, ChatterCharacter> CHARACTERS = new HashMap<>();
	public static final float PRIORITY_PER_MESSAGE = 20;
	public static final float PRIORITY_DECAY = 3;
	//public static final Map<MessageType, Float> MESSAGE_TYPE_PRIORITY = new HashMap<>();
	public static final Map<MessageType, Float> MESSAGE_TYPE_MAX_PRIORITY = new HashMap<>();
	public static final Set<MessageType> IDLE_CHATTER_TYPES = new HashSet<>();
	public static final float MAX_TIME_FOR_INTRO = 8;
	public static final float MESSAGE_INTERVAL = 3;
	
	public static boolean idleChatter = true;
	public static boolean allyChatter = true;
	
	protected CombatEngineAPI engine;
	protected IntervalUtil interval = new IntervalUtil(0.25f, 0.3f);
	protected Map<FleetMemberAPI, ShipStateData> states = new HashMap<>();
	protected Map<String, Float> messageCooldown = new HashMap<>();
	protected FleetMemberAPI lastTalker = null;
	protected float timeElapsed = 0;
	protected float lastMessageTime = 0;
	protected float priorityThreshold = 0;	// if this is high, low priority messages won't be displayed
	protected boolean introDone = false;
	protected boolean victory = false;
	
	// TODO externalise
	static {
		try {
			JSONObject settings = Global.getSettings().loadJSON(CONFIG_FILE);
			idleChatter = settings.optBoolean("idleChatter", idleChatter);
			allyChatter = settings.optBoolean("allyChatter", allyChatter);
		} 
		catch (IOException | JSONException ex) {
			log.error(ex);
		}
		
		loadCharacters();
		initPriorities();
	}
	
	protected static void initPriorities()
	{
		MESSAGE_TYPE_MAX_PRIORITY.put(MessageType.PURSUING, 0f);
		MESSAGE_TYPE_MAX_PRIORITY.put(MessageType.RUNNING, 20f);
		MESSAGE_TYPE_MAX_PRIORITY.put(MessageType.OVERLOAD, 30f);
		MESSAGE_TYPE_MAX_PRIORITY.put(MessageType.NEED_HELP, 5f);
		MESSAGE_TYPE_MAX_PRIORITY.put(MessageType.ENGAGED, 5f);
		MESSAGE_TYPE_MAX_PRIORITY.put(MessageType.HULL_90, 20f);
		MESSAGE_TYPE_MAX_PRIORITY.put(MessageType.HULL_50, 40f);
		MESSAGE_TYPE_MAX_PRIORITY.put(MessageType.HULL_30, 60f);
		MESSAGE_TYPE_MAX_PRIORITY.put(MessageType.DEATH, 10f);
		
		IDLE_CHATTER_TYPES.add(MessageType.START);
		IDLE_CHATTER_TYPES.add(MessageType.RETREAT);
		IDLE_CHATTER_TYPES.add(MessageType.PURSUING);
		IDLE_CHATTER_TYPES.add(MessageType.RUNNING);
		//RANDOM_CHATTER_TYPES.add(MessageType.OVERLOAD);
		IDLE_CHATTER_TYPES.add(MessageType.NEED_HELP);
		IDLE_CHATTER_TYPES.add(MessageType.ENGAGED);
		IDLE_CHATTER_TYPES.add(MessageType.VICTORY);
		IDLE_CHATTER_TYPES.add(MessageType.DEATH);
	}
	
	protected static void loadCharacters()
	{
		try {
			JSONArray charCSV = Global.getSettings().getMergedSpreadsheetDataForMod("character", CHARACTERS_LIST, "chatter");
			for(int x = 0; x < charCSV.length(); x++)
			{
				JSONObject row = charCSV.getJSONObject(x);
				String characterName = row.getString("character");
				try {
					JSONObject characterEntry = Global.getSettings().loadJSON(CHARACTERS_DIR + characterName + ".json");
					ChatterCharacter character = new ChatterCharacter();
					character.name = characterName;
					character.personalities = GeneralUtils.JSONArrayToStringList(characterEntry.optJSONArray("personalities"));
					character.gender = GeneralUtils.JSONArrayToStringList(characterEntry.optJSONArray("gender"));
					character.chance = (float)characterEntry.optDouble("chance", 1);
					character.talkativeness = (float)characterEntry.optDouble("chance", 1);
					
					JSONObject lines = characterEntry.getJSONObject("lines");
					Iterator<?> keys = lines.keys();
					while( keys.hasNext() ) {
						String key = (String)keys.next();
						MessageType type;
						try {
							type = MessageType.valueOf(key.toUpperCase());
						} catch (IllegalArgumentException ex) {
							continue;
						}
						JSONArray linesForKey = lines.getJSONArray(key);
						List<String> linesForKeyList = GeneralUtils.JSONArrayToStringList(linesForKey);
						character.lines.put(type, linesForKeyList);
					}
					
					CHARACTERS.put(characterName, character);
				} catch (IOException | JSONException ex) {	// can't read character file
					log.error(ex);
				}
			}
		} catch (Exception ex) {
			log.error(ex);
		}
	}
	
	protected ShipStateData makeShipStateEntry(FleetMemberAPI member)
	{
		ShipStateData data = new ShipStateData();
		data.hull = engine.getFleetManager(FleetSide.PLAYER).getShipFor(member).getHullLevel();
		
		boolean timid = false;
		if (member.getHullSpec().getHints().contains(ShipTypeHints.CIVILIAN))
			timid = true;
		PersonAPI captain = member.getCaptain();
		if (captain != null)
		{
			if (captain.getPersonalityAPI().getId().equals("timid"))
				timid = true;
			else if (captain.getPersonalityAPI().getId().equals("aggressive"))
				timid = false;
		}
		if (timid) data.characterName = "default_timid";
		
		states.put(member, data);
		return data;
	}
	
	protected String getShipName(FleetMemberAPI member)
	{
		String shipName = "";
		if (member.isFighterWing())
			shipName = member.getHullSpec().getHullName() + " wing";
		else
			shipName = member.getShipName() + " (" + member.getHullSpec().getHullName() + "-class)";
		return shipName;
	}
	
	protected float getMessageMaxPriority(MessageType category)
	{
		if (!MESSAGE_TYPE_MAX_PRIORITY.containsKey(category))
			return 999;
		return MESSAGE_TYPE_MAX_PRIORITY.get(category);
	}
	
	protected boolean hasLine(FleetMemberAPI member, MessageType category)
	{
		ShipStateData stateData = getShipStateData(member);
		String character = stateData.characterName;
		if (!CHARACTERS.containsKey(character))
			character = "default";
		
		List<String> lines = CHARACTERS.get(character).lines.get(category);
		return lines != null && !lines.isEmpty();
	}
	
	protected ShipStateData getShipStateData(FleetMemberAPI member)
	{
		if (!states.containsKey(member))
			makeShipStateEntry(member);
		return states.get(member);
	}
	
	protected Color getShipNameColor(FleetMemberAPI member)
	{
		if (member.isAlly()) return Misc.getHighlightColor();
		return Global.getSettings().getColor("textFriendColor");
	}
	
	/**
	 * Writes a random message of the appropriate category, based on the {@code member}'s assigned character
	 * @param member
	 * @param category
	 * @return true if message was printed
	 */
	protected boolean printRandomMessage(FleetMemberAPI member, MessageType category)
	{
		if (!idleChatter && IDLE_CHATTER_TYPES.contains(category))
			return false;
		
		if (!meetsPriorityThreshold(member, category))
		{
			//log.info("Threshold too high for message " + category.name() + ": " + threshold);
			return false;
		}
		
		ShipStateData stateData = getShipStateData(member);
		String character = stateData.characterName;
		if (!CHARACTERS.containsKey(character))
			character = "default";
		
		List<String> lines = CHARACTERS.get(character).lines.get(category);
		if (lines == null)
		{
			log.warn("Missing line category " + category.name() + " for character " + character);
			return false;
		}
		if (lines.isEmpty()) return false;
		
		String message = ": \"" + GeneralUtils.getRandomListElement(lines) + "\"";
		String name = getShipName(member);
		Color textColor = Global.getSettings().getColor("standardTextColor");
		if (category == MessageType.HULL_50)
			textColor = Color.YELLOW;
		else if (category == MessageType.HULL_30)
			textColor = Misc.getNegativeHighlightColor();
		engine.getCombatUI().addMessage(1, member, getShipNameColor(member), name, textColor, message);
		lastMessageTime = timeElapsed;
		log.info("Time elapsed: " + lastMessageTime);
		priorityThreshold += PRIORITY_PER_MESSAGE;
		lastTalker = member;
		return true;
	}
	
	protected boolean meetsPriorityThreshold(FleetMemberAPI member, MessageType category)
	{
		float threshold = priorityThreshold;
		if (lastTalker == member) threshold *= 2f;
		if (member.isAlly()) threshold *= 2f;
		
		return (getMessageMaxPriority(category) >= threshold);
	}
	
	/**
	 * Writes hull damage warning messages
	 * @param member
	 * @param category Can be HULL_30, HULL_50 or HULL_90
	 * @param amount Number to write in short form message (30, 50 or 90)
	 * @return true if long form message was printed, false otherwise
	 */
	protected boolean printHullMessage(FleetMemberAPI member, MessageType category, int amount)
	{
		if (category == MessageType.HULL_50)
			Global.getSoundPlayer().playUISound("cr_allied_warning", 1, 1);
		else if (category == MessageType.HULL_30)
			Global.getSoundPlayer().playUISound("cr_allied_malfunction", 1, 1);
		if (!meetsPriorityThreshold(member, category) || !hasLine(member, category))
		{
			// short form
			String message1 = " " + StringHelper.getString("chatter_general", "isAt") + " ";
			String message2 = amount + "% " + StringHelper.getString("chatter_general", "hull") + "!";
			String name = getShipName(member);
			Color textColor = Global.getSettings().getColor("standardTextColor");
			if (category == MessageType.HULL_50)
				textColor = Color.YELLOW;
			else if (category == MessageType.HULL_30)
				textColor = Misc.getNegativeHighlightColor();
			engine.getCombatUI().addMessage(1, member, getShipNameColor(member), name, 
					Global.getSettings().getColor("standardTextColor"), message1, textColor, message2);
			return false;
		}
		else
		{
			return printRandomMessage(member, category);
		}
	}
	
	/**
	 * Writes hull damage warning messages
	 * @param member
	 * @return true if long form message was printed, false otherwise
	 */
	protected boolean printOverloadMessage(FleetMemberAPI member)
	{
		if (meetsPriorityThreshold(member, MessageType.OVERLOAD) || !hasLine(member, MessageType.OVERLOAD))
		{
			// short form
			String message = " " + StringHelper.getString("chatter_general", "hasOverloaded") + "!";
			String name = getShipName(member);
			Color textColor = Global.getSettings().getColor("standardTextColor");
			engine.getCombatUI().addMessage(1, member, getShipNameColor(member), name, 
					Global.getSettings().getColor("standardTextColor"), message);
			return false;
		}
		else
		{
			return printRandomMessage(member, MessageType.OVERLOAD);
		}
	}
	
	protected void playIntroMessage(List<FleetMemberAPI> deployed)
	{
		if (timeElapsed > MAX_TIME_FOR_INTRO) {
			introDone = true;
		}
		else {
			FleetMemberAPI random = pickRandomMemberFromList(deployed);
			if (random != null)
			{
				//DeployedFleetMemberAPI randomD = fm.getDeployedFleetMember(fm.getShipFor(random));
				//engine.getCombatUI().addMessage(0, engine.getContext().getPlayerGoal().name());
				if (engine.getContext().getPlayerGoal() == FleetGoal.ESCAPE)
					printRandomMessage(random, MessageType.RETREAT);
				else
					printRandomMessage(random, MessageType.START);
				introDone = true;
			}
		}
	}
	
	// TODO
	protected FleetMemberAPI pickRandomMemberFromList(List<FleetMemberAPI> members)
	{
		WeightedRandomPicker<FleetMemberAPI> picker = new WeightedRandomPicker<>();
		for (FleetMemberAPI member : members)
		{
			//if (member.isFighterWing()) continue;
			if (member.isFlagship()) continue;
			CombatFleetManagerAPI fm = engine.getFleetManager(FleetSide.PLAYER);
			if (fm.getShipFor(member).getShipAI() == null) continue;	// under AI control;
			if (fm.getShipFor(member).isHulk()) continue;
			float weight = FleetFactoryV2.getPointsForVariant(member.getVariant().getHullVariantId());
			if (member.getCaptain() != null) weight *= 4;
			if (member.isFighterWing()) weight *= 0.5f;
			if (member.isAlly()) {
				if (allyChatter) weight *= 0.5f;
				else continue;
			}
			
			ShipStateData stateData = getShipStateData(member);
			String character = stateData.characterName;
			if (!CHARACTERS.containsKey(character))
				character = "default";

			weight *= CHARACTERS.get(character).talkativeness;
			
			picker.add(member, weight);
		}
		if (picker.isEmpty()) return null;
		return picker.pick();
	}
	
	@Override
	public void advance(float amount, List<InputEventAPI> events) {
		if (engine == null) return;
		if (engine.isPaused()) return;
		if (engine.isSimulation()) return;
		if (Global.getCurrentState() == GameState.TITLE) return;
		
		timeElapsed += amount;
		priorityThreshold -= amount * PRIORITY_DECAY;
		if (priorityThreshold < 0) priorityThreshold = 0;
		interval.advance(amount);
		if (!interval.intervalElapsed()) return;
		
		CombatFleetManagerAPI fm = engine.getFleetManager(FleetSide.PLAYER);
		List<FleetMemberAPI> deployed = fm.getDeployedCopy();
		if (deployed.isEmpty()) return;
		
		if (!introDone)
		{
			playIntroMessage(deployed);
		}
		
		boolean printed = false;
		
		// victory message
		if (!victory && engine.getFleetManager(FleetSide.ENEMY).getTaskManager(false).isInFullRetreat() 
				&& engine.getFleetManager(FleetSide.ENEMY).getGoal() != FleetGoal.ESCAPE)
		{
			FleetMemberAPI random = pickRandomMemberFromList(deployed);
			if (random != null)
			{
				printRandomMessage(random, MessageType.VICTORY);
			}
			victory = true;
		}
		// full retreat message (same as start escape)
		else if (!victory && engine.getFleetManager(FleetSide.PLAYER).getTaskManager(false).isInFullRetreat())
		{
			FleetMemberAPI random = pickRandomMemberFromList(deployed);
			if (random != null)
			{
				printRandomMessage(random, MessageType.RETREAT);
			}
			victory = true;
		}
		
		for (FleetMemberAPI member : deployed)
		{
			if (member.isFighterWing()) continue;
			if (member.isFlagship()) continue;
			if (!allyChatter && member.isAlly()) continue;
			
			ShipStateData stateData = getShipStateData(member);
			if (stateData.dead) continue;
			ShipAPI ship = fm.getShipFor(member);
			if (ship.getShipAI() == null) continue;	// under AI control;
			float hull = ship.getHullLevel();
			float oldHull = stateData.hull;
			
			if (!ship.isAlive()) continue;
			
			if (hull <= 0.3 && oldHull > 0.3)
				printed = printHullMessage(member, MessageType.HULL_30, 30);
			else if (hull <= 0.5 && oldHull > 0.5)
				printed = printHullMessage(member, MessageType.HULL_50, 50);
			else if (hull <= 0.9 && oldHull > 0.9)
				printed = printHullMessage(member, MessageType.HULL_90, 90);
			
			stateData.hull = hull;
		}
		
		boolean canPrint = printed;
		//if (printed) return;
		if (timeElapsed < lastMessageTime + MESSAGE_INTERVAL)
		{
			//log.info("Too soon for next message: " + timeElapsed + " / " + lastMessageTime);
			//return;
			canPrint = false;
		}
		
		for (FleetMemberAPI member : deployed)
		{
			boolean isFighter = member.isFighterWing();
			//if (member.isFighterWing()) continue;
			if (member.isFlagship()) continue;
			if (!allyChatter && member.isAlly()) continue;
			
			ShipStateData stateData = getShipStateData(member);
			if (stateData.dead) continue;
			ShipAPI ship = fm.getShipFor(member);
			if (ship.getShipAI() == null) continue;	// under AI control;
			ShipwideAIFlags flags = ship.getAIFlags();
			
			if (!ship.isAlive() && !stateData.dead) {
				if (!printed && !isFighter)
					printed = printRandomMessage(member, MessageType.DEATH);
				stateData.dead = true;
				continue;
			}
			boolean overloaded = false;
			if (ship.getFluxTracker() != null) overloaded = ship.getFluxTracker().isOverloaded();
			
			if (!isFighter && overloaded && !stateData.overloaded)
				printed = printOverloadMessage(member);
			
			stateData.overloaded = overloaded;
			
			if (flags != null)
			{
				if ((canPrint || !printed))
				{
					//if (!isFighter && flags.hasFlag(AIFlags.NEEDS_HELP) && !stateData.needHelp)
					//	printed = printRandomMessage(member, MessageType.NEED_HELP);
					//else if (flags.hasFlag(AIFlags.PURSUING) && !stateData.pursuing)	// fighters can say this
						//printed = printRandomMessage(member, MessageType.PURSUING);
					if (!isFighter && flags.hasFlag(AIFlags.RUN_QUICKLY) && !stateData.running)
						printed = printRandomMessage(member, MessageType.RUNNING);
					//else if (!isFighter && flags.hasFlag(AIFlags.NEEDS_HELP) && !stateData.needHelp)
					//	printed = printRandomMessage(member, MessageType.ENGAGED);
				}
				stateData.pursuing = flags.hasFlag(AIFlags.PURSUING);
				stateData.running = flags.hasFlag(AIFlags.RUN_QUICKLY);
				stateData.needHelp = flags.hasFlag(AIFlags.NEEDS_HELP);
			}
			//log.info(member.getShipName() + " pursuing target? " + flags.hasFlag(AIFlags.PURSUING));
			//log.info(member.getShipName() + " running? " + flags.hasFlag(AIFlags.RUN_QUICKLY));
			//log.info(member.getShipName() + " needs help? " + flags.hasFlag(AIFlags.NEEDS_HELP));
			
			
		}
	}

	@Override
	public void renderInWorldCoords(ViewportAPI vapi) {
	}

	@Override
	public void renderInUICoords(ViewportAPI vapi) {
	}

	@Override
	public void init(CombatEngineAPI engine) {
		this.engine = engine;
	}
	
	protected static class ShipStateData
	{
		public String characterName = "default";
		public boolean dead = false;
		public boolean needHelp = false;
		public boolean pursuing = false;
		public boolean backingOff = false;
		public boolean running = false;
		public float hull = 0;
		public boolean overloaded = false;
	}
	
	protected static class ChatterCharacter
	{
		public String name;
		public List<String> personalities = new ArrayList<>();
		public List<String> gender = new ArrayList<>();
		public float chance = 1;
		public float talkativeness = 1;
		public final Map<MessageType, List<String>> lines = new HashMap<>();
	}
	
	protected static enum MessageType {
		START, RETREAT, VICTORY,
		PURSUING, RUNNING, NEED_HELP, OUT_OF_MISSILES, ENGAGED,
		HULL_90, HULL_50, HULL_30, OVERLOAD, DEATH
	}
}
