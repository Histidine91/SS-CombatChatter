package org.histidine.chatter.utils;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.characters.PersonAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import static org.histidine.chatter.combat.ChatterCombatPlugin.PERSISTENT_DATA_KEY;
import org.json.JSONArray;

public class GeneralUtils {
	
	// get rid of the LazyLib requirement
	public static <T> T getRandomListElement(List<T> list)
	{
		if (list.isEmpty())
			return null;
		WeightedRandomPicker<T> picker = new WeightedRandomPicker<>();
		picker.addAll(list);
		return picker.pick();
	}	
	
	public static List<String> JSONArrayToStringList(JSONArray jsonArray)
	{
		List<String> ret = new ArrayList<>();
		try
		{
			//return jsonArray.toString().substring(1, jsonArray.toString().length() - 1).replaceAll("\"","").split(",");
			for (int i=0; i<jsonArray.length(); i++)
			{
				ret.add( jsonArray.getString(i));
			}
		}
		catch(Exception e)
		{
			Global.getLogger(GeneralUtils.class).error(e);
		}
		return ret;
	}
	
	public static float getHullSizePoints(FleetMemberAPI member)
	{
		HullSize size = member.getHullSpec().getHullSize();
		switch (size)
		{
			case FIGHTER:
			case FRIGATE:
				return 1;
			case DESTROYER:
				return 2;
			case CRUISER:
				return 4;
			case CAPITAL_SHIP:
				return 8;
			default:
				return 1;
		}
	}
	
	public static Map<String, String> getSavedCharacters()
	{
		Map<String, Object> data = Global.getSector().getPersistentData();
		Object loaded = data.get(PERSISTENT_DATA_KEY);
		if (loaded == null)
		{
			Global.getLogger(GeneralUtils.class).info("No saved chatter characters found, creating table");
			Map<String, String> officers = new HashMap<>();
			data.put(PERSISTENT_DATA_KEY, officers);
			return officers;
		}
		
		Map<String, String> savedOfficers = (HashMap<String, String>) loaded;
		
		// reverse compatibility
		try {
			Iterator<Map.Entry<String, String>> iter = savedOfficers.entrySet().iterator();
			while (iter.hasNext())
			{
				Map.Entry<String, String> tmp = iter.next();
				String officerId = tmp.getKey();
				break;
			}
		} 
		catch (ClassCastException ccex) // table has old PersonAPI keys, port
		{
			Map<PersonAPI, String> savedOfficersOld = (HashMap<PersonAPI, String>) loaded;
			savedOfficers = new HashMap<>();
			
			Iterator<Map.Entry<PersonAPI, String>> iter = savedOfficersOld.entrySet().iterator();
			while (iter.hasNext())
			{
				Map.Entry<PersonAPI, String> tmp = iter.next();
				savedOfficers.put(tmp.getKey().getId(), tmp.getValue());
			}
			data.put(PERSISTENT_DATA_KEY, savedOfficers);
		}
		return savedOfficers;
	}
}
