package org.histidine.chatter.scripts;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import lunalib.lunaRefit.LunaRefitManager;
import org.histidine.chatter.campaign.CharacterSelectorRefitButton;
import org.magiclib.util.MagicSettings;
import java.util.Map;
import org.histidine.chatter.ChatterConfig;
import org.histidine.chatter.ChatterDataManager;
import org.histidine.chatter.campaign.ChatterCampaignListener;

public class ChatterModPlugin extends BaseModPlugin
{
	public static final boolean HAVE_LUNALIB = Global.getSettings().getModManager().isModEnabled("lunalib");
	
	@Override
	public void onGameLoad(boolean newGame) {
		Global.getSector().addTransientListener(new ChatterCampaignListener());
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

		if (HAVE_LUNALIB) {
			LunaRefitManager.addRefitButton(new CharacterSelectorRefitButton());
		}
	}
}