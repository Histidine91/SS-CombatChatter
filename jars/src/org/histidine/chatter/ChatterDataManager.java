package org.histidine.chatter;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.FullName.Gender;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.campaign.events.OfficerManagerEvent;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;
import org.histidine.chatter.ChatterLine.MessageType;
import org.histidine.chatter.utils.GeneralUtils;
import org.histidine.chatter.utils.StringHelper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ChatterDataManager {
	
	public static final String PERSISTENT_DATA_KEY = "combatChatter";
	public static final String CONFIG_DIR = "data/config/chatter/";
	public static final String CHARACTERS_DIR = CONFIG_DIR + "characters/";
	public static final String CHARACTERS_LIST = CONFIG_DIR + "characters.csv";
	public static final String FACTION_TAGS_FILE = CONFIG_DIR + "factiontags.csv";
	public static final String CHARACTER_FACTIONS_FILE = CONFIG_DIR + "character_factions.csv";
	public static final String NAME_TO_CHARACTER_FILE = CONFIG_DIR + "officer_name_to_character.csv";
	public static final String HULL_FACTION_PREFIX_FILE = CONFIG_DIR + "hull_prefixes.csv";
	public static final String SHIP_NAME_FACTION_PREFIX_FILE = CONFIG_DIR + "ship_name_prefixes.csv";
	public static final String EXCLUDED_HULL_FILE = CONFIG_DIR + "excluded_hulls.csv";
	public static final String BOSS_SHIP_FILE = CONFIG_DIR + "boss_ships.csv";
	public static final String CHARACTER_MEMORY_KEY = "$chatterChar";
	
	public static final List<ChatterCharacter> CHARACTERS = new ArrayList<>();
	public static final Map<String, ChatterCharacter> CHARACTERS_MAP = new HashMap<>();
	public static final Map<String, Set<String>> FACTION_TAGS = new HashMap<>();
	
	// first String key is character ID
	// value is a map where the key is a faction ID or tag ("military", "corporate", etc.) and value is its score (-1, 0 or 1);
	public static final Map<String, Map<String, Integer>> CHARACTER_FACTIONS = new HashMap<>();
	
	public static final Set<String> EXCLUDED_HULLS = new HashSet<>();
	public static final Set<String> BOSS_SHIPS = new HashSet<>();
	
	public static final List<NameToCharacterMapping> NAME_TO_CHARACTER = new ArrayList<>();
	public static final List<String[]> FACTION_HULL_PREFIXES = new ArrayList<>();
	public static final List<String[]> FACTION_SHIPNAME_PREFIXES = new ArrayList<>();
	
	public static final boolean DEBUG_MODE = false;
	
	public static Logger log = Global.getLogger(ChatterDataManager.class);
	
	protected static boolean loaded = false;
	
	// FIXME: this lags when the class is first loaded, 
	// and right now the class is loaded at the start of the first battle
	static {
		loadCharacters();
	}
	
	public static void debugPrint(String str)
	{
		if (!DEBUG_MODE) return;
		log.info(str);
	}
	
	public static void loadCharacters()
	{
		if (loaded) return;
		try {
			// load faction tags
			JSONArray tagsCSV = Global.getSettings().getMergedSpreadsheetDataForMod("faction", FACTION_TAGS_FILE, "chatter");
			debugPrint("Loading faction tags");
			for(int x = 0; x < tagsCSV.length(); x++)
			{
				JSONObject row = tagsCSV.getJSONObject(x);
				String factionId = row.getString("faction");
				String tagsStr = row.getString("tags");
				String[] tagsArray = tagsStr.split(",");
				Set<String> tags = new HashSet<>();
				for (String tag: tagsArray) {
					String tagTrimmed = tag.trim();
					tags.add(tagTrimmed);
				}
				debugPrint("\t" + factionId + ": " + tags);
				FACTION_TAGS.put(factionId, tags);
			}
			
			// load character-faction compatibility data
			JSONArray charFactionsCSV = Global.getSettings().getMergedSpreadsheetDataForMod("character", CHARACTER_FACTIONS_FILE, "chatter");
			debugPrint("Loading character-faction compatibility data");
			for(int x = 0; x < charFactionsCSV.length(); x++)
			{
				JSONObject row = charFactionsCSV.getJSONObject(x);
				String characterId = row.getString("character");
				debugPrint("\tCharacter: " + characterId);
				Map<String, Integer> factionCompat = new HashMap<>();
				Iterator<?> factionsAndGroups = row.keys();
				while( factionsAndGroups.hasNext() ) {
					String factionOrGroup = (String)factionsAndGroups.next();
					if (factionOrGroup.equals("fs_rowSource")) continue;
					if (factionOrGroup.equals("character")) continue;
					factionCompat.put(factionOrGroup, row.getInt(factionOrGroup));
					debugPrint("\t\t" + factionOrGroup + ": " + row.getInt(factionOrGroup));
				}
				CHARACTER_FACTIONS.put(characterId, factionCompat);
			}
			
			// load the actual characters
			debugPrint("Loading characters");
			JSONArray charCSV = Global.getSettings().getMergedSpreadsheetDataForMod("character", CHARACTERS_LIST, "chatter");
			for(int x = 0; x < charCSV.length(); x++)
			{
				JSONObject row = charCSV.getJSONObject(x);
				String characterId = row.getString("character");
				try {
					JSONObject characterEntry = Global.getSettings().loadJSON(CHARACTERS_DIR + characterId + ".json");
					ChatterCharacter character = new ChatterCharacter();
					character.id = characterId;
					character.name = characterEntry.optString("name", characterId);
					character.personalities = GeneralUtils.JSONArrayToStringList(characterEntry.optJSONArray("personalities"));
					character.gender = GeneralUtils.JSONArrayToStringList(characterEntry.optJSONArray("gender"));
					character.chance = (float)characterEntry.optDouble("chance", 1);
					character.talkativeness = (float)characterEntry.optDouble("talkativeness", 1);
					character.categoryTags = new HashSet<>(GeneralUtils.JSONArrayToStringList(characterEntry.optJSONArray("categoryTags")));
					character.isDefault = character.categoryTags.contains("default");
					
					JSONObject lines = characterEntry.getJSONObject("lines");
					Iterator<?> keys = lines.keys();
					while( keys.hasNext() ) {
						String key = (String)keys.next();
						MessageType type;
						try {
							type = MessageType.valueOf(StringHelper.flattenToAscii(key).toUpperCase());
						} catch (IllegalArgumentException ex) {
							continue;
						}
						JSONArray linesForKey = lines.getJSONArray(key);
						List<ChatterLine> linesForKeyList = new ArrayList<>();
						for (int i=0; i<linesForKey.length(); i++)
						{
							JSONObject lineEntry = linesForKey.getJSONObject(i);
							String text = lineEntry.optString("text");
							String sound = null;
							if (lineEntry.has("sound"))
								sound = lineEntry.getString("sound");
							linesForKeyList.add(new ChatterLine(text, sound));
						}
						character.lines.put(type, linesForKeyList);
					}
					
					character.allowedFactions = getAllowedFactionsForCharacter(character.id);
					character.allowedForAI = isValidAICharacter(character.id);
					
					CHARACTERS.add(character);
					CHARACTERS_MAP.put(characterId, character);
				} catch (IOException | JSONException ex) {	// can't read character file
					throw new RuntimeException("Error loading chatter character " + characterId, ex);
				} catch (RuntimeException rex) {	// character file missing?
					throw new RuntimeException("Error loading chatter character" + characterId + ", file missing?", rex);
				}
			}
			
			// map for officer names getting specific characters
			debugPrint("Loading officer name to character file map");
			JSONArray name2Char = Global.getSettings().getMergedSpreadsheetDataForMod("id", NAME_TO_CHARACTER_FILE, "chatter");
			for(int x = 0; x < name2Char.length(); x++)
			{
				String id = "<unknown>";
				try {
					JSONObject row = name2Char.getJSONObject(x);
					id = row.getString("id");
					if (id.isEmpty()) continue;
					String firstName = row.optString("first name", null);
					String lastName = row.optString("last name", null);
					String character = row.getString("character");
					NAME_TO_CHARACTER.add(new NameToCharacterMapping(id, firstName, lastName, character));
				} catch (JSONException ex) {
					log.error("Failed to load officer name–character mapping for " + id, ex);
				}
			}
			
			// map for getting faction ID based on hull ID prefix (e.g. ii_olympus is an II ship)
			debugPrint("Loading hull prefixes");
			JSONArray prefixes = Global.getSettings().getMergedSpreadsheetDataForMod("prefix", HULL_FACTION_PREFIX_FILE, "chatter");
			for(int x = 0; x < prefixes.length(); x++)
			{
				String prefix = "<unknown>";
				try {
					JSONObject row = prefixes.getJSONObject(x);
					prefix = row.getString("prefix");
					if (prefix.isEmpty()) continue;
					String faction = row.getString("faction");
					FACTION_HULL_PREFIXES.add(new String[]{prefix, faction});
				} catch (JSONException ex) {
					log.error("Failed to load hull ID prefix – faction mapping for " + prefix, ex);
				}
			}
		
			// map for getting faction ID based on ship name's prefix (e.g. TTS for Tri-Tachyon)
			debugPrint("Loading ship name prefixes");
			JSONArray prefixes2 = Global.getSettings().getMergedSpreadsheetDataForMod("prefix", SHIP_NAME_FACTION_PREFIX_FILE, "chatter");
			for(int x = 0; x < prefixes2.length(); x++)
			{
				String prefix = "<unknown>";
				try {
					JSONObject row = prefixes2.getJSONObject(x);
					prefix = row.getString("prefix");
					if (prefix.isEmpty()) continue;
					String faction = row.getString("faction");
					FACTION_SHIPNAME_PREFIXES.add(new String[]{prefix, faction});
				} catch (JSONException ex) {
					log.error("Failed to load ship name prefix – faction mapping for " + prefix, ex);
				}
			}
			
			// hull exclusion
			debugPrint("Loading hull exclusion list");
			JSONArray excluded = Global.getSettings().getMergedSpreadsheetDataForMod("hull id", EXCLUDED_HULL_FILE, "chatter");
			for(int x = 0; x < excluded.length(); x++)
			{
				try {
					JSONObject row = excluded.getJSONObject(x);
					String hullId = row.getString("hull id");
					if (hullId.isEmpty()) continue;
					EXCLUDED_HULLS.add(hullId);
				} catch (JSONException ex) {}
			}
			
			// boss ships
			debugPrint("Loading boss ships");
			JSONArray bosses = Global.getSettings().getMergedSpreadsheetDataForMod("hull id", BOSS_SHIP_FILE, "chatter");
			for(int x = 0; x < bosses.length(); x++)
			{
				try {
					JSONObject row = bosses.getJSONObject(x);
					String hullId = row.getString("hull id");
					if (hullId.isEmpty()) continue;
					BOSS_SHIPS.add(hullId);
				} catch (JSONException ex) {}
			}
			
		} catch (IOException | JSONException ex) {	// can't read CSV
			log.error(ex);
		}
		
		loaded = true;
	}
	
	/**
	 * Returns true if this character has a tag listed in the mod config's disallowedTags set
	 * @param character
	 * @return
	 */
	public static boolean isCharacterDisallowedByTag(ChatterCharacter character)
	{
		for (String disabledTag : ChatterConfig.disallowedTags)
		{
			if (character.categoryTags.contains(disabledTag))
			{
				//log.info("Character " + character.name + " verboten by tag " + disabledTag);
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Attempts to match an officer name to a chatter character based on their name.
	 * @param name The officer's name object.
	 * @return The character ID of the best match.
	 */
	public static String getBestNameMatch(FullName name) {
		for (ChatterCharacter character : CHARACTERS)
		{
			if (character.name.equals(name.getFullName())) {
				return character.id;		
			}
		}
		
		String bestMatch = null;
		int bestScore = 0;
		for (NameToCharacterMapping map : NAME_TO_CHARACTER) {
			int score = 0;
			if (map.firstName != null) {
				//log.info("First name: " + name.getFirst());
				if (map.firstName.equals(name.getFirst()))
					score += 1;
				else continue;
			}
			if (map.lastName != null) {
				//log.info("Last name: " + name.getLast());
				if (map.lastName.equals(name.getLast()))
					score += 1;
				else continue;
			}
			
			if (score > bestScore) {
				bestMatch = map.characterId;
				bestScore = score;
			}
		}
		
		return bestMatch;
	}
	
	public static String getFactionFromShip(FleetMemberAPI ship) {
		String factionId = getFactionIDFromShipNamePrefix(ship.getShipName());
		if (factionId.isEmpty())
			factionId = getFactionIDFromHullID(ship.getHullId());
			
		return factionId;
	}
	
	/**
	 * Picks a new chatter character for the specified officer, 
	 * or retrieves it from memory if available.
	 * @param captain
	 * @param ship
	 * @param engine
	 * @param save If true and certain conditions are met (not in a mission, 
	 * not a fighter, and not a generic captain), save the selected character 
	 * to memory and persistent data.
	 * @return
	 */
	public static String getCharacterForOfficer(PersonAPI captain, FleetMemberAPI ship, 
			CombatEngineAPI engine, boolean save)
	{
		// try to load officer if available
		String saved = getCharacterFromMemory(captain);
		if (saved != null) return saved;
		
		WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();
		WeightedRandomPicker<String> pickerBackup = new WeightedRandomPicker<>();
		
		String gender = "n";
		if (captain.isFemale()) gender = "f";
		else if (captain.isMale()) gender = "m";
		boolean isMission = engine != null && engine.isMission();
		boolean isCampaign = engine != null && (engine.isInCampaign() || engine.isInCampaignSim());
		boolean isFighter = ship.isFighterWing();
		
		save = save && !isMission && !captain.isDefault() && !isFighter;
		
		String factionId = captain.getFaction().getId();
		if (captain.getMemoryWithoutUpdate().contains("$originalFaction"))
		{
			factionId = captain.getMemoryWithoutUpdate().getString("$originalFaction");
		}
		
		if (isMission)
		{
			factionId = getFactionFromShip(ship);
		}
		
		// Auto assign from officer name if appropriate
		if (!captain.isDefault()) {
			String id = getBestNameMatch(captain.getName());
			if (id != null) {
				if (save) saveCharacter(captain, id);
				return id;
			}
		}
		
		debugPrint("Getting character for faction " + factionId);
		for (ChatterCharacter character : CHARACTERS)
		{
			if (isCharacterDisallowedByTag(character))
				continue;
			
			if (!isMission && !isFighter) {
				if (!character.gender.contains(gender)) continue;
			}
			
			// if captain is an AI, check if this character is allowed for AIs
			if (captain.isAICore() && ChatterConfig.restrictAICharacters && !character.allowedForAI) 
			{
				continue;
			}
			// check if this character is allowed for captain's faction (except for fighters)
			else if (!isFighter && ChatterConfig.factionSpecificCharacters 
					&& !character.allowedFactions.contains(factionId)) 
				continue;
			
			//debugPrint("\tFaction allowed to use character " + character.id);
			String personalityId = captain.getPersonalityAPI().getId();
			//log.info(String.format("Captain %s has personality %s", captain.getNameString(), personalityId));
			
			if (character.personalities.contains(captain.getPersonalityAPI().getId()))
			{
				if (!isFighter) debugPrint("\tAllowed to pick character: " + character.id);
				float weight = character.chance;
				if (ChatterConfig.personalityChanceScaling) {
					int numPersonalities = character.personalities.size();
					if (numPersonalities == 1)
						weight *= 2f;
					else if (numPersonalities == 2)
						weight *= 1.2f;
				}
				if (character.gender.size() == 1) {
					weight *= ChatterConfig.genderChanceScaling;
				}
				
				picker.add(character.id, weight);
				pickerBackup.add(character.id, weight);
			}
		}
		
		// try to not have duplicate chatter chars among our fleet's officers (unless we've run out)
		if ( !ship.isAlly() && !isFighter && isCampaign)
		{
			Set<String> usedChars = getUsedCharacters();
			for (String usedChar : usedChars) {
				if (picker.getItems().contains(usedChar) && !isFighter)
					debugPrint("\tRemoving used character: " + usedChar);
				picker.remove(usedChar);
			}
				
		}
		if (picker.isEmpty()) picker = pickerBackup;
		
		if (picker.isEmpty()) return "default";
		
		String charId = picker.pick();
		if (charId == null) return "default";
		
		//log.info("Assigning character " + charId + " to officer " + captain.getName().getFullName());
		if (save) saveCharacter(captain, charId);
		return charId;
	}
	
	protected static Set<String> getAllowedFactionsForCharacter(String charId) {
		// this will probably break by running from static initializer, find some other way to do this
		Set<String> allowedFactions = new HashSet<>();
		for (FactionAPI faction : Global.getSector().getAllFactions()) {
			String factionId = faction.getId();
			
			if (isCharacterAllowedForFaction(charId, factionId)) {
				allowedFactions.add(factionId);
				//log.info("Character " + charId + " allows faction " + factionId);
			}
		}
		return allowedFactions;
	}
	
	protected static boolean isValidAICharacter(String charId) {
		if (!CHARACTER_FACTIONS.containsKey(charId)) {
			return true;
		}
		try {
			Map<String, Integer> charFactionEntry = CHARACTER_FACTIONS.get(charId);
			Integer aiScore = charFactionEntry.get("ai");
			return aiScore >= 1;
		} catch (Exception ex) {
			
		}
		return true;
	}
	
	protected static boolean isCharacterAllowedForFaction(String charId, String factionId)
	{
		debugPrint("Testing character comptability: " + charId + ", " + factionId);
		if (!CHARACTER_FACTIONS.containsKey(charId)) {
			return true;	// character-faction entry not found; ignore factions
		}
		
		Map<String, Integer> allowedFactionsOrGroups = CHARACTER_FACTIONS.get(charId);
		int compatibility = 0;
		if (allowedFactionsOrGroups.containsKey(factionId))
			compatibility = allowedFactionsOrGroups.get(factionId);
		
		if (compatibility != 0) debugPrint("Base compatiblity: " + compatibility);
		
		if (compatibility == 1) return true;	// explicitly allow this faction
		else if (compatibility == -1) return false;	// explicitly forbid this faction
		
		Set<String> tags = FACTION_TAGS.get(factionId);
		if (tags == null) return true;
		for (String tag: tags) {
			if (!allowedFactionsOrGroups.containsKey(tag)) continue;
			int groupCompat = allowedFactionsOrGroups.get(tag);
			
			debugPrint("Compatibility with tag " + tag + ": " + groupCompat);
			
			if (groupCompat == 1) return true;
			else if (groupCompat == -1) return false;
			//else return false;
		}
		
		return false;
	}
	
	/**
	 * Used to guess the faction of the opposing fleet in missions, based on the hull ID of the first enemy ship
	 * @param hullID
	 * @return Faction ID, or empty string if no faction found
	 */
	public static String getFactionIDFromHullID(String hullID)
	{
		hullID = hullID.toLowerCase(Locale.ROOT);
		//log.info("Getting faction for hull ID " + hullID);
		if (hullID.endsWith("_cabal"))
			return "cabal";
		for (String[] mapEntry : FACTION_HULL_PREFIXES)
		{
			if (hullID.startsWith(mapEntry[0]))
				return mapEntry[1];
		}
		return "";
	}
	
	/**
	 * Used to guess the faction of the opposing fleet in missions, based on the name of the first enemy ship
	 * @param shipName
	 * @return Faction ID, or empty string if no faction found
	 */
	public static String getFactionIDFromShipNamePrefix(String shipName)
	{
		if (shipName == null) return "";
		//log.info("Getting faction for ship name " + shipName);
		for (String[] mapEntry : FACTION_SHIPNAME_PREFIXES)
		{
			if (shipName.startsWith(mapEntry[0] + " "))
				return mapEntry[1];
		}
		return "";
	}
	
	/**
	 * Tries to get the officer's chatter character from their memory
	 * @param officer
	 * @return Chatter character ID, or null if no character saved
	 */
	public static String getCharacterFromMemory(PersonAPI officer)
	{
		String result = null;
		if (officer.getMemoryWithoutUpdate().contains(CHARACTER_MEMORY_KEY))
		{
			result = officer.getMemoryWithoutUpdate().getString(CHARACTER_MEMORY_KEY);
		}
		
		// this check makes sure it doesn't break if a previously used character is deleted
		if (CHARACTERS_MAP.containsKey(result)) return result;
		return null;
	}
	
	public static ChatterCharacter getCharacterData(String id)
	{
		return CHARACTERS_MAP.get(id);
	}
	
	/**
	 * Returns a list of officers in the player fleet.
	 * @param includePlayer Include the player character
	 * @return
	 */
	public static List<PersonAPI> getOfficers(boolean includePlayer)
	{
		List<PersonAPI> officers = new ArrayList<>();
		if (Global.getSector().getPlayerFleet() == null) {
			return officers;	// called from menu mission, probably
		}
		
		if (includePlayer)
		{
			PersonAPI playerPerson = Global.getSector().getPlayerPerson();
			officers.add(playerPerson);
		}
		for (OfficerDataAPI officer : Global.getSector().getPlayerFleet().getFleetData().getOfficersCopy())
		{
			PersonAPI person = officer.getPerson();
			officers.add(person);
		}
		// iterate over fleet members too, to get AI core captains
		for (FleetMemberAPI member : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy())
		{
			PersonAPI captain = member.getCaptain();
			if (captain != null && !captain.isDefault() && !officers.contains(captain)) {
				officers.add(captain);
			}
		}
		
		return officers;
	}
	
	/**
	 * Returns a map of officer IDs and their chatter characters present in the player fleet
	 * @return
	 */
	public static Map<String, String> getSavedCharacters()
	{
		Map<String, String> characters = new HashMap<>();
		
		for (PersonAPI person: getOfficers(true))
		{
			characters.put(person.getId(), getCharacterFromMemory(person));
		}
		
		return characters;
	}
	
	/**
	 * Returns a set of all characters that have been used by one or another player's officer in the current campaign
	 * Should only be used for this purpose, not getting saved characters
	 * @return
	 */
	public static Set<String> getUsedCharacters()
	{
		Set<String> used = new HashSet<>();
		Map<String, Object> data = Global.getSector().getPersistentData();
		Object loadedData = data.get(PERSISTENT_DATA_KEY);
		if (loadedData == null)
		{
			Map<String, String> officers = new HashMap<>();
			data.put(PERSISTENT_DATA_KEY, officers);
			return new HashSet<>();
		}
		
		Map<String, String> savedOfficers = (HashMap<String, String>) loadedData;
		
		Iterator<Map.Entry<String, String>> iter = savedOfficers.entrySet().iterator();
		while (iter.hasNext())
		{
			Map.Entry<String, String> tmp = iter.next();
			String character = tmp.getValue();
			used.add(character);
		}
		return used;
	}
	
	public static void resetUsedCharacters() {
		Global.getSector().getPersistentData().remove(PERSISTENT_DATA_KEY);
	}
	
	/**
	 * Saves an assigned character for an officer to memory and persistent data.
	 * @param person
	 * @param character
	 */
	public static void saveCharacter(PersonAPI person, String character)
	{
		// save to person's memory
		person.getMemoryWithoutUpdate().set(CHARACTER_MEMORY_KEY, character);
		
		// save to used characters map in persistent data
		Map<String, Object> data = Global.getSector().getPersistentData();
		Map<String, String> officers = null;
		Object loadedData = data.get(PERSISTENT_DATA_KEY);
		if (loadedData == null)
		{
			officers = new HashMap<>();
			data.put(PERSISTENT_DATA_KEY, officers);
		}
		else
			officers = (HashMap<String, String>)loadedData;
		officers.put(person.getId(), character);
	}
	
	// runcode org.histidine.chatter.ChatterDataManager.debug("aggressive")
	public static void debug(String personality) {
		FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, "paragon_Elite");
		PersonAPI officerM = OfficerManagerEvent.createOfficer(Global.getSector().getPlayerFaction(), 5, false);
		PersonAPI officerF = OfficerManagerEvent.createOfficer(Global.getSector().getPlayerFaction(), 5, false);
		officerM.setGender(Gender.MALE);
		officerF.setGender(Gender.FEMALE);
		officerM.setPersonality(personality);
		officerF.setPersonality(personality);
		
		log.info("Trying characters for male-gendered officer:");
		for (int i=0;i<50;i++) 
		{
			String character = ChatterDataManager.getCharacterForOfficer(officerM, member, null, false);
			log.info("  " + character);
		}
		
		log.info("Trying characters for female-gendered officer:");
		for (int i=0;i<50;i++) 
		{
			String character = ChatterDataManager.getCharacterForOfficer(officerF, member, null, false);
			log.info("  " + character);
		}
	}
	
	/**
	 * Defines mapping of officers with certain names to chatter characters.
	 */
	public static class NameToCharacterMapping {
		// first/last terminology matches what game uses, although I dislike it
		public String id;
		public String firstName;
		public String lastName;
		public String characterId;
		
		public NameToCharacterMapping(String id, String firstName, String lastName, String characterId) 
		{
			this.id = id;
			this.firstName = firstName;
			this.lastName = lastName;
			this.characterId = characterId;
		}
	}
}
