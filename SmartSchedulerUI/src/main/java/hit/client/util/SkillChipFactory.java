package hit.client.util;

import javafx.scene.control.Label;

/** Factory for the little-rounded skill pills in every TaskCard. */
public final class SkillChipFactory {
    private SkillChipFactory() {}

    public static Label create(String text) {
        Label chip = new Label(text);
        chip.getStyleClass().add("skill-tag");
        return chip;
    }
}