package org.histidine.chatter.console;

import com.fs.starfarer.api.characters.PersonAPI;
import java.util.List;
import org.histidine.chatter.ChatterCharacter;
import org.histidine.chatter.ChatterDataManager;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;

public class PrintChatterChars implements BaseCommand {

	@Override
	public CommandResult runCommand(String args, CommandContext context) {
		
		String[] tmp = args.split(" ");
		if (tmp.length == 0 || tmp[0].isEmpty() || tmp[0].equalsIgnoreCase("current"))
		{
			//Map<String, String> savedOfficers = GeneralUtils.getSavedCharacters();

			List<PersonAPI> officers = ChatterDataManager.getOfficers(true);

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
		else if (tmp[0].equalsIgnoreCase("all"))
		{
			Console.showMessage("Available chatter characters:");
			for (ChatterCharacter character : ChatterDataManager.CHARACTERS)
			{
				Console.showMessage("  " + character.name + " (" + character.id + ")");
			}
		}
		else
			return CommandResult.BAD_SYNTAX;
		
		return CommandResult.SUCCESS;
	}
}
