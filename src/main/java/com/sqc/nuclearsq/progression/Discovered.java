package com.sqc.nuclearsq.progression;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import com.sqc.nuclearsq.NuclearSQ;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@EventBusSubscriber(modid = NuclearSQ.MODID)
public final class Discovered {
    private Discovered() {}

    private static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, NuclearSQ.MODID);

    private static final Codec<Set<String>> CODEC = Codec.STRING.listOf().xmap(LinkedHashSet::new, List::copyOf);

    public static final Supplier<AttachmentType<Set<String>>> ITEMS = TYPES.register("discovered_items",
            () -> AttachmentType.<Set<String>>builder(() -> new LinkedHashSet<>()).serialize(CODEC).copyOnDeath().build());

    private static volatile Set<String> client = Set.of();

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }

    public static boolean known(String path) {
        return client.contains(path);
    }

    public static boolean known(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return id.getNamespace().equals(NuclearSQ.MODID) && known(id.getPath());
    }

    public static boolean known(ServerPlayer p, String path) {
        return p.getData(ITEMS.get()).contains(path);
    }

    public static boolean scan(ServerPlayer p) {
        Set<String> seen = p.getData(ITEMS.get());
        boolean added = false;
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty()) continue;
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(st.getItem());
            if (id.getNamespace().equals(NuclearSQ.MODID)) added |= seen.add(id.getPath());
        }
        if (added) {
            p.setData(ITEMS.get(), seen);
            sync(p);
        }
        return added;
    }

    public static void sync(ServerPlayer p) {
        PacketDistributor.sendToPlayer(p, new Payload(new ArrayList<>(p.getData(ITEMS.get()))));
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer sp && sp.tickCount % 20 == 7) scan(sp);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) sync(sp);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) sync(sp);
    }

    public record Payload(List<String> items) implements CustomPacketPayload {
        public static final Type<Payload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "discovered_items"));
        public static final StreamCodec<ByteBuf, Payload> STREAM_CODEC =
                StreamCodec.composite(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), Payload::items, Payload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void receive(Payload p) {
        client = Set.copyOf(p.items());
    }
}
