package com.sqc.nuclearsq.exo;

import java.util.List;

import javax.annotation.Nullable;

import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public class ExoModule extends Item implements ExoModuleItem {
    public static final int MAX_LEVEL = 5;
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V"};
    public static final int COST_PER_LEVEL = 10;

    public enum Kind {
        SPEED("speed", Attributes.MOVEMENT_SPEED, Operation.ADD_MULTIPLIED_BASE, 0.1, ArmorItem.Type.LEGGINGS),
        STEP("step", Attributes.STEP_HEIGHT, Operation.ADD_VALUE, 0.2, ArmorItem.Type.LEGGINGS),
        JUMP("jump", Attributes.JUMP_STRENGTH, Operation.ADD_MULTIPLIED_BASE, 0.1, ArmorItem.Type.BOOTS),
        FALL("fall", Attributes.SAFE_FALL_DISTANCE, Operation.ADD_VALUE, 2.0, ArmorItem.Type.BOOTS),
        ARMOR("armor", Attributes.ARMOR, Operation.ADD_VALUE, 1.0, ArmorItem.Type.CHESTPLATE),
        DIG("dig", Attributes.BLOCK_BREAK_SPEED, Operation.ADD_MULTIPLIED_BASE, 0.2, ArmorItem.Type.CHESTPLATE),
        REACH("reach", Attributes.BLOCK_INTERACTION_RANGE, Operation.ADD_VALUE, 0.5, ArmorItem.Type.CHESTPLATE),
        STRENGTH("strength", Attributes.ATTACK_DAMAGE, Operation.ADD_VALUE, 1.0, ArmorItem.Type.CHESTPLATE),
        BREATH("breath", Attributes.OXYGEN_BONUS, Operation.ADD_VALUE, 4.0, ArmorItem.Type.HELMET),
        JETPACK("jetpack", null, Operation.ADD_VALUE, 1.0, ArmorItem.Type.CHESTPLATE),
        LUCK("luck", null, Operation.ADD_VALUE, 1.0, ArmorItem.Type.CHESTPLATE),
        FIRE("fire", null, Operation.ADD_MULTIPLIED_BASE, 0.2, ArmorItem.Type.LEGGINGS),
        LOOT("loot", null, Operation.ADD_VALUE, 1.0, ArmorItem.Type.HELMET),
        AREA("area", null, Operation.ADD_VALUE, 1.0, ArmorItem.Type.CHESTPLATE, 3),
        VEIN("vein", null, Operation.ADD_VALUE, 1.0, ArmorItem.Type.CHESTPLATE, 5),
        CLAWS("claws", null, Operation.ADD_VALUE, 1.0, ArmorItem.Type.CHESTPLATE, 1),
        ORE("ore", null, Operation.ADD_VALUE, 1.0, ArmorItem.Type.HELMET, 5),
        RAD("rad", null, Operation.ADD_VALUE, 1.0, ArmorItem.Type.HELMET),
        MAGNET("magnet", null, Operation.ADD_VALUE, 1.0, ArmorItem.Type.CHESTPLATE),
        HOTBAR("hotbar", null, Operation.ADD_VALUE, 1.0, ArmorItem.Type.CHESTPLATE, 1),
        BATTERY("battery", null, Operation.ADD_MULTIPLIED_BASE, 0.2, ArmorItem.Type.LEGGINGS);

        public final String id;
        @Nullable public final Holder<Attribute> attribute;
        public final Operation op;
        public final double perLevel;
        public final ArmorItem.Type piece;
        public final int maxLevel;

        Kind(String id, @Nullable Holder<Attribute> attribute, Operation op, double perLevel, ArmorItem.Type piece) {
            this(id, attribute, op, perLevel, piece, MAX_LEVEL);
        }

        Kind(String id, @Nullable Holder<Attribute> attribute, Operation op, double perLevel, ArmorItem.Type piece, int maxLevel) {
            this.maxLevel = maxLevel;
            this.id = id;
            this.attribute = attribute;
            this.op = op;
            this.perLevel = perLevel;
            this.piece = piece;
        }

        public double amount(int level) {
            return perLevel * level;
        }
    }

    private final Kind kind;
    private final int level;

    public ExoModule(Properties props, Kind kind, int level) {
        super(props.stacksTo(16));
        this.kind = kind;
        this.level = level;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public int level() {
        return level;
    }

    @Override
    public boolean fits(ItemStack piece) {
        return piece.getItem() instanceof ExoArmorItem a && a.getType() == kind.piece;
    }

    @Nullable
    public static ExoModule of(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, id)) instanceof ExoModule m ? m : null;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        double v = kind.amount(level);
        String value = kind.op == Operation.ADD_VALUE ? "+" + (v == Math.rint(v) ? String.valueOf((int) v) : String.valueOf(v)) : "+" + Math.round(v * 100) + " %";
        if (kind == Kind.FIRE) value = "-" + value.substring(1);
        else if (kind == Kind.AREA) value = ExoMining.AREA_SIZE[level] + "×" + ExoMining.AREA_SIZE[level];
        else if (kind == Kind.VEIN) value = String.valueOf(ExoMining.VEIN_LIMIT[level]);
        else if (kind == Kind.ORE) value = String.valueOf(ExoOre.RANGE[level]);
        else if (kind == Kind.RAD) value = Math.round(ExoRadiation.SHIELD[level] * 100) + " %";
        else if (kind == Kind.MAGNET) value = String.valueOf(ExoMagnet.RADIUS[level]);
        lines.add(Component.translatable("tooltip.nuclearsq.exo_module.level", ROMAN[level]).withStyle(ChatFormatting.YELLOW));
        lines.add(Component.translatable("tooltip.nuclearsq.exo_module." + kind.id, value).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.nuclearsq.exo_module.piece." + kind.piece.getName()).withStyle(ChatFormatting.DARK_GRAY));
        if (kind != Kind.BATTERY) lines.add(Component.translatable("tooltip.nuclearsq.exo_module.cost", COST_PER_LEVEL * level).withStyle(ChatFormatting.DARK_GRAY));
    }
}
