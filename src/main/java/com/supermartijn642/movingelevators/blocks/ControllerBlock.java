package com.supermartijn642.movingelevators.blocks;

import com.supermartijn642.core.TextComponents;
import com.supermartijn642.core.block.BlockProperties;
import com.supermartijn642.movingelevators.MovingElevatorsClient;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.function.Consumer;

/**
 * Created 3/28/2020 by SuperMartijn642
 */
public class ControllerBlock extends ElevatorInputBlock {

    public static final PropertyDirection FACING = BlockHorizontal.FACING;

    public ControllerBlock(BlockProperties properties){
        super(properties, ControllerBlockEntity::new);
    }

    @Override
    protected boolean onRightClick(IBlockState state, World level, CamoBlockEntity blockEntity, BlockPos pos, EntityPlayer player, EnumHand hand, EnumFacing hitSide, Vec3d hitLocation){
        if(player != null && player.getHeldItem(hand).getItem() instanceof RemoteControllerBlockItem && blockEntity instanceof ControllerBlockEntity){
            if(!level.isRemote){
                ItemStack stack = player.getHeldItem(hand);
                NBTTagCompound tag = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
                tag.setInteger("controllerDim", level.provider.getDimensionType().getId());
                tag.setInteger("controllerX", pos.getX());
                tag.setInteger("controllerY", pos.getY());
                tag.setInteger("controllerZ", pos.getZ());
                tag.setInteger("controllerFacing", ((ControllerBlockEntity)blockEntity).getFacing().getHorizontalIndex());
                stack.setTagCompound(tag);
                player.sendStatusMessage(TextComponents.translation("movingelevators.remote_controller.bind").get(), true);
            }
            return true;
        }

        if(super.onRightClick(state, level, blockEntity, pos, player, hand, hitSide, hitLocation))
            return true;

        if(state.getValue(FACING) != hitSide){
            if(level.isRemote)
                MovingElevatorsClient.openElevatorScreen(pos);
            return true;
        }
        return false;
    }

    @Override
    public IBlockState getStateForPlacement(World level, BlockPos pos, EnumFacing facing, float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer, EnumHand hand){
        return this.getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite());
    }

    @Override
    protected IProperty<?>[] getProperties(){
        return new IProperty[]{FACING};
    }

    @Override
    public int getMetaFromState(IBlockState state){
        return state.getValue(FACING).getIndex();
    }

    @Override
    public IBlockState getStateFromMeta(int meta){
        EnumFacing facing = EnumFacing.getFront(meta);
        if(facing.getAxis() == EnumFacing.Axis.Y)
            facing = EnumFacing.NORTH;
        return this.getDefaultState().withProperty(FACING, facing);
    }

    @Override
    public boolean hasComparatorInputOverride(IBlockState state){
        return true;
    }

    @Override
    public int getComparatorInputOverride(IBlockState state, World level, BlockPos pos){
        TileEntity entity = level.getTileEntity(pos);
        if(entity instanceof ControllerBlockEntity
            && ((ControllerBlockEntity)entity).hasGroup()
            && ((ControllerBlockEntity)entity).getGroup().isCageAvailableAt((ControllerBlockEntity)entity)){
            return 15;
        }
        return 0;
    }

    @Override
    protected void appendItemInformation(ItemStack stack, @Nullable IBlockAccess level, Consumer<ITextComponent> info, boolean advanced){
        info.accept(TextComponents.translation("movingelevators.elevator_controller.tooltip").color(TextFormatting.AQUA).get());
    }

    @Override
    public void breakBlock(World level, BlockPos pos, IBlockState state){
        if(this.hasTileEntity(state)){
            TileEntity entity = level.getTileEntity(pos);
            if(entity instanceof ControllerBlockEntity)
                ((ControllerBlockEntity)entity).onRemove();
        }
        super.breakBlock(level, pos, state);
    }
}
