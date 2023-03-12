package org.histidine.chatter.utils;

import com.fs.starfarer.api.Global;
import lunalib.lunaSettings.LunaSettings;
import lunalib.lunaSettings.LunaSettingsListener;
import org.apache.log4j.Logger;

import org.histidine.chatter.ChatterConfig;

public class LunaConfigHelper implements LunaSettingsListener {
	
	public static final String MOD_ID = "chatter";
	
	public static Logger log = Global.getLogger(LunaConfigHelper.class);

    // runcode exerelin.utilities.LunaConfigHelper.initLunaConfig()
    public static void initLunaConfig() {
        String mid = MOD_ID;
        //List<String> tags = DEFAULT_TAGS;

        addHeader("general");
        addSetting("lowImportanceChatter", "boolean", ChatterConfig.lowImportanceChatter);
		addSetting("allyChatter", "boolean", ChatterConfig.allyChatter);
		addSetting("selfChatter", "boolean", ChatterConfig.selfChatter);
		addSetting("enemyChatter", "boolean", ChatterConfig.enemyChatter);
		addSetting("minMissileOPFractionForChatter", "float", ChatterConfig.minMissileOPFractionForChatter, 0, 1);
		
		addHeader("ui");
		addSetting("chatterBox", "boolean", ChatterConfig.chatterBox);
		addSetting("chatterBoxOfficerMode", "boolean", ChatterConfig.chatterBoxOfficerMode);
		addSetting("fleetIntro", "boolean", ChatterConfig.fleetIntro);
		
		addHeader("characters");		
		addSetting("factionSpecificCharacters", "boolean", ChatterConfig.factionSpecificCharacters);
		addSetting("personalityChanceScaling", "boolean", ChatterConfig.personalityChanceScaling);
		addSetting("genderChanceScaling", "float", ChatterConfig.genderChanceScaling, 1, 5);
		addSetting("restrictAICharacters", "boolean", ChatterConfig.restrictAICharacters);

        LunaSettings.SettingsCreator.refresh(mid);

        try {
            loadConfigFromLuna();
        } catch (NullPointerException npe) {
            // config not created yet I guess, do nothing
        }
    }

    public static void loadConfigFromLuna() {
        ChatterConfig.lowImportanceChatter = (boolean)loadSetting("lowImportanceChatter", "boolean");
		ChatterConfig.allyChatter = (boolean)loadSetting("allyChatter", "boolean");
		ChatterConfig.selfChatter = (boolean)loadSetting("selfChatter", "boolean");
		ChatterConfig.enemyChatter = (boolean)loadSetting("enemyChatter", "boolean");
		ChatterConfig.minMissileOPFractionForChatter = (float)loadSetting("minMissileOPFractionForChatter", "float");
		
		ChatterConfig.chatterBox = (boolean)loadSetting("chatterBox", "boolean");
		ChatterConfig.chatterBoxOfficerMode = (boolean)loadSetting("chatterBoxOfficerMode", "boolean");
		ChatterConfig.fleetIntro = (boolean)loadSetting("fleetIntro", "boolean");
		
		ChatterConfig.factionSpecificCharacters = (boolean)loadSetting("factionSpecificCharacters", "boolean");
		ChatterConfig.personalityChanceScaling = (boolean)loadSetting("personalityChanceScaling", "boolean");
		ChatterConfig.genderChanceScaling = (float)loadSetting("genderChanceScaling", "float");
		ChatterConfig.restrictAICharacters = (boolean)loadSetting("restrictAICharacters", "boolean");
    }

    public static Object loadSetting(String var, String type) {
        String mid = MOD_ID;
        switch (type) {
            case "bool":
            case "boolean":
                return LunaSettings.getBoolean(mid, var);
            case "int":
            case "integer":
            case "key":
                return LunaSettings.getInt(mid, var);
            case "float":
                return (float)(double)LunaSettings.getDouble(mid, var);
            case "double":
                return LunaSettings.getDouble(mid, var);
            default:
                log.error(String.format("Setting %s has invalid type %s", var, type));
        }
        return null;
    }

    public static void addSetting(String var, String type, Object defaultVal) {
        addSetting(var, type, defaultVal, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }

    public static void addSetting(String var, String type, Object defaultVal, double min, double max) {
        String tooltip = Global.getSettings().getString("chatter_lunaSettings", "tooltip_" + var);
        if (tooltip.startsWith("Missing string:")) {
            tooltip = "";
        }
        String mid = MOD_ID;
        String name = getString("name_" + var);

        switch (type) {
            case "boolean":
                LunaSettings.SettingsCreator.addBoolean(mid, var, name, tooltip, (boolean)defaultVal);
                break;
            case "int":
            case "integer":
                if (defaultVal instanceof Float) {
                    defaultVal = Math.round((float)defaultVal);
                }
                LunaSettings.SettingsCreator.addInt(mid, var, name, tooltip,
                        (int)defaultVal, (int)Math.round(min), (int)Math.round(max));
                break;
            case "float":
                // fix float -> double conversion causing an unround number
                String floatStr = ((Float)defaultVal).toString();
                LunaSettings.SettingsCreator.addDouble(mid, var, name, tooltip,
                        Double.parseDouble(floatStr), min, max);
                break;
            case "double":
                LunaSettings.SettingsCreator.addDouble(mid, var, name, tooltip,
                        (double)defaultVal, min, max);
                break;
            case "key":
                LunaSettings.SettingsCreator.addKeybind(mid, var, name, tooltip, (int)defaultVal);
            default:
                log.error(String.format("Setting %s has invalid type %s", var, type));
        }
    }

    public static void addHeader(String id) {
        LunaSettings.SettingsCreator.addHeader(MOD_ID, id, getString("header_" + id));
    }

    public static void addHeader(String id, String title) {
        LunaSettings.SettingsCreator.addHeader(MOD_ID, id, title);
    }

    public static LunaConfigHelper createListener() {
        LunaConfigHelper helper = new LunaConfigHelper();
        Global.getSector().getListenerManager().addListener(helper, true);
        return helper;
    }

    @Override
    public void settingsChanged(String modId) {
        if (MOD_ID.equals(modId)) {
            loadConfigFromLuna();
        }
    }

    public static String getString(String id) {
        return StringHelper.getString("chatter_lunaSettings", id);
    }
}
