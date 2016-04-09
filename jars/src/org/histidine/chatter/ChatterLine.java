package org.histidine.chatter;

public class ChatterLine {
	public String text;
	public String sound;

	public ChatterLine(String text)
	{
		this.text = text;
	}
	public ChatterLine(String text, String sound)
	{
		this.text = text;
		this.sound = sound;
	}
	
	public static enum MessageType {
		START, RETREAT, VICTORY,
		PURSUING, RUNNING, NEED_HELP, OUT_OF_MISSILES, ENGAGED,
		HULL_90, HULL_50, HULL_30, OVERLOAD, DEATH
	}
}
