package com.sqc.nuclearsq.storage;

import java.util.List;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.sqc.nuclearsq.registry.ModDataComponents;

import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class DriveItem extends Item {
    public record Fill(long used, int types) {
        public static final Codec<Fill> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("used").forGetter(Fill::used), Codec.INT.fieldOf("types").forGetter(Fill::types)).apply(i, Fill::new));
        public static final StreamCodec<ByteBuf, Fill> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, Fill::used, ByteBufCodecs.VAR_INT, Fill::types, Fill::new);
    }

    public final int tier;
    public final String valuesId;

    public DriveItem(Properties props, int tier, String valuesId) {
        super(props.stacksTo(1));
        this.tier = tier;
        this.valuesId = valuesId;
    }

    public StorageValues.Drive limits() {
        return StorageValues.drive(valuesId);
    }

    public static int tierOf(ItemStack stack) {
        return stack.getItem() instanceof DriveItem d ? d.tier : 0;
    }

    public static UUID id(ItemStack stack) {
        UUID id = stack.get(ModDataComponents.DRIVE_ID.get());
        if (id == null) {
            id = UUID.randomUUID();
            stack.set(ModDataComponents.DRIVE_ID.get(), id);
        }
        return id;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        StorageValues.Drive d = limits();
        Fill f = stack.get(ModDataComponents.DRIVE_FILL.get());
        long used = f == null ? 0 : f.used();
        int types = f == null ? 0 : f.types();
        lines.add(Component.translatable("tooltip.nuclearsq.drive.items", StorageFormat.count(used), StorageFormat.count(d.items()))
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.nuclearsq.drive.types", types, d.types()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.nuclearsq.drive.power", d.fePerTick()).withStyle(ChatFormatting.DARK_GRAY));
    }
}
