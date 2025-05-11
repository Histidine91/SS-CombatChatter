package org.histidine.chatter;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import org.apache.log4j.Logger;
import org.histidine.chatter.utils.StringHelper;
import org.jetbrains.annotations.Nullable;

public class ChatterLine {
	
	public static Logger log = Global.getLogger(ChatterLine.class);
	
	@Nullable public String id;
	public String text;
	@Nullable public String sound;
	@Nullable public String replyToId;
	@Nullable public Float time;

	public ChatterLine(String text)
	{
		this.text = text;
	}
	public ChatterLine(String text, @Nullable String sound)
	{
		this.text = text;
		this.sound = sound;
	}
	public ChatterLine(@Nullable String id, String text, @Nullable String sound)
	{
		this.id = id;
		this.text = text;
		this.sound = sound;
	}

	public ChatterLine(@Nullable String id, String text, @Nullable String sound, @Nullable String replyToId) {
		this.id = id;
		this.text = text;
		this.sound = sound;
		this.replyToId = replyToId;
	}

	public String getSubstitutedLine(PersonAPI person, FleetMemberAPI member) {
		String str = text;
		try {
			if (person != null) {
				String last = person.getName().getLast();
				if (last == null || last.isEmpty()) last = person.getName().getFirst();
				str = StringHelper.substituteToken(str, "$officerName", person.getNameString());
				str = StringHelper.substituteToken(str, "$officerLastName", last);
				str = StringHelper.substituteToken(str, "$officerSurname", last);
				str = StringHelper.substituteToken(str, "$officerFirstName", person.getName().getFirst());
				str = StringHelper.substituteToken(str, "$officerGivenName", person.getName().getFirst());
				str = StringHelper.substituteToken(str, "$officerFaction", person.getFaction().getDisplayName());
				str = StringHelper.substituteToken(str, "$officerRank", person.getRank());
			}
			if (member != null) {
				str = StringHelper.substituteToken(str, "$shipNameNoPrefix", stripShipNamePrefix(member));
				str = StringHelper.substituteToken(str, "$shipName", member.getShipName());
				str = StringHelper.substituteToken(str, "$shipClass", member.getHullSpec().getHullName());
				str = StringHelper.substituteToken(str, "$shipSizeClass", StringHelper.getString(member.getHullSpec().getHullSize().toString().toLowerCase()));
				FactionAPI faction = Global.getSector().getFaction(ChatterDataManager.getFactionFromShip(member));
				if (faction != null) str = StringHelper.substituteToken(str, "$shipFaction", faction.getDisplayName());
			}
			
			PersonAPI player = Global.getSector().getPlayerPerson();
			if (player != null)
			{
				String last = player.getName().getLast();
				if (last == null || last.isEmpty()) last = player.getName().getFirst();
				str = StringHelper.substituteToken(str, "$playerName", player.getNameString());
				str = StringHelper.substituteToken(str, "$playerLastName", last);
				str = StringHelper.substituteToken(str, "$playerSurname", last);
				str = StringHelper.substituteToken(str, "$playerFirstName", player.getName().getFirst());
				str = StringHelper.substituteToken(str, "$playerGivenName", player.getName().getFirst());
				str = StringHelper.substituteToken(str, "$playerHonorific", Global.getSector().getCharacterData().getHonorific());

			}
			PersonAPI commander = member.getFleetCommander();
			if (commander != null)
			{
				str = StringHelper.substituteToken(str, "$commanderName", commander.getNameString());
				str = StringHelper.substituteToken(str, "$commanderLastName", commander.getName().getLast());
				str = StringHelper.substituteToken(str, "$commanderFirstName", commander.getName().getFirst());
				str = StringHelper.substituteToken(str, "$commanderRank", commander.getRank());
				str = StringHelper.substituteToken(str, "$commanderFaction", commander.getFaction().getDisplayName());
				str = StringHelper.substituteToken(str, "$commanderHonorific", getHonorific(commander));
			}
			else {
				log.warn("Missing commander for member " + member.getShipName() + ", " + member.getHullSpec().getNameWithDesignationWithDashClass());
			}

			
		} catch (Exception ex) {
			log.warn(String.format("Error substituting text in line '%s'", text), ex);
		}
		
		return str;
	}

	protected String getHonorific(PersonAPI person) {
		String key = "General";
		if (person.getGender() == FullName.Gender.FEMALE) key = "Female";
		else if (person.getGender() == FullName.Gender.MALE) key = "Male";
		return StringHelper.getString("chatter_general", "honorific" + key);
	}
	
	/**
	 * Gets the fleet member's name with the ship name prefix removed (as far as the method can guess, anyway).
	 * @param member
	 * @return
	 */
	public String stripShipNamePrefix(FleetMemberAPI member) {
		String shipName = member.getShipName();
		if (shipName == null) return "";
		FactionAPI faction = null;
		if (member.getFleetData() != null && member.getFleetData().getFleet() != null) {
			faction = member.getFleetData().getFleet().getFaction();
		}
		String[] array = shipName.split(" ");
		if (array.length < 2) return shipName;
		String maybePrefix = array[0];
		//Global.getLogger(this.getClass()).info(String.format("Extracted maybePrefix %s from ship name %s", maybePrefix, member.getShipName()));
		
		if (maybePrefix.length() > 5) return shipName;
		
		// compare to faction prefix if faction is known
		if (faction != null && !faction.isPlayerFaction()) {
			if (!maybePrefix.equals(faction.getShipNamePrefix())) {
				return shipName;
			}
		}
		// else, is it uppercase? if not, do nothing
		else {
			if (!maybePrefix.equals(maybePrefix.toLowerCase())) {
				return shipName;
			}
		}
		
		shipName = shipName.replace(maybePrefix, "");
		shipName = shipName.trim();
		
		return shipName;
	}
	
	public enum MessageType {
		START, START_BOSS, RETREAT, VICTORY, VICTORY_BOSS,
		PURSUING, RUNNING, NEED_HELP, OUT_OF_MISSILES, ENGAGED,
		HULL_90, HULL_50, HULL_30, OVERLOAD, DEATH, REPLY;

		public boolean isHullMessage() {
			return this == HULL_30 || this == HULL_50 || this == HULL_90;
		}
	}
}
