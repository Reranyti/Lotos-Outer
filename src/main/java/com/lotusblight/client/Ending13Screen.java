package com.lotusblight.client;

import com.lotusblight.cinema.CutsceneScreen;
import net.minecraft.client.gui.screens.Screen;

/**
 * The thirteenth ending, started by clicking its circle in the endings menu. Everything about playing it (the warning page, the choice of
 * the graphics card or the processor, the music, skipping) is the engine's {@link CutsceneScreen}; this only says what is its own.
 */
public final class Ending13Screen extends CutsceneScreen {
    public Ending13Screen(Screen parent) {
        super(parent, new Info("ending13_music", new String[]{
                "This scene shows self-harm and violent imagery. It lasts about three minutes.",
                "В этой сцене есть самоповреждение и жестокость. Длится около трёх минут.",
        }, Ending13Scene.LENGTH), skin -> {
            Ending13Scene scene = skin == null ? new Ending13Scene() : new Ending13Scene(skin);
            scene.setKicks(Ending13Scene.loadKicks());
            return scene;
        });
    }
}
