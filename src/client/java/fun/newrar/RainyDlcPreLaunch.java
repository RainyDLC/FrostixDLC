package fun.newrar;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

public final class RainyDlcPreLaunch implements PreLaunchEntrypoint {
    @Override
    public void onPreLaunch() {
        RainyDlcLoader.loadClasses();
    }
}

