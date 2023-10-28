package org.histidine.chatter.combat;

import com.fs.starfarer.api.fleet.FleetMemberAPI;
import org.histidine.chatter.*;
import org.histidine.chatter.combat.ChatterCombatPlugin.ShipStateData;
import org.histidine.chatter.utils.ChatterListener;

import java.awt.*;
import java.util.List;
import java.util.Map;

public class ChatterCoreListener implements ChatterListener {

    public static final float BASE_DELAY_BETWEEN_REPLIES = 0.5f;   // maybe should be based on text length instead lol
    public static final float DELAY_PER_CHARACTER = 0.04f;

    @Override
    public boolean preShowChatMessage(FleetMemberAPI member, ChatterLine line, String text, boolean floaty, boolean inMessageBox, Color textColor) {
        return true;
    }

    @Override
    public void shownChatMessage(FleetMemberAPI member, ChatterLine line, String text, boolean floaty, boolean inMessageBox, Color textColor) {
        if (line == null) return;

        // look for people who want to reply to our message
        Map<FleetMemberAPI, ShipStateData> states = ChatterCombatPlugin.getInstance().getShipStates();
        ShipStateData state = states.get(member);

        for (FleetMemberAPI otherMember : ChatterCombatPlugin.getInstance().getShipStates().keySet()) {
            ShipStateData otherState = states.get(otherMember);
            if (otherState.isEnemy != state.isEnemy) continue;
            if (otherState.isPlayer && !ChatterConfig.selfChatter) continue;

            ChatterCharacter otherChar = ChatterDataManager.getCharacterData(otherState.characterId);
            if (otherChar == null) continue;

            List<ChatterLine> replies = otherChar.lines.get(ChatterLine.MessageType.REPLY);
            if (replies == null) continue;
            for (ChatterLine potentialReply : replies) {
                if (potentialReply.replyToId == null || !potentialReply.replyToId.equals(line.id)) continue;

                ChatterMessage msg = new ChatterMessage(otherMember, line, ChatterLine.MessageType.REPLY);
                msg.floaty = floaty;
                msg.inMessageBox = inMessageBox;
                msg.force = true;

                float delay = text.length() * DELAY_PER_CHARACTER;

                ChatterCombatPlugin.getInstance().queueMessage(msg, delay);
                return;
            }
        }
    }
}
