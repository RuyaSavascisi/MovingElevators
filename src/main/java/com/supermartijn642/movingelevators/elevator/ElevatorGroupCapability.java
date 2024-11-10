package com.supermartijn642.movingelevators.elevator;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.supermartijn642.movingelevators.MovingElevators;
import com.supermartijn642.movingelevators.blocks.ControllerBlockEntity;
import com.supermartijn642.movingelevators.packets.PacketAddElevatorGroup;
import com.supermartijn642.movingelevators.packets.PacketRemoveElevatorGroup;
import com.supermartijn642.movingelevators.packets.PacketUpdateElevatorGroups;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Created 11/7/2020 by SuperMartijn642
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ElevatorGroupCapability {

    private static Capability<ElevatorGroupCapability> CAPABILITY = CapabilityManager.get(new CapabilityToken<ElevatorGroupCapability>() {
    });

    public static ElevatorGroupCapability get(Level level){
        return level.getCapability(CAPABILITY).orElse(null);
    }

    @SubscribeEvent
    public static void register(RegisterCapabilitiesEvent e){
        e.register(ElevatorGroupCapability.class);
    }

    @SubscribeEvent
    public static void attachCapabilities(AttachCapabilitiesEvent<Level> e){
        Level level = e.getObject();

        LazyOptional<ElevatorGroupCapability> capability = LazyOptional.of(() -> new ElevatorGroupCapability(level));
        e.addCapability(new ResourceLocation("movingelevators", "elevator_groups"), new ICapabilitySerializable<Tag>() {
            @Nonnull
            @Override
            public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side){
                return cap == CAPABILITY ? capability.cast() : LazyOptional.empty();
            }

            @Override
            public Tag serializeNBT(){
                return capability.map(ElevatorGroupCapability::write).orElse(null);
            }

            @Override
            public void deserializeNBT(Tag nbt){
                capability.ifPresent(capability -> capability.read(nbt));
            }
        });
        e.addListener(capability::invalidate);
    }


    @SubscribeEvent
    public static void onTick(TickEvent.WorldTickEvent e){
        if(e.phase != TickEvent.Phase.END)
            return;

        tickWorldCapability(e.world);
    }

    public static void tickWorldCapability(Level level){
        level.getCapability(CAPABILITY).ifPresent(ElevatorGroupCapability::tick);
    }

    @SubscribeEvent
    public static void onJoinWorld(PlayerEvent.PlayerChangedDimensionEvent e){
        ServerPlayer player = (ServerPlayer)e.getPlayer();
        player.level.getCapability(CAPABILITY).ifPresent(groups ->
            MovingElevators.CHANNEL.sendToPlayer(player, new PacketUpdateElevatorGroups(groups.write()))
        );
    }

    @SubscribeEvent
    public static void onJoin(PlayerEvent.PlayerLoggedInEvent e){
        ServerPlayer player = (ServerPlayer)e.getPlayer();
        player.level.getCapability(CAPABILITY).ifPresent(groups ->
            MovingElevators.CHANNEL.sendToPlayer(player, new PacketUpdateElevatorGroups(groups.write()))
        );
    }

    @SubscribeEvent
    public static void onLoadChunk(ChunkEvent.Load e){
        if(e.getWorld() instanceof Level)
            get((Level)e.getWorld()).validateGroupsInChunk(e.getChunk());
    }

    private final Level level;
    private final Map<ElevatorGroupPosition,ElevatorGroup> groups = new HashMap<>();
    private final Multimap<ChunkPos,ElevatorGroup> groupsPerChunk = MultimapBuilder.hashKeys().hashSetValues(1).build();

    public ElevatorGroupCapability(Level level){
        this.level = level;
    }

    public ElevatorGroup get(int x, int z, Direction facing){
        return this.groups.get(new ElevatorGroupPosition(x, z, facing));
    }

    public void add(ControllerBlockEntity controller){
        ElevatorGroupPosition pos = new ElevatorGroupPosition(controller.getBlockPos(), controller.getFacing());
        ElevatorGroup group = this.groups.computeIfAbsent(pos, p -> new ElevatorGroup(this.level, p.x, p.z, p.facing));
        this.groupsPerChunk.put(pos.chunkPos(), group);
        group.add(controller);
    }

    public void remove(ControllerBlockEntity controller){
        ElevatorGroupPosition pos = new ElevatorGroupPosition(controller.getBlockPos(), controller.getFacing());
        ElevatorGroup group = this.groups.get(pos);
        group.remove(controller);
        if(group.getFloorCount() == 0)
            this.removeGroup(pos);
    }

    public void tick(){
        for(ElevatorGroup group : this.groups.values())
            group.update();
    }

    public void updateGroup(ElevatorGroup group){
        if(!this.level.isClientSide && group != null)
            MovingElevators.CHANNEL.sendToDimension(this.level.dimension(), new PacketAddElevatorGroup(this.writeGroup(group)));
    }

    private void removeGroup(ElevatorGroupPosition pos){
        ElevatorGroup group = this.groups.remove(pos);
        if(group != null){
            this.groupsPerChunk.remove(pos.chunkPos(), group);
            if(!this.level.isClientSide)
                MovingElevators.CHANNEL.sendToDimension(this.level.dimension(), new PacketRemoveElevatorGroup(group));
        }
    }

    /**
     * This should only be called client-side from the {@link PacketRemoveElevatorGroup}
     */
    public void removeGroup(int x, int z, Direction facing){
        if(this.level.isClientSide)
            this.removeGroup(new ElevatorGroupPosition(x, z, facing));
    }

    public ElevatorGroup getGroup(ControllerBlockEntity entity){
        return this.groups.get(new ElevatorGroupPosition(entity.getBlockPos().getX(), entity.getBlockPos().getZ(), entity.getFacing()));
    }

    public Collection<ElevatorGroup> getGroups(){
        return this.groups.values();
    }

    public void validateGroupsInChunk(ChunkAccess chunk){
        if(!this.groupsPerChunk.containsKey(chunk.getPos()))
            return;
        for(ElevatorGroup group : new ArrayList<>(this.groupsPerChunk.get(chunk.getPos()))){ // Groups may be removed during validation, hence create a copy of the list
            group.validateControllersExist(chunk);
            if(group.getFloorCount() == 0)
                this.removeGroup(new ElevatorGroupPosition(group.x, group.z, group.facing));
        }
    }

    public CompoundTag write(){
        CompoundTag compound = new CompoundTag();
        for(Map.Entry<ElevatorGroupPosition,ElevatorGroup> entry : this.groups.entrySet()){
            CompoundTag groupTag = new CompoundTag();
            groupTag.put("group", entry.getValue().write());
            groupTag.put("pos", entry.getKey().write());
            compound.put(entry.getKey().x + ";" + entry.getKey().z, groupTag);
        }
        return compound;
    }

    public void read(Tag tag){
        if(tag instanceof CompoundTag){
            CompoundTag compound = (CompoundTag)tag;
            this.groups.clear();
            for(String key : compound.getAllKeys())
                this.readGroup(compound.getCompound(key));
        }
    }

    private CompoundTag writeGroup(ElevatorGroup group){
        CompoundTag tag = new CompoundTag();
        tag.put("group", group.write());
        tag.put("pos", new ElevatorGroupPosition(group.x, group.z, group.facing).write());
        return tag;
    }

    public void readGroup(CompoundTag tag){
        if(tag.contains("group") && tag.contains("pos")){
            ElevatorGroupPosition pos = ElevatorGroupPosition.read(tag.getCompound("pos"));
            ElevatorGroup group = new ElevatorGroup(this.level, pos.x, pos.z, pos.facing);
            group.read(tag.getCompound("group"));
            this.groups.put(pos, group);
            this.groupsPerChunk.put(pos.chunkPos(), group);
        }
    }

    private static class ElevatorGroupPosition {

        public final int x, z;
        public final Direction facing;

        private ElevatorGroupPosition(int x, int z, Direction facing){
            this.x = x;
            this.z = z;
            this.facing = facing;
        }

        public ElevatorGroupPosition(BlockPos pos, Direction facing){
            this(pos.getX(), pos.getZ(), facing);
        }

        public ChunkPos chunkPos(){
            return new ChunkPos(SectionPos.blockToSectionCoord(this.x), SectionPos.blockToSectionCoord(this.z));
        }

        @Override
        public boolean equals(Object o){
            if(this == o) return true;
            if(o == null || this.getClass() != o.getClass()) return false;

            ElevatorGroupPosition that = (ElevatorGroupPosition)o;

            if(this.x != that.x) return false;
            if(this.z != that.z) return false;
            return this.facing == that.facing;
        }

        @Override
        public int hashCode(){
            int result = this.x;
            result = 31 * result + this.z;
            result = 31 * result + (this.facing != null ? this.facing.hashCode() : 0);
            return result;
        }

        public CompoundTag write(){
            CompoundTag tag = new CompoundTag();
            tag.putInt("x", this.x);
            tag.putInt("z", this.z);
            tag.putInt("facing", this.facing.get2DDataValue());
            return tag;
        }

        public static ElevatorGroupPosition read(CompoundTag tag){
            return new ElevatorGroupPosition(tag.getInt("x"), tag.getInt("z"), Direction.from2DDataValue(tag.getInt("facing")));
        }
    }

}
