package org.histidine.chatter;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.mission.FleetSide;
import org.apache.log4j.Logger;
import org.histidine.chatter.combat.ChatterCombatPlugin;
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
			ChatterCombatPlugin combatPlugin = ChatterCombatPlugin.getInstance();
			FleetSide side = null;
			if (combatPlugin != null) {
				side = combatPlugin.getSideForMember(member);
			}

			if (person != null) {
				String first = person.getName().getFirst();
				String last = person.getName().getLast();
				if (last == null || last.isEmpty()) last = first;
				str = StringHelper.substituteToken(str, "$officerName", person.getNameString());
				str = StringHelper.substituteToken(str, "$officerLastName", last);
				str = StringHelper.substituteToken(str, "$officerSurname", last);
				str = StringHelper.substituteToken(str, "$officerFirstName", first);
				str = StringHelper.substituteToken(str, "$officerGivenName", first);
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
				String first = player.getName().getFirst();
				String last = player.getName().getLast();
				if (last == null || last.isEmpty()) last = first;
				str = StringHelper.substituteToken(str, "$playerName", player.getNameString());
				str = StringHelper.substituteToken(str, "$playerLastName", last);
				str = StringHelper.substituteToken(str, "$playerSurname", last);
				str = StringHelper.substituteToken(str, "$playerFirstName", first);
				str = StringHelper.substituteToken(str, "$playerGivenName", first);
				String honorific = null;
				if (Global.getSector().getCharacterData() != null) {
					honorific = Global.getSector().getCharacterData().getHonorific();
				}
				if (honorific == null) honorific = getHonorific(player);
				str = StringHelper.substituteToken(str, "$playerHonorific", honorific);
			}
			PersonAPI commander = member.getFleetCommander();
			if (commander == null && side != null) {
				commander = combatPlugin.getCommanderForSide(side);
			}
			if (commander != null)
			{
				String first = commander.getName().getFirst();
				String last = commander.getName().getLast();
				if (last == null || last.isEmpty()) last = first;
				str = StringHelper.substituteToken(str, "$commanderName", commander.getNameString());
				str = StringHelper.substituteToken(str, "$commanderLastName", last);
				str = StringHelper.substituteToken(str, "$commanderSurname", last);
				str = StringHelper.substituteToken(str, "$commanderFirstName", first);
				str = StringHelper.substituteToken(str, "$commanderGivenName", first);
				str = StringHelper.substituteToken(str, "$commanderRank", commander.getRank());
				str = StringHelper.substituteToken(str, "$commanderFaction", commander.getFaction().getDisplayName());
				str = StringHelper.substituteToken(str, "$commanderHonorific", getHonorific(commander));
			}
			else {
				//log.warn("Missing commander for member " + member.getShipName() + ", " + member.getHullSpec().getNameWithDesignationWithDashClass());
			}

			// enemy flagship substitution, enemy faction substitution, commission faction substitution
			String enemyFaction = null;
			PersonAPI enemyCommander = null;
			FleetMemberAPI enemyFlagship = null;
			if (combatPlugin != null) {
				if (side != null) {
					FleetSide enemy = ChatterCombatPlugin.getEnemySide(side);
					enemyCommander = combatPlugin.getCommanderForSide(enemy);
					enemyFlagship = combatPlugin.getFlagshipForSide(enemy);
					if (enemyFlagship != null) {
						enemyFaction = ChatterDataManager.getFactionFromShip(enemyFlagship);
					}
				}
			}

			if (enemyCommander != null)
			{
				String first = enemyCommander.getName().getFirst();
				String last = enemyCommander.getName().getLast();
				if (last == null || last.isEmpty()) last = first;
				str = StringHelper.substituteToken(str, "$enemyCommanderName", commander.getNameString());
				str = StringHelper.substituteToken(str, "$enemyCommanderLastName", last);
				str = StringHelper.substituteToken(str, "$enemyCommanderSurname", last);
				str = StringHelper.substituteToken(str, "$enemyCommanderFirstName", first);
				str = StringHelper.substituteToken(str, "$enemyCommanderGivenName", first);
				str = StringHelper.substituteToken(str, "$enemyCommanderRank", enemyCommander.getRank());
				str = StringHelper.substituteToken(str, "$enemyCommanderFaction", enemyCommander.getFaction().getDisplayName());
				str = StringHelper.substituteToken(str, "$enemyCommanderHonorific", getHonorific(enemyCommander));
			}
			if (enemyFlagship != null) {
				str = StringHelper.substituteToken(str, "$enemyFlagshipNameNoPrefix", stripShipNamePrefix(enemyFlagship));
				str = StringHelper.substituteToken(str, "$enemyFlagshipName", enemyFlagship.getShipName());
				str = StringHelper.substituteToken(str, "$enemyFlagshipClass", enemyFlagship.getHullSpec().getHullName());
				str = StringHelper.substituteToken(str, "$enemyFlagshipSizeClass", StringHelper.getString(enemyFlagship.getHullSpec().getHullSize().toString().toLowerCase()));
			}
			if (enemyFaction != null) {
				FactionAPI faction = Global.getSector().getFaction(enemyFaction);
				str = StringHelper.substituteToken(str, "$enemyFaction", faction.getDisplayName());
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
