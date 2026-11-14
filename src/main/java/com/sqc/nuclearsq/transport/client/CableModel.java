package com.sqc.nuclearsq.transport.client;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import javax.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Transformation;
import com.sqc.nuclearsq.NuclearSQ;
import com.sqc.nuclearsq.transport.CableBlock;
import com.sqc.nuclearsq.transport.ConduitBlockEntity;
import com.sqc.nuclearsq.transport.ConduitSide;
import com.sqc.nuclearsq.transport.ItemPipeBlock;
import com.sqc.nuclearsq.transport.TransportRegistry;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.SimpleModelState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;
import net.neoforged.neoforge.common.util.ConcatenatedListView;
import net.neoforged.neoforge.common.util.TriState;

import org.joml.Vector3f;

@EventBusSubscriber(modid = NuclearSQ.MODID, value = Dist.CLIENT)
public final class CableModel implements IUnbakedGeometry<CableModel> {
    public static final ResourceLocation LOADER = ResourceLocation.fromNamespaceAndPath(NuclearSQ.MODID, "cable");

    @SubscribeEvent
    public static void onRegisterLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(LOADER, Loader.INSTANCE);
    }

    @SubscribeEvent
    public static void onBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, level, pos, tint) -> !full(level, pos) ? 0xFFFFFF : state.getBlock() instanceof ItemPipeBlock ? 0xFFC040 : 0x6FA8FF,
                java.util.stream.Stream.concat(TransportRegistry.PIPES.values().stream(), TransportRegistry.ITEM_PIPES.values().stream())
                        .map(b -> b.get()).toArray(net.minecraft.world.level.block.Block[]::new));
        event.register((state, level, pos, tint) -> full(level, pos) ? 0xFFFFFF : 0x707070,
                TransportRegistry.CABLES.values().stream().map(b -> b.get()).toArray(net.minecraft.world.level.block.Block[]::new));
    }

    private static boolean full(@Nullable net.minecraft.world.level.BlockAndTintGetter level, @Nullable net.minecraft.core.BlockPos pos) {
        return level != null && pos != null && level.getBlockEntity(pos) instanceof ConduitBlockEntity c && c.full();
    }

    record Part(Rule when, ResourceLocation model, int x, int y, int dy) {}

    private final List<Part> parts;
    private boolean resolved;

    CableModel(List<Part> parts) {
        this.parts = parts;
    }

    @Override
    public void resolveParents(Function<ResourceLocation, UnbakedModel> modelGetter, IGeometryBakingContext context) {
        if (resolved) return;
        resolved = true;
        Set<ResourceLocation> models = new LinkedHashSet<>();
        for (Part p : parts) models.add(p.model());
        for (ResourceLocation m : models) modelGetter.apply(m).resolveParents(modelGetter);
    }

    @Override
    public BakedModel bake(IGeometryBakingContext context, ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter,
                           ModelState modelState, ItemOverrides overrides) {
        Rule[] rules = new Rule[parts.size()];
        BakedModel[] models = new BakedModel[parts.size()];
        for (int i = 0; i < parts.size(); i++) {
            Part p = parts.get(i);
            rules[i] = p.when();
            ModelState st = p.dy() == 0 ? BlockModelRotation.by(p.x(), p.y())
                    : new SimpleModelState(new Transformation(new Vector3f(0, p.dy(), 0), null, null, null));
            models[i] = baker.bake(p.model(), st, spriteGetter);
        }
        return new Baked(rules, models);
    }

    static final class Baked implements IDynamicBakedModel {
        private final Rule[] rules;
        private final BakedModel[] models;
        private final BakedModel first;
        private final Map<Long, BakedModel[]> selected = new ConcurrentHashMap<>();

        Baked(Rule[] rules, BakedModel[] models) {
            this.rules = rules;
            this.models = models;
            this.first = models[0];
        }

        private BakedModel[] select(BlockState state, ModelData data) {
            Integer c = data.get(ConduitBlockEntity.CONN);
            int conn = c == null ? 0 : c;
            int mount = state.hasProperty(CableBlock.MOUNT) ? state.getValue(CableBlock.MOUNT).ordinal() + 1 : 0;
            Integer post = data.get(ConduitBlockEntity.POST);
            long key = (conn | mount << 12) & 0xFFFFFFFFL | (post == null ? 0L : (long) post << 32);
            BakedModel[] hit = selected.get(key);
            if (hit != null) return hit;
            List<BakedModel> out = new ArrayList<>();
            for (int i = 0; i < rules.length; i++) if (rules[i].test(state, key)) out.add(models[i]);
            hit = out.toArray(BakedModel[]::new);
            selected.put(key, hit);
            return hit;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData data,
                                        @Nullable RenderType renderType) {
            if (state == null) return List.of();
            BakedModel[] sel = select(state, data);
            List<List<BakedQuad>> lists = new ArrayList<>(sel.length);
            long seed = rand.nextLong();
            for (BakedModel m : sel) {
                if (renderType == null || m.getRenderTypes(state, rand, data).contains(renderType)) {
                    lists.add(m.getQuads(state, side, RandomSource.create(seed), data, renderType));
                }
            }
            return ConcatenatedListView.of(lists);
        }

        @Override
        public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
            if (state == null) return ChunkRenderTypeSet.none();
            List<ChunkRenderTypeSet> sets = new ArrayList<>();
            for (BakedModel m : select(state, data)) sets.add(m.getRenderTypes(state, rand, data));
            return ChunkRenderTypeSet.union(sets);
        }

        @Override
        public boolean useAmbientOcclusion() {
            return first.useAmbientOcclusion();
        }

        @Override
        public TriState useAmbientOcclusion(BlockState state, ModelData data, RenderType renderType) {
            return first.useAmbientOcclusion(state, data, renderType);
        }

        @Override
        public boolean isGui3d() {
            return first.isGui3d();
        }

        @Override
        public boolean usesBlockLight() {
            return first.usesBlockLight();
        }

        @Override
        public boolean isCustomRenderer() {
            return false;
        }

        @Override
        @SuppressWarnings("deprecation")
        public TextureAtlasSprite getParticleIcon() {
            return first.getParticleIcon();
        }

        @Override
        public TextureAtlasSprite getParticleIcon(ModelData data) {
            return first.getParticleIcon(data);
        }

        @Override
        @SuppressWarnings("deprecation")
        public ItemTransforms getTransforms() {
            return first.getTransforms();
        }

        @Override
        public BakedModel applyTransform(ItemDisplayContext context, PoseStack poseStack, boolean leftHand) {
            return first.applyTransform(context, poseStack, leftHand);
        }

        @Override
        public ItemOverrides getOverrides() {
            return first.getOverrides();
        }
    }

    interface Rule {
        boolean test(BlockState state, long conn);

        static Rule parse(JsonObject o) {
            if (o.has("OR")) {
                Rule[] any = list(GsonHelper.getAsJsonArray(o, "OR"));
                return (s, c) -> {
                    for (Rule r : any) if (r.test(s, c)) return true;
                    return false;
                };
            }
            if (o.has("AND")) {
                Rule[] all = list(GsonHelper.getAsJsonArray(o, "AND"));
                return (s, c) -> {
                    for (Rule r : all) if (!r.test(s, c)) return false;
                    return true;
                };
            }
            List<String> keys = new ArrayList<>();
            List<Set<String>> values = new ArrayList<>();
            for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                keys.add(e.getKey());
                values.add(Set.of(e.getValue().getAsString().split("\\|")));
            }
            return (s, c) -> {
                for (int i = 0; i < keys.size(); i++) {
                    String key = keys.get(i);
                    if (key.equals("post")) {
                        if (!values.get(i).contains(Long.toString(c >>> 32))) return false;
                        continue;
                    }
                    int shift = key.startsWith("plain_") ? ConduitBlockEntity.PLAIN_SHIFT : key.startsWith("hold_") ? ConduitBlockEntity.HOLD_SHIFT : -1;
                    if (shift >= 0) {
                        Direction pd = Direction.byName(key.substring(key.indexOf('_') + 1));
                        if (!values.get(i).contains((c >>> (shift + pd.ordinal()) & 1) == 1 ? "1" : "0")) return false;
                        continue;
                    }
                    Direction side = Direction.byName(key);
                    if (side != null) {
                        if (!values.get(i).contains(ConduitSide.of((int) c, side).getSerializedName())) return false;
                        continue;
                    }
                    Property<?> p = s.getBlock().getStateDefinition().getProperty(keys.get(i));
                    if (p == null || !values.get(i).contains(name(s, p))) return false;
                }
                return true;
            };
        }

        private static Rule[] list(JsonArray a) {
            Rule[] out = new Rule[a.size()];
            for (int i = 0; i < out.length; i++) out[i] = parse(a.get(i).getAsJsonObject());
            return out;
        }

        private static <T extends Comparable<T>> String name(BlockState s, Property<T> p) {
            return p.getName(s.getValue(p));
        }
    }

    static final class Loader implements IGeometryLoader<CableModel> {
        static final Loader INSTANCE = new Loader();

        @Override
        public CableModel read(JsonObject json, JsonDeserializationContext context) throws JsonParseException {
            List<Part> parts = new ArrayList<>();
            for (JsonElement e : GsonHelper.getAsJsonArray(json, "parts")) {
                JsonObject p = e.getAsJsonObject();
                JsonObject apply = GsonHelper.getAsJsonObject(p, "apply");
                Rule when = p.has("when") ? Rule.parse(GsonHelper.getAsJsonObject(p, "when")) : (s, c) -> true;
                parts.add(new Part(when, ResourceLocation.parse(GsonHelper.getAsString(apply, "model")),
                        GsonHelper.getAsInt(apply, "x", 0), GsonHelper.getAsInt(apply, "y", 0), GsonHelper.getAsInt(apply, "dy", 0)));
            }
            if (parts.isEmpty()) throw new JsonParseException("Kabelmodell ohne Teile");
            return new CableModel(parts);
        }
    }
}
