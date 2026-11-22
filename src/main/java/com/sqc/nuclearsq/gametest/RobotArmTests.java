package com.sqc.nuclearsq.gametest;

import com.mojang.authlib.GameProfile;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.robotics.RobotArmBlockEntity;
import com.sqc.nuclearsq.robotics.RoboticsRegistry;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(NuclearSQ.MODID)
@PrefixGameTestTemplate(false)
public final class RobotArmTests {
    private RobotArmTests() {}

    private static final String FLOOR = "floor";
    private static final BlockPos SRC = new BlockPos(1, 4, 2), DST = new BlockPos(4, 4, 2), ARM = new BlockPos(2, 4, 4);

    private static void upgrade(RobotArmBlockEntity arm, int n) {
        for (int i = 0; i < RobotArmBlockEntity.UPGRADE_SLOTS; i++)
            arm.upgrades().setItem(i, i < n ? new ItemStack(RoboticsRegistry.ARM_UPGRADE.get()) : ItemStack.EMPTY);
    }

    private static RobotArmBlockEntity setup(GameTestHelper h, boolean powered, BlockPos dst) {
        for (int x = 0; x <= 12; x++) for (int z = 0; z <= 12; z++) for (int y = 4; y <= 6; y++) h.setBlock(new BlockPos(x, y, z), Blocks.AIR);
        h.setBlock(SRC, Blocks.CHEST);
        h.setBlock(dst, Blocks.CHEST);
        h.setBlock(ARM, RoboticsRegistry.ARM.get());
        RobotArmBlockEntity arm = (RobotArmBlockEntity) h.getLevel().getBlockEntity(h.absolutePos(ARM));
        if (arm == null) throw new GameTestAssertException("Greifarm hat keine BlockEntity");
        while (powered && arm.energy().receiveEnergy(RobotArmBlockEntity.BUFFER, false) > 0) { }
        arm.setSource(h.absolutePos(SRC), Direction.UP);
        arm.setTarget(h.absolutePos(dst), Direction.UP);
        return arm;
    }

    private static int count(GameTestHelper h, BlockPos rel, net.minecraft.world.item.Item item) {
        Container c = (Container) h.getLevel().getBlockEntity(h.absolutePos(rel));
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(item)) n += c.getItem(i).getCount();
        return n;
    }

    private static void fill(GameTestHelper h, BlockPos rel, int slot, ItemStack st) {
        ((Container) h.getLevel().getBlockEntity(h.absolutePos(rel))).setItem(slot, st);
    }

    @GameTest(template = FLOOR, batch = "robot_arm_drop", timeoutTicks = 20)
    public static void dropsItemWhenBroken(GameTestHelper h) {
        for (GameType mode : new GameType[] {GameType.SURVIVAL, GameType.CREATIVE}) {
            BlockPos pos = h.absolutePos(mode == GameType.SURVIVAL ? new BlockPos(2, 4, 2) : new BlockPos(8, 4, 8));
            h.getLevel().setBlockAndUpdate(pos, RoboticsRegistry.HEAVY_ARM.get().defaultBlockState());
            var p = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.nameUUIDFromBytes(("arm_drop_" + mode).getBytes()), "arm_drop_" + mode));
            p.setGameMode(mode);
            p.gameMode.destroyBlock(pos);
            if (h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(1.5), e -> e.getItem().is(RoboticsRegistry.HEAVY_ARM_ITEM.get())).size() != 1)
                throw new GameTestAssertException("Greifarm liegt nach dem Abbauen nicht am Boden (" + mode + ")");
        }
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "robot_arm", timeoutTicks = 60)
    public static void movesChestToChest(GameTestHelper h) {
        setup(h, true, DST);
        fill(h, SRC, 0, new ItemStack(Items.COBBLESTONE, 20));
        h.runAfterDelay(5, () -> {
            int moved = count(h, DST, Items.COBBLESTONE);
            if (moved != 1) throw new GameTestAssertException("Ohne Upgrade bewegte der erste Zug " + moved + " statt 1");
            if (count(h, SRC, Items.COBBLESTONE) != 20 - moved) throw new GameTestAssertException("Quelle passt nicht");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "robot_arm", timeoutTicks = 60)
    public static void filterOnlyMovesMatching(GameTestHelper h) {
        RobotArmBlockEntity arm = setup(h, true, DST);
        upgrade(arm, 2);
        arm.setFilter(new ItemStack(Items.STICK));
        fill(h, SRC, 0, new ItemStack(Items.COBBLESTONE, 5));
        fill(h, SRC, 1, new ItemStack(Items.STICK, 3));
        h.runAfterDelay(5, () -> {
            if (count(h, DST, Items.STICK) != 3) throw new GameTestAssertException("Stoecke nicht angekommen");
            if (count(h, DST, Items.COBBLESTONE) != 0) throw new GameTestAssertException("Filter liess Bruchstein durch");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "robot_arm", timeoutTicks = 60)
    public static void needsPower(GameTestHelper h) {
        setup(h, false, DST);
        fill(h, SRC, 0, new ItemStack(Items.COBBLESTONE, 5));
        h.runAfterDelay(25, () -> {
            if (count(h, DST, Items.COBBLESTONE) != 0) throw new GameTestAssertException("Ohne Strom wurde bewegt");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "robot_arm", timeoutTicks = 60)
    public static void ignoresOutOfReach(GameTestHelper h) {
        setup(h, true, new BlockPos(11, 4, 11));
        fill(h, SRC, 0, new ItemStack(Items.COBBLESTONE, 5));
        h.runAfterDelay(25, () -> {
            if (count(h, new BlockPos(11, 4, 11), Items.COBBLESTONE) != 0) throw new GameTestAssertException("Ziel ausser Reichweite wurde bedient");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "robot_arm", timeoutTicks = 60)
    public static void redstonePauses(GameTestHelper h) {
        setup(h, true, DST);
        h.setBlock(ARM.east(), Blocks.REDSTONE_BLOCK);
        fill(h, SRC, 0, new ItemStack(Items.COBBLESTONE, 5));
        h.runAfterDelay(25, () -> {
            if (count(h, DST, Items.COBBLESTONE) != 0) throw new GameTestAssertException("Mit Redstone wurde bewegt");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "robot_arm", timeoutTicks = 60)
    public static void filterListMatchesAnyEntry(GameTestHelper h) {
        RobotArmBlockEntity arm = setup(h, true, DST);
        upgrade(arm, 2);
        arm.setFilter(new ItemStack(Items.STICK));
        arm.setFilter(new ItemStack(Items.COBBLESTONE));
        arm.setFilter(new ItemStack(Items.STICK));
        fill(h, SRC, 0, new ItemStack(Items.DIRT, 4));
        fill(h, SRC, 1, new ItemStack(Items.COBBLESTONE, 2));
        fill(h, SRC, 2, new ItemStack(Items.STICK, 3));
        h.runAfterDelay(5, () -> {
            if (count(h, DST, Items.DIRT) != 0) throw new GameTestAssertException("Filterliste liess Erde durch");
            if (count(h, DST, Items.COBBLESTONE) != 2 || count(h, DST, Items.STICK) != 3) throw new GameTestAssertException("Filterliste lieferte nicht beide Eintraege");
            int entries = 0;
            for (int i = 0; i < RobotArmBlockEntity.FILTER_SLOTS; i++) if (!arm.filters().getItem(i).isEmpty()) entries++;
            if (entries != 2) throw new GameTestAssertException("Filter hat " + entries + " Eintraege statt 2");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "robot_arm", timeoutTicks = 60)
    public static void menuButtonsWork(GameTestHelper h) {
        RobotArmBlockEntity arm = setup(h, true, DST);
        var player = FakePlayerFactory.getMinecraft(h.getLevel());
        var menu = (com.sqc.nuclearsq.robotics.RobotArmMenu) arm.createMenu(1, player.getInventory(), player);
        if (menu.data(4) != 1 || menu.data(8) != 1) throw new GameTestAssertException("Menue kennt Quelle/Ziel nicht");
        menu.clickMenuButton(player, com.sqc.nuclearsq.robotics.RobotArmMenu.BTN_TOGGLE);
        if (arm.enabled()) throw new GameTestAssertException("Knopf Ausschalten wirkte nicht");
        menu.clickMenuButton(player, com.sqc.nuclearsq.robotics.RobotArmMenu.BTN_CLEAR_TARGET);
        if (arm.target() != null) throw new GameTestAssertException("Ziel nicht geloescht");
        menu.clickMenuButton(player, com.sqc.nuclearsq.robotics.RobotArmMenu.BTN_CLEAR_SOURCE);
        if (arm.source() != null) throw new GameTestAssertException("Quelle nicht geloescht");
        arm.setFilter(new ItemStack(Items.STICK));
        menu.clickMenuButton(player, com.sqc.nuclearsq.robotics.RobotArmMenu.BTN_CLEAR_FILTER);
        if (!arm.filters().isEmpty()) throw new GameTestAssertException("Filter nicht geleert");
        menu.setCarried(new ItemStack(Items.STICK, 5));
        menu.clicked(0, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
        if (!arm.filters().getItem(0).is(Items.STICK) || arm.filters().getItem(0).getCount() != 1 || menu.getCarried().getCount() != 5)
            throw new GameTestAssertException("Geisterplatz setzte den Filter falsch");
        menu.setCarried(ItemStack.EMPTY);
        menu.clicked(0, 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
        if (!arm.filters().isEmpty()) throw new GameTestAssertException("Geisterplatz loeschte den Filter nicht");
        h.succeed();
    }

    private static net.minecraft.world.InteractionResult tool(GameTestHelper h, net.minecraft.world.entity.player.Player player, ItemStack tool, BlockPos rel, boolean shift) {
        player.setShiftKeyDown(shift);
        var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(h.absolutePos(rel)), Direction.UP, h.absolutePos(rel), false);
        var ctx = new net.minecraft.world.item.context.UseOnContext(h.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND, tool, hit);
        return tool.getItem().onItemUseFirst(tool, ctx);
    }

    @GameTest(template = FLOOR, batch = "robot_arm", timeoutTicks = 60)
    public static void linkToolArmFirst(GameTestHelper h) {
        RobotArmBlockEntity arm = setup(h, true, DST);
        arm.clearLinks();
        upgrade(arm, 2);
        var player = FakePlayerFactory.getMinecraft(h.getLevel());
        ItemStack t = new ItemStack(RoboticsRegistry.LINK_TOOL.get());
        tool(h, player, t, ARM, false);
        tool(h, player, t, SRC, false);
        tool(h, player, t, DST, true);
        tool(h, player, t, ARM, false);
        if (!h.absolutePos(SRC).equals(arm.source()) || !h.absolutePos(DST).equals(arm.target())) throw new GameTestAssertException("Quelle/Ziel nicht gesetzt oder wieder geloescht");
        fill(h, SRC, 0, new ItemStack(Items.COBBLESTONE, 5));
        h.runAfterDelay(5, () -> {
            if (count(h, DST, Items.COBBLESTONE) != 5) throw new GameTestAssertException("Kiste nach Kiste kam nicht an");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "robot_arm", timeoutTicks = 60)
    public static void linkToolChestsFirst(GameTestHelper h) {
        RobotArmBlockEntity arm = setup(h, true, DST);
        arm.clearLinks();
        var player = FakePlayerFactory.getMinecraft(h.getLevel());
        ItemStack t = new ItemStack(RoboticsRegistry.LINK_TOOL.get());
        tool(h, player, t, SRC, false);
        tool(h, player, t, DST, true);
        if (arm.source() != null || arm.target() != null) throw new GameTestAssertException("Ohne Arm darf nichts gesetzt sein");
        tool(h, player, t, ARM, false);
        if (!h.absolutePos(SRC).equals(arm.source()) || !h.absolutePos(DST).equals(arm.target())) throw new GameTestAssertException("Gemerkte Punkte wurden nicht uebernommen");
        if (t.get(RoboticsRegistry.LINK_PENDING.get()) != null) throw new GameTestAssertException("Gemerkte Punkte blieben im Werkzeug");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "robot_arm", timeoutTicks = 60)
    public static void armSyncsLinksToClient(GameTestHelper h) {
        RobotArmBlockEntity arm = setup(h, true, DST);
        var tag = arm.getUpdateTag(h.getLevel().registryAccess());
        if (!tag.contains("src") || !tag.contains("dst")) throw new GameTestAssertException("Update-Tag enthaelt Quelle/Ziel nicht");
        if (arm.getUpdatePacket() == null) throw new GameTestAssertException("Kein Update-Paket");
        if (arm.animTicks() < 6 || arm.animTicks() > RobotArmBlockEntity.PERIOD[1]) throw new GameTestAssertException("Animationsdauer unsinnig");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "robot_arm", timeoutTicks = 60)
    public static void moveEventReachesClients(GameTestHelper h) {
        RobotArmBlockEntity arm = setup(h, true, DST);
        if (!arm.triggerEvent(1, 0)) throw new GameTestAssertException("Server bestaetigt das Zug-Event nicht, es ginge nie an die Clients");
        if (!h.getLevel().getBlockState(h.absolutePos(ARM)).triggerEvent(h.getLevel(), h.absolutePos(ARM), 1, 0))
            throw new GameTestAssertException("Block leitet das Zug-Event nicht an die BlockEntity weiter");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "robot_arm", timeoutTicks = 60)
    public static void upgradesStepTheAmount(GameTestHelper h) {
        RobotArmBlockEntity arm = setup(h, true, DST);
        int[] want = {1, 4, 16, 32, 64};
        for (int n = 0; n <= RobotArmBlockEntity.UPGRADE_SLOTS; n++) {
            upgrade(arm, n);
            if (arm.amount() != want[n]) throw new GameTestAssertException(n + " Upgrades geben " + arm.amount() + " statt " + want[n]);
        }
        var player = FakePlayerFactory.getMinecraft(h.getLevel());
        var menu = (com.sqc.nuclearsq.robotics.RobotArmMenu) arm.createMenu(1, player.getInventory(), player);
        var slot = menu.getSlot(RobotArmBlockEntity.FILTER_SLOTS);
        if (slot.mayPlace(new ItemStack(Items.STICK)) || !slot.mayPlace(new ItemStack(RoboticsRegistry.ARM_UPGRADE.get())))
            throw new GameTestAssertException("Upgrade-Platz nimmt das Falsche oder verweigert das Upgrade");
        h.succeed();
    }

    @GameTest(template = FLOOR, batch = "robot_arm", timeoutTicks = 60)
    public static void upgradedArmMovesMore(GameTestHelper h) {
        RobotArmBlockEntity arm = setup(h, true, DST);
        upgrade(arm, 4);
        fill(h, SRC, 0, new ItemStack(Items.COBBLESTONE, 64));
        h.runAfterDelay(5, () -> {
            int moved = count(h, DST, Items.COBBLESTONE);
            if (moved != 64) throw new GameTestAssertException("Mit 4 Upgrades kamen " + moved + " statt 64 an");
            h.succeed();
        });
    }

    @GameTest(template = FLOOR, batch = "robot_arm", timeoutTicks = 100)
    public static void deliversRoundRobinToTwoTargets(GameTestHelper h) {
        BlockPos dst2 = new BlockPos(3, 4, 6);
        RobotArmBlockEntity arm = setup(h, true, DST);
        h.setBlock(dst2, Blocks.CHEST);
        if (!arm.setTarget(h.absolutePos(dst2), Direction.UP) || arm.targets().size() != 2) throw new GameTestAssertException("Zweites Ziel nicht hinzugefuegt");
        fill(h, SRC, 0, new ItemStack(Items.COBBLESTONE, 20));
        h.runAfterDelay(4 * RobotArmBlockEntity.PERIOD[1] + 5, () -> {
            int a = count(h, DST, Items.COBBLESTONE), b = count(h, dst2, Items.COBBLESTONE);
            if (a < 2 || b < 2 || Math.abs(a - b) > 1) throw new GameTestAssertException("Nicht reihum verteilt: " + a + " / " + b);
            if (arm.setTarget(h.absolutePos(dst2), Direction.UP) || arm.targets().size() != 1) throw new GameTestAssertException("Ziel nicht wieder entfernt");
            h.succeed();
        });
    }
}
