package io.github.mooy1.autodoors;

import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.material.Door;
import org.bukkit.material.MaterialData;

public class DoorManagerLegacy extends DoorManager {

    private final Material ironDoorBlock = Material.valueOf("IRON_DOOR_BLOCK");
    private final Sound ironDoorOpen;
    private final Sound ironDoorClose;
    private final Sound woodenDoorOpen;
    private final Sound woodenDoorClose;

    public DoorManagerLegacy(AutoDoors plugin) {
        super(plugin);

        if (plugin.mcVersion == 8) {
            ironDoorOpen = Sound.valueOf("DOOR_OPEN");
            ironDoorClose = Sound.valueOf("DOOR_CLOSE");
            woodenDoorOpen = ironDoorOpen;
            woodenDoorClose = ironDoorClose;
        } else {
            ironDoorOpen = Sound.BLOCK_IRON_DOOR_OPEN;
            ironDoorClose = Sound.BLOCK_IRON_DOOR_CLOSE;
            woodenDoorOpen = Sound.BLOCK_WOODEN_DOOR_OPEN;
            woodenDoorClose = Sound.BLOCK_WOODEN_DOOR_CLOSE;
        }
    }

    @Override
    protected boolean isDoor(Block block) {
        return block.getType().getData() == Door.class;
    }

    @Override
    protected boolean isBottom(Block block) {
        Door door = (Door) block.getState().getData();
        return !door.isTopHalf();
    }

    @Override
    protected boolean updateDoor(World world, Location location, Set<Location> players) {
        BlockState state = location.getBlock().getState();
        MaterialData data = state.getData();

        if (!(data instanceof Door)) {
            // The block is no longer a door
            return true;
        }

        boolean open = false;

        for (Location player : players) {
            // The player must be within 4 blocks of the door
            if (location.distanceSquared(player) <= DOOR_DIST_SQUARED) {
                open = true;
                break;
            }
        }

        // Update the door's state if needed
        Door door = (Door) data;
        if (open != door.isOpen()) {
            Sound sound = state.getType() == ironDoorBlock ?
                    open ? ironDoorOpen : ironDoorClose :
                    open ? woodenDoorOpen : woodenDoorClose;
            world.playSound(location, sound, 1, 1);
            door.setOpen(open);
            state.update();
        }

        return false;
    }

}
