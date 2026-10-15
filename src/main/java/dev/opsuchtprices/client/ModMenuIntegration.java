package dev.opsuchtprices.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Mod Menu: "Einstellungen"-Knopf beim OP-Assistenten (nur aktiv, wenn Mod Menu installiert ist). */
public final class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> OpSuchtPricesClient.settingsScreen(parent);
    }
}
