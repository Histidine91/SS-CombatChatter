package org.histidine.chatter;

import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.Misc;
import org.histidine.chatter.combat.ChatterCombatPlugin;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public class ChatterMessage {
    public FleetMemberAPI member;
    @Nullable public String characterId;
    @Nullable public ChatterLine line;
    public String string;
    public Color color; // = Misc.getTextColor();
    @Nullable public Boolean floater;
    @Nullable public Boolean inMessageBox;
    public ChatterLine.MessageType type;
    public boolean force;
    public boolean printEvenIfDead;

    public ChatterMessage(FleetMemberAPI member, ChatterLine line, ChatterLine.MessageType type) {
        this.member = member;
        characterId = ChatterCombatPlugin.getInstance() != null ? ChatterCombatPlugin.getInstance().getCharacterForFleetMember(member) : null;
        this.line = line;
        string = "\"" + line.getSubstitutedLine(member.getCaptain(), member) + "\"";
        this.type = type;
    }

    public ChatterMessage(FleetMemberAPI member, String string, ChatterLine.MessageType type) {
        this.member = member;
        characterId = ChatterCombatPlugin.getInstance() != null ? ChatterCombatPlugin.getInstance().getCharacterForFleetMember(member) : null;
        this.string = string;
        this.type = type;
    }

    public static class QueuedChatterMessage {
        public ChatterMessage message;
        public float timer;

        public QueuedChatterMessage(ChatterMessage message, float timer) {
            this.message = message;
            this.timer = timer;
        }
    }
}
