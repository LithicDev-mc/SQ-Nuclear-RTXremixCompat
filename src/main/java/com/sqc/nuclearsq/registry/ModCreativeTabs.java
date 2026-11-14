package com.sqc.nuclearsq.registry;

import com.sqc.nuclearsq.progression.ProgressionBookItem;

import com.sqc.nuclearsq.NuclearSQ;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    private ModCreativeTabs() {}

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, NuclearSQ.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NuclearSQ.MODID);

    public static final DeferredItem<Item> SPACER = ITEMS.registerSimpleItem("spacer_sqc");
    private static final DeferredItem<Item> H_ORES = ITEMS.registerSimpleItem("header_ores_sqc");
    private static final DeferredItem<Item> H_PARTS = ITEMS.registerSimpleItem("header_parts_sqc");
    private static final DeferredItem<Item> H_HULLS = ITEMS.registerSimpleItem("header_hulls_sqc");
    private static final DeferredItem<Item> H_WATER = ITEMS.registerSimpleItem("header_water_sqc");
    private static final DeferredItem<Item> H_TRANSPORT = ITEMS.registerSimpleItem("header_transport_sqc");
    private static final DeferredItem<Item> H_DATA = ITEMS.registerSimpleItem("header_data_sqc");
    private static final DeferredItem<Item> H_FUEL = ITEMS.registerSimpleItem("header_fuel_sqc");
    private static final DeferredItem<Item> H_PROTECTION = ITEMS.registerSimpleItem("header_protection_sqc");
    private static final DeferredItem<Item> H_MODULES = ITEMS.registerSimpleItem("header_modules_sqc");
    private static final DeferredItem<Item> H_MACHINES = ITEMS.registerSimpleItem("header_machines_sqc");

    public static final DeferredItem<Item> PROGRESSION_BOOK = ITEMS.register("progression_book_sqc",
            () -> new ProgressionBookItem(new Item.Properties().stacksTo(1)));

    private static int count;
    private static int spacerId;

    private static void pad(CreativeModeTab.Output out, String group) {
        ItemStack st = new ItemStack(SPACER.get());
        final int n = spacerId++;
        CustomData.update(DataComponents.CUSTOM_DATA, st, tag -> {
            tag.putInt("n", n);
            if (group != null) tag.putString("g", group);
        });
        out.accept(st, CreativeModeTab.TabVisibility.PARENT_TAB_ONLY);
    }

    private static ItemLike b(String id) { return CatalogRegistry.BLOCK_ITEM_MAP.get(id).get(); }
    private static ItemLike i(String id) { return CatalogRegistry.ITEM_MAP.get(id).get(); }
    private static ItemLike kit(String id) { return com.sqc.nuclearsq.kit.KitRegistry.KITS.get(id).get(); }

    private static void section(CreativeModeTab.Output out, DeferredItem<Item> header, ItemLike... items) {
        while (count % 9 != 0) { pad(out, null); count++; }
        String group = header.getId().getPath().replace("header_", "").replace("_sqc", "");
        out.accept(header.get(), CreativeModeTab.TabVisibility.PARENT_TAB_ONLY); count++;
        while (count % 9 != 0) { pad(out, group); count++; }
        for (ItemLike it : items) { out.accept(it); count++; }
    }

    private static void row(CreativeModeTab.Output out) {
        while (count % 9 != 0) { pad(out, null); count++; }
    }

    private static void groups(CreativeModeTab.Output out, DeferredItem<Item> header, java.util.List<java.util.List<ItemLike>> groups) {
        section(out, header);
        for (java.util.List<ItemLike> g : groups) {
            row(out);
            for (ItemLike it : g) { out.accept(it); count++; }
        }
    }

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.nuclearsq.main"))
            .withTabsBefore(CreativeModeTabs.COMBAT)
            .icon(() -> new ItemStack(ReactorRegistry.FUEL_ROD_BLOCK_ITEM.get()))
            .displayItems((params, out) -> {
                count = 0;
                spacerId = 0;
                java.util.List<ItemLike> ores = new java.util.ArrayList<>(ResourceRegistry.tabItems());
                ores.addAll(FusionRegistry.tabItems());
                ores.add(com.sqc.nuclearsq.oil.OilRegistry.WELL_ITEM.get());
                ores.addAll(java.util.List.of(ReactorRegistry.URANIUM_FRAGMENT.get(), ReactorRegistry.YELLOWCAKE.get(), ReactorRegistry.ENRICHED_URANIUM.get(),
                        ReactorRegistry.URANIUM_OXIDE_DUST.get(), ReactorRegistry.URANIUM_OXIDE.get(), ReactorRegistry.FUEL_ROD.get(),
                        ReactorRegistry.IRON_DUST.get(), ReactorRegistry.GOLD_DUST.get(), ReactorRegistry.COPPER_DUST.get(),
                        i("zirconium_dust_sqc"), i("zirconium_ingot_sqc"), kit("ore_mill"), kit("acid_plant"), kit("boric_acid_plant"), kit("ion_exchanger"), kit("heavy_water_plant"), i("heavy_water_cell_sqc"), kit("polymer_plant"), kit("oil_derrick"), kit("distillation_column"), kit("cracker"), i("rubber_sqc"), i("rubber_seal_sqc"), i("photoresist_sqc"), i("coke_sqc"), i("silicon_raw_sqc"), i("silicon_pure_sqc"), i("silicon_wafer_sqc"), i("chip_1_sqc"), i("chip_2_sqc"), i("chip_3_sqc"), kit("vitrification_plant"), kit("chemical_lab"), i("lubricant_sqc"), i("glass_canister_sqc"), kit("leaching_plant"), ReactorRegistry.URANIUM_SLUDGE.get(), kit("uf6_converter"), ReactorRegistry.UF6_CYLINDER.get(),
                        kit("enrichment_centrifuge"), i("depleted_uranium_sqc"), kit("pellet_factory"), ReactorRegistry.URANIUM_PELLET.get(), kit("foundry"), kit("slag_crusher"), kit("plate_mill"), kit("hammer_mill")));
                section(out, H_ORES, ores.toArray(new ItemLike[0]));
                java.util.List<ItemLike> plant = new java.util.ArrayList<>();
                plant.add(MultiblockRegistry.TURBINE_CONTROLLER_ITEM.get());
                for (com.sqc.nuclearsq.machine.big.BigMachine m : com.sqc.nuclearsq.machine.big.BigMachine.values()) {
                    if (m == com.sqc.nuclearsq.machine.big.BigMachine.REACTOR_ASSEMBLER || m == com.sqc.nuclearsq.machine.big.BigMachine.COAL_GENERATOR
                            || m == com.sqc.nuclearsq.machine.big.BigMachine.SMALL_PARTS_ASSEMBLER
                            || m == com.sqc.nuclearsq.machine.big.BigMachine.MICRO_ASSEMBLER
                            || m == com.sqc.nuclearsq.machine.big.BigMachine.EQUIPMENT_ASSEMBLER
                            || m == com.sqc.nuclearsq.machine.big.BigMachine.EXO_STATION) continue;
                    plant.add(BigMachineRegistry.CONTROLLER_ITEMS.get(m).get());
                }
                BigMachineRegistry.PORT_ITEMS.values().forEach(it -> plant.add(it.get()));
                groups(out, H_PARTS, java.util.List.of(
                        java.util.List.of(i("zircaloy_sheet_sqc"), i("steel_sheet_sqc"), i("iron_plate_sqc"), i("copper_plate_sqc"), i("gold_plate_sqc"), i("reactor_plate_sqc"),
                                i("osmium_heat_plate_sqc"), i("obsidian_composite_plate_sqc"), i("atomic_hull_plate_sqc"),
                                i("glass_seal_sqc"), i("hdpe_seal_sqc"), i("lead_shielding_sqc")),
                        java.util.List.of(i("uranium_core_sqc"), i("fluorite_cooling_core_sqc"), i("plutonium_core_sqc"),
                                i("mox_core_sqc"), i("carbon_filter_sqc"), i("boric_acid_sqc"), i("ion_resin_sqc"), i("spent_resin_sqc"), ReactorRegistry.RECOMBINER_ITEM.get(), ReactorRegistry.XENON_FILTER_ITEM.get(),
                                i("control_board_1_sqc"), i("control_board_2_sqc"), i("control_board_3_sqc")),
                        java.util.List.of(i("copper_coil_sqc"), i("andesite_housing_sqc"), i("brass_bearing_sqc"), i("glowstone_conductor_sqc")),
                        java.util.List.of(i("superconducting_coil_sqc"), i("first_wall_tile_sqc"), i("worn_wall_tile_sqc"), i("lithium_pellet_sqc"),
                                i("plasma_optimizer_sqc")),
                        java.util.List.of(b("vent_grille_sqc"), b("heat_exchanger_sqc"), b("active_cooler_sqc"),
                                b("translator_basic_sqc"), b("translator_brass_sqc"), b("translator_composite_sqc"),
                                b("moderator_coal_sqc"), b("moderator_carbon_sqc"), b("moderator_obsidian_sqc"),
                                b("pump_basic_sqc"), b("pump_electric_sqc"), b("pump_high_pressure_sqc"), b("pump_sodium_sqc"),
                                b("pressure_chamber_sqc"), b("pressure_chamber_reinforced_sqc"), b("pressure_chamber_hardened_sqc"),
                                ReactorRegistry.CASING_ITEM.get()),
                        java.util.List.of(b("steam_purifier_sqc"), b("turbine_basic_sqc"), b("turbine_brass_rotor_sqc"), b("turbine_steel_blades_sqc"),
                                b("condenser_sqc"), b("cooling_tower_segment_sqc"), b("cooling_tower_plated_sqc"), b("cooling_tower_hardened_sqc"),
                                b("water_treatment_sqc"), b("feedwater_pump_sqc"), b("water_inlet_sqc")),
                        plant));
                section(out, H_HULLS, ReactorRegistry.FUEL_ROD_BLOCK_ITEM.get(), ReactorRegistry.FUEL_ROD_HEAD_ITEM.get(),
                        ReactorRegistry.FUEL_ROD_BLOCK_REINFORCED_ITEM.get(), ReactorRegistry.FUEL_ROD_HEAD_REINFORCED_ITEM.get(),
                        ReactorRegistry.FUEL_ROD_BLOCK_HARDENED_ITEM.get(), ReactorRegistry.FUEL_ROD_HEAD_HARDENED_ITEM.get(),
                        ReactorRegistry.FUEL_ROD_BLOCK_EXPERIMENTAL_ITEM.get(), ReactorRegistry.FUEL_ROD_HEAD_EXPERIMENTAL_ITEM.get());
                java.util.List<ItemLike> loop = new java.util.ArrayList<>(java.util.List.of(
                        kit("control_rod_drive"), kit("steam_generator"), kit("pressurizer"),
                        kit("steam_purifier"), kit("steam_separator"), kit("steam_dryer"), kit("reheater"),
                        kit("turbine"), MultiblockRegistry.SHAFT_COUPLING_ITEM.get()));
                var createOut = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.sqc.nuclearsq.NuclearSQ.MODID, "create_shaft_output_sqc"));
                if (createOut != net.minecraft.world.item.Items.AIR) loop.add(createOut);
                loop.addAll(java.util.List.of(kit("shaft_generator"),
                        kit("condenser"), kit("cooling_tower_large"), kit("cooling_tower_xl"),
                        kit("intake_pump"), com.sqc.nuclearsq.transport.TransportRegistry.BIG_PIPE_ITEM.get(),
                        kit("water_treatment"), i("filter_residue_sqc"), kit("feedwater_heater"),
                        kit("feedwater_pump"), kit("main_coolant_pump"), kit("high_pressure_pump"),
                        kit("containment"), kit("relief_tank"), kit("emergency_cooling"), kit("high_pressure_injection"),
                        ModFluids.COOLANT.bucket.get(), ModFluids.SODIUM_COOLANT.bucket.get(), ModFluids.CRYO_COOLANT.bucket.get(), ModFluids.HOT_COOLANT.bucket.get(),
                        ModFluids.FEEDWATER.bucket.get(), ModFluids.PREHEATED_FEEDWATER.bucket.get(), ModFluids.SULFURIC_ACID.bucket.get()));
                section(out, H_WATER, loop.toArray(new ItemLike[0]));
                java.util.List<ItemLike> power = new java.util.ArrayList<>(java.util.List.of(kit("coal_generator"), PowerRegistry.SOLAR_PANEL_ITEM.get(), PowerRegistry.SOLAR_COLLECTOR_ITEM.get(), kit("solar_field"),
                        PowerRegistry.RTG_ITEM.get(), PowerRegistry.ISOTOPE_CAPSULE.get(), PowerRegistry.PLUTONIUM_CAPSULE.get(), PowerRegistry.STRONTIUM_CAPSULE.get(),
                        kit("wind_turbine_small"), kit("wind_turbine_medium"), kit("wind_turbine_large"),
                        kit("battery_small"), kit("battery_medium"), kit("battery_large")));
                com.sqc.nuclearsq.transport.TransportRegistry.BLOCK_ITEMS.forEach(d -> power.add(d.get()));
                power.addAll(com.sqc.nuclearsq.transport.GridRegistry.tabItems());
                power.addAll(java.util.List.of(kit("substation"),
                        kit("electrolyzer"), kit("fuel_cell"), kit("gas_generator"), kit("fluid_buffer"), kit("deuterium_separator"), kit("fusion_reactor")));
                var pgTerminal = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.sqc.nuclearsq.NuclearSQ.MODID, "powergrid_terminal_sqc"));
                if (pgTerminal != net.minecraft.world.item.Items.AIR) power.add(pgTerminal);
                section(out, H_TRANSPORT, power.toArray(new ItemLike[0]));
                java.util.List<ItemLike> data = new java.util.ArrayList<>();
                com.sqc.nuclearsq.data.DataRegistry.blocks().stream()
                        .filter(d -> d != com.sqc.nuclearsq.data.DataRegistry.AREA_MONITOR && d != com.sqc.nuclearsq.data.DataRegistry.AREA_SENSOR)
                        .forEach(d -> data.add(d.get()));
                data.add(kit("control_desk"));
                data.add(CatalogRegistry.NET_SCANNER.get());
                data.add(com.sqc.nuclearsq.storage.StorageRegistry.TERMINAL.get());
                data.add(com.sqc.nuclearsq.storage.StorageRegistry.MONITOR.get());
                data.add(com.sqc.nuclearsq.storage.StorageRegistry.CRAFTING_TERMINAL.get());
                data.add(com.sqc.nuclearsq.storage.StorageRegistry.ITEM_PORT.get());
                data.addAll(java.util.List.of(com.sqc.nuclearsq.crate.CrateRegistry.ITEM.get(), com.sqc.nuclearsq.crate.CrateRegistry.UPGRADE_1.get(), com.sqc.nuclearsq.crate.CrateRegistry.UPGRADE_2.get()));
                data.add(com.sqc.nuclearsq.storage.StorageRegistry.WIFI_ADAPTER.get());
                data.add(com.sqc.nuclearsq.storage.StorageRegistry.WIFI_ANTENNA.get());
                data.add(com.sqc.nuclearsq.storage.StorageRegistry.WIFI_TERMINAL.get());
                data.add(com.sqc.nuclearsq.monitor.ImageMonitorRegistry.MONITOR.get());
                data.add(com.sqc.nuclearsq.economy.EconomyRegistry.SERVER.get());
                data.add(com.sqc.nuclearsq.economy.EconomyRegistry.TERMINAL.get());
                data.add(com.sqc.nuclearsq.economy.EconomyRegistry.MODEM.get());
                data.add(kit("server_rack_small"));
                data.add(kit("server_rack"));
                data.add(kit("server_rack_large"));
                data.add(kit("compute_cluster"));
                data.add(kit("pattern_rack"));
                com.sqc.nuclearsq.storage.StorageRegistry.drives().forEach(d -> data.add(d.get()));
                for (var cpu : com.sqc.nuclearsq.storage.StorageRegistry.CPU_MODULES) data.add(cpu.get());
                for (var ram : com.sqc.nuclearsq.storage.StorageRegistry.RAM_MODULES) data.add(ram.get());
                data.add(com.sqc.nuclearsq.storage.StorageRegistry.QUANTUM_MODULE.get());
                data.add(kit("component_assembler"));
                data.add(kit("micro_assembler"));
                data.add(kit("equipment_assembler"));
                data.add(kit("decon_dock"));
                data.add(kit("exo_station"));
                data.add(kit("clean_room"));
                data.add(kit("compute_assembler"));
                data.add(kit("quantum_computer"));
                data.add(com.sqc.nuclearsq.storage.StorageRegistry.PATTERN_CARD.get());
                data.add(com.sqc.nuclearsq.storage.StorageRegistry.ASSEMBLER_TEMPO.get());
                section(out, H_DATA, data.toArray(new ItemLike[0]));
                section(out, H_FUEL, kit("spent_fuel_rack"), SpentFuelRegistry.SPENT_FUEL.get(), SpentFuelRegistry.LARGE_CASTOR_ITEM.get(),
                        kit("reprocessing_plant"), kit("waste_repository"),
                        kit("fast_breeder"), i("breeder_blanket_sqc"), i("bred_blanket_sqc"), i("plutonium_oxide_sqc"),
                        i("neptunium_oxide_sqc"), i("neptunium_blanket_sqc"), i("bred_neptunium_sqc"), i("plutonium_238_oxide_sqc"),
                        kit("weapons_processing"), i("plutonium_metal_sqc"), i("weapons_plutonium_core_sqc"));
                java.util.List<ItemLike> prot = new java.util.ArrayList<>(java.util.List.of(CatalogRegistry.GEIGER_COUNTER.get(),
                        com.sqc.nuclearsq.data.DataRegistry.AREA_MONITOR.get(), com.sqc.nuclearsq.data.DataRegistry.AREA_SENSOR.get(),
                        CatalogRegistry.ANTI_RAD.get()));
                prot.addAll(com.sqc.nuclearsq.radiation.ProtectionRegistry.tabItems());
                prot.addAll(java.util.List.of(com.sqc.nuclearsq.door.DoorRegistry.CONTROLLER_ITEM.get(), com.sqc.nuclearsq.door.DoorRegistry.HANGAR_PANEL_ITEM.get(), com.sqc.nuclearsq.door.DoorRegistry.AIRLOCK_PANEL_ITEM.get(), com.sqc.nuclearsq.door.DoorRegistry.VAULT_PANEL_ITEM.get(), com.sqc.nuclearsq.door.DoorRegistry.SECTIONAL_PANEL_ITEM.get(), com.sqc.nuclearsq.door.DoorRegistry.GRILLE_PANEL_ITEM.get(), com.sqc.nuclearsq.door.DoorRegistry.FOLDING_PANEL_ITEM.get(), com.sqc.nuclearsq.door.DoorRegistry.AIRLOCK_ITEM.get()));
                section(out, H_PROTECTION, prot.toArray(new ItemLike[0]));
                section(out, H_MODULES, com.sqc.nuclearsq.module.ModuleRegistry.ITEM.values().stream().map(d -> (ItemLike) d.get()).toArray(ItemLike[]::new));
                java.util.List<ItemLike> tools = new java.util.ArrayList<>(java.util.List.of(PROGRESSION_BOOK.get(),
                        com.sqc.nuclearsq.transport.TransportRegistry.WRENCH.get()));
                tools.addAll(java.util.List.of(AssemblyRegistry.ASSEMBLER_ITEM.get(), kit("heavy_assembler"), kit("particle_assembler"),
                        kit("small_parts_assembler"),
                        AssemblyRegistry.DETONATOR_ITEM.get(),
                        BombRegistry.BOMB_ITEM.get(), BombRegistry.LARGE_BOMB_ITEM.get(), BombRegistry.TSAR_BOMB_ITEM.get(),
                        TestSourceRegistry.ENERGY_SOURCE_ITEM.get(), TestSourceRegistry.FLUID_SOURCE_ITEM.get()));
                tools.add(com.sqc.nuclearsq.nanobot.NanobotRegistry.CANISTER.get());
                com.sqc.nuclearsq.nanobot.NanobotRegistry.POCKET_BATTERIES.forEach(d -> tools.add(d.get()));
                tools.add(com.sqc.nuclearsq.nanobot.NanobotRegistry.DRONE_ITEM_PORT_ITEM.get());
                tools.addAll(java.util.List.of(com.sqc.nuclearsq.robotics.RoboticsRegistry.ARM_ITEM.get(),
                        com.sqc.nuclearsq.robotics.RoboticsRegistry.HEAVY_ARM_ITEM.get(), com.sqc.nuclearsq.robotics.RoboticsRegistry.ROD_GRIPPER_ITEM.get(), com.sqc.nuclearsq.robotics.RoboticsRegistry.LINK_TOOL.get(),
                        com.sqc.nuclearsq.robotics.RoboticsRegistry.ARM_UPGRADE.get()));
                tools.addAll(java.util.List.of(com.sqc.nuclearsq.robotics.decon.DeconRegistry.ROBOT_ITEM.get(), com.sqc.nuclearsq.robotics.decon.DeconRegistry.REMOTE.get(),
                        com.sqc.nuclearsq.robotics.pet.RoboPetRegistry.DOG_KIT.get(), com.sqc.nuclearsq.robotics.pet.RoboPetRegistry.CAT_KIT.get(),
                        com.sqc.nuclearsq.robotics.pet.RoboPetRegistry.BALL.get(), com.sqc.nuclearsq.robotics.pet.RoboPetRegistry.WAND.get(),
                        com.sqc.nuclearsq.robotics.pet.RoboPetRegistry.COLLAR_SNIFFER.get(), com.sqc.nuclearsq.robotics.pet.RoboPetRegistry.COLLAR_TIDY.get(),
                        com.sqc.nuclearsq.robotics.pet.RoboPetRegistry.COLLAR_LAMP.get(),
                        com.sqc.nuclearsq.robotics.drone.DroneRegistry.DRONE_ITEM.get(), com.sqc.nuclearsq.robotics.drone.DroneRegistry.TABLET.get()));
                tools.add(com.sqc.nuclearsq.futurechest.FutureChestRegistry.ITEM.get());
                com.sqc.nuclearsq.futurechest.FutureChestRegistry.UPGRADE_ITEMS.values().forEach(d -> tools.add(d.get()));
                tools.addAll(com.sqc.nuclearsq.nanobot.NanobotRegistry.UPGRADE_ITEMS.values().stream().map(d -> (ItemLike) d.get()).toList());
                tools.addAll(com.sqc.nuclearsq.particle.ParticleRegistry.UPGRADES.values().stream().map(d -> (ItemLike) d.get()).toList());
                tools.addAll(com.sqc.nuclearsq.particle.ParticleRegistry.CARDS.stream().map(d -> (ItemLike) d.get()).toList());
                tools.addAll(com.sqc.nuclearsq.exo.ExoRegistry.tabItems());
                section(out, H_MACHINES, tools.toArray(new ItemLike[0]));
            }).build());

    public static void register(IEventBus modBus) {
        ITEMS.register(modBus);
        TABS.register(modBus);
    }
}
