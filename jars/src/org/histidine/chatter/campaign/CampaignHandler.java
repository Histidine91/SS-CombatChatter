package org.histidine.chatter.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;
import org.histidine.chatter.ChatterCharacter;
import org.histidine.chatter.ChatterConfig;
import org.histidine.chatter.ChatterLine;
import org.histidine.chatter.ChatterLine.MessageType;
import org.histidine.chatter.combat.ChatterCombatPlugin;
import org.histidine.chatter.utils.GeneralUtils;
import org.histidine.chatter.utils.StringHelper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class CampaignHandler {
	
	public static final String CHARACTERS_DIR = "data/config/chatter/";
	public static final String CHARACTERS_LIST = CHARACTERS_DIR + "characters.csv";
	public static final String FACTION_TAGS_FILE = CHARACTERS_DIR + "factiontags.csv";
	public static final String CHARACTER_FACTIONS_FILE = CHARACTERS_DIR + "character_factions.csv";
	
	public static final List<ChatterCharacter> CHARACTERS = new ArrayList<>();
	public static final Map<String, ChatterCharacter> CHARACTERS_MAP = new HashMap<>();
	public static final Map<String, Set<String>> FACTION_TAGS = new HashMap<>();
	public static final Map<String, Map<String, Integer>> CHARACTER_FACTIONS = new HashMap<>();
	
	public static Logger log = Global.getLogger(CampaignHandler.class);
	
	protected static boolean loaded = false;
	
	// FIXME: this lags when the class is first loaded, 
	// and right now the class is loaded at the start of the first battle
	static {
		loadCharacters();
	}
	
	public static void loadCharacters()
	{
		if (loaded) return;
		try {
			// load faction tags
			JSONArray tagsCSV = Global.getSettings().getMergedSpreadsheetDataForMod("faction", FACTION_TAGS_FILE, "chatter");
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
				FACTION_TAGS.put(factionId, tags);
			}
			
			// load character-faction compatibility data
			JSONArray charFactionsCSV = Global.getSettings().getMergedSpreadsheetDataForMod("character", CHARACTER_FACTIONS_FILE, "chatter");
			for(int x = 0; x < charFactionsCSV.length(); x++)
			{
				JSONObject row = charFactionsCSV.getJSONObject(x);
				String characterId = row.getString("character");
				Map<String, Integer> factionCompat = new HashMap<>();
				Iterator<?> factionsAndGroups = row.keys();
				while( factionsAndGroups.hasNext() ) {
					String factionOrGroup = (String)factionsAndGroups.next();
					if (factionOrGroup.equals("fs_rowSource")) continue;
					if (factionOrGroup.equals("character")) continue;
					factionCompat.put(factionOrGroup, row.getInt(factionOrGroup));
				}
				CHARACTER_FACTIONS.put(characterId, factionCompat);
			}
			
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
					character.talkativeness = (float)characterEntry.optDouble("chance", 1);
					
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
					
					CHARACTERS.add(character);
					CHARACTERS_MAP.put(characterId, character);
				} catch (IOException | JSONException ex) {	// can't read character file
					log.error(ex);
				}
			}
		} catch (IOException | JSONException ex) {	// can't read CSV
			log.error(ex);
		}
		
		loaded = true;
	}
	
	public static String getCharacterForOfficer(PersonAPI captain, boolean isAlly, CombatEngineAPI engine)
	{
		// try to load officer if available
		String officerID = captain.getId();
		Map<String, String> savedOfficers = GeneralUtils.getSavedCharacters();
		
		if (savedOfficers.containsKey(officerID))
		{
			String saved = savedOfficers.get(officerID);
			// this check makes sure it doesn't break if a previously used character is deleted
			if (CHARACTERS_MAP.containsKey(saved)) return saved;
		}
		
		WeightedRandomPicker<String> picker = new WeightedRandomPicker<>();
		WeightedRandomPicker<String> pickerBackup = new WeightedRandomPicker<>();
		
		String gender = "n";
		if (captain.isFemale()) gender = "f";
		else if (captain.isMale()) gender = "m";
		boolean isMission = engine.isMission();
		String factionId = captain.getFaction().getId();
		if (captain.getMemoryWithoutUpdate().contains("$originalFaction"))
		{
			factionId = captain.getMemoryWithoutUpdate().getString("$originalFaction");
		}
		
		for (ChatterCharacter character : CHARACTERS)
		{
			if (!isMission) {
				if (!character.gender.contains(gender)) continue;
				if (ChatterConfig.factionSpecificCharacters && !character.allowedFactions.contains(factionId)) 
					continue;
			}
			if (character.personalities.contains(captain.getPersonalityAPI().getId()))
			{
				picker.add(character.name, character.chance);
				pickerBackup.add(character.name, character.chance);
			}
		}
		
		// try to not have duplicate chatter chars among our fleet's officers (unless we've run out)
		if ( !isAlly && (engine.isInCampaign() || engine.isInCampaignSim()) )
		{
			Iterator<Map.Entry<String, String>> iter = savedOfficers.entrySet().iterator();
			while (iter.hasNext())
			{
				Map.Entry<String, String> tmp = iter.next();
				String existing = tmp.getValue();
				if (picker.getItems().contains(existing))
					picker.remove(existing);
			}
		}
		if (picker.isEmpty()) picker = pickerBackup;
		
		if (picker.isEmpty()) return "default";
		
		String charName = picker.pick();
		if (charName == null) return "default";
		
		log.info("Assigning character " + charName + " to officer " + captain.getName().getFullName());
		if (!isAlly && !isMission) 
			savedOfficers.put(captain.getId(), charName);
		return charName;
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
	
	protected static boolean isCharacterAllowedForFaction(String charId, String factionId)
	{
		if (!CHARACTER_FACTIONS.containsKey(charId)) {
			return true;	// character-faction entry not found; ignore factions
		}
		
		Map<String, Integer> allowedFactionsOrGroups = CHARACTER_FACTIONS.get(charId);
		int compatibility = 0;
		if (allowedFactionsOrGroups.containsKey(charId))
			compatibility = allowedFactionsOrGroups.get(charId);
		
		if (compatibility == 1) return true;	// explicitly allow this faction
		else if (compatibility == -1) return false;	// explicitly forbid this faction
		
		Set<String> tags = FACTION_TAGS.get(factionId);
		if (tags == null) return true;
		for (String tag: tags) {
			if (!allowedFactionsOrGroups.containsKey(tag)) continue;
			int groupCompat = allowedFactionsOrGroups.get(tag);
			if (groupCompat == 1) return true;
			else if (groupCompat == -1) return false;
			//else return false;
		}
		
		return false;
	}
}
