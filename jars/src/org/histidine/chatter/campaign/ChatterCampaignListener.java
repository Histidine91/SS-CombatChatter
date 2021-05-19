package org.histidine.chatter.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CommDirectoryAPI;
import com.fs.starfarer.api.campaign.CommDirectoryEntryAPI;
import com.fs.starfarer.api.campaign.CommDirectoryEntryAPI.EntryType;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.MemFlags;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import org.apache.log4j.Logger;

public class ChatterCampaignListener extends BaseCampaignEventListener {
	
	public static Logger log = Global.getLogger(ChatterCampaignListener.class);
	
	public ChatterCampaignListener() {
		super(false);
	}

	@Override
	public void reportPlayerOpenedMarket(MarketAPI market) {
		CommDirectoryAPI board = market.getCommDirectory();
		for (CommDirectoryEntryAPI comm : board.getEntriesCopy()) {
			if (comm.getType() != EntryType.PERSON) continue;
			if (comm.getEntryData() instanceof PersonAPI) {
				PersonAPI contact = (PersonAPI)(comm.getEntryData());
				if (!contact.getPostId().equals(Ranks.POST_MERCENARY)) continue;
				MemoryAPI memory = contact.getMemoryWithoutUpdate();
				if (!memory.contains("$originalFaction"))
					memory.set("$originalFaction", market.getFactionId());
			}
		}
	}
	
	@Override
	public void reportFleetSpawned(CampaignFleetAPI fleet) {
		if (!Global.getSettings().getBoolean("chatter_renameBounties"))
			return;
		
		String type = fleet.getMemoryWithoutUpdate().getString(MemFlags.MEMORY_KEY_FLEET_TYPE);
		if (!FleetTypes.PERSON_BOUNTY_FLEET.equals(type))
			return;
		
		if (fleet.getCommander() == null)
			return;
		
		String name = fleet.getMemoryWithoutUpdate().getString("$chatter_introSplash_name");
		if (name == null) {
			fleet.getMemoryWithoutUpdate().set("$chatter_introSplash_name", fleet.getCommander().getNameString());
			fleet.getMemoryWithoutUpdate().set("$chatter_introSplash_maxPlayerStrength", fleet.getEffectiveStrength()/0.8f);
		}
	}
}
