package org.histidine.chatter.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;
import org.histidine.chatter.ChatterCharacter;
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
	
	public static final List<ChatterCharacter> CHARACTERS = new ArrayList<>();
	public static final Map<String, ChatterCharacter> CHARACTERS_MAP = new HashMap<>();
	
	public static Logger log = Global.getLogger(CampaignHandler.class);
	
	static {
		loadCharacters();
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
					
					CHARACTERS.add(character);
					CHARACTERS_MAP.put(characterName, character);
				} catch (IOException | JSONException ex) {	// can't read character file
					log.error(ex);
				}
			}
		} catch (IOException | JSONException ex) {	// can't read CSV
			log.error(ex);
		}
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
		
		for (ChatterCharacter character : CHARACTERS)
		{
			if (!isMission && !character.gender.contains(gender)) continue;
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
}
