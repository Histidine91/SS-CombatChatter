package org.histidine.chatter.campaign;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import lunalib.lunaRefit.BaseRefitButton;
import org.histidine.chatter.utils.StringHelper;

public class CharacterSelectorRefitButton extends BaseRefitButton {
    @Override
    public String getButtonName(FleetMemberAPI member, ShipVariantAPI variant) {
        return StringHelper.getString("chatter_general", "refitButton");
    }

    @Override
    public String getIconName(FleetMemberAPI member, ShipVariantAPI variant) {
        return member.getCaptain().getPortraitSprite();
    }

    @Override
    public boolean shouldShow(FleetMemberAPI member, ShipVariantAPI variant, MarketAPI market) {
        return member.getCaptain() != null && !member.getCaptain().isDefault();
    }

    @Override
    public boolean hasPanel(FleetMemberAPI member, ShipVariantAPI variant, MarketAPI market) {
        return true;
    }

    @Override
    public void initPanel(CustomPanelAPI backgroundPanel, FleetMemberAPI member, ShipVariantAPI variant, MarketAPI market) {
        new CharacterSelectorPanel(member.getCaptain()).addElements(backgroundPanel);
    }
}
