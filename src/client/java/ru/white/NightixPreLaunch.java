package ru.white;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

public final class NightixPreLaunch implements PreLaunchEntrypoint {
    @Override
    public void onPreLaunch() {
        NightixLoader.loadClasses();
    }
}
