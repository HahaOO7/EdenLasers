package at.haha007.edenlasers;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.wrappers.WrappedDataValue;
import com.comphenix.protocol.wrappers.WrappedDataWatcher;
import com.google.common.collect.Lists;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.UUID;

public class LaserUtils {
    private LaserUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static long showLaser(Player player, int x, int z, long laserIds) {
        int idA = (int) laserIds;
        int idB = (int) (laserIds >> 32);
        Vector position = new Vector(x + 0.5, -64, z + 0.5);

        PacketContainer spawnPacket1 = createSpawnPacket(idA, UUID.randomUUID(), position);
        ProtocolLibrary.getProtocolManager().sendServerPacket(player, spawnPacket1);

        PacketContainer spawnPacket2 = createSpawnPacket(idB, UUID.randomUUID(), position.clone().add(new Vector(0, 500, 0)));
        ProtocolLibrary.getProtocolManager().sendServerPacket(player, spawnPacket2);

        PacketContainer metadataPacket1 = createMetadataPacket(idA, idB);
        ProtocolLibrary.getProtocolManager().sendServerPacket(player, metadataPacket1);

        PacketContainer metadataPacket2 = createMetadataPacket(idB, idA);
        ProtocolLibrary.getProtocolManager().sendServerPacket(player, metadataPacket2);

        return laserIds;
    }

    public static void removeLaser(Player player, long entityId) {
        PacketContainer destroyPacket = new PacketContainer(PacketType.Play.Server.ENTITY_DESTROY);
        int idA = (int) entityId;
        int idB = (int) (entityId >> 32);
        destroyPacket.getModifier().write(0, new IntArrayList(new int[]{idA, idB}));
        ProtocolLibrary.getProtocolManager().sendServerPacket(player, destroyPacket);
    }

    private static PacketContainer createMetadataPacket(int id, int target) {
        PacketContainer packet = new PacketContainer(PacketType.Play.Server.ENTITY_METADATA);
        packet.getIntegers().write(0, id);

        List<WrappedDataValue> metadata = Lists.newArrayList();
        metadata.add(new WrappedDataValue(17, WrappedDataWatcher.Registry.get(Integer.class), target));
        metadata.add(new WrappedDataValue(0, WrappedDataWatcher.Registry.get(Byte.class), (byte) 0x20));
        packet.getDataValueCollectionModifier().write(0, metadata);

        return packet;
    }

    private static PacketContainer createSpawnPacket(int id, UUID uuid, Vector position) {
        PacketContainer packet = new PacketContainer(PacketType.Play.Server.SPAWN_ENTITY);
        packet.getIntegers().write(0, id);
        packet.getUUIDs().write(0, uuid);
        packet.getEntityTypeModifier().write(0, EntityType.GUARDIAN);
        packet.getDoubles().write(0, position.getX());
        packet.getDoubles().write(1, position.getY());
        packet.getDoubles().write(2, position.getZ());
        return packet;
    }
}
