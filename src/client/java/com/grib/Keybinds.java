package com.grib.client;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;


public class Keybinds {

    public static KeyBinding TOGGLE;
    public static KeyBinding OPEN_MENU;

    public static void register() {
        Identifier categoryId = Identifier.of("sculk", "controls");
        
        KeyBinding.Category sculkCategory =
                KeyBinding.Category.create(categoryId);

        TOGGLE = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.sculk.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                sculkCategory
        ));

        OPEN_MENU = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.sculk.open_menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_P,
                sculkCategory
        ));
    }
}