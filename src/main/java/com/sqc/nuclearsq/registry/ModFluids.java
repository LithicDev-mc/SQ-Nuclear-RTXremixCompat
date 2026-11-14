package com.sqc.nuclearsq.registry;

import java.util.ArrayList;
import java.util.List;

import com.sqc.nuclearsq.NuclearSQ;

import javax.annotation.Nullable;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModFluids {
    private ModFluids() {}

    public static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, NuclearSQ.MODID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, NuclearSQ.MODID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NuclearSQ.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NuclearSQ.MODID);

    public static final List<Entry> ALL = new ArrayList<>();

    public static final class Entry {
        public final String id;
        final int color;
        DeferredHolder<FluidType, FluidType> type;
        public DeferredHolder<Fluid, BaseFlowingFluid.Source> source;
        DeferredHolder<Fluid, BaseFlowingFluid.Flowing> flowing;
        @Nullable
        DeferredBlock<LiquidBlock> block;
        @Nullable
        public DeferredItem<BucketItem> bucket;

        private final boolean isGas;

        Entry(String id, int color, int temperature, int density, boolean gas, boolean sealed) {
            this.id = id;
            this.color = color;
            this.isGas = gas;
            final Entry self = this;
            this.type = TYPES.register(id, () -> new FluidType(FluidType.Properties.create()
                    .density(density).viscosity(1000).temperature(temperature)));
            this.source = FLUIDS.register(id, () -> new BaseFlowingFluid.Source(self.props()));
            this.flowing = FLUIDS.register("flowing_" + id, () -> new BaseFlowingFluid.Flowing(self.props()));
            if (!gas && !sealed) {
                this.block = BLOCKS.register(id + "_block_sqc",
                        () -> new LiquidBlock(self.source.get(), BlockBehaviour.Properties.ofFullCopy(Blocks.WATER)));
                this.bucket = ITEMS.register(id + "_bucket_sqc",
                        () -> new BucketItem(self.source.get(), new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));
            }
            ALL.add(this);
        }

        public boolean gas() {
            return isGas;
        }

        public boolean sealed() {
            return !isGas && bucket == null;
        }

        private BaseFlowingFluid.Properties props() {
            BaseFlowingFluid.Properties p = new BaseFlowingFluid.Properties(type, source, flowing);
            if (block != null) p.block(block);
            if (bucket != null) p.bucket(bucket);
            return p;
        }
    }

    private static Entry liquid(String id, int color, int temperature, int density) {
        return new Entry(id, color, temperature, density, false, false);
    }

    private static Entry sealed(String id, int color, int temperature, int density) {
        return new Entry(id, color, temperature, density, false, true);
    }

    private static Entry gas(String id, int color, int temperature, int density) {
        return new Entry(id, color, temperature, density, true, false);
    }

    public static final Entry COOLANT = liquid("coolant", 0xFF3FB6E8, 300, 1000);
    public static final Entry SODIUM_COOLANT = liquid("sodium_coolant", 0xFFD9D49A, 450, 900);
    public static final Entry CRYO_COOLANT = liquid("cryo_coolant", 0xFF9FE8FF, 120, 1100);
    public static final Entry HOT_COOLANT = liquid("hot_coolant", 0xFFE8742A, 450, 900);
    public static final Entry STEAM = gas("steam", 0xCCE8E8E8, 500, -100);
    public static final Entry CONTAMINATED_STEAM = gas("contaminated_steam", 0xCCB4D48C, 500, -100);
    public static final Entry EXHAUST_STEAM = gas("exhaust_steam", 0xCCB8C4CE, 380, -50);
    public static final Entry FEEDWATER = liquid("feedwater", 0xFF7CC4F0, 300, 1000);
    public static final Entry WET_STEAM = gas("wet_steam", 0xCCB6CBD8, 420, -80);
    public static final Entry SUPERHEATED_STEAM = gas("superheated_steam", 0xCCF2CC9C, 620, -120);
    public static final Entry PREHEATED_FEEDWATER = liquid("preheated_feedwater", 0xFF82D2D8, 400, 980);
    public static final Entry PRIMARY_WATER = sealed("primary_water", 0xFF6A7FE0, 560, 1000);
    public static final Entry HOT_PRIMARY_WATER = sealed("hot_primary_water", 0xFFC85AD2, 600, 950);

    public static final Entry DEUTERIUM = gas("deuterium", 0xCC6FB4F0, 300, -20);
    public static final Entry TRITIUM = gas("tritium", 0xCC7AE08A, 300, -30);
    public static final Entry HELIUM = gas("helium", 0xCCF0E08A, 300, -10);
    public static final Entry HYDROGEN = gas("hydrogen", 0xCCE8F2FF, 300, -20);
    public static final Entry OXYGEN = gas("oxygen", 0xCC9FD8F0, 300, -20);

    public static final Entry SULFURIC_ACID = liquid("sulfuric_acid", 0xFFB4E63A, 300, 1830);
    public static final Entry HYDROFLUORIC_ACID = sealed("hydrofluoric_acid", 0xFFC8F0E0, 293, 1150);
    public static final Entry HEAVY_WATER = sealed("heavy_water", 0xFF9CC4E4, 293, 1105);
    public static final Entry LYE = sealed("lye", 0xFFE8E4C0, 293, 1220);
    public static final Entry NITRIC_ACID = sealed("nitric_acid", 0xFFE8B040, 293, 1510);
    public static final Entry CRUDE_OIL = sealed("crude_oil", 0xFF14101A, 300, 900);
    public static final Entry NAPHTHA = sealed("naphtha", 0xFFE8D27A, 293, 700);
    public static final Entry DIESEL = sealed("diesel", 0xFFB89A3A, 293, 830);
    public static final Entry TAR = sealed("tar", 0xFF1E1812, 330, 1100);
    public static final Entry ETHYLENE = gas("ethylene", 0xCCD8E8F0, 293, -20);
    public static final Entry HLW = sealed("hlw", 0xFF7CFF5A, 350, 1450);

    public static boolean isHotPrimary(net.neoforged.neoforge.fluids.FluidStack fs) {
        return !fs.isEmpty() && fs.is(HOT_PRIMARY_WATER.source.get());
    }

    public static final class ClientSetup {
        public static void onRegister(net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) {
            for (Entry e : ALL) {
                final ResourceLocation still = ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "block/fluid/" + e.id + "_still");
                final ResourceLocation flow = ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "block/fluid/" + e.id + "_flow");
                event.registerFluidType(new IClientFluidTypeExtensions() {
                    @Override
                    public ResourceLocation getStillTexture() {
                        return still;
                    }

                    @Override
                    public ResourceLocation getFlowingTexture() {
                        return flow;
                    }

                }, e.type.get());
            }
        }
    }

    public static void register(IEventBus modBus) {
        TYPES.register(modBus);
        FLUIDS.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) modBus.addListener(ClientSetup::onRegister);
    }
}
