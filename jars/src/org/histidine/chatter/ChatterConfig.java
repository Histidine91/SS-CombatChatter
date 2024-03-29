package org.histidine.chatter;

import com.fs.starfarer.api.Global;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.apache.log4j.Logger;
import org.histidine.chatter.utils.GeneralUtils;
import org.histidine.chatter.utils.LunaConfigHelper;
import org.json.JSONException;
import org.json.JSONObject;

public class ChatterConfig {
	
	public static final String CONFIG_FILE = "chatterConfig.json";
	
	public static boolean lowImportanceChatter = true;
	public static boolean allyChatter = true;
	public static boolean selfChatter = false;
	public static boolean enemyChatter = false;
	public static boolean chatterBox = false;
	public static boolean chatterBoxOfficerMode = false;
	public static boolean factionSpecificCharacters = true;
	public static float minMissileOPFractionForChatter = 0.2f;
	public static boolean personalityChanceScaling = true;
	public static float genderChanceScaling = 1.5f;
	public static boolean restrictAICharacters = true;
	public static boolean fleetIntro = true;
	public static Set<String> disallowedTags;
	public static Set<String> noEnemyChatterFactions;
	public static Set<String> introSplashFleetTypes;
	public static Set<String> introSplashFactions;
	
	public static Logger log = Global.getLogger(ChatterConfig.class);
	
	static {
		try {
			JSONObject settings = Global.getSettings().loadJSON(CONFIG_FILE);
			lowImportanceChatter = settings.optBoolean("lowImportanceChatter", lowImportanceChatter);
			allyChatter = settings.optBoolean("allyChatter", allyChatter);
			selfChatter = settings.optBoolean("selfChatter", selfChatter);
			enemyChatter = settings.optBoolean("enemyChatter", enemyChatter);
			chatterBox = settings.optBoolean("chatterBox", chatterBox);
			chatterBoxOfficerMode = settings.optBoolean("chatterBoxOfficerMode", chatterBoxOfficerMode);
			
			factionSpecificCharacters = settings.optBoolean("factionSpecificCharacters", factionSpecificCharacters);
			minMissileOPFractionForChatter = (float)settings.optDouble("minMissileOPFractionForChatter", minMissileOPFractionForChatter);
			personalityChanceScaling = settings.optBoolean("personalityChanceScaling", personalityChanceScaling);
			genderChanceScaling = (float)settings.optDouble("genderChanceScaling", genderChanceScaling);
			restrictAICharacters = settings.optBoolean("restrictAICharacters", restrictAICharacters);
			
			fleetIntro = settings.optBoolean("fleetIntro", fleetIntro);
			
			disallowedTags = new HashSet<>(GeneralUtils.JSONArrayToStringList(settings.optJSONArray("disallowedTags")));
			noEnemyChatterFactions = new HashSet<>(GeneralUtils.JSONArrayToStringList(settings.optJSONArray("noEnemyChatterFactions")));
			introSplashFleetTypes = new HashSet<>(GeneralUtils.JSONArrayToStringList(settings.optJSONArray("introSplashFleetTypes")));
			introSplashFactions = new HashSet<>(GeneralUtils.JSONArrayToStringList(settings.optJSONArray("introSplashFactions")));
		} 
		catch (IOException | JSONException ex) {
			throw new RuntimeException("Failed to load chatter config", ex);
		}
		if (Global.getSettings().getModManager().isModEnabled("lunalib")) {
			LunaConfigHelper.initLunaConfig();
		}
	}
}
