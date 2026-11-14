package com.sqc.nuclearsq.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.annotation.Nullable;

import com.sqc.nuclearsq.machine.big.BigMachine;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.machine.big.BigPortBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

public class NetScannerItem extends Item {
    public static final int RANGE = 160;
    public static final int PERIOD = 20;

    private static final String TAG_POS = "net_start", TAG_DIM = "net_dim";

    public NetScannerItem(Properties props) {
        super(props);
    }

    @Nullable
    public static List<BlockPos> starts(Level level, BlockPos pos) {
        if (!ChunkGuard.loaded(level, pos)) return null;
        if (level.getBlockState(pos).getBlock() instanceof DataCableBlock) return List.of(pos);
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof BigPortBlockEntity p && p.findController() instanceof BigMachineBlockEntity c) be = c;
        if (be instanceof BigMachineBlockEntity b) return b.machine() == BigMachine.CONTROL_DESK ? b.cellPositions() : b.dataStarts();
        if (DataNet.isTerminal(level, pos) || DataSources.isPort(level, pos)) return List.of(pos);
        return null;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (!(ctx.getPlayer() instanceof ServerPlayer sp) || !(level instanceof ServerLevel sl)) return InteractionResult.sidedSuccess(level.isClientSide);
        List<BlockPos> starts = starts(level, ctx.getClickedPos());
        if (starts == null) {
            sp.displayClientMessage(Component.translatable("message.nuclearsq.net_scanner.not_net"), true);
            return InteractionResult.FAIL;
        }
        ItemStack stack = ctx.getItemInHand();
        select(stack, sl, ctx.getClickedPos());
        NetScanPayload p = payload(sl, starts, sp);
        sp.displayClientMessage(Component.translatable("message.nuclearsq.net_scanner.selected", p.machines().size(), p.cables().length), true);
        send(sp, p);
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        if (player.isShiftKeyDown()) {
            if (has(stack)) {
                clear(stack);
                player.displayClientMessage(Component.translatable("message.nuclearsq.net_scanner.cleared"), true);
                if (player instanceof ServerPlayer sp) send(sp, NetScanPayload.EMPTY);
            }
        } else {
            player.displayClientMessage(Component.translatable(has(stack) ? "message.nuclearsq.net_scanner.active" : "message.nuclearsq.net_scanner.hint"), true);
        }
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (!(level instanceof ServerLevel sl) || !(entity instanceof ServerPlayer sp)) return;
        if (!(selected || sp.getOffhandItem() == stack)) return;
        if ((sl.getGameTime() + sp.getId()) % PERIOD != 0) return;
        BlockPos start = start(stack, sl);
        if (start == null) return;
        List<BlockPos> starts = starts(sl, start);
        if (starts == null) {
            for (Direction d : Direction.values()) {
                starts = starts(sl, start.relative(d));
                if (starts != null) {
                    select(stack, sl, start.relative(d));
                    break;
                }
            }
        }
        if (starts == null) {
            clear(stack);
            sp.displayClientMessage(Component.translatable("message.nuclearsq.net_scanner.lost"), true);
            send(sp, NetScanPayload.EMPTY);
            return;
        }
        send(sp, payload(sl, starts, sp));
    }

    public static NetScanPayload payload(ServerLevel level, Collection<BlockPos> starts, Player player) {
        DataNet.Trace t = DataNet.trace(level, starts);
        double r2 = (double) RANGE * RANGE;
        BlockPos at = player.blockPosition();
        long[] cables = near(level, t.cables(), at, r2);
        long[] devices = near(level, t.devices(), at, r2);
        List<NetScanPayload.Machine> machines = new ArrayList<>();
        List<DataSource> sources = new ArrayList<>(t.sources());
        java.util.Map<BlockPos, BlockPos> ports = new java.util.HashMap<>(t.ports());
        for (BlockPos s : starts) {
            if (!DataSources.isPort(level, s)) continue;
            DataSource own = DataSources.at(level, s);
            if (own != null && sources.stream().noneMatch(x -> x.pos().equals(own.pos()))) {
                sources.add(own);
                ports.put(own.pos(), s);
            }
        }
        for (DataSource s : sources) {
            if (machines.size() >= NetScanPayload.MAX) break;
            if (com.sqc.nuclearsq.compat.sable.SableCompat.distSqr(level, s.pos(), at) > r2) continue;
            BlockPos[] box = bounds(level, s.pos());
            machines.add(new NetScanPayload.Machine(s.pos(), box[0], box[1], ports.getOrDefault(s.pos(), s.pos()), s.state(), s.name()));
        }
        if (t.capped() && player instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable("data.nuclearsq.cap.msg", DataNet.MAX_CABLES).withStyle(net.minecraft.ChatFormatting.RED), true);
        }
        return new NetScanPayload(cables, devices, machines, t.incomplete());
    }

    private static long[] near(net.minecraft.world.level.Level level, List<BlockPos> list, BlockPos at, double r2) {
        long[] out = new long[Math.min(list.size(), NetScanPayload.MAX)];
        int n = 0;
        for (BlockPos p : list) {
            if (n >= out.length) break;
            if (com.sqc.nuclearsq.compat.sable.SableCompat.distSqr(level, p, at) <= r2) out[n++] = p.asLong();
        }
        return java.util.Arrays.copyOf(out, n);
    }

    static BlockPos[] bounds(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof BigMachineBlockEntity b && b.isFormed()) {
            BigMachine m = b.machine();
            int rot = Math.max(b.formedRot(), 0);
            int minX = Integer.MAX_VALUE, minY = minX, minZ = minX, maxX = Integer.MIN_VALUE, maxY = maxX, maxZ = maxX;
            for (int x : new int[] {0, m.w - 1}) for (int y : new int[] {0, m.h - 1}) for (int z : new int[] {0, m.d - 1}) {
                BlockPos c = m.structure.world(pos, rot, x, y, z);
                minX = Math.min(minX, c.getX()); maxX = Math.max(maxX, c.getX());
                minY = Math.min(minY, c.getY()); maxY = Math.max(maxY, c.getY());
                minZ = Math.min(minZ, c.getZ()); maxZ = Math.max(maxZ, c.getZ());
            }
            return new BlockPos[] {new BlockPos(minX, minY, minZ), new BlockPos(maxX, maxY, maxZ)};
        }
        return new BlockPos[] {pos, pos};
    }

    private static void send(ServerPlayer sp, NetScanPayload p) {
        PacketDistributor.sendToPlayer(sp, p);
    }

    public static boolean has(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).contains(TAG_POS);
    }

    private static void select(ItemStack stack, ServerLevel level, BlockPos pos) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putLong(TAG_POS, pos.asLong());
            tag.putString(TAG_DIM, level.dimension().location().toString());
        });
    }

    private static void clear(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.remove(TAG_POS);
            tag.remove(TAG_DIM);
        });
    }

    @Nullable
    public static BlockPos start(ItemStack stack, ServerLevel level) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!tag.contains(TAG_POS)) return null;
        if (!tag.getString(TAG_DIM).equals(level.dimension().location().toString())) return null;
        return BlockPos.of(tag.getLong(TAG_POS));
    }
}
