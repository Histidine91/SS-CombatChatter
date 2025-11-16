package org.histidine.chatter.campaign

import com.fs.starfarer.api.characters.PersonAPI
import com.fs.starfarer.api.ui.Alignment
import com.fs.starfarer.api.ui.CustomPanelAPI
import com.fs.starfarer.api.ui.TooltipMakerAPI
import com.fs.starfarer.api.util.Misc
import lunalib.lunaExtensions.addLunaElement
import lunalib.lunaExtensions.addLunaTextfield
import me.xdrop.fuzzywuzzy.FuzzySearch
import org.histidine.chatter.ChatterDataManager

// Adapted from CombatChatterSelector in Random Assortment of Things (RAT)
class CharacterSelectorPanel(var person: PersonAPI) {

    var basePanel: CustomPanelAPI? = null
    var panel: CustomPanelAPI? = null

    var width = 0f
    var height = 0f

    var searchText = ""

    var searchElement: TooltipMakerAPI? = null
    var scroller = 0f

    fun addElements(panel: CustomPanelAPI?) {

        basePanel = panel

        width = panel!!.position.width
        height = panel.position.height

        searchElement = basePanel!!.createUIElement(width, 75f, false)
        basePanel!!.addUIElement(searchElement)
        searchElement!!.position.inTL(0f, 0f)

        searchElement!!.addSectionHeading("Search", Alignment.MID, 0f)
        searchElement!!.addSpacer(3f)

        var textfield = searchElement!!.addLunaTextfield(searchText, false, width - 10, 30f)
        textfield.enableTransparency = true
        textfield.advance {
            if (searchText != textfield.getText()) {
                searchText = textfield.getText()
                scroller = 0f
                refresh()
            }
        }

        /*
        searchElement!!.addTooltip(textfield.elementPanel, TooltipMakerAPI.TooltipLocation.BELOW, 400f) {
            it.addPara("Searches for name or id, does not have to perfectly match.", 0f)
        }
         */

        searchElement!!.addSpacer(3f)
        searchElement!!.addSectionHeading("Characters", Alignment.MID, 0f)



        refresh()
    }

    fun refresh() {

        if (panel != null) {
            basePanel!!.removeComponent(panel)
        }

        panel = basePanel!!.createCustomPanel(width + 5, height - 75f, null)
        basePanel!!.addComponent(panel)
        panel!!.position.inTL(0f, 75f)
        var element = panel!!.createUIElement(width + 5, height - 75f, true)

        element.addSpacer(3f)

        var currentCharacter = ChatterDataManager.getCharacterFromMemory(person)?.let { ChatterDataManager.getCharacterData(it) }
        var characters = ChatterDataManager.CHARACTERS
        for (character in characters) {

            if (character.chance <= 0f && character.categoryTags.none { it == "comradery" }) {
                continue
            }

            var ratio = FuzzySearch.extractOne(searchText, listOf(character.name, character.id))
            if (searchText != "" && ratio.score <= 55) continue

            element.addLunaElement(width - 10, 30f).apply {
                enableTransparency = true
                backgroundAlpha = 0.5f
                if (currentCharacter == character) backgroundAlpha = 1f
                borderAlpha = 0.5f
                addText(character.name, baseColor = Misc.getBasePlayerColor())
                centerText()

                onClick {
                    playClickSound()
                    ChatterDataManager.saveCharacter(person, character.id)
                    scroller = element.externalScroller.yOffset
                    refresh()
                }

                onHoverEnter {
                    playScrollSound()
                    borderAlpha = 1f
                }
                onHoverExit {
                    borderAlpha = 0.5f
                }
            }

            element.addSpacer(5f)
        }



        panel!!.addUIElement(element)
        element.externalScroller.yOffset = scroller
        //  element.externalScroller.yOffset = scroller
    }

}