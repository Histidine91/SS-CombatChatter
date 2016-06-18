package org.histidine.chatter.combat;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI.CrewXPLevel;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI;
import com.fs.starfarer.api.combat.EveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipHullSpecAPI.ShipTypeHints;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;
import org.histidine.chatter.utils.GeneralUtils;
import java.awt.Color;
import java.util.HashSet;
import java.util.Set;
import org.histidine.chatter.ChatterConfig;
import org.histidine.chatter.ChatterLine;
import org.histidine.chatter.ChatterLine.MessageType;
import org.histidine.chatter.campaign.CampaignHandler;
import static org.histidine.chatter.campaign.CampaignHandler.CHARACTERS_MAP;
import org.histidine.chatter.utils.StringHelper;
import org.lwjgl.util.vector.Vector2f;


public class ChatterCombatPlugin implements EveryFrameCombatPlugin {

	public static final String PERSISTENT_DATA_KEY = "combatChatter";
	public static Logger log = Global.getLogger(ChatterCombatPlugin.class);
	
	public static final float PRIORITY_PER_MESSAGE = 20;
	public static final float PRIORITY_DECAY = 3;
	//public static final Map<MessageType, Float> MESSAGE_TYPE_PRIORITY = new HashMap<>();
	public static final Map<MessageType, Float> MESSAGE_TYPE_MAX_PRIORITY = new HashMap<>();
	public static final Set<MessageType> IDLE_CHATTER_TYPES = new HashSet<>();
	public static final Set<MessageType> FLOAT_CHATTER_TYPES = new HashSet<>();
	public static final Set<String> EXCLUDED_HULLS = new HashSet<>();
	public static final float MAX_TIME_FOR_INTRO = 8;
	public static final float MESSAGE_INTERVAL = 3;
	public static final float MESSAGE_INTERVAL_IDLE = 6;
	public static final float MESSAGE_INTERVAL_FLOAT = 4;
	
	protected CombatEngineAPI engine;
	protected IntervalUtil interval = new IntervalUtil(0.4f, 0.5f);
	protected Map<FleetMemberAPI, ShipStateData> states = new HashMap<>();
	protected Map<String, Float> messageCooldown = new HashMap<>();
	protected FleetMemberAPI lastTalker = null;
	protected float timeElapsed = 0;
	protected float lastMessageTime = -9999;
	protected float priorityThreshold = 0;	// if this is high, low priority messages won't be displayed
	protected boolean introDone = false;
	protected boolean victory = false;
	
	// TODO externalise
	static {
		initPriorities();
	}
	
	protected static void initPriorities()
	{
		MESSAGE_TYPE_MAX_PRIORITY.put(MessageType.PURSUING, 0f);
		MESSAGE_TYPE_MAX_PRIORITY.put(MessageType.RUNNING, 20f);
		MESSAGE_TYPE_MAX_PRIORITY.put(MessageType.OVERLOAD, 30f);
		MESSAGE_TYPE_MAX_PRIORITY.put(MessageType.NEED_HELP, 5f);
		MESSAGE_TYPE_MAX_PRIORITY.put(MessageType.ENGAGED, 5f);
		MESSAGE_TYPE_MAX_PRIORITY.put(MessageType.OUT_OF_MISSILES, 8f);
		MESSAGE_TYPE_MAX_PRIORITY.put(MessageType.HULL_90, 20f);
		MESSAGE_TYPE_MAX_PRIORITY.put(MessageType.HULL_50, 40f);
		MESSAGE_TYPE_MAX_PRIORITY.put(MessageType.HULL_30, 60f);
		MESSAGE_TYPE_MAX_PRIORITY.put(MessageType.DEATH, 15f);
		
		IDLE_CHATTER_TYPES.add(MessageType.START);
		IDLE_CHATTER_TYPES.add(MessageType.RETREAT);
		IDLE_CHATTER_TYPES.add(MessageType.PURSUING);
		IDLE_CHATTER_TYPES.add(MessageType.RUNNING);
		//IDLE_CHATTER_TYPES.add(MessageType.OVERLOAD);
		IDLE_CHATTER_TYPES.add(MessageType.NEED_HELP);
		IDLE_CHATTER_TYPES.add(MessageType.ENGAGED);
		IDLE_CHATTER_TYPES.add(MessageType.VICTORY);
		IDLE_CHATTER_TYPES.add(MessageType.DEATH);
		
		FLOAT_CHATTER_TYPES.add(MessageType.PURSUING);
		FLOAT_CHATTER_TYPES.add(MessageType.NEED_HELP);
		FLOAT_CHATTER_TYPES.add(MessageType.RUNNING);
		
		EXCLUDED_HULLS.add("ii_titan");
		EXCLUDED_HULLS.add("ii_mirv");
	}
	
	protected String getCharacterForFleetMember(FleetMemberAPI member)
	{
		if (EXCLUDED_HULLS.contains(member.getHullId()))
			return "null";
		
		PersonAPI captain = member.getCaptain();
		if ((captain != null && !captain.isDefault()) || engine.isMission())
		{
			return CampaignHandler.getCharacterForOfficer(captain, member.isAlly(), engine);
		}
		
		String name = "default";
		CrewXPLevel xpLevel = member.getCrewXPLevel();
		boolean timid = false;
		if (member.getHullSpec().getHints().contains(ShipTypeHints.CIVILIAN) && !(xpLevel == CrewXPLevel.VETERAN || xpLevel == CrewXPLevel.ELITE))
			timid = true;
		else if (xpLevel == CrewXPLevel.GREEN)
			timid = true;
		
		if (timid) name = "default_timid";
		else if (xpLevel == CrewXPLevel.ELITE) name = "default_professional";
		if (Math.random() > 0.5) name += "2";
		
		return name;
	}
	
	/**
	 * Makes a ShipStateData to track the condition of the {@code member} for chatter
	 * @param member
	 * @return
	 */
	protected ShipStateData makeShipStateEntry(FleetMemberAPI member)
	{
		ShipStateData data = new ShipStateData();
		ShipAPI ship = engine.getFleetManager(FleetSide.PLAYER).getShipFor(member);
		if (ship != null) {
			data.hull = ship.getHullLevel();
			//data.maxOPs = ship.getHullSpec().
			for (WeaponAPI wep : ship.getAllWeapons())
			{
				float op = wep.getSpec().getOrdnancePointCost(null);
				data.maxOPs += op;
				if (wep.usesAmmo() && wep.getType() == WeaponType.MISSILE)
					data.missileOPs += op;
			}
			if (data.missileOPs < data.maxOPs * ChatterConfig.minMissileOPFractionForChatter)
				data.canWriteOutOfMissiles = false;	// don't bother writing out of missiles message
		}
		data.characterName = getCharacterForFleetMember(member);
		
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
		if (!CHARACTERS_MAP.containsKey(character))
			character = "default";
		
		List<ChatterLine> lines = CHARACTERS_MAP.get(character).lines.get(category);
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
	
	protected boolean isFloatingMessage(MessageType category)
	{
		boolean floater = FLOAT_CHATTER_TYPES.contains(category);
		if (!ChatterConfig.idleChatter)
			floater = floater || IDLE_CHATTER_TYPES.contains(category);
		return floater;
	}
	
	/**
	 * Writes a random message of the appropriate category, based on the {@code member}'s assigned character
	 * @param member
	 * @param category
	 * @return true if message was printed
	 */
	protected boolean printRandomMessage(FleetMemberAPI member, MessageType category)
	{
		boolean floater = isFloatingMessage(category);
		
		if (!floater && !meetsPriorityThreshold(member, category))
		{
			//log.info("Threshold too high for message " + category.name());
			return false;
		}
		
		
		float msgInterval = MESSAGE_INTERVAL;
		if (IDLE_CHATTER_TYPES.contains(category)) msgInterval = MESSAGE_INTERVAL_IDLE;
		if (lastTalker == member) msgInterval *= 1.5f;
		if (!floater && timeElapsed < lastMessageTime + msgInterval)
		{
			//log.info("Too soon for next message: " + timeElapsed + " / " + lastMessageTime);
			return false;
		}
		
		ShipStateData stateData = getShipStateData(member);
		if (floater && stateData.lastFloatMessageType == category)
		{
			if (timeElapsed < stateData.lastFloatMessageTime + MESSAGE_INTERVAL_FLOAT)
				return false;
		}
		
		String character = stateData.characterName;
		if (!CHARACTERS_MAP.containsKey(character))
			character = "default";
		
		List<ChatterLine> lines = CHARACTERS_MAP.get(character).lines.get(category);
		if (lines == null)
		{
			//log.warn("Missing line category " + category.name() + " for character " + character);
			return false;
		}
		if (lines.isEmpty()) return false;
		
		ChatterLine line = (ChatterLine)GeneralUtils.getRandomListElement(lines);
		String message = "\"" + line.text + "\"";
		String name = getShipName(member);
		Color textColor = Global.getSettings().getColor("standardTextColor");
		if (category == MessageType.HULL_50)
			textColor = Color.YELLOW;
		else if (category == MessageType.HULL_30)
			textColor = Misc.getNegativeHighlightColor();
		else if (category == MessageType.OVERLOAD)
			textColor = Color.CYAN;
		
		if (floater)
		{
			ShipAPI ship = engine.getFleetManager(FleetSide.PLAYER).getShipFor(member);
			if (ship == null) return false;
			Vector2f pos = ship.getLocation();
			Vector2f textPos = new Vector2f(pos.x, pos.y + ship.getCollisionRadius());
			engine.addFloatingText(textPos, message, 32, textColor, ship, 0, 0);
			stateData.lastFloatMessageTime = timeElapsed;
			stateData.lastFloatMessageType = category;
			return false;
		}
		
		engine.getCombatUI().addMessage(1, member, getShipNameColor(member), name, Misc.getTextColor(), ": ", textColor, message);
		if (line.sound != null)
			Global.getSoundPlayer().playUISound(line.sound, 1, 1);
		
		lastMessageTime = timeElapsed;
		//log.info("Time elapsed: " + lastMessageTime);
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
	 * Writes overload warning messages
	 * @param member
	 * @return true if long form message was printed, false otherwise
	 */
	protected boolean printOverloadMessage(FleetMemberAPI member)
	{
		if (!meetsPriorityThreshold(member, MessageType.OVERLOAD) || !hasLine(member, MessageType.OVERLOAD))
		{
			// short form
			String message = " " + StringHelper.getString("chatter_general", "hasOverloaded") + "!";
			String name = getShipName(member);
			engine.getCombatUI().addMessage(1, member, getShipNameColor(member), name, Color.CYAN, message);
			return false;
		}
		else
		{
			return printRandomMessage(member, MessageType.OVERLOAD);
		}
	}
	
	/**
	 * Writes missile depletion warning messages
	 * @param member
	 * @return true if long form message was printed, false otherwise
	 */
	protected boolean printOutOfMissilesMessage(FleetMemberAPI member)
	{
		if (!meetsPriorityThreshold(member, MessageType.OUT_OF_MISSILES) || !hasLine(member, MessageType.OUT_OF_MISSILES))
		{
			// short form
			String message = " " + StringHelper.getString("chatter_general", "outOfMissiles");
			String name = getShipName(member);
			engine.getCombatUI().addMessage(1, member, getShipNameColor(member), name, Global.getSettings().getColor("standardTextColor"), message);
			return false;
		}
		else
		{
			return printRandomMessage(member, MessageType.OUT_OF_MISSILES);
		}
	}
	
	protected void playIntroMessage(List<FleetMemberAPI> deployed)
	{
		if (timeElapsed > MAX_TIME_FOR_INTRO) {
			log.info("Too late for intro message: " + timeElapsed);
			introDone = true;
		}
		else {
			MessageType type = MessageType.START;
			if (engine.getContext().getPlayerGoal() == FleetGoal.ESCAPE)
				type = MessageType.RETREAT;
			
			FleetMemberAPI random = pickRandomMemberFromList(deployed, type);
			if (random != null)
			{
				//DeployedFleetMemberAPI randomD = fm.getDeployedFleetMember(fm.getShipFor(random));
				//engine.getCombatUI().addMessage(0, engine.getContext().getPlayerGoal().name());
				printRandomMessage(random, type);
				introDone = true;
			}
		}
	}
	
	/**
	 * Picks a random FleetMemberAPI to say a "team" message, such as the battle start or retreat messages.
	 * @param members The FleetMemberAPIs to choose a random speaker from (usually this is engine.getFleetManager(FleetSide.PLAYER).getDeployedCopy())
	 * @return
	 */
	protected FleetMemberAPI pickRandomMemberFromList(List<FleetMemberAPI> members, MessageType category)
	{
		boolean floater = isFloatingMessage(category);
		WeightedRandomPicker<FleetMemberAPI> picker = new WeightedRandomPicker<>();
		for (FleetMemberAPI member : members)
		{
			//if (member.isFighterWing()) continue;
			if (member.isFlagship() && !ChatterConfig.selfChatter) continue;
			CombatFleetManagerAPI fm = engine.getFleetManager(FleetSide.PLAYER);
			if (fm.getShipFor(member).getShipAI() == null && !ChatterConfig.selfChatter) continue;	// being player-piloted;
			if (fm.getShipFor(member).isHulk()) continue;
			float weight = 1;	//GeneralUtils.getHullSizePoints(member);
			if (member.getCaptain() != null && !member.getCaptain().isDefault()) weight *= 4;
			if (member.isFighterWing()) weight *= 0.5f;
			if (member.isAlly()) {
				if (ChatterConfig.allyChatter) weight *= 0.5f;
				else continue;
			}
			if (floater) {
				ShipAPI ship = fm.getShipFor(member);
				if (!Global.getCombatEngine().getViewport().isNearViewport(ship.getLocation(), ship.getCollisionRadius()))
					continue;
			}
			
			ShipStateData stateData = getShipStateData(member);
			String character = stateData.characterName;
			if (!CHARACTERS_MAP.containsKey(character))
				character = "default";

			weight *= CHARACTERS_MAP.get(character).talkativeness;
			
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
		
		//log.info("Advancing: " + amount);
		
		timeElapsed += amount;
		priorityThreshold -= amount * PRIORITY_DECAY;
		if (priorityThreshold < 0) priorityThreshold = 0;
		interval.advance(amount);
		if (!interval.intervalElapsed()) return;
		
		CombatFleetManagerAPI fm = engine.getFleetManager(FleetSide.PLAYER);
		List<FleetMemberAPI> deployed = fm.getDeployedCopy();
		List<FleetMemberAPI> dead = fm.getDisabledCopy();
		dead.addAll(fm.getDestroyedCopy());
		//if (deployed.isEmpty()) return;
		
		if (!introDone)
		{
			//log.info("Trying to play intro message");
			playIntroMessage(deployed);
		}
		
		boolean printed = false;
		
		// victory message
		if (!victory && engine.getFleetManager(FleetSide.ENEMY).getTaskManager(false).isInFullRetreat() 
				&& engine.getFleetManager(FleetSide.ENEMY).getGoal() != FleetGoal.ESCAPE)
		{
			FleetMemberAPI random = pickRandomMemberFromList(deployed, MessageType.VICTORY);
			if (random != null)
			{
				printRandomMessage(random, MessageType.VICTORY);
			}
			victory = true;
		}
		// full retreat message (same as start escape)
		else if (!victory && engine.getFleetManager(FleetSide.PLAYER).getTaskManager(false).isInFullRetreat())
		{
				FleetMemberAPI random = pickRandomMemberFromList(deployed, MessageType.RETREAT);
				if (random != null)
				{
					printRandomMessage(random, MessageType.RETREAT);
				}
			victory = true;
		}
		
		for (FleetMemberAPI member : deployed)
		{
			if (member.isFighterWing()) continue;
			if (member.isFlagship() && !ChatterConfig.selfChatter) continue;
			if (!ChatterConfig.allyChatter && member.isAlly()) continue;
			
			ShipStateData stateData = getShipStateData(member);
			if (stateData.dead) continue;
			ShipAPI ship = fm.getShipFor(member);
			if (ship.getShipAI() == null && !ChatterConfig.selfChatter) continue;	// being player-piloted;
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
		
		for (FleetMemberAPI member : dead)
		{
			if (member.isFlagship() && !ChatterConfig.selfChatter) continue;
			if (!ChatterConfig.allyChatter && member.isAlly()) continue;
			
			ShipStateData stateData = getShipStateData(member);
			if (!stateData.dead) {
				//log.info(member.getShipName() + " is dead!");
				stateData.dead = true;
				if (!member.isFighterWing())
					printRandomMessage(member, MessageType.DEATH);
			}
		}
		
		for (FleetMemberAPI member : deployed)
		{
			boolean isFighter = member.isFighterWing();
			//if (member.isFighterWing()) continue;
			if (member.isFlagship() && !ChatterConfig.selfChatter) continue;
			if (!ChatterConfig.allyChatter && member.isAlly()) continue;
			
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
			
			// check missiles
			if (!isFighter && stateData.canWriteOutOfMissiles)
			{
				boolean haveMissileAmmo = false;
				for (WeaponAPI wep : ship.getAllWeapons())
				{
					if (wep.usesAmmo() && wep.getType() == WeaponType.MISSILE)
					{
						if (wep.getSpec().getAmmoPerSecond() > 0 || wep.getAmmo() > 0)
						{
							haveMissileAmmo = true;
							break;
						}
					}
				}
				if (!haveMissileAmmo) {
					stateData.canWriteOutOfMissiles = false;
					printOutOfMissilesMessage(member);
				}
			}
			
			if (flags != null)
			{
				if ((canPrint || !printed))
				{
					if (!isFighter && flags.hasFlag(AIFlags.NEEDS_HELP) && !stateData.needHelp)
						printed = printRandomMessage(member, MessageType.NEED_HELP);
					else if (flags.hasFlag(AIFlags.PURSUING) && !stateData.pursuing)	// fighters can say this
						printed = printRandomMessage(member, MessageType.PURSUING);
					else if (!isFighter && flags.hasFlag(AIFlags.RUN_QUICKLY) && !stateData.running)
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
		log.info("Chatter plugin initialized");
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
		public boolean canWriteOutOfMissiles = true;
		public int missileOPs = 0;
		public int maxOPs = 0;
		public float hull = 0;
		public boolean overloaded = false;
		public float lastFloatMessageTime = -999;
		public MessageType lastFloatMessageType = MessageType.START;
	}
}
