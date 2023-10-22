package org.histidine.chatter.utils;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import org.histidine.chatter.ChatterLine;
import org.histidine.chatter.combat.ChatterCombatPlugin;
import org.json.JSONArray;

public class GeneralUtils {
	
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
		if (jsonArray == null) return ret;
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
	
	public static boolean preShowChatMessage(ChatterCombatPlugin plugin, FleetMemberAPI member, ChatterLine line, String text, boolean floaty, boolean inMessageBox, Color textColor) {
		for (ChatterListener listener : plugin.getListeners()) {
			if (!listener.preShowChatMessage(member, line, text, floaty, floaty, textColor))
				return false;
		}
		return true;
	}
	
	public static void shownChatMessage(ChatterCombatPlugin plugin, FleetMemberAPI member, ChatterLine line, String text, boolean floaty, boolean inMessageBox, Color textColor) {
		for (ChatterListener listener : plugin.getListeners()) {
			listener.shownChatMessage(member, line, text, floaty, floaty, textColor);
		}
	}
}
