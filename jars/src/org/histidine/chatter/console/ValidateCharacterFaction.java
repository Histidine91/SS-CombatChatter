package org.histidine.chatter.console;

import java.util.Map;
import java.util.Set;
import static org.histidine.chatter.ChatterDataManager.CHARACTER_FACTIONS;
import static org.histidine.chatter.ChatterDataManager.FACTION_TAGS;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;

public class ValidateCharacterFaction implements BaseCommand {

	@Override
	public CommandResult runCommand(String args, CommandContext context) {
		if (args.isEmpty())
		{
			return CommandResult.BAD_SYNTAX;
		}

		String[] tmp = args.split(" ");

		if (tmp.length < 2)
		{
			return CommandResult.BAD_SYNTAX;
		}
		
		String charID = tmp[0];
		String factionID = tmp[1];
		
		String result = isCharacterAllowedForFaction(charID, factionID);
		Console.showMessage(result);
		return CommandResult.SUCCESS;
	}
	
	protected static String isCharacterAllowedForFaction(String charId, String factionId)
	{
		if (!CHARACTER_FACTIONS.containsKey(charId)) {
			return "Character faction not found";
		}
		
		Map<String, Integer> allowedFactionsOrGroups = CHARACTER_FACTIONS.get(charId);
		int compatibility = 0;
		if (allowedFactionsOrGroups.containsKey(charId))
			compatibility = allowedFactionsOrGroups.get(charId);
		
		if (compatibility == 1) return "Compatible with faction";
		else if (compatibility == -1) return "Not compatible with faction";
		
		Set<String> tags = FACTION_TAGS.get(factionId);
		if (tags == null) return "No faction tags";
		for (String tag: tags) {
			if (!allowedFactionsOrGroups.containsKey(tag)) continue;
			int groupCompat = allowedFactionsOrGroups.get(tag);
			if (groupCompat == 1) return "Faction tag compatible with character: " + tag;
			else if (groupCompat == -1) return "Faction tag incompatible with character: " + tag;
			//else return false;
		}
		
		return "No data available";
	}
}
