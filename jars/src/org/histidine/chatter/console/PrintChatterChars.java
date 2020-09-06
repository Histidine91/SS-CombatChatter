package org.histidine.chatter.console;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import java.util.List;
import java.util.Map;
import org.histidine.chatter.ChatterCharacter;
import org.histidine.chatter.ChatterDataManager;
import org.histidine.chatter.combat.ChatterCombatPlugin;
import org.histidine.chatter.combat.ChatterCombatPlugin.ShipStateData;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;

public class PrintChatterChars implements BaseCommand {

	@Override
	public CommandResult runCommand(String args, CommandContext context) {
		
		String[] tmp = args.split(" ");
		String arg = null;
		if (tmp.length > 0 && !tmp[0].isEmpty())
			arg = tmp[0];
		
		if (arg == null || arg.equalsIgnoreCase("current"))
		{
			//Map<String, String> savedOfficers = GeneralUtils.getSavedCharacters();
			List<PersonAPI> officers;
			
			if (context == CommandContext.COMBAT_MISSION) {
				return runCommand("battle", context);
			}
			
			else officers = ChatterDataManager.getOfficers(true);

			Console.showMessage("Current officers:");
			for (PersonAPI officer: officers)
			{
				String officerName = officer.getName().getFullName();
				String characterId = ChatterDataManager.getCharacterFromMemory(officer);
				ChatterCharacter character = ChatterDataManager.getCharacterData(characterId);
				if (character != null)
					Console.showMessage("  " + officerName + ": " + character.name + " (" + character.id + ")");
			}
		}
		else if (arg.equalsIgnoreCase("all"))
		{
			Console.showMessage("Available chatter characters:");
			for (ChatterCharacter character : ChatterDataManager.CHARACTERS)
			{
				Console.showMessage("  " + character.name + " (" + character.id + ")");
			}
		}
		else if (arg.equals("battle")) 
		{
			if (Global.getCombatEngine() == null || ChatterCombatPlugin.getInstance() == null) 
			{
				Console.showMessage("No battle is currently running!");
				return CommandResult.WRONG_CONTEXT;
			}
			Console.showMessage("Printing characters for ships in current battle:");
			ChatterCombatPlugin plugin = ChatterCombatPlugin.getInstance();
			Map<FleetMemberAPI, ShipStateData> states = plugin.getShipStates();
			for (Map.Entry<FleetMemberAPI, ShipStateData> entry : states.entrySet()) 
			{
				String name = plugin.getShipName(entry.getKey(), true);
				String character = entry.getValue().characterId;
				Console.showMessage("  " + name + ": " + character);
			}
		}
		else
			return CommandResult.BAD_SYNTAX;
		
		return CommandResult.SUCCESS;
	}
}
