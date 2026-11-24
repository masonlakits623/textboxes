package com.textboxes.gui;

import net.minecraft.client.gui.components.EditBox;

import java.util.function.Consumer;
import java.util.function.Predicate;

/** EditBox lost setFilter in 26.x: reject bad input from the responder instead. */
final class Fields {
    private Fields() {}

    static void respond(EditBox f, Predicate<String> ok, Consumer<String> responder) {
        String[] last = {f.getValue()};
        f.setResponder(s -> {
            if (!ok.test(s)) {
                f.setValue(last[0]);
                return;
            }
            last[0] = s;
            responder.accept(s);
        });
    }
}
