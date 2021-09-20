package io.github.mooy1.autodoors;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

public class DoorManager implements Listener {

    protected static final double DOOR_DIST_SQUARED = 16.0;
    private static final long UPDATE_INTERVAL = 10;

    // Maps each world to a map of chunks to door locations
    private final Map<World, Map<Long, Set<Location>>> worldToChunks = new HashMap<>();
    private final AutoDoors plugin;

    public DoorManager(AutoDoors plugin) {
        this.plugin = plugin;

        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::updateDoors, 1, UPDATE_INTERVAL);
    }

    private void updateDoors() {
        // Map worlds to player location
        Map<World, Set<Location>> allPlayers = new HashMap<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            allPlayers.computeIfAbsent(p.getWorld(), w -> new HashSet<>()).add(p.getLocation());
        }

        for (Map.Entry<World, Map<Long, Set<Location>>> entry : worldToChunks.entrySet()) {
            World world = entry.getKey();
            Set<Location> players = allPlayers.get(world);

            if (players == null) {
                continue;
            }

            // Update doors, removing any that are no longer doors
            for (Set<Location> doorsInChunk : entry.getValue().values()) {
                doorsInChunk.removeIf(location -> updateDoor(world, location, players));
            }
        }
    }

    // Returns true if the door should be removed from map
    protected boolean updateDoor(World world, Location location, Set<Location> players) {
        Block block = location.getBlock();
        BlockData data = block.getBlockData();

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
            Sound sound = data.getMaterial() == Material.IRON_DOOR ?
                    open ? Sound.BLOCK_IRON_DOOR_OPEN : Sound.BLOCK_IRON_DOOR_CLOSE :
                    open ? Sound.BLOCK_WOODEN_DOOR_OPEN : Sound.BLOCK_WOODEN_DOOR_CLOSE;
            world.playSound(location, sound, 1, 1);
            door.setOpen(open);
            block.setBlockData(data);
        }

        return false;
    }

    // Returns whether the block is a door
    protected boolean isDoor(Block block) {
        return block.getType().data == Door.class;
    }

    // Returns whether the block is the bottom part of a door
    protected boolean isBottom(Block block) {
        Door door = (Door) block.getBlockData();
        return door.getHalf() == Bisected.Half.BOTTOM;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public final void onChunkLoad(ChunkLoadEvent event) {
        Chunk chunk = event.getChunk();
        Map<Long, Set<Location>> chunkToDoors = worldToChunks
                .computeIfAbsent(event.getWorld(), world -> new HashMap<>());

        // Only load the chunk if it isn't already loaded
        chunkToDoors.computeIfAbsent(chunkKey(chunk), key -> {
            Set<Location> doors = new HashSet<>();
            if (event.isNewChunk()) {
                // Need to wait a tick for blocks to generate
                Bukkit.getScheduler().runTaskLater(plugin, () -> loadDoors(chunk, doors), 1);
            } else {
                loadDoors(chunk, doors);
            }
            return doors;
        });
    }

    private void loadDoors(Chunk chunk, Set<Location> doors) {
        int maxHeight = chunk.getWorld().getMaxHeight();

        // Check each block of chunk
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < maxHeight; y++) {
                for (int z = 0; z < 16; z++) {
                    Block block = chunk.getBlock(x, y, z);
                    if (isDoor(block) && isBottom(block)) {
                        doors.add(block.getLocation());
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public final void onWorldUnload(WorldUnloadEvent event) {
        // Remove all doors for that world
        worldToChunks.remove(event.getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public final void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();

        // Check if it is a door
        if (isDoor(block)) {

            // Add the door to map
            Map<Long, Set<Location>> chunksToDoors = worldToChunks.get(block.getWorld());
            if (chunksToDoors != null) {

                Set<Location> doors = chunksToDoors.get(chunkKey(block.getChunk()));
                if (doors != null) {
                    doors.add(block.getLocation());
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public final void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();

        // Check if it is a door
        if (isDoor(block)) {
            if (!isBottom(block)) {
                // They broke the top of door, we need the bottom
                block = block.getRelative(0, -1, 0);
            }

            // Remove the door from map
            Map<Long, Set<Location>> chunksToDoors = worldToChunks.get(block.getWorld());
            if (chunksToDoors != null) {

                Set<Location> doors = chunksToDoors.get(chunkKey(block.getChunk()));
                if (doors != null) {
                    doors.remove(block.getLocation());
                }
            }
        }
    }

    private static long chunkKey(Chunk chunk) {
        // Combines the x and z ints into a single long
        return ((long) chunk.getX() << 32) + chunk.getZ();
    }

}
