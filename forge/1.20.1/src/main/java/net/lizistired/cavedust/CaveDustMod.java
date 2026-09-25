// SPDX-License-Identifier: MPL-2.0
package net.lizistired.cavedust;

import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(CaveDustMod.MOD_ID)
public final class CaveDustMod {
    public static final String MOD_ID = "cavedust";

    public CaveDustMod(FMLJavaModLoadingContext context) {
        context.registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (minecraft, parent) -> new CaveDustConfigScreen(parent)));
    }
}
