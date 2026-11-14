package com.sqc.nuclearsq.progression;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.Codec;
import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

@EventBusSubscriber(modid = NuclearSQ.MODID)
public final class Titles {
    private Titles() {}

    private static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, NuclearSQ.MODID);

    public static final Supplier<AttachmentType<String>> SELECTED = TYPES.register("selected_title",
            () -> AttachmentType.<String>builder(() -> "").serialize(Codec.STRING).copyOnDeath().build());

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }

    private static final int[] STREAK_HOURS = {1, 10, 24, 100};

    public static List<String> ids() {
        List<String> out = new ArrayList<>(List.of("first_power", "reactor_start", "turbine_power"));
        for (var s : AchievementTree.STAGES) out.add("stage_" + s.key());
        for (var l : LineTrees.LINES) out.add("line_" + l.key());
        for (int h : STREAK_HOURS) out.add("streak_" + h + "h");
        return out;
    }

    public static boolean has(ServerPlayer p, String id) {
        return GuideProgress.known(p, "title:" + id);
    }

    private static boolean reached(ServerPlayer p, String id) {
        var ms = p.getData(GuideProgress.MILESTONES.get());
        return switch (id) {
            case "first_power" -> ms.stream().anyMatch(k -> k.startsWith("power:"));
            case "reactor_start" -> ms.contains("reactor_power");
            case "turbine_power" -> ms.contains("power:turbine");
            default -> id.startsWith("streak_") ? Streak.ticks(p) >= Integer.parseInt(id.substring(7, id.length() - 1)) * 72000
                    : id.startsWith("line_") ? lineBuilt(p, id.substring("line_".length()))
                    : stageBuilt(p, id.substring("stage_".length()));
        };
    }

    private static boolean stageBuilt(ServerPlayer p, String stage) {
        var nodes = AchievementTree.NODES;
        boolean in = false;
        for (int k = 0; k < nodes.size(); k++) {
            var st = AchievementTree.stageAt(k);
            if (st != null) in = st.key().equals(stage);
            if (!in) continue;
            boolean built = false;
            for (String item : AchievementTree.proofItems(nodes.get(k))) built |= Discovered.known(p, item);
            if (!built) return false;
        }
        return true;
    }

    private static boolean lineBuilt(ServerPlayer p, String key) {
        for (var l : LineTrees.LINES) {
            if (!l.key().equals(key)) continue;
            for (var b : l.blocks())
                for (String k : b.keys()) {
                    boolean built = false;
                    for (String item : AchievementTree.proofItems(AchievementTree.NODES.get(AchievementTree.index(k)))) built |= Discovered.known(p, item);
                    if (!built) return false;
                }
            return true;
        }
        return false;
    }

    public static Component name(String id) {
        if (id.startsWith("line_")) return Component.translatable("title.nuclearsq.line", Component.translatable("compendium." + id + ".title"));
        return id.startsWith("stage_")
                ? Component.translatable("title.nuclearsq.stage", Component.translatable("compendium.ach.stage." + id.substring(6)))
                : Component.translatable("title.nuclearsq." + id);
    }

    public static boolean check(ServerPlayer p) {
        boolean any = false;
        for (String id : ids()) {
            if (has(p, id) || !reached(p, id) || !GuideProgress.award(p, "title:" + id)) continue;
            any = true;
            if (p.connection != null) p.sendSystemMessage(Component.translatable("title.nuclearsq.earned", name(id)).withStyle(ChatFormatting.GOLD));
            if (p.getData(SELECTED.get()).isEmpty()) select(p, id);
            reward(p, id);
        }
        return any;
    }

    private static void reward(ServerPlayer p, String id) {
        if (!id.startsWith("stage_")) return;
        for (var item : List.of(Items.IRON_INGOT, Items.COPPER_INGOT, Items.REDSTONE)) ItemHandlerHelper.giveItemToPlayer(p, new ItemStack(item, 16));
        if (!id.equals("stage_" + AchievementTree.STAGES.get(AchievementTree.STAGES.size() - 1).key())) return;
        ItemStack trophy = new ItemStack(Items.GOLD_BLOCK);
        trophy.set(DataComponents.CUSTOM_NAME, Component.translatable("title.nuclearsq.trophy").withStyle(s -> s.withItalic(false).withColor(ChatFormatting.GOLD)));
        ItemHandlerHelper.giveItemToPlayer(p, trophy);
    }

    public static void select(ServerPlayer p, String id) {
        p.setData(SELECTED.get(), id);
        p.refreshDisplayName();
        p.refreshTabListName();
    }

    private static Component prefixed(ServerPlayer p, Component name) {
        String id = p.getData(SELECTED.get());
        if (id.isEmpty()) return name;
        return Component.empty().append(Component.literal("[").append(name(id)).append("] ").withStyle(ChatFormatting.GOLD)).append(name);
    }

    @SubscribeEvent
    public static void onTick(PlayerTickEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer sp && sp.tickCount % 100 == 21) check(sp);
    }

    @SubscribeEvent
    public static void onName(PlayerEvent.NameFormat e) {
        if (e.getEntity() instanceof ServerPlayer sp) e.setDisplayname(prefixed(sp, e.getDisplayname()));
    }

    @SubscribeEvent
    public static void onTabName(PlayerEvent.TabListNameFormat e) {
        if (e.getEntity() instanceof ServerPlayer sp) e.setDisplayName(prefixed(sp, e.getDisplayName() != null ? e.getDisplayName() : sp.getName()));
    }

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("nsqtitle")
                .executes(c -> {
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    var own = ids().stream().filter(i -> has(sp, i)).map(i -> i + " = " + name(i).getString()).toList();
                    c.getSource().sendSuccess(() -> Component.translatable("title.nuclearsq.list", own.isEmpty() ? "-" : String.join(", ", own)), false);
                    int min = Streak.ticks(sp) / 1200;
                    c.getSource().sendSuccess(() -> Component.translatable("title.nuclearsq.streak", min / 60, min % 60), false);
                    return own.size();
                })
                .then(Commands.argument("id", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                ids().stream().filter(i -> c.getSource().getPlayer() != null && has(c.getSource().getPlayer(), i)), b))
                        .executes(c -> {
                            ServerPlayer sp = c.getSource().getPlayerOrException();
                            String id = StringArgumentType.getString(c, "id");
                            if (!id.equals("none") && !(ids().contains(id) && has(sp, id))) {
                                c.getSource().sendFailure(Component.translatable("title.nuclearsq.unknown"));
                                return 0;
                            }
                            select(sp, id.equals("none") ? "" : id);
                            c.getSource().sendSuccess(() -> Component.translatable("title.nuclearsq.selected", id.equals("none") ? "-" : name(id).getString()), false);
                            return 1;
                        })));
    }
}
