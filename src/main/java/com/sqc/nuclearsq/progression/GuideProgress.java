package com.sqc.nuclearsq.progression;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.machine.big.BigMachineBlockEntity;
import com.sqc.nuclearsq.registry.ModCreativeTabs;
import com.sqc.nuclearsq.transport.BigPipeBlock;
import com.sqc.nuclearsq.transport.ConduitBlock;
import com.sqc.nuclearsq.transport.PowerGateBlock;
import com.sqc.nuclearsq.transport.PowerMastBlock;
import com.sqc.nuclearsq.turbine.TurbineControllerBlockEntity;

import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@EventBusSubscriber(modid = NuclearSQ.MODID)
public final class GuideProgress {
    private GuideProgress() {}

    private static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, NuclearSQ.MODID);

    private static final Codec<Set<String>> CODEC = Codec.STRING.listOf().xmap(LinkedHashSet::new, List::copyOf);

    public static final Supplier<AttachmentType<Set<String>>> MILESTONES = TYPES.register("guide_milestones",
            () -> AttachmentType.<Set<String>>builder(() -> new LinkedHashSet<>()).serialize(CODEC).copyOnDeath().build());

    public static final Supplier<AttachmentType<Boolean>> BOOK_GIVEN = TYPES.register("book_given",
            () -> AttachmentType.<Boolean>builder(() -> false).serialize(Codec.BOOL).copyOnDeath().build());

    public static final Supplier<AttachmentType<Boolean>> TEXTURE_HINT_SHOWN = TYPES.register("texture_hint_shown",
            () -> AttachmentType.<Boolean>builder(() -> false).serialize(Codec.BOOL).copyOnDeath().build());

    public static final int SCAN_CHUNKS = 6;

    private static volatile Set<String> client = Set.of();

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }

    public static boolean known(String key) {
        return client.contains(key);
    }

    public static boolean known(ServerPlayer p, String key) {
        return p.getData(MILESTONES.get()).contains(key);
    }

    public static boolean award(ServerPlayer p, String key) {
        Set<String> seen = p.getData(MILESTONES.get());
        if (!seen.add(key)) return false;
        p.setData(MILESTONES.get(), seen);
        sync(p);
        return true;
    }

    public static boolean scan(ServerPlayer p) {
        if (!(p.level() instanceof ServerLevel level)) return false;
        Set<String> found = new LinkedHashSet<>();
        int pcx = SectionPos.blockToSectionCoord(p.getBlockX()), pcz = SectionPos.blockToSectionCoord(p.getBlockZ());
        for (int cx = pcx - SCAN_CHUNKS; cx <= pcx + SCAN_CHUNKS; cx++) {
            for (int cz = pcz - SCAN_CHUNKS; cz <= pcz + SCAN_CHUNKS; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (BlockEntity be : List.copyOf(chunk.getBlockEntities().values())) {
                    if (be instanceof BigMachineBlockEntity m) look(m, found);
                    else if (be instanceof TurbineControllerBlockEntity t && t.isFormed()) {
                        found.add("formed:turbine");
                        if (t.lastFe() > 0) found.add("power:turbine");
                    }
                }
            }
        }
        Set<String> seen = p.getData(MILESTONES.get());
        boolean added = false;
        for (String k : found) added |= seen.add(k);
        if (added) {
            p.setData(MILESTONES.get(), seen);
            sync(p);
        }
        return added;
    }

    private static void look(BigMachineBlockEntity m, Set<String> out) {
        if (!m.isFormed()) return;
        String id = m.machine().id;
        out.add("formed:" + id);
        if (m.lastOut() > 0) out.add("power:" + id);
        if (m.rodCount() > 0) out.add("rods");
        if (m.reactorPower() > 0) out.add("reactor_power");
    }

    public static void sync(ServerPlayer p) {
        if (p.connection == null) return;
        PacketDistributor.sendToPlayer(p, new Payload(new ArrayList<>(p.getData(MILESTONES.get()))));
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer sp && sp.tickCount % 40 == 13) scan(sp);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        sync(sp);
        giveBook(sp);
        if (sp.getStats().getValue(Stats.CUSTOM.get(Stats.LEAVE_GAME)) == 0) welcome(sp);
    }

    public static boolean giveBook(ServerPlayer sp) {
        if (sp.getData(BOOK_GIVEN.get())) return false;
        sp.setData(BOOK_GIVEN.get(), true);
        if (sp.getInventory().countItem(ModCreativeTabs.PROGRESSION_BOOK.get()) > 0) return false;
        ItemStack book = new ItemStack(ModCreativeTabs.PROGRESSION_BOOK.get());
        if (!sp.getInventory().add(book)) sp.drop(book, false);
        return true;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || sp.getData(TEXTURE_HINT_SHOWN.get())) return;
        boolean machine = isMachine(e.getPlacedBlock());
        if (!machine && e instanceof BlockEvent.EntityMultiPlaceEvent m) {
            for (BlockSnapshot s : m.getReplacedBlockSnapshots()) {
                if (isMachine(e.getLevel().getBlockState(s.getPos()))) {
                    machine = true;
                    break;
                }
            }
        }
        if (machine) textureHint(sp);
    }

    public static boolean isMachine(BlockState st) {
        Block b = st.getBlock();
        return b instanceof EntityBlock && NuclearSQ.MODID.equals(BuiltInRegistries.BLOCK.getKey(b).getNamespace())
                && !(b instanceof ConduitBlock || b instanceof BigPipeBlock || b instanceof PowerMastBlock || b instanceof PowerGateBlock);
    }

    public static boolean textureHint(ServerPlayer sp) {
        if (sp.getData(TEXTURE_HINT_SHOWN.get())) return false;
        sp.setData(TEXTURE_HINT_SHOWN.get(), true);
        Component cmd = Component.literal("/nsqperf chunk").withStyle(s -> s
                .withColor(ChatFormatting.AQUA).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/nsqperf chunk")));
        sp.sendSystemMessage(Component.translatable("message.nuclearsq.texture_hint", cmd).withStyle(ChatFormatting.GRAY));
        return true;
    }

    private static final String DISCORD = "https://discord.gg/9DUcANyAvV";

    private static void welcome(ServerPlayer sp) {
        Component link = Component.literal(DISCORD).withStyle(s -> s
                .withColor(ChatFormatting.AQUA).withUnderlined(true)
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, DISCORD)));
        sp.sendSystemMessage(Component.translatable("message.nuclearsq.welcome").withStyle(ChatFormatting.GREEN));
        sp.sendSystemMessage(Component.translatable("message.nuclearsq.welcome_discord", link));
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) sync(sp);
    }

    public record Payload(List<String> keys) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "guide_milestones"));
        public static final StreamCodec<ByteBuf, Payload> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Payload::keys, Payload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void receive(Payload p) {
        client = Set.copyOf(p.keys());
    }
}
