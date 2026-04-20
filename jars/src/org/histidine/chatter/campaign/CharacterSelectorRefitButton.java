package org.histidine.chatter.campaign;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
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
        return member.getCaptain() != null; // && !member.getCaptain().isDefault();
    }

    @Override
    public boolean hasPanel(FleetMemberAPI member, ShipVariantAPI variant, MarketAPI market) {
        return true;
    }

    @Override
    public boolean hasTooltip(FleetMemberAPI member, ShipVariantAPI variant, MarketAPI market) {
        return (member.getCaptain() != null && member.getCaptain().isDefault());
    }

    @Override
    public void addTooltip(TooltipMakerAPI tooltip, FleetMemberAPI member, ShipVariantAPI variant, MarketAPI market) {
        if (member.getCaptain() != null && member.getCaptain().isDefault()) {
            tooltip.addPara(StringHelper.getString("chatter_general", "refitButtonTooltip_defaultCaptain"), 3);
        }
    }

    @Override
    public void initPanel(CustomPanelAPI backgroundPanel, FleetMemberAPI member, ShipVariantAPI variant, MarketAPI market) {
        //Global.getLogger(this.getClass()).info("Captain is " + member.getCaptain().getId());
        new CharacterSelectorPanel(member.getCaptain()).addElements(backgroundPanel);
    }
}
