package com.sqc.nuclearsq.explosion.client;

import com.sqc.nuclearsq.explosion.ReactorDetonatorBlock;
import com.sqc.nuclearsq.registry.AssemblyRegistry;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
public final class DetonatorRangeHud {
    private DetonatorRangeHud() {}

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.screen != null || !Screen.hasControlDown()) return;
        BlockPos origin = null;
        HitResult hit = mc.hitResult;
        if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK
                && mc.level.getBlockState(bhr.getBlockPos()).getBlock() instanceof ReactorDetonatorBlock) {
            origin = bhr.getBlockPos();
        } else if (mc.player.getMainHandItem().is(AssemblyRegistry.DETONATOR_ITEM.get())
                || mc.player.getOffhandItem().is(AssemblyRegistry.DETONATOR_ITEM.get())) {
            origin = mc.player.blockPosition();
        }
        if (origin == null) return;
        BigMachineBlockEntity d = BigMachineBlockEntity.nearestDrive(mc.level, origin, ReactorDetonatorBlock.SEARCH_RANGE, BigMachineBlockEntity::isFormed);
        Component msg = d != null
                ? Component.translatable("message.nuclearsq.detonator_in_range",
                        (int) d.coreCenter().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(origin))).withStyle(ChatFormatting.GREEN)
                : Component.translatable("message.nuclearsq.detonator_out_range", ReactorDetonatorBlock.SEARCH_RANGE).withStyle(ChatFormatting.RED);
        mc.player.displayClientMessage(msg, true);
    }
}
