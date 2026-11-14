package com.sqc.nuclearsq.multiblock;

import java.util.List;
import java.util.Set;

import com.sqc.nuclearsq.reactor.PartStages;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

public class PartBlockItem extends BlockItem {
    public static final Set<String> CATALOG = Set.of(
            "vent_grille_sqc", "heat_exchanger_sqc", "active_cooler_sqc", "cryo_cooling_core_sqc",
            "translator_basic_sqc", "translator_brass_sqc", "translator_composite_sqc", "translator_glowstone_sqc",
            "moderator_coal_sqc", "moderator_carbon_sqc", "moderator_obsidian_sqc", "moderator_fluorite_sqc", "moderator_heavy_water_sqc",
            "pump_basic_sqc", "pump_electric_sqc", "pump_high_pressure_sqc", "pump_sodium_sqc",
            "pressure_chamber_sqc", "pressure_chamber_reinforced_sqc", "pressure_chamber_hardened_sqc", "pressure_chamber_experimental_sqc",
            "turbine_basic_sqc", "turbine_brass_rotor_sqc", "turbine_steel_blades_sqc", "turbine_glowstone_generator_sqc",
            "cooling_tower_segment_sqc", "cooling_tower_plated_sqc", "cooling_tower_hardened_sqc", "cooling_tower_closed_sqc",
            "steam_purifier_sqc", "condenser_sqc", "water_treatment_sqc", "feedwater_pump_sqc",
            "water_inlet_sqc", "sodium_heat_exchanger_sqc");

    public PartBlockItem(Block block, Properties props) {
        super(block, props);
    }

    @Override
    public InteractionResult place(BlockPlaceContext ctx) {
        Player p = ctx.getPlayer();
        if (p != null && !ctx.getLevel().isClientSide) {
            p.displayClientMessage(Component.translatable("message.nuclearsq.part_not_placeable").withStyle(ChatFormatting.GOLD), true);
        }
        return InteractionResult.FAIL;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        super.appendHoverText(stack, ctx, tip, flag);
        tip.add(Component.translatable("item.nuclearsq.part.tip").withStyle(ChatFormatting.DARK_AQUA));
        int m = PartStages.moderatorStage(stack), c = PartStages.coolerStage(stack), t = PartStages.translatorStage(stack);
        if (m > 0) {
            tip.add(Component.translatable("tooltip.nuclearsq.part.moderator", m, Math.round(PartStages.moderatorPower(m) * 100),
                    Math.round(PartStages.moderatorHeat(m) * 100)).withStyle(ChatFormatting.GOLD));
        }
        if (c > 0) {
            tip.add(Component.translatable("tooltip.nuclearsq.part.cooler", c, PartStages.coolerCooling(c))
                    .withStyle(ChatFormatting.AQUA));
        }
        if (t > 0) {
            tip.add(Component.translatable("tooltip.nuclearsq.part.translator", t, PartStages.translatorFePct(t), PartStages.translatorFlowPct(t))
                    .withStyle(ChatFormatting.YELLOW));
        }
    }
}
