package org.histidine.chatter.console;

import com.fs.starfarer.api.util.Pair;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.histidine.chatter.ChatterCharacter;
import org.histidine.chatter.ChatterDataManager;
import org.histidine.chatter.utils.StringHelper;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;

public class PrintChatterTags implements BaseCommand {
	
	@Override
	public CommandResult runCommand(String args, CommandContext context) {
		List<ChatterCharacter> chars = ChatterDataManager.CHARACTERS;
		Map<String, List<String>> tags = new HashMap<>();
		List<Pair<String, List<String>>> tagsSorted = new ArrayList<>();
		
		for (ChatterCharacter character : chars) {
			for (String tag : character.categoryTags) {
				if (!tags.containsKey(tag)) {
					tags.put(tag, new ArrayList<String>());
				}
				tags.get(tag).add(character.id);
			}
		}
		for (Map.Entry<String, List<String>> entry : tags.entrySet()) {
			tagsSorted.add(new Pair<>(entry.getKey(), entry.getValue()));
		}
		
		Collections.sort(tagsSorted, new Comparator<Pair<String, List<String>>>() 
		{
			@Override
			public int compare(Pair<String, List<String>> arg0, Pair<String, List<String>> arg1) {
				return arg0.one.compareTo(arg1.one);
			}
		});
		
		for (Pair<String, List<String>> entry : tagsSorted) {
			String characters = StringHelper.writeStringCollection(entry.two, false, true);
			Console.showMessage(String.format("%s: %s uses (%s)", entry.one, entry.two.size(), characters));
		}
		
		return CommandResult.SUCCESS;
	}
}
