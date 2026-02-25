package com.grib.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.server.MinecraftServer;

@Mixin(MinecraftServer.class)
public class ExampleMixin {

    @Inject(method = "<init>", at = @At("RETURN"))
    private void initAfterSuper(CallbackInfo ci) {
        // Этот код сработает после того как MinecraftServer полностью создан
        System.out.println("[SculkMODtreker] MinecraftServer готов!");
    }
}