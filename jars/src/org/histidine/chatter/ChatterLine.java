package org.histidine.chatter;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import org.apache.log4j.Logger;
import org.histidine.chatter.utils.StringHelper;

public class ChatterLine {
	
	public static Logger log = Global.getLogger(ChatterLine.class);
	
	public String text;
	public String sound;

	public ChatterLine(String text)
	{
		this.text = text;
	}
	public ChatterLine(String text, String sound)
	{
		this.text = text;
		this.sound = sound;
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
			}
			if (member != null) {
				str = StringHelper.substituteToken(str, "$shipNameNoPrefix", stripShipNamePrefix(member));
				str = StringHelper.substituteToken(str, "$shipName", member.getShipName());
				str = StringHelper.substituteToken(str, "$shipClass", member.getHullSpec().getHullName());
				str = StringHelper.substituteToken(str, "$shipSizeClass", StringHelper.getString(member.getHullSpec().getHullSize().toString().toLowerCase()));
			}
			
			PersonAPI player = Global.getSector().getPlayerPerson();
			{
				String last = player.getName().getLast();
				if (last == null || last.isEmpty()) last = player.getName().getFirst();
				str = StringHelper.substituteToken(str, "$playerName", player.getNameString());
				str = StringHelper.substituteToken(str, "$playerLastName", last);
				str = StringHelper.substituteToken(str, "$playerSurname", last);
				str = StringHelper.substituteToken(str, "$playerFirstName", player.getName().getFirst());
				str = StringHelper.substituteToken(str, "$playerGivenName", player.getName().getFirst());
			}
			
		} catch (Exception ex) {
			log.error(String.format("Error substituting text in line '%s'", text), ex);
		}
		
		return str;
	}
	
	/**
	 * Gets the fleet member's name with the ship name prefix removed (as far as the method can guess, anyway).
	 * @param member
	 * @return
	 */
	public String stripShipNamePrefix(FleetMemberAPI member) {
		String shipName = member.getShipName();
		FactionAPI faction = null;
		if (member.getFleetData() != null && member.getFleetData().getFleet() != null) {
			faction = member.getFleetData().getFleet().getFaction();
		}
		String[] array = shipName.split(" ");
		if (array.length < 2) return shipName;
		String maybePrefix = array[0];
		Global.getLogger(this.getClass()).info(String.format("Extracted maybePrefix %s from ship name %s", maybePrefix, member.getShipName()));
		
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
	
	public static enum MessageType {
		START, START_BOSS, RETREAT, VICTORY,
		PURSUING, RUNNING, NEED_HELP, OUT_OF_MISSILES, ENGAGED,
		HULL_90, HULL_50, HULL_30, OVERLOAD, DEATH
	}
}
