package com.supermartijn642.movingelevators.elevator;

import com.mojang.blaze3d.vertex.PoseStack;
import com.supermartijn642.core.ClientUtils;
import com.supermartijn642.core.render.RenderUtils;
import com.supermartijn642.core.render.RenderWorldEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Created 11/8/2020 by SuperMartijn642
 */
public class ElevatorGroupRenderer {

    public static final double RENDER_DISTANCE = 255 * 255 * 4;

    public static void registerEventListeners(){
        RenderWorldEvent.EVENT.register(ElevatorGroupRenderer::onRender);
    }

    public static void onRender(RenderWorldEvent e){
        ElevatorGroupCapability groups = ElevatorGroupCapability.get(ClientUtils.getWorld());

        e.getPoseStack().pushPose();
        Vec3 camera = RenderUtils.getCameraPosition();
        e.getPoseStack().translate(-camera.x, -camera.y, -camera.z);
        for(ElevatorGroup group : groups.getGroups()){
            BlockPos elevatorPos = new BlockPos(group.x, (int)group.getCurrentY(), group.z);
            if(elevatorPos.distSqr(Minecraft.getInstance().player.blockPosition()) < RENDER_DISTANCE)
                renderGroup(e.getPoseStack(), group, RenderUtils.getMainBufferSource(), e.getPartialTicks());
        }
        e.getPoseStack().popPose();
    }

    public static void renderGroup(PoseStack poseStack, ElevatorGroup group, MultiBufferSource buffer, float partialTicks){
        if(ClientUtils.getMinecraft().getEntityRenderDispatcher().shouldRenderHitBoxes())
            renderGroupCageOutlines(poseStack, group);

        if(!group.isMoving())
            return;

        ElevatorCage cage = group.getCage();
        double lastY = group.getLastY(), currentY = group.getCurrentY();
        double renderY = lastY + (currentY - lastY) * partialTicks;
        Vec3 startPos = group.getCageAnchorPos(renderY);

        BlockPos topPos = new BlockPos(group.x, (int)renderY, group.z).relative(group.facing, (int)Math.ceil(group.getCageDepth() / 2f));
        int currentLight = LevelRenderer.getLightColor(group.level, topPos);

        for(int x = 0; x < group.getCageSizeX(); x++){
            for(int y = 0; y < group.getCageSizeY(); y++){
                for(int z = 0; z < group.getCageSizeZ(); z++){
                    if(cage.blockStates[x][y][z] == null)
                        continue;

                    poseStack.pushPose();

                    poseStack.translate(startPos.x + x, startPos.y + y, startPos.z + z);

                    ClientUtils.getBlockRenderer().renderSingleBlock(cage.blockStates[x][y][z], poseStack, buffer, currentLight, OverlayTexture.NO_OVERLAY);

                    poseStack.popPose();
                }
            }
        }

        if(ClientUtils.getMinecraft().getEntityRenderDispatcher().shouldRenderHitBoxes()){
            RenderUtils.renderBox(poseStack, new AABB(startPos, startPos.add(group.getCageSizeX(), group.getCageSizeY(), group.getCageSizeZ())), 1, 0, 0, true);
            RenderUtils.renderShape(poseStack, cage.shape.move(startPos.x, startPos.y, startPos.z), 49 / 255f, 224 / 255f, 219 / 255f, true);
        }
    }

    public static void renderGroupCageOutlines(PoseStack poseStack, ElevatorGroup group){
        for(int floor = 0; floor < group.getFloorCount(); floor++){
            BlockPos anchorPos = group.getCageAnchorBlockPos(group.getFloorYLevel(floor));
            AABB cageArea = new AABB(anchorPos, anchorPos.offset(group.getCageSizeX(), group.getCageSizeY(), group.getCageSizeZ()));
            cageArea.inflate(0.01);
            RenderUtils.renderBox(poseStack, cageArea, 1, 1, 1, true);
        }
    }
}
