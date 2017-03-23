package org.histidine.chatter.console;

import com.fs.starfarer.api.characters.PersonAPI;
import java.util.List;
import org.histidine.chatter.ChatterDataManager;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.CommonStrings;
import org.lazywizard.console.Console;

public class PrintChatterChars implements BaseCommand {

	@Override
	public CommandResult runCommand(String args, CommandContext context) {
		if (context != CommandContext.CAMPAIGN_MAP) {
			Console.showMessage(CommonStrings.ERROR_CAMPAIGN_ONLY);
			return CommandResult.WRONG_CONTEXT;
		}
		
		//Map<String, String> savedOfficers = GeneralUtils.getSavedCharacters();
		
		List<PersonAPI> officers = ChatterDataManager.getOfficers(true);
		
		Console.showMessage("Current officers:");
		for (PersonAPI officer: officers)
		{
			String officerName = officer.getName().getFullName();
			String character = ChatterDataManager.getCharacterFromMemory(officer);
			
			Console.showMessage(officerName + ": " + character);
		}
		
		return CommandResult.SUCCESS;
	}
}
