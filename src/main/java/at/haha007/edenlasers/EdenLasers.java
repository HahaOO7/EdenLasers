package at.haha007.edenlasers;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.event.packet.PlayerChunkLoadEvent;
import io.papermc.paper.event.packet.PlayerChunkUnloadEvent;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import org.bukkit.Chunk;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

@SuppressWarnings("UnstableApiUsage")
public final class EdenLasers extends JavaPlugin implements Listener {
    private final NamespacedKey nskPos = new NamespacedKey(this, "pos");
    private final NamespacedKey nskIds = new NamespacedKey(this, "ids");


    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> registerCommands(event.registrar()));
    }

    private void registerCommands(@NotNull Commands registrar) {
        LiteralArgumentBuilder<CommandSourceStack> cmd = literal("laser");
        cmd.requires(c -> c.getSender().hasPermission("edenlasers.command.laser"));
        cmd.executes(c -> {
            c.getSource().getSender().sendMessage(Component.text("/laser [create,delete] <world> <x> <z>"));
            return 1;
        });
        cmd.then(literal("create")
                .then(argument("world", ArgumentTypes.world())
                        .then(argument("x", IntegerArgumentType.integer())
                                .then(argument("z", IntegerArgumentType.integer())
                                        .executes(c -> {
                                            createLaser(c);
                                            c.getSource().getSender().sendMessage(Component.text("Created laser"));
                                            return 1;
                                        })))));
        cmd.then(literal("remove")
                .then(argument("world", ArgumentTypes.world())
                        .then(argument("x", IntegerArgumentType.integer())
                                .then(argument("z", IntegerArgumentType.integer())
                                        .executes(c -> {
                                            removeLaser(c);
                                            c.getSource().getSender().sendMessage(Component.text("Removed laser"));
                                            return 1;
                                        })))));
        registrar.register(cmd.build());
    }

    private void removeLaser(CommandContext<CommandSourceStack> c) {
        int x = IntegerArgumentType.getInteger(c,"x");
        int z = IntegerArgumentType.getInteger(c,"z");
        World world = c.getArgument("world", World.class);
        Block block = world.getBlockAt(x, 0, z);

        int blockX = (x % 16 + 16) % 16;
        int blockZ = (z % 16 + 16) % 16;
        byte posInChunk = (byte) ((blockX << 4) | blockZ);

        world.getChunkAtAsync(block, (Consumer<Chunk>) chunk -> {

            PersistentDataContainer pdc = chunk.getPersistentDataContainer();
            byte[] laserPositions = pdc.getOrDefault(nskPos, PersistentDataType.BYTE_ARRAY, new byte[0]);
            long[] lasers = pdc.getOrDefault(nskIds, PersistentDataType.LONG_ARRAY, new long[0]);

            int found = -1;
            for (int i = 0; i < laserPositions.length; i++) {
                if (laserPositions[i] != posInChunk) continue;
                found = i;
                break;
            }
            if (found == -1) return;

            long laserId = lasers[found];
            for (Player player : world.getPlayers()) {
                if (player.isChunkSent(chunk)) {
                    LaserUtils.removeLaser(player, laserId);
                }
            }

            byte[] newLaserPositions = new byte[laserPositions.length - 1];
            long[] newLaserIds = new long[lasers.length - 1];

            System.arraycopy(laserPositions, 0, newLaserPositions, 0, found);
            System.arraycopy(laserPositions, found + 1, newLaserPositions, found, laserPositions.length - found - 1);
            System.arraycopy(lasers, 0, newLaserIds, 0, found);
            System.arraycopy(lasers, found + 1, newLaserIds, found, lasers.length - found - 1);

            pdc.set(nskPos, PersistentDataType.BYTE_ARRAY, newLaserPositions);
            pdc.set(nskIds, PersistentDataType.LONG_ARRAY, newLaserIds);
        });
    }

    private void createLaser(CommandContext<CommandSourceStack> c) {
        int x = IntegerArgumentType.getInteger(c,"x");
        int z = IntegerArgumentType.getInteger(c,"z");
        World world = c.getArgument("world", World.class);

        Block block = world.getBlockAt(x, 0, z);
        world.getChunkAtAsync(block, (Consumer<Chunk>) chunk -> {
            long laserIds = ThreadLocalRandom.current().nextLong();
            for (Player player : world.getPlayers()) {
                if (player.isChunkSent(chunk)) {
                    LaserUtils.showLaser(player, x, z, laserIds);
                }
            }
            PersistentDataContainer pdc = chunk.getPersistentDataContainer();
            byte[] laserPositions = pdc.getOrDefault(nskPos, PersistentDataType.BYTE_ARRAY, new byte[0]);
            long[] lasers = pdc.getOrDefault(nskIds, PersistentDataType.LONG_ARRAY, new long[0]);
            int blockX = (block.getX() % 16 + 16) % 16;
            int blockZ = (block.getZ() % 16 + 16) % 16;
            byte blockXZ = (byte) (blockX << 4 | blockZ);
            byte[] newPositions = new byte[laserPositions.length + 1];
            long[] newIds = new long[laserPositions.length + 1];
            System.arraycopy(laserPositions, 0, newPositions, 0, laserPositions.length);
            System.arraycopy(lasers, 0, newIds, 0, lasers.length);
            newIds[laserPositions.length] = laserIds;
            newPositions[laserPositions.length] = blockXZ;
            pdc.set(nskPos, PersistentDataType.BYTE_ARRAY, newPositions);
            pdc.set(nskIds, PersistentDataType.LONG_ARRAY, newIds);
        });

    }

    @EventHandler
    public void onPlayerLoadChunk(PlayerChunkLoadEvent event) {
        Player player = event.getPlayer();
        Chunk chunk = event.getChunk();
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        byte[] laserPositions = pdc.getOrDefault(nskPos, PersistentDataType.BYTE_ARRAY, new byte[0]);
        long[] lasers = pdc.getOrDefault(nskIds, PersistentDataType.LONG_ARRAY, new long[0]);

        for (int i = 0; i < laserPositions.length; i++) {
            byte laserPosition = laserPositions[i];
            int blockX = (laserPosition >> 4);
            int blockZ = laserPosition & 0xF;
            long entityId = lasers[i];

            Block block = chunk.getBlock(blockX, 0, blockZ);
            LaserUtils.showLaser(player, block.getX(), block.getZ(), entityId);
        }
    }

    @EventHandler
    public void onPlayerUnloadChunk(PlayerChunkUnloadEvent event) {
        Player player = event.getPlayer();
        Chunk chunk = event.getChunk();
        PersistentDataContainer pdc = chunk.getPersistentDataContainer();
        long[] lasers = pdc.getOrDefault(nskIds, PersistentDataType.LONG_ARRAY, new long[0]);

        for (long entityId : lasers) {
            LaserUtils.removeLaser(player, entityId);
        }
    }
}
