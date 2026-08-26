package ru.white;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/**
 * Ранняя точка входа Fabric: загружает и внедряет все зашифрованные классы ядра
 * в KnotClassLoader ещё до инициализации миксинов и старта клиента.
 */
public final class NightixPreLaunch implements PreLaunchEntrypoint {
    @Override
    public void onPreLaunch() {
        NightixLoader.loadClasses();
    }
}
