package org.histidine.chatter.scripts;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import data.scripts.util.MagicSettings;
import java.util.Map;
import org.histidine.chatter.ChatterConfig;
import org.histidine.chatter.ChatterDataManager;
import org.histidine.chatter.campaign.ChatterCampaignListener;
import org.histidine.chatter.utils.LunaConfigHelper;

public class ChatterModPlugin extends BaseModPlugin
{
	public static boolean hasTwigLib = Global.getSettings().getModManager().isModEnabled("ztwiglib");
	
	@Override
	public void onGameLoad(boolean newGame) {
		Global.getSector().addTransientListener(new ChatterCampaignListener());
		if (Global.getSettings().getModManager().isModEnabled("lunalib")) {
			LunaConfigHelper.createListener();
		}
		ChatterDataManager.loadCharacters();
	}
	
	@Override
	public void onApplicationLoad() throws Exception {
		// preload images
		if (ChatterConfig.fleetIntro) {
			Map<String, String> roundels = MagicSettings.getStringMap("chatter", "factionRoundels");
			for (String path : roundels.values()) {
				Global.getSettings().loadTexture(path);
			}
			
			Map<String, String> flagshipToLogo = MagicSettings.getStringMap("chatter", "flagshipToLogoMap");
			for (String path : flagshipToLogo.values()) {
				Global.getSettings().loadTexture(path);
			}
		}
	}
}