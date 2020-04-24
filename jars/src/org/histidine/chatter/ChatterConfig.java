package org.histidine.chatter;

import com.fs.starfarer.api.Global;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.apache.log4j.Logger;
import org.histidine.chatter.utils.GeneralUtils;
import org.json.JSONException;
import org.json.JSONObject;

public class ChatterConfig {
	
	public static final String CONFIG_FILE = "chatterConfig.json";
	
	public static boolean lowImportanceChatter = true;
	public static boolean allyChatter = true;
	public static boolean selfChatter = false;
	public static boolean factionSpecificCharacters = true;
	public static float minMissileOPFractionForChatter = 0.2f;
	public static boolean personalityChanceScaling = true;
	public static float genderChanceScaling = 1.5f;
	public static Set<String> disallowedTags = null;
	
	public static Logger log = Global.getLogger(ChatterConfig.class);
	
	static {
		try {
			JSONObject settings = Global.getSettings().loadJSON(CONFIG_FILE);
			lowImportanceChatter = settings.optBoolean("lowImportanceChatter", lowImportanceChatter);
			allyChatter = settings.optBoolean("allyChatter", allyChatter);
			selfChatter = settings.optBoolean("selfChatter", selfChatter);
			factionSpecificCharacters = settings.optBoolean("factionSpecificCharacters", factionSpecificCharacters);
			minMissileOPFractionForChatter = (float)settings.optDouble("minMissileOPFractionForChatter", minMissileOPFractionForChatter);
			personalityChanceScaling = settings.optBoolean("personalityChanceScaling", personalityChanceScaling);
			genderChanceScaling = (float)settings.optDouble("genderChanceScaling", genderChanceScaling);
			
			disallowedTags = new HashSet<>(GeneralUtils.JSONArrayToStringList(settings.optJSONArray("disallowedTags")));
		} 
		catch (IOException | JSONException ex) {
			log.error(ex);
		}
	}
}
