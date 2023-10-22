package org.histidine.chatter.utils;

import com.fs.starfarer.api.fleet.FleetMemberAPI;
import java.awt.Color;
import org.histidine.chatter.ChatterLine;

public interface ChatterListener {
	/**
	 * @param member
	 * @param line
	 * @param text
	 * @param floaty Whether the message wants to be shown as floaty text instead of top-left message field.
	 * @param inMessageBox Whether the message wants to be shown in the right-side message box. Will be false if {@code floaty} is false.
	 * @param textColor
	 * @return False to prevent the message from being shown.
	 */
	boolean preShowChatMessage(FleetMemberAPI member, ChatterLine line, String text, boolean floaty, boolean inMessageBox, Color textColor);
	void shownChatMessage(FleetMemberAPI member, ChatterLine line, String text, boolean floaty, boolean inMessageBox, Color textColor);
}
