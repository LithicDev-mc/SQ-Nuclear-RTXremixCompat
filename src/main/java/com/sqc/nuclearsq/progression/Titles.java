package com.sqc.nuclearsq.progression;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.serialization.Codec;
import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
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

    public static final Supplier<AttachmentType<Integer>> CLEAN = TYPES.register("clean_power_built",
            () -> AttachmentType.<Integer>builder(() -> 0).serialize(Codec.INT).copyOnDeath().build());

    public static final Supplier<AttachmentType<Integer>> FOSSIL = TYPES.register("fossil_power_built",
            () -> AttachmentType.<Integer>builder(() -> 0).serialize(Codec.INT).copyOnDeath().build());

    public static final Supplier<AttachmentType<Long>> POWER_FE = TYPES.register("power_fe_total",
            () -> AttachmentType.<Long>builder(() -> 0L).serialize(Codec.LONG).copyOnDeath().build());

    public static final Supplier<AttachmentType<Boolean>> HIGH_DOSE = TYPES.register("high_dose_reached",
            () -> AttachmentType.<Boolean>builder(() -> false).serialize(Codec.BOOL).build());

    public static final double SURVIVOR_LOW_SV = 0.05;

    public static final long POWER_BARON_FE = 1_000_000_000L;

    public static final long DATA_HOARDER_ITEMS = 100_000L;

    public static final int POWER_RANK = 5;

    public static final Set<String> FOSSIL_MACHINES = Set.of("coal_generator", "gas_generator");

    public static final Set<String> CLEAN_MACHINES = Set.of("solar_field", "solar_panel", "wind_turbine_small", "wind_turbine_medium",
            "wind_turbine_large", "rtg", "turbine", "shaft_generator", "fuel_cell", "fusion_reactor");

    public static final Set<String> CHEM_MACHINES = Set.of("acid_plant", "polymer_plant", "oil_derrick", "distillation_column",
            "cracker", "gas_generator", "fluid_buffer");

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
    }

    private static final int[] STREAK_HOURS = {1, 10, 24, 100};

    public static List<String> ids() {
        List<String> out = new ArrayList<>(List.of("upcoming_engineer", "constructor", "first_power", "chemist",
                "environmental_engineer", "coal_mogul", "reactor_start", "reactor_newcomer", "reactor_operator", "turbine_power",
                "oil_baron", "fusion_pioneer", "waste_manager", "breeder", "chip_maker", "data_hoarder", "banker", "decon_specialist",
                "survey_pilot", "auctioneer", "iron_man", "survivor", "power_baron"));
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
        int clean = p.getData(CLEAN.get()), fossil = p.getData(FOSSIL.get());
        return switch (id) {
            case "upcoming_engineer" -> false;
            case "constructor" -> ms.stream().anyMatch(k -> k.startsWith("formed:"));
            case "chemist" -> ms.stream().anyMatch(k -> k.startsWith("formed:") && CHEM_MACHINES.contains(k.substring(7)));
            case "environmental_engineer" -> clean + fossil >= POWER_RANK && clean > fossil;
            case "coal_mogul" -> clean + fossil >= POWER_RANK && fossil > clean;
            case "first_power" -> ms.stream().anyMatch(k -> k.startsWith("power:"));
            case "reactor_start" -> ms.contains("reactor_power");
            case "reactor_newcomer" -> Streak.total(p) >= 5 * 1200;
            case "reactor_operator" -> Streak.total(p) >= 72000;
            case "turbine_power" -> ms.contains("power:turbine");
            case "oil_baron" -> ms.contains("rank:oil");
            case "fusion_pioneer" -> ms.contains("rank:fusion");
            case "waste_manager" -> ms.contains("rank:waste");
            case "breeder" -> ms.contains("rank:breeder");
            case "chip_maker" -> ms.contains("rank:chip");
            case "data_hoarder" -> ms.contains("rank:storage");
            case "banker" -> ms.contains("rank:bank");
            case "decon_specialist" -> ms.contains("rank:decon");
            case "survey_pilot" -> ms.contains("rank:survey");
            case "auctioneer" -> ms.contains("rank:auction");
            case "iron_man" -> java.util.Arrays.stream(com.sqc.nuclearsq.exo.ExoModule.Kind.values()).allMatch(k -> com.sqc.nuclearsq.exo.ExoEvents.level(p, k) > 0);
            case "survivor" -> survived(p);
            case "power_baron" -> p.getData(POWER_FE.get()) >= POWER_BARON_FE;
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
        return Component.translatable("title.nuclearsq." + id);
    }

    public static boolean check(ServerPlayer p) {
        boolean any = false;
        for (String id : ids()) if (!has(p, id) && reached(p, id)) any |= earn(p, id);
        return any;
    }

    private static boolean earn(ServerPlayer p, String id) {
        if (!GuideProgress.award(p, "title:" + id)) return false;
        if (p.connection != null) p.sendSystemMessage(Component.translatable("title.nuclearsq.earned", name(id)).withStyle(ChatFormatting.GOLD));
        if (p.getData(SELECTED.get()).isEmpty()) select(p, id);
        reward(p, id);
        return true;
    }

    public static boolean join(ServerPlayer p) {
        return earn(p, "upcoming_engineer");
    }

    private static boolean survived(ServerPlayer p) {
        double dose = p.getData(com.sqc.nuclearsq.radiation.RadiationAttachments.DOSE.get());
        if (p.isAlive() && dose >= com.sqc.nuclearsq.radiation.RadiationValues.SV_DAMAGE) p.setData(HIGH_DOSE.get(), true);
        return p.isAlive() && p.getData(HIGH_DOSE.get()) && dose <= SURVIVOR_LOW_SV;
    }

    @javax.annotation.Nullable
    private static ServerPlayer online(net.minecraft.world.level.Level level, @javax.annotation.Nullable java.util.UUID owner) {
        return owner == null || level.getServer() == null ? null : level.getServer().getPlayerList().getPlayer(owner);
    }

    public static void mark(net.minecraft.world.level.Level level, @javax.annotation.Nullable java.util.UUID owner, String key) {
        ServerPlayer p = online(level, owner);
        if (p != null) mark(p, key);
    }

    public static boolean mark(ServerPlayer p, String key) {
        return !GuideProgress.known(p, key) && GuideProgress.award(p, key);
    }

    public static void power(net.minecraft.world.level.Level level, @javax.annotation.Nullable java.util.UUID owner, long fe) {
        ServerPlayer p = online(level, owner);
        if (p != null) power(p, fe);
    }

    public static void power(ServerPlayer p, long fe) {
        if (fe > 0) p.setData(POWER_FE.get(), p.getData(POWER_FE.get()) + fe);
    }

    public static boolean placed(ServerPlayer p, Block b) {
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(b);
        if (!NuclearSQ.MODID.equals(key.getNamespace()) || !has(p, "first_power")) return false;
        String id = key.getPath().replace("_controller_sqc", "").replace("_sqc", "");
        boolean fossil = FOSSIL_MACHINES.contains(id);
        if (!fossil && !CLEAN_MACHINES.contains(id)) return false;
        var type = fossil ? FOSSIL : CLEAN;
        p.setData(type.get(), p.getData(type.get()) + 1);
        check(p);
        String sel = p.getData(SELECTED.get()), lead = reached(p, "coal_mogul") ? "coal_mogul" : reached(p, "environmental_engineer") ? "environmental_engineer" : sel;
        if ((sel.equals("coal_mogul") || sel.equals("environmental_engineer")) && !sel.equals(lead)) select(p, lead);
        return true;
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
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) join(sp);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        if (!(e instanceof BlockEvent.EntityMultiPlaceEvent m)) {
            placed(sp, e.getPlacedBlock().getBlock());
            return;
        }
        for (BlockSnapshot s : m.getReplacedBlockSnapshots()) placed(sp, e.getLevel().getBlockState(s.getPos()).getBlock());
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
