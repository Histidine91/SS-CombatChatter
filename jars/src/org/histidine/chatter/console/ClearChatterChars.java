package org.histidine.chatter.console;

import com.fs.starfarer.api.characters.PersonAPI;
import java.util.List;
import org.histidine.chatter.ChatterDataManager;
import static org.histidine.chatter.ChatterDataManager.CHARACTER_MEMORY_KEY;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;

public class ClearChatterChars implements BaseCommand {

	@Override
	public CommandResult runCommand(String args, CommandContext context) {
		
		List<PersonAPI> officers = ChatterDataManager.getOfficers(true);
		
		for (PersonAPI officer: officers)
		{
			officer.getMemoryWithoutUpdate().unset(CHARACTER_MEMORY_KEY);
		}
		ChatterDataManager.resetUsedCharacters();
		
		Console.showMessage("Cleared chatter characters.");
		
		return CommandResult.SUCCESS;
	}
}
