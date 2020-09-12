package org.histidine.chatter.combat;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.CombatAssignmentType;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.CombatFleetManagerAPI.AssignmentInfo;
import com.fs.starfarer.api.combat.CombatTaskManagerAPI;
import com.fs.starfarer.api.combat.EveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.ShipHullSpecAPI.ShipTypeHints;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.combat.ShipwideAIFlags.AIFlags;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.combat.WeaponAPI.WeaponType;
import com.fs.starfarer.api.fleet.FleetGoal;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.ids.Personalities;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Random;
import java.util.Set;
import org.histidine.chatter.ChatterConfig;
import org.histidine.chatter.ChatterLine;
import org.histidine.chatter.ChatterLine.MessageType;
import org.histidine.chatter.ChatterDataManager;
import org.histidine.chatter.utils.StringHelper;
import org.lazywizard.lazylib.ui.FontException;
import org.lazywizard.lazylib.ui.LazyFont;
import org.lazywizard.lazylib.ui.LazyFont.DrawableString;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;


public class ChatterCombatPlugin implements EveryFrameCombatPlugin {

	public static Logger log = Global.getLogger(ChatterCombatPlugin.class);
	
	public static final String DATA_KEY = "chatter_plugin";
	public static final boolean DEBUG_MODE = false;
	
	public static final float PRIORITY_PER_MESSAGE = 20;
	public static final float PRIORITY_DECAY = 3;
	//public static final Map<MessageType, Float> MESSAGE_TYPE_PRIORITY = new HashMap<>();
	public static final Map<MessageType, Float> MESSAGE_TYPE_MAX_PRIORITY = new HashMap<>();
	public static final Set<MessageType> LOW_IMPORTANCE_CHATTER_TYPES = new HashSet<>();
	public static final Set<MessageType> FLOAT_CHATTER_TYPES = new HashSet<>();
	public static final float MAX_TIME_FOR_INTRO = 8;
	public static final float MESSAGE_INTERVAL = 3;			// global, for non-floater text
	public static final float MESSAGE_INTERVAL_IDLE = 5;	// global, for non-floater text
	public static final float MESSAGE_INTERVAL_FLOAT = 6;	// per ship
	public static final float ANTI_REPETITION_DIVISOR = 3;
	public static final float ANTI_REPETITION_DIVISOR_FLOATER = 5;
	
	// Message box parameters	
	public static final boolean DRAW_BOX = false;
	public static final int BOX_WIDTH = 400;
	public static final int BOX_NAME_WIDTH = 144;
	public static final int BOX_TEXT_WIDTH = BOX_WIDTH - BOX_NAME_WIDTH - 8;
	public static final int BOX_OFFSET_X = 16;
	public static final int BOX_OFFSET_Y = 160;
	public static final int BOX_HEIGHT = 240;
	public static final Color BOX_COLOR = Color.CYAN;
	
	public static final Vector2f NAME_POS = new Vector2f(8, -8);
	public static final Vector2f TEXT_POS = new Vector2f(BOX_NAME_WIDTH + 16, -8);
	public static LazyFont font;
	protected static boolean fontLoaded = false;
	
	protected CombatEngineAPI engine;
	protected IntervalUtil interval = new IntervalUtil(0.4f, 0.5f);
	protected Map<FleetMemberAPI, ShipStateData> states = new HashMap<>();
	protected Set<FleetMemberAPI> ignore = new HashSet<>();
	protected List<BoxMessage> boxMessages = new LinkedList<>();
	
	protected FleetMemberAPI lastTalker = null;
	protected float timeElapsed = 0;
	protected float lastMessageTime = -9999;
	protected float messageBoxLimiter = 0;
	protected float priorityThreshold = 0;	// if this is high, low priority messages won't be displayed
	protected boolean introDone = false;
	protected boolean victory = false;
	protected int victoryIncrement = 0;
	
	protected boolean wantDebugChat = DEBUG_MODE;
	
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
		//MESSAGE_TYPE_MAX_PRIORITY.put(MessageType.HULL_90, 20f);
		//MESSAGE_TYPE_MAX_PRIORITY.put(MessageType.HULL_50, 40f);
		//MESSAGE_TYPE_MAX_PRIORITY.put(MessageType.HULL_30, 60f);
		MESSAGE_TYPE_MAX_PRIORITY.put(MessageType.DEATH, 15f);
		
		LOW_IMPORTANCE_CHATTER_TYPES.add(MessageType.START);
		LOW_IMPORTANCE_CHATTER_TYPES.add(MessageType.START_BOSS);
		LOW_IMPORTANCE_CHATTER_TYPES.add(MessageType.RETREAT);
		LOW_IMPORTANCE_CHATTER_TYPES.add(MessageType.PURSUING);
		LOW_IMPORTANCE_CHATTER_TYPES.add(MessageType.RUNNING);
		LOW_IMPORTANCE_CHATTER_TYPES.add(MessageType.NEED_HELP);
		LOW_IMPORTANCE_CHATTER_TYPES.add(MessageType.ENGAGED);
		LOW_IMPORTANCE_CHATTER_TYPES.add(MessageType.VICTORY);
		LOW_IMPORTANCE_CHATTER_TYPES.add(MessageType.DEATH);
		
		FLOAT_CHATTER_TYPES.add(MessageType.ENGAGED);
		FLOAT_CHATTER_TYPES.add(MessageType.PURSUING);
		FLOAT_CHATTER_TYPES.add(MessageType.NEED_HELP);
		FLOAT_CHATTER_TYPES.add(MessageType.RUNNING);
	}
	
	protected static void loadFont() {
		fontLoaded = true;
		try
		{
			font = LazyFont.loadFont(Global.getSettings().getString("chatter_boxFont"));
		}
		catch (FontException ex)
		{
			throw new RuntimeException("Failed to load font", ex);
		}
	}
	
	public static ChatterCombatPlugin getInstance() {
		return (ChatterCombatPlugin)Global.getCombatEngine().getCustomData().get(DATA_KEY);
	}
	
	public Map<FleetMemberAPI, ShipStateData> getShipStates() {
		return states;
	}
	
	protected float getRandomForStringSeed(String seed)
	{
		if (seed == null || seed.isEmpty()) return (float)Math.random();
		Random generator = new Random(seed.hashCode());
		return generator.nextFloat();
	}
	
	protected String getCharacterForFleetMember(FleetMemberAPI member)
	{
		ShipAPI ship = getShipForMember(member);
		
		PersonAPI captain = member.getCaptain();
		if ((captain != null && !captain.isDefault()) || engine.isMission() || member.isFighterWing())
		{
			return ChatterDataManager.getCharacterForOfficer(captain, member, engine, true);
		}
		
		String name = "default";
		String personality = "";
		if (captain != null)
			personality = captain.getPersonalityAPI().getId();
		
		if (captain != null && captain.isDefault()) {
			//log.info("Default captain has personality " + personality);
		}
		
		boolean timid = member.getHullSpec().getHints().contains(ShipTypeHints.CIVILIAN) || personality.equals(Personalities.TIMID);
		boolean aggressive = personality.equals(Personalities.AGGRESSIVE) || personality.equals(Personalities.RECKLESS);
		
		//else if (xpLevel == CrewXPLevel.ELITE) name = "default_professional";
		int hash = member.getShipName() != null ? member.getShipName().hashCode() : (int)(Math.random() * 65536);
		Random rand = new Random(hash);
		if (timid) 
			name = "default_timid";
		else if (aggressive)
			name = "default_aggressive";
		else if (rand.nextFloat() > 0.5) 
			name = "default_professional";
		if (rand.nextFloat() > 0.5) name += "2";
		
		return name;
	}
	
	// Called from SetChatterChar console command
	public void setCharacterForOfficer(PersonAPI officer, String charId) {
		for (Map.Entry<FleetMemberAPI, ShipStateData> tmp : states.entrySet()) {
			FleetMemberAPI member = tmp.getKey();
			if (member.getCaptain() == officer) {
				tmp.getValue().characterId = charId;
				return;
			}
		}
	}
	
	protected ShipAPI getShipForMember(FleetMemberAPI member) 
	{
		ShipAPI ship = engine.getFleetManager(FleetSide.PLAYER).getShipFor(member);
		if (ship == null) ship = engine.getFleetManager(FleetSide.ENEMY).getShipFor(member);
		return ship;
	}
	
	protected boolean isIgnored(FleetMemberAPI member)
	{
		if (ignore.contains(member)) return true;
		
		if (!ChatterConfig.allyChatter && member.isAlly()) {
			ignore.add(member);
			return true;
		}
		if (ChatterDataManager.EXCLUDED_HULLS.contains(member.getHullId()))
		{
			ignore.add(member);
			return true;
		}
		if (member.isFighterWing() && member.getHullSpec().getMaxCrew() <= 0)
		{
			ignore.add(member);
			return true;
		}
		// non-fighter fleet member that's fighter-sized
		if (!member.isFighterWing() && member.getHullSpec().getHullSize() == HullSize.FIGHTER) {
			ignore.add(member);
			return true;
		}
		
		ShipAPI ship = getShipForMember(member);
		if (ship != null)
		{
			// ignore modules
			if (ship.getParentStation() != null)
			{
				ignore.add(member);
				return true;
			}
			
			boolean isEnemy = ship.getOwner() == 1;
			if (isEnemy)
			{
				String faction = ChatterDataManager.getFactionFromShip(member);
				if (ChatterConfig.noEnemyChatterFactions.contains(faction)
						|| !ChatterConfig.enemyChatter) {
					//log.info("Enemy ship " + member.getShipName() + " being ignored: " + faction);
					ignore.add(member);
					return true;
				}
			}
		}
		
		return false;
	}
	
	/**
	 * Makes a ShipStateData to track the condition of the {@code member} for chatter
	 * @param member
	 * @return
	 */
	protected ShipStateData makeShipStateEntry(FleetMemberAPI member)
	{
		if (isIgnored(member)) return null;
		
		ShipStateData data = new ShipStateData();
		ShipAPI ship = getShipForMember(member);
		if (ship != null) {
			//log.info("Creating data for ship " + member.getShipName() + ", side " + ship.getOwner());
			data.ship = ship;
			data.hull = ship.getHullLevel();
			//data.maxOPs = ship.getHullSpec().
			for (WeaponAPI wep : ship.getAllWeapons())
			{
				float op = wep.getSpec().getOrdnancePointCost(null);
				data.maxOPs += op;
				if (wep.usesAmmo() && wep.getType() == WeaponType.MISSILE)
					data.missileOPs += op;
			}
			if (data.maxOPs <= 0 || data.missileOPs < data.maxOPs * ChatterConfig.minMissileOPFractionForChatter)
				data.canWriteOutOfMissiles = false;	// don't bother writing out of missiles message
			
			data.isEnemy = ship.getOwner() == 1;
			data.isPlayer = ship == engine.getPlayerShip();
		}
		data.characterId = getCharacterForFleetMember(member);
		
		
		states.put(member, data);
		return data;
	}
	
	public String getShipName(FleetMemberAPI member, boolean includeClass) {
		return getShipName(member, includeClass, false);
	}
	
	protected String getShipName(FleetMemberAPI member, boolean includeClass, boolean useOfficerName)
	{
		String shipName = "";
		if (member.isFighterWing()) {
			shipName = member.getHullSpec().getHullName();
			if (includeClass) shipName = StringHelper.getStringAndSubstituteToken(
					"chatter_general", "wing", "$wing", shipName);
		}
		else {
			if (useOfficerName && !member.getCaptain().isDefault()) {
				return member.getCaptain().getNameString();
			}
			
			shipName = member.getShipName();
			if (includeClass) shipName += " (" + StringHelper.getStringAndSubstituteToken(
					"chatter_general", "class", "$class", member.getHullSpec().getHullName())
					+ ")";
		}
			
		return shipName;
	}
	
	protected float getMessageMaxPriority(MessageType category)
	{
		if (!MESSAGE_TYPE_MAX_PRIORITY.containsKey(category))
			return 999;
		return MESSAGE_TYPE_MAX_PRIORITY.get(category);
	}
	
	protected boolean hasLine(FleetMemberAPI member, MessageType category, boolean floater, boolean useAntiRepetition)
	{
		if (isIgnored(member)) return false;
		
		ShipStateData stateData = getShipStateData(member);
		String character = stateData.characterId;
		if (ChatterDataManager.getCharacterData(character) == null)
			character = "default";
		
		List<ChatterLine> lines = ChatterDataManager.getCharacterData(character).lines.get(category);
		if (lines == null || lines.isEmpty())
			return false;
		// hax to reduce repetition if only 1-2 lines are defined
		if (useAntiRepetition)
		{
			int count = lines.size();
			float divisor = floater ? ANTI_REPETITION_DIVISOR_FLOATER : ANTI_REPETITION_DIVISOR;
			if (Math.random() > (count / divisor))
				return false;
		}
		return true;
	}
	
	protected boolean hasLine(FleetMemberAPI member, MessageType category, boolean floater)
	{
		return hasLine(member, category, floater, true);
	}
	
	protected boolean haveBoss()
	{
		List<FleetMemberAPI> enemies = engine.getFleetManager(FleetSide.ENEMY).getDeployedCopy();
		enemies.addAll(engine.getFleetManager(FleetSide.ENEMY).getReservesCopy());
		for (FleetMemberAPI member : enemies)
		{
			if (member.getVariant().hasHullMod("vastbulk") && 
					(member.getHullSpec().getHullSize() == HullSize.CAPITAL_SHIP))
			{
				return true;
			}
			if (ChatterDataManager.BOSS_SHIPS.contains(member.getHullId()))
			{
				return true;
			}
		}
		return false;
	}
	
	protected ShipStateData getShipStateData(FleetMemberAPI member)
	{
		if (!states.containsKey(member))
			makeShipStateEntry(member);
		return states.get(member);
	}
	
	protected Color getShipNameColor(FleetMemberAPI member) {
		if (member.isAlly()) return Misc.getHighlightColor();
		if (getShipStateData(member).isEnemy) return Global.getSettings().getColor("textEnemyColor");
		return Global.getSettings().getColor("textFriendColor");
	}
	
	protected Color getShipNameColor(FleetMemberAPI member, float alpha)
	{
		Color color = getShipNameColor(member);
		if (alpha == 1) return color;
		color = new Color(color.getRed()/255f, color.getGreen()/255f, color.getBlue()/255f, alpha);
		return color;
	}
	
	protected boolean isFloatingMessage(MessageType category)
	{
		boolean floater = FLOAT_CHATTER_TYPES.contains(category);
		if (!ChatterConfig.lowImportanceChatter)
			floater = floater || LOW_IMPORTANCE_CHATTER_TYPES.contains(category);
		return floater;
	}
	
	protected void addMessageBoxMessage(ShipStateData stateData, FleetMemberAPI member, 
			String message, MessageType category) 
	{
		if (!ChatterConfig.chatterBox) return;
		if (messageBoxLimiter >= 7)
			return;
		if (ChatterConfig.chatterBoxOfficerMode && (member.getCaptain() == null || member.getCaptain().isDefault()))
			return;
				
		float requiredInterval = MESSAGE_INTERVAL_FLOAT * 2;
		if (stateData.lastFloatMessageType != category) requiredInterval *= 0.5f;
		if (category == MessageType.ENGAGED) requiredInterval *= 2.5f;
		if (timeElapsed < stateData.lastFloatMessageTime + requiredInterval)
			return;
		
		float charge = 3;
		if (member.isAlly() || stateData.isEnemy) charge = 4.5f;
		if (messageBoxLimiter + charge > 10)
			return;
		
		boxMessages.add(new BoxMessage(member, message));
		messageBoxLimiter += charge;
	}
	
	protected boolean printRandomMessage(FleetMemberAPI member, MessageType category) {
		return printRandomMessage(member, category, null);
	}
	
	/**
	 * Writes a random message of the appropriate category, based on the {@code member}'s assigned character
	 * @param member
	 * @param category
	 * @param genericFallback
	 * @return true if a non-fallback message was printed to message field
	 */
	protected boolean printRandomMessage(FleetMemberAPI member, MessageType category, String genericFallback)
	{
		if (isIgnored(member)) return false;
		
		ShipStateData stateData = getShipStateData(member);
		
		boolean enemy = stateData.isEnemy;
		boolean floater = enemy || isFloatingMessage(category);
		
		if (!floater && !meetsPriorityThreshold(member, category))
		{
			//log.info("Threshold too high for message " + category.name());
			return false;
		}
		
		float msgInterval = MESSAGE_INTERVAL;
		if (LOW_IMPORTANCE_CHATTER_TYPES.contains(category)) msgInterval = MESSAGE_INTERVAL_IDLE;
		if (lastTalker == member) msgInterval *= 1.5f;
		if (!floater && timeElapsed < lastMessageTime + msgInterval)
		{
			//log.info("Too soon for next message: " + timeElapsed + " / " + lastMessageTime);
			return false;
		}
		
		if (floater)
		{
			float requiredInterval = MESSAGE_INTERVAL_FLOAT;
			if (stateData.lastFloatMessageType != category) requiredInterval *= 0.5f;
			if (category == MessageType.ENGAGED) requiredInterval *= 2.5f;
			if (timeElapsed < stateData.lastFloatMessageTime + requiredInterval)
				return false;
		}
		
		String character = stateData.characterId;
		if (ChatterDataManager.getCharacterData(character) == null)
			character = "default";
		
		boolean fallback = false;
		
		// If this character doesn't have a line for that category, or failed the anti-repetition check...
		if (!hasLine(member, category, floater)) {
			// Don't print anything if enemy, or no generic fallback is specified,
			// or this doesn't meet our priority threshold for messages
			if (enemy) return false;
			if (genericFallback == null) return false;
			if (!meetsPriorityThreshold(member, category)) return false;
			
			// Fine, use the fallback
			fallback = true;
		}
		
		String message;
		ChatterLine line = null;
		if (!fallback) {
			List<ChatterLine> lines = ChatterDataManager.getCharacterData(character).lines.get(category);		
			line = (ChatterLine)GeneralUtils.getRandomListElement(lines);
			message = "\"" + line.text + "\"";
		} else {
			message = genericFallback;
		}

		String name = getShipName(member, true);
		Color textColor;
		switch (category) {
			case HULL_50:
				textColor = Color.YELLOW;
				break;
			case HULL_30:
				textColor = Misc.getNegativeHighlightColor();
				break;
			case OVERLOAD:
				textColor = Color.CYAN;
				break;
			default:
				textColor = Global.getSettings().getColor("standardTextColor");
				break;
		}
		
		if (line != null && line.sound != null)
			Global.getSoundPlayer().playUISound(line.sound, 1, 1);
		
		if (floater)
		{
			ShipAPI ship = getShipStateData(member).ship;
			if (ship == null) {
				return false;
			}
			Vector2f pos = ship.getLocation();
			Vector2f textPos = new Vector2f(pos.x, pos.y + ship.getCollisionRadius());
			engine.addFloatingText(textPos, message, Global.getSettings().getInt("chatter_floaterFontSize"), textColor, ship, 0, 0);
			
			if (!rollFilterEnemyAndDefault(member)) {
				addMessageBoxMessage(stateData, member, message, category);
			}
				
			stateData.lastFloatMessageTime = timeElapsed;
			stateData.lastFloatMessageType = category;
			
			return false;
		}
		
		if (fallback) {
			engine.getCombatUI().addMessage(1, member, getShipNameColor(member), name, textColor, message);
		}
		else {
			engine.getCombatUI().addMessage(1, member, getShipNameColor(member), name, Misc.getTextColor(), ": ", textColor, message);
		}
		
		if (!fallback) {
			lastMessageTime = timeElapsed;
			//log.info("Time elapsed: " + lastMessageTime);
			priorityThreshold += PRIORITY_PER_MESSAGE;
			lastTalker = member;
		}
		
		return !fallback;
	}
	
	protected boolean meetsPriorityThreshold(FleetMemberAPI member, MessageType category)
	{
		float threshold = priorityThreshold;
		if (lastTalker == member) threshold *= 2f;
		if (member.isAlly() || getShipStateData(member).isEnemy) threshold *= 2f;
		
		return (getMessageMaxPriority(category) >= threshold);
	}
	
	protected boolean rollFilterEnemyAndDefault(FleetMemberAPI member) 
	{
		PersonAPI captain = member.getCaptain();
		boolean enemy = getShipStateData(member).isEnemy;
		
		// always allow player ships
		if (!enemy && !member.isAlly())
			return false;
		
		return rollFilterEnemyAndDefault(enemy, 
				captain == null || captain.isDefault());
	}
	
	/**
	 * Used to reduce the rate at which enemies and default officers play messages.
	 * @param isEnemy
	 * @param isDefault
	 * @return False if we should play the message, true otherwise.
	 */
	protected boolean rollFilterEnemyAndDefault(boolean isEnemy, boolean isDefault) 
	{
		if (isEnemy && Math.random() < 0.5f) {
			return true;
		}
		if (isDefault && Math.random() < 0.5f) {
			return true;
		}
		return false;
	}
	
	/**
	 * Writes hull damage warning messages, and also plays a sound.
	 * @param member
	 * @param category Can be HULL_30, HULL_50 or HULL_90
	 * @param amount Number to write in short form message (30, 50 or 90)
	 * @return true if long form message was printed, false otherwise
	 */
	protected boolean printHullMessage(FleetMemberAPI member, MessageType category, int amount)
	{
		boolean enemy = getShipStateData(member).isEnemy;
		if (rollFilterEnemyAndDefault(member))
			return false;
		
		if (!enemy) {
			if (category == MessageType.HULL_50)
				Global.getSoundPlayer().playUISound("cr_allied_warning", 1, 1);
			else if (category == MessageType.HULL_30)
				Global.getSoundPlayer().playUISound("cr_allied_malfunction", 1, 1);
		}
		
		// short form message as fallback
		String message1 = " " + StringHelper.getString("chatter_general", "isAt") + " ";
		String message2 = amount + "% " + StringHelper.getString("chatter_general", "hull") + "!";
		
		return printRandomMessage(member, category, message1 + " " + message2);
	}
	
	/**
	 * Writes overload warning messages.
	 * @param member
	 * @return true if long form message was printed, false otherwise
	 */
	protected boolean printOverloadMessage(FleetMemberAPI member)
	{
		if (rollFilterEnemyAndDefault(member))
			return false;
		
		String fallback = " " + StringHelper.getString("chatter_general", "hasOverloaded") + "!";
		return printRandomMessage(member, MessageType.OVERLOAD, fallback);
	}
	
	/**
	 * Writes missile depletion warning messages.
	 * @param member
	 * @return true if long form message was printed, false otherwise
	 */
	protected boolean printOutOfMissilesMessage(FleetMemberAPI member)
	{
		if (rollFilterEnemyAndDefault(member))
			return false;
		
		String fallback = " " + StringHelper.getString("chatter_general", "outOfMissiles");
		return printRandomMessage(member, MessageType.OUT_OF_MISSILES, fallback);
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
			else if (haveBoss())
				type = MessageType.START_BOSS;
			
			FleetMemberAPI random = pickRandomMemberFromList(deployed, type);
			if (random == null && type == MessageType.START_BOSS)
			{
				type = MessageType.START;
				random = pickRandomMemberFromList(deployed, type);
			}
			
			if (random != null)
			{
				printRandomMessage(random, type);
				introDone = true;
			}
		}
	}
	
	/**
	 * Picks a random FleetMemberAPI to say a "team" message, such as the battle start or retreat messages.
	 * @param members The FleetMemberAPIs to choose a random speaker from (usually this is engine.getFleetManager(FleetSide.PLAYER).getDeployedCopy())
	 * @param category
	 * @return
	 */
	protected FleetMemberAPI pickRandomMemberFromList(List<FleetMemberAPI> members, MessageType category)
	{		
		boolean floater = isFloatingMessage(category);
		WeightedRandomPicker<FleetMemberAPI> picker = new WeightedRandomPicker<>();
		for (FleetMemberAPI member : members)
		{
			//if (member.isFighterWing()) continue;
			if (isIgnored(member)) continue;
			ShipAPI ship = getShipForMember(member);
			if (ship == null) continue;
			
			if (ship == engine.getPlayerShip() && !ChatterConfig.selfChatter) continue;	// being player-piloted;
			if (ship.isHulk()) continue;
			float weight = 1;	//GeneralUtils.getHullSizePoints(member);
			if (member.getCaptain() != null && !member.getCaptain().isDefault()) weight *= 4;
			if (member.isFighterWing()) weight *= 0.5f;
			if (member.isAlly()) {
				if (ChatterConfig.allyChatter) weight *= 0.5f;
				else continue;
			}
			if (getShipStateData(member).isEnemy) {
				weight *= 0.5f;
			}
			
			if (!hasLine(member, category, false))
				continue;
			if (floater) {
				if (!Global.getCombatEngine().getViewport().isNearViewport(ship.getLocation(), ship.getCollisionRadius()))
					continue;
			}
			
			ShipStateData stateData = getShipStateData(member);
			String character = stateData.characterId;
			if (ChatterDataManager.getCharacterData(character) == null)
				character = "default";
			
			weight *= ChatterDataManager.getCharacterData(character).talkativeness;
			picker.add(member, weight);
		}
		if (picker.isEmpty()) return null;
		return picker.pick();
	}
	
	protected void timeoutBoxMessages(float time) {
		messageBoxLimiter -= time;
		if (messageBoxLimiter < 0) messageBoxLimiter = 0;
		
		List<BoxMessage> toRemove = new ArrayList<>();
		for (BoxMessage msg : boxMessages) {
			msg.ttl -= time;
			if (msg.ttl <= 0) {
				toRemove.add(msg);
			}
		}
		boxMessages.removeAll(toRemove);
	}
	
	protected void addDebugChat() {
		FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, "paragon_Elite");
		member.setShipName("ISS Talky Man");
		boxMessages.add(new BoxMessage(member, "dorime"));
		boxMessages.add(new BoxMessage(member, "the quick brown fox jumps over the lazy dog"));
		boxMessages.add(new BoxMessage(member, "ameno"));
		boxMessages.add(new BoxMessage(member, "This is a long string. It will fill most of the box. This is a haiku."));
	}
	
	protected List<FleetMemberAPI> getDeployed(boolean includeEnemy) {
		List<FleetMemberAPI> list = engine.getFleetManager(FleetSide.PLAYER).getDeployedCopy();
		if (includeEnemy) list.addAll(engine.getFleetManager(FleetSide.ENEMY).getDeployedCopy());
		return list;
	}
	
	protected List<FleetMemberAPI> getDead() {
		List<FleetMemberAPI> list = engine.getFleetManager(FleetSide.PLAYER).getDisabledCopy();
		list.addAll(engine.getFleetManager(FleetSide.PLAYER).getDestroyedCopy());
		if (ChatterConfig.enemyChatter) {
			list.addAll(engine.getFleetManager(FleetSide.ENEMY).getDisabledCopy());
			list.addAll(engine.getFleetManager(FleetSide.ENEMY).getDestroyedCopy());
		}
		return list;
	}
	
	@Override
	public void advance(float amount, List<InputEventAPI> events) {
		if (engine == null) return;
		if (Global.getCurrentState() == GameState.TITLE) return;
		
		if (wantDebugChat) {
			addDebugChat();
			wantDebugChat = false;
		}
		if (!DEBUG_MODE && engine.isSimulation()) return;
		
		drawMessages();
		
		if (engine.isPaused()) return;
		
		//log.info("Advancing: " + amount);
		timeoutBoxMessages(amount);
		
		timeElapsed += amount;
		priorityThreshold -= amount * PRIORITY_DECAY;
		if (priorityThreshold < 0) priorityThreshold = 0;
		interval.advance(amount);
		if (!interval.intervalElapsed()) return;
		
		List<FleetMemberAPI> deployed = getDeployed(ChatterConfig.enemyChatter);
		List<FleetMemberAPI> deployedFriendly = getDeployed(false);
		List<FleetMemberAPI> dead = getDead();
		//if (deployed.isEmpty()) return;
		
		if (!introDone)
		{
			//log.info("Trying to play intro message");
			playIntroMessage(engine.getFleetManager(FleetSide.PLAYER).getDeployedCopy());
		}
		
		boolean printed = false;
		CombatTaskManagerAPI playerManager = engine.getFleetManager(FleetSide.PLAYER).getTaskManager(false);
		
		// victory message
		if (!victory)
		{
			CombatTaskManagerAPI enemyManager = engine.getFleetManager(FleetSide.ENEMY).getTaskManager(false);
			if (enemyManager.isInFullRetreat() && !enemyManager.isPreventFullRetreat() 
					&& engine.getFleetManager(FleetSide.ENEMY).getGoal() != FleetGoal.ESCAPE)
			{
				// try to fix premature victory message (still happens)
				victoryIncrement++;
				if (victoryIncrement >= 3)
				{
					FleetMemberAPI random = pickRandomMemberFromList(deployedFriendly, MessageType.VICTORY);
					if (random != null)
					{
						printRandomMessage(random, MessageType.VICTORY);
					}
					victory = true;
				}
			}
			// full retreat message (same as start escape)
			else if (playerManager.isInFullRetreat() && !playerManager.isPreventFullRetreat())
			{
				if (engine.getContext().getPlayerGoal() != FleetGoal.ESCAPE)
				{
					FleetMemberAPI random = pickRandomMemberFromList(deployedFriendly, MessageType.RETREAT);
					if (random != null)
					{
						printRandomMessage(random, MessageType.RETREAT);
					}
				}
				victory = true;
			}
			else
				victoryIncrement = 0;
		}
		for (FleetMemberAPI member : deployed)
		{
			if (isIgnored(member)) continue;
			if (member.isFighterWing()) continue;
			
			ShipStateData stateData = getShipStateData(member);
			if (stateData.dead) continue;
			
			ShipAPI ship = getShipForMember(member);
			if (ship == null || !ship.isAlive()) continue;
			else stateData.ship = ship;
			
			float hull = ship.getHullLevel();
			float oldHull = stateData.hull;
			
			stateData.hull = hull;
			stateData.isEnemy = ship.getOwner() == 1;
			
			if (stateData.isPlayer && !ChatterConfig.selfChatter) // being player-piloted
			{
				continue;
			}
			
			if (hull <= 0.3 && oldHull > 0.3)
				printed = printHullMessage(member, MessageType.HULL_30, 30);
			else if (hull <= 0.5 && oldHull > 0.5)
				printed = printHullMessage(member, MessageType.HULL_50, 50);
			else if (hull <= 0.9 && oldHull > 0.9)
				printed = printHullMessage(member, MessageType.HULL_90, 90);
		}
		
		boolean canPrint = printed;
		//if (printed) return;
		
		for (FleetMemberAPI member : dead)
		{
			if (isIgnored(member)) continue;
			ShipStateData stateData = getShipStateData(member);
			
			if (!stateData.dead) {
				//log.info(member.getShipName() + " is dead!");
				if (stateData.isPlayer && !ChatterConfig.selfChatter) // being player-piloted
				{
					continue;
				}
				stateData.dead = true;
				printRandomMessage(member, MessageType.DEATH);
			}
		}
		
		for (FleetMemberAPI member : deployed)
		{
			boolean isFighter = member.isFighterWing();
			//if (member.isFighterWing()) continue;
			if (isIgnored(member)) continue;
			
			ShipStateData stateData = getShipStateData(member);
			if (stateData.dead) continue;
			if (stateData.isPlayer && !ChatterConfig.selfChatter) continue;
			
			ShipAPI ship = getShipForMember(member);
			if (ship == null) continue;
			
			boolean player = ship == engine.getPlayerShip();
			if (player != stateData.isPlayer) {
				stateData.isPlayer = player;
				stateData.characterId = getCharacterForFleetMember(member);
			}
			
			// Second check for death state? Not sure if needed
			if (!ship.isAlive() && !stateData.dead) {
				if (!printed && !isFighter)
					printed = printRandomMessage(member, MessageType.DEATH);
				stateData.dead = true;
				continue;
			}
			
			boolean overloaded = false;
			if (ship.getFluxTracker() != null) 
			{
				overloaded = ship.getFluxTracker().getOverloadTimeRemaining() > 2;
			}
			
			if (!isFighter && overloaded && !stateData.overloaded) {
				printed = printOverloadMessage(member);
			}
				
			
			stateData.overloaded = overloaded;
			
			// check missiles
			if (!isFighter && stateData.canWriteOutOfMissiles)
			{
				boolean haveMissileAmmo = false;
				// check for missile autoforge
				ShipSystemAPI system = ship.getSystem();
				if (system != null && system.getId().equals("forgevats") && !system.isOutOfAmmo())
				{
					haveMissileAmmo = true;
				}
				else
				{
					// check all missile weapons
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
				}
				if (!haveMissileAmmo) {
					stateData.canWriteOutOfMissiles = false;
					if (!stateData.isEnemy || Math.random() < 0.5f)
						printOutOfMissilesMessage(member);
				}
			}
			
			if (ship.getShipAI() != null)
			{
				ShipwideAIFlags flags = ship.getAIFlags();
				if (flags != null)
				{
					boolean engaged = flags.hasFlag(AIFlags.HAS_INCOMING_DAMAGE) && !flags.hasFlag(AIFlags.BACK_OFF)
							&& !flags.hasFlag(AIFlags.PURSUING);
					boolean pursuing = flags.hasFlag(AIFlags.PURSUING) && !engaged;
					boolean running = false;//!engaged && !pursuing && 
							//(flags.hasFlag(AIFlags.BACKING_OFF) || flags.hasFlag(AIFlags.BACKING_OFF));
					AssignmentInfo assign = playerManager.getAssignmentFor(ship);
					if (assign != null && assign.getType() == CombatAssignmentType.RETREAT)
						running = true;
					
					if ((canPrint || !printed) && (!stateData.isEnemy || Math.random() < 0.5f))
					{
						if (!isFighter && running && !stateData.running)
							printed = printRandomMessage(member, MessageType.RUNNING);
						else if (!isFighter && flags.hasFlag(AIFlags.NEEDS_HELP) && !stateData.needHelp)
							printed = printRandomMessage(member, MessageType.NEED_HELP);
						else if (!isFighter && engaged && !stateData.engaged)
							printed = printRandomMessage(member, MessageType.ENGAGED);
						else if (pursuing && !stateData.pursuing)	// fighters can say this
							printed = printRandomMessage(member, MessageType.PURSUING);
					}
					stateData.pursuing = pursuing;
					stateData.running = running;
					stateData.needHelp = flags.hasFlag(AIFlags.NEEDS_HELP);
					stateData.engaged = engaged;
				}
				//log.info(member.getShipName() + " pursuing target? " + flags.hasFlag(AIFlags.PURSUING));
				//log.info(member.getShipName() + " running? " + flags.hasFlag(AIFlags.RUN_QUICKLY));
				//log.info(member.getShipName() + " needs help? " + flags.hasFlag(AIFlags.NEEDS_HELP));
			}
		}
	}
	
	/**
	 * GL11 to start, when you want render text of Lazyfont.
	 */
	public static void openGL11ForText()
	{
		GL11.glPushAttrib(GL11.GL_ENABLE_BIT);
		GL11.glMatrixMode(GL11.GL_PROJECTION);
		GL11.glPushMatrix();
		GL11.glViewport(0, 0, Display.getWidth(), Display.getHeight());
		GL11.glOrtho(0.0, Display.getWidth(), 0.0, Display.getHeight(), -1.0, 1.0);
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
	}

	/**
	 * GL11 to close, when you want render text of Lazyfont.
	 */
	public static void closeGL11ForText()
	{
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glDisable(GL11.GL_BLEND);
		GL11.glPopMatrix();
		GL11.glPopAttrib();
	}
	
	/**
	 * Draws a message line in the text box.
	 * @param message
	 * @param remainingHeight Available height remaining in the box. If the new message's
	 * height exceeds this amount, do not draw.
	 * @return Height of the message.
	 */
	public float drawMessage(BoxMessage message, float remainingHeight)
	{
		int fontSize = Global.getSettings().getInt("chatter_boxFontSize");
		
		// prepare ship name text
		float alpha = engine.isUIShowingDialog() ? 0.5f : 1;
		Color color = getShipNameColor(message.ship, alpha);
		String name = getShipName(message.ship, false, ChatterConfig.chatterBoxOfficerMode);
		if (name == null || name.isEmpty()) name = "<unknown>";	// safety
		DrawableString str = font.createText(name,	color, fontSize, BOX_NAME_WIDTH);
		
		// prepare message text
		color = Misc.getTextColor();
		color = new Color(color.getRed()/255f, color.getGreen()/255f, color.getBlue()/255f, alpha);
		DrawableString str2 = font.createText(message.text, 
				color, fontSize, BOX_TEXT_WIDTH);
		
		float height = Math.max(str.getHeight(), str2.getHeight());
		if (height < 40) height = 40;
		if (height > remainingHeight) {
			return 99999;
		}
		
		// draw portrait;
		String sprite = null;
		if (!message.ship.getCaptain().isDefault()) {
			sprite = message.ship.getCaptain().getPortraitSprite();
		}
		else if (DEBUG_MODE) {
			sprite = "graphics/portraits/portrait_ai2.png";
		}
		if (sprite != null) {
			// pushing/popping attrib is needed to keep sprite from messing with text opacity
			GL11.glPushAttrib(GL11.GL_ENABLE_BIT);
			GL11.glPushMatrix();
			SpriteAPI sprite2 = Global.getSettings().getSprite(sprite);
			float sizeMult = 32/sprite2.getWidth();
			GL11.glScalef(sizeMult, sizeMult, 1);
			sprite2.setAlphaMult(alpha);
			sprite2.render(-128, -160);
			GL11.glPopMatrix();
			GL11.glPopAttrib();
		}
		
		str.draw(NAME_POS);
		str2.draw(TEXT_POS);
		return height;
	}
	
	public void drawMessages() 
	{
		if (!ChatterConfig.chatterBox) return;
		if (engine == null || !engine.isUIShowingHUD() || engine.getCombatUI().isShowingCommandUI())
		{
			return;
		}
		
		if (!fontLoaded || DEBUG_MODE) loadFont();
		
		openGL11ForText();
		
		GL11.glTranslatef(Display.getWidth() - BOX_WIDTH - BOX_OFFSET_X, 
				Display.getHeight() - BOX_OFFSET_Y, 0);
		
		float remainingHeight = BOX_HEIGHT - 8 * 2;
		for (int i=boxMessages.size() - 1; i>=0; i--) 
		{
			BoxMessage msg = boxMessages.get(i);
			float height = drawMessage(msg, remainingHeight);
			GL11.glTranslatef(0, -height - 4, 0);
			remainingHeight -= height + 4;
			if (remainingHeight < 14)
				break;
		}
		closeGL11ForText();
	}
	
	public void drawBox() {
		if (!DRAW_BOX || !ChatterConfig.chatterBox) return;
		
		float alpha = engine.getCombatUI().getCommandUIOpacity();
		
		GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
		GL11.glMatrixMode(GL11.GL_PROJECTION);
		GL11.glPushMatrix();
		GL11.glLoadIdentity();
		
		GL11.glViewport(0, 0, Display.getWidth(), Display.getHeight());
		GL11.glOrtho(0.0, Display.getWidth(), 0.0, Display.getHeight(), -1.0, 1.0);
		GL11.glLineWidth(1);
		GL11.glTranslatef(Display.getWidth() - BOX_WIDTH - BOX_OFFSET_X, 
				Display.getHeight() - BOX_OFFSET_Y, 0);
		GL11.glColor4f(BOX_COLOR.getRed(), BOX_COLOR.getGreen(), BOX_COLOR.getBlue(), alpha);
		
		GL11.glBegin(GL11.GL_LINE_LOOP);		
		GL11.glVertex2i(0, 0);
		GL11.glVertex2i(BOX_WIDTH, 0);
		GL11.glVertex2i(BOX_WIDTH, -BOX_HEIGHT);
		GL11.glVertex2i(0, -BOX_HEIGHT);
		GL11.glEnd();
		
		GL11.glColor4f(1, 1, 1, 1);
		GL11.glPopMatrix();
		GL11.glPopAttrib();
	}

	@Override
	public void renderInWorldCoords(ViewportAPI vapi) {
	}

	@Override
	public void renderInUICoords(ViewportAPI vapi) {
		if (Global.getCurrentState() == GameState.TITLE)
			return;
		if (engine == null || !engine.isUIShowingHUD() || engine.getCombatUI().isShowingCommandUI())
		{
			return;
		}
		
		drawBox();
	}

	@Override
	public void init(CombatEngineAPI engine) {
		log.info("Chatter plugin initialized");
		this.engine = engine;
		engine.getCustomData().put(DATA_KEY, this);
	}

	@Override
	public void processInputPreCoreControls(float arg0, List<InputEventAPI> arg1) {
	}
	
	protected static class BoxMessage {
		public FleetMemberAPI ship;
		public String text;
		public float ttl = DEBUG_MODE? 99999 : 7;
		
		public BoxMessage(FleetMemberAPI ship, String text) {
			this.ship = ship;
			this.text = text;
		}
	}
	
	public static class ShipStateData
	{
		public String characterId = "default";
		public ShipAPI ship;
		public boolean isPlayer = false;
		public boolean dead = false;
		public boolean engaged = false;
		public boolean needHelp = false;
		public boolean pursuing = false;
		public boolean backingOff = false;
		public boolean running = false;
		public boolean isEnemy = false;
		public boolean canWriteOutOfMissiles = true;
		public int missileOPs = 0;
		public int maxOPs = 0;
		public float hull = 0;
		public boolean overloaded = false;
		public float lastFloatMessageTime = -999;
		public MessageType lastFloatMessageType = MessageType.START;
	}
}
