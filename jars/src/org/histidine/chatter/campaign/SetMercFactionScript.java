package org.histidine.chatter.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.CommDirectoryAPI;
import com.fs.starfarer.api.campaign.CommDirectoryEntryAPI;
import com.fs.starfarer.api.campaign.CommDirectoryEntryAPI.EntryType;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.rules.MemoryAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import org.apache.log4j.Logger;

public class SetMercFactionScript extends BaseCampaignEventListener {

	public static String MISSION_FACTION = "hegemony";
	
	public static Logger log = Global.getLogger(SetMercFactionScript.class);
	
	public SetMercFactionScript() {
		super(false);
	}

	@Override
	public void reportPlayerOpenedMarket(MarketAPI market) {
		CommDirectoryAPI board = market.getCommDirectory();
		for (CommDirectoryEntryAPI comm : board.getEntriesCopy()) {
			if (comm.getType() != EntryType.PERSON) continue;
			log.info("lalala " + comm.getEntryData().getClass().getName());
			if (comm.getEntryData() instanceof PersonAPI) {
				PersonAPI contact = (PersonAPI)(comm.getEntryData());
				if (!contact.getPostId().equals(Ranks.POST_MERCENARY)) continue;
				MemoryAPI memory = contact.getMemoryWithoutUpdate();
				if (!memory.contains("$originalFaction"))
					memory.set("$originalFaction", market.getFactionId());
			}
		}
	}
}
