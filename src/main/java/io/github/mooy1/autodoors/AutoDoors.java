package io.github.mooy1.autodoors;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class AutoDoors extends JavaPlugin {

    public final int mcVersion = Integer.parseInt(Bukkit.getVersion().split("\\.")[1]);

    @Override
    public void onEnable() {
        if (mcVersion >= 13) {
            new DoorManager(this);
        } else {
            new DoorManagerLegacy(this);
        }
    }

}
