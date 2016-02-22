package org.histidine.chatter.console;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.OfficerDataAPI;
import com.fs.starfarer.api.characters.PersonAPI;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import static org.histidine.chatter.combat.ChatterCombatPlugin.PERSISTENT_DATA_KEY;
import org.histidine.chatter.utils.GeneralUtils;
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
		
        Map<String, String> savedOfficers = GeneralUtils.getSavedCharacters();
		
		Map<String, PersonAPI> officersById = new HashMap<>();
		for (OfficerDataAPI officer : Global.getSector().getPlayerFleet().getFleetData().getOfficersCopy())
		{
			officersById.put(officer.getPerson().getId(), officer.getPerson());
		}
		PersonAPI playerPerson = Global.getSector().getPlayerPerson();
		officersById.put(playerPerson.getId(), playerPerson);
		
		Console.showMessage("Saved officers:");
		Iterator<Map.Entry<String, String>> iter = savedOfficers.entrySet().iterator();
		while (iter.hasNext())
		{
			Map.Entry<String, String> tmp = iter.next();
			PersonAPI officer = officersById.get(tmp.getKey());
			String officerName = "<unknown officer>";
			if (officer != null) officerName = officer.getName().getFullName();
			Console.showMessage(officerName + " (" + tmp.getKey() + "): " + tmp.getValue());
		}
		/*
		Console.showMessage("Current officers:");
		for (OfficerDataAPI officer : Global.getSector().getPlayerFleet().getFleetData().getOfficersCopy())
		{
			Console.showMessage(officer.getPerson().getName().getFullName() + " (" + officer.getPerson() + ")");
		}
		*/
        
        return CommandResult.SUCCESS;
    }
}
