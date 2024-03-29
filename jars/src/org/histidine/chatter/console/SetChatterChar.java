package org.histidine.chatter.console;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import java.util.ArrayList;
import java.util.List;
import org.histidine.chatter.ChatterDataManager;
import org.histidine.chatter.combat.ChatterCombatPlugin;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommandUtils;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;

public class SetChatterChar implements BaseCommand {

	@Override
	public CommandResult runCommand(String args, CommandContext context) {
		if (context == CommandContext.COMBAT_MISSION) {
			Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
			return CommandResult.WRONG_CONTEXT;
		}
		
		if (args.isEmpty())
		{
			return CommandResult.BAD_SYNTAX;
		}

		String[] tmp = args.split(" ");

		if (tmp.length < 2)
		{
			return CommandResult.BAD_SYNTAX;
		}
		
		String charID = tmp[tmp.length - 1];
		StringBuilder builder = new StringBuilder();
		for(int i=0; i<tmp.length-1; i++)
			builder.append(tmp[i]);		
		String nameSearch = builder.toString();
		PersonAPI selectedOfficer = null;
		
		
		if (!ChatterDataManager.CHARACTERS_MAP.containsKey(charID))
		{
			String bestChar = CommandUtils.findBestStringMatch(charID, ChatterDataManager.CHARACTERS_MAP.keySet());
			if (bestChar == null) {
				Console.showMessage("Character not found for ID: " + charID);
				return CommandResult.ERROR;
			}
			charID = bestChar;
		}
		
		if (nameSearch.toLowerCase().equalsIgnoreCase("self") || nameSearch.toLowerCase().equalsIgnoreCase("player"))
		{
			selectedOfficer = Global.getSector().getPlayerPerson();
		}
		else
		{
			List<String> officerFullNames = new ArrayList<>();
			List<String> officerLastNames = new ArrayList<>();
			List<String> officerFirstNames = new ArrayList<>();
			List<PersonAPI> officersOrdered = ChatterDataManager.getOfficers(true);
			
			for (PersonAPI person : officersOrdered)
			{
				FullName name = person.getName();
				officerFullNames.add(name.getFullName());
				officerLastNames.add(name.getLast());
				officerFirstNames.add(name.getFirst());
			}

			String bestFullName = CommandUtils.findBestStringMatch(nameSearch, officerFullNames);
			if (bestFullName != null) {
				selectedOfficer = officersOrdered.get(getStringIndexInList(bestFullName, officerFullNames));
			}
			else {
				String bestLastName = CommandUtils.findBestStringMatch(nameSearch, officerLastNames);
				if (bestLastName != null) {
					selectedOfficer = officersOrdered.get(getStringIndexInList(bestLastName, officerLastNames));
				}
				else {
					String bestFirstName = CommandUtils.findBestStringMatch(nameSearch, officerFirstNames);
					if (bestFirstName != null) {
						selectedOfficer = officersOrdered.get(getStringIndexInList(bestFirstName, officerFirstNames));
					}
				}
			}
		}
		if (selectedOfficer == null)
		{
			Console.showMessage("Unable to find officer with name " + nameSearch);
			return CommandResult.ERROR;
		}
		
		ChatterDataManager.saveCharacter(selectedOfficer, charID);
		ChatterCombatPlugin plugin = ChatterCombatPlugin.getInstance();
		if (plugin != null) {
			plugin.setCharacterForOfficer(selectedOfficer, charID);
		}
		
		Console.showMessage("Assigning character " + charID + " to officer " + selectedOfficer.getName().getFullName());
		return CommandResult.SUCCESS;
	}
	
	protected int getStringIndexInList(String str, List<String> list)
	{
		for (int i=0; i < list.size(); i++)
		{
			Global.getLogger(this.getClass()).info(str + ", " + list.get(i));
			String entry = list.get(i);
			if (entry.equals(str)) return i;
		}
		return -1;
	}
}
