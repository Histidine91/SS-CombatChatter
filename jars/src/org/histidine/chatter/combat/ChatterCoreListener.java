package org.histidine.chatter.combat;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import org.apache.log4j.Logger;
import org.histidine.chatter.*;
import org.histidine.chatter.combat.ChatterCombatPlugin.ShipStateData;
import org.histidine.chatter.utils.ChatterListener;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ChatterCoreListener implements ChatterListener {

    public static final float BASE_DELAY_BETWEEN_REPLIES = 0.5f;   // maybe should be based on text length instead lol
    public static final float DELAY_PER_CHARACTER = 0.05f;

    public static Logger log = Global.getLogger(ChatterCoreListener.class);

    @Override
    public boolean preShowChatMessage(FleetMemberAPI member, ChatterLine line, String text, boolean floaty, boolean inMessageBox, Color textColor) {
        return true;
    }

    @Override
    public void shownChatMessage(FleetMemberAPI member, ChatterLine line, String text, boolean floaty, boolean inMessageBox, Color textColor) {
        try {
            //log.info(String.format("Received chat message from %s: %s, %s, %s, %s, %s", member.getShipName(), line, text, floaty, inMessageBox, textColor));

            if (line == null) return;

            // look for people who want to reply to our message
            ShipStateData state = ChatterCombatPlugin.getInstance().getShipStateData(member);
            if (state == null) return;

            CombatEngineAPI engine = Global.getCombatEngine();
            List<FleetMemberAPI> members = new ArrayList<>();
            members.addAll(engine.getFleetManager(FleetSide.PLAYER).getDeployedCopy());
            members.addAll(engine.getFleetManager(FleetSide.ENEMY).getDeployedCopy());

            WeightedRandomPicker<ChatterMessage> picker = new WeightedRandomPicker<>();

            for (FleetMemberAPI otherMember : members) {
                if (otherMember == member) continue;
                //log.info("Checking " + otherMember.getShipName() + " for reply");

                ShipStateData otherState = ChatterCombatPlugin.getInstance().getShipStateData(otherMember);
                if (otherState == null) continue;
                if (otherState.isEnemy != state.isEnemy) {
                    //log.info("  Is enemy, skipping");
                    continue;
                }
                if (otherState.isPlayer && !ChatterConfig.selfChatter) {
                    //log.info("  No self-chatter");
                    continue;
                }

                ChatterCharacter otherChar = ChatterDataManager.getCharacterData(otherState.characterId);
                if (otherChar == null) {
                    //log.info("  Character not found");
                    continue;
                }

                List<ChatterLine> replies = otherChar.lines.get(ChatterLine.MessageType.REPLY);
                if (replies == null) {
                    //log.info("  No reply lines found");
                    continue;
                }
                for (ChatterLine potentialReply : replies) {
                    if (potentialReply.replyToId == null || !potentialReply.replyToId.equals(line.id)) {
                        continue;
                    }

                    ChatterMessage msg = new ChatterMessage(otherMember, potentialReply, ChatterLine.MessageType.REPLY);
                    msg.floater = floaty;
                    msg.inMessageBox = inMessageBox;
                    msg.force = true;

                    picker.add(msg, otherChar.talkativeness);
                }
            }

            ChatterMessage msg = picker.pick();
            if (msg == null) return;

            float delay = line.time != null ? line.time + BASE_DELAY_BETWEEN_REPLIES :
                    text.length() * DELAY_PER_CHARACTER + BASE_DELAY_BETWEEN_REPLIES;

            ChatterCombatPlugin.getInstance().queueMessage(msg, delay);
        } catch (Throwable t) {
            log.error("Error processing reply", t);
        }
    }
}
