package com.sqc.nuclearsq.gametest;

import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.transport.GridNode;
import com.sqc.nuclearsq.transport.GridNodeBlockEntity;
import com.sqc.nuclearsq.transport.GridRegistry;
import com.sqc.nuclearsq.transport.LargePowerMastBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class OverheadWireTests {
    private OverheadWireTests() {}

    private static GridNodeBlockEntity mast(GameTestHelper h, Block mast, BlockPos base) {
        BlockState st = mast.defaultBlockState();
        h.setBlock(base, st);
        mast.setPlacedBy(h.getLevel(), h.absolutePos(base), st, null, ItemStack.EMPTY);
        return h.getBlockEntity(base);
    }

    private static void click(GameTestHelper h, ServerPlayer p, BlockPos rel) {
        BlockPos at = h.absolutePos(rel);
        p.gameMode.useItemOn(p, h.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(at), Direction.NORTH, at, false));
    }

    static void linkByHand(GameTestHelper h, Block mast, int dist) {
        GridNodeBlockEntity a = mast(h, mast, new BlockPos(1, 2, 1)), b = mast(h, mast, new BlockPos(1 + dist, 2, 1));
        ServerPlayer p = FakePlayerFactory.getMinecraft(h.getLevel());
        p.setGameMode(GameType.SURVIVAL);
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GridRegistry.WIRE.get(), 16));
        click(h, p, new BlockPos(1, 4, 1));
        click(h, p, new BlockPos(1 + dist, 3, 1));
        if (!a.links().contains(b.getBlockPos()) || !b.links().contains(a.getBlockPos()))
            throw new GameTestAssertException("Leiterseil per Rechtsklick nicht gespannt");
        h.succeed();
    }

    @GameTest(template = "floor", batch = "overhead_wire")
    public static void wireLinksMastsByHand(GameTestHelper h) {
        linkByHand(h, GridRegistry.MAST.get(), 6);
    }

    @GameTest(template = "floor", batch = "overhead_wire_large")
    public static void largeMastsSpanTwiceAsFar(GameTestHelper h) {
        GridNodeBlockEntity a = mast(h, GridRegistry.MAST_LARGE.get(), new BlockPos(1, 2, 8)), b = mast(h, GridRegistry.MAST.get(), new BlockPos(6, 2, 8));
        if (!h.getBlockState(new BlockPos(1, 2 + 23, 8)).is(GridRegistry.MAST_LARGE.get())) throw new GameTestAssertException("Grosser Mast nicht 24 Bloecke hoch");
        if (GridNode.span(a, a) != LargePowerMastBlock.SPAN || GridNode.span(a, b) != GridNode.MAX_SPAN)
            throw new GameTestAssertException("Feldlaengen " + GridNode.span(a, a) + "/" + GridNode.span(a, b));
        linkByHand(h, GridRegistry.MAST_LARGE.get(), 8);
    }

    @GameTest(template = "floor", batch = "overhead_wire_far")
    public static void largeMastsSpan256(GameTestHelper h) {
        linkByHand(h, GridRegistry.MAST_LARGE.get(), 250);
        for (int y = LargePowerMastBlock.HEIGHT - 1; y >= 0; y--) h.setBlock(new BlockPos(251, 2 + y, 1), net.minecraft.world.level.block.Blocks.AIR);
    }
}
