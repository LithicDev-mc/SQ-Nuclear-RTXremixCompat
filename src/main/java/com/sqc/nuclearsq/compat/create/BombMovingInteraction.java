package com.sqc.nuclearsq.compat.create;

import com.simibubi.create.content.contraptions.Contraption;
import com.simibubi.create.content.contraptions.behaviour.SimpleBlockMovingInteraction;
import com.sqc.nuclearsq.explosion.bomb.NuclearBombBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

public class BombMovingInteraction extends SimpleBlockMovingInteraction {
    @Override
    protected BlockState handle(Player player, Contraption contraption, BlockPos pos, BlockState state) {
        NuclearBombBlock bomb = NuclearBombBlock.of(state);
        if (bomb == null) return state;
        boolean armed = !state.getValue(NuclearBombBlock.ARMED);
        playSound(player, armed ? SoundEvents.TNT_PRIMED : SoundEvents.LEVER_CLICK, armed ? 0.6f : 1.0f);
        if (contraption.entity != null) {
            for (BlockPos p : bomb.parts(bomb.core(pos, state))) {
                if (p.equals(pos)) continue;
                StructureBlockInfo info = contraption.getBlocks().get(p);
                if (info != null && info.state().is(bomb))
                    contraption.entity.setBlock(p, new StructureBlockInfo(p, info.state().setValue(NuclearBombBlock.ARMED, armed), info.nbt()));
            }
        }
        return state.setValue(NuclearBombBlock.ARMED, armed);
    }
}
