package org.histidine.chatter.utils;

import com.fs.starfarer.api.Global;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.lazywizard.lazylib.MathUtils;

public class GeneralUtils {
	
	public static <T> T getRandomListElement(List<T> list)
	{
		if (list.isEmpty())
			return null;
		int randomIndex = MathUtils.getRandomNumberInRange(0, list.size() - 1);
		return list.get(randomIndex);
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
}
