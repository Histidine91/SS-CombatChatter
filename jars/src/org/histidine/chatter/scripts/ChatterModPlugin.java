package org.histidine.chatter.scripts;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import org.histidine.chatter.campaign.SetMercFactionScript;

public class ChatterModPlugin extends BaseModPlugin
{
	@Override
	public void onGameLoad(boolean newGame) {
		Global.getSector().addTransientListener(new SetMercFactionScript());
	}
}