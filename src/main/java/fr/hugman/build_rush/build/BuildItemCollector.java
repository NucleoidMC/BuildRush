package fr.hugman.build_rush.build;

import com.mojang.logging.LogUtils;
import fr.hugman.build_rush.BuildRush;
import fr.hugman.build_rush.mixin.CandleCakeBlockAccessor;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.Unit;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.ChiseledBookShelfBlock;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.MultifaceSpreadeableBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.TagValueOutput;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;
import java.util.function.Consumer;

public class BuildItemCollector {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Comparator<ItemStack> COMPARATOR = Comparator.comparingInt(ItemStack::getCount).reversed();

    private final List<ItemStack> additionalStacks = new ArrayList<>();
    private final Object2IntOpenHashMap<Item> counts = new Object2IntOpenHashMap<>();
    private final Map<Item, ItemStack> singletonStacks = new HashMap<>();

    private void add(ItemStack stack) {
        if (!stack.isEmpty()) {
            this.additionalStacks.add(stack);
        }
    }

    private void addCount(ItemLike item, int count) {
        this.counts.addTo(item.asItem(), count);
    }

    private void addSingletonStack(Item item, Consumer<ItemStack> creator) {
        this.singletonStacks.computeIfAbsent(item, i -> {
            var stack = new ItemStack(i);
            creator.accept(stack);
            return stack;
        });
    }

    public void accept(ServerLevel world, BlockPos pos) {
        var state = world.getBlockState(pos);

        if (state.hasBlockEntity()) {
            var blockEntity = world.getBlockEntity(pos);

            if (blockEntity == null) {
                BuildRush.LOGGER.warn("Block entity was null for " + state.getBlock() + " even though the game said it had one");
            }

            this.accept(world, pos, state, blockEntity);
        } else {
            this.accept(world, pos, state, null);
        }
    }

    public void accept(ServerLevel world, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
        if (isIgnored(state)) return;

        var block = state.getBlock();
        var pickStack = state.getCloneItemStack(world, pos, true);

        // Blocks requiring tools
        if (needsFlintAndSteel(state)) {
            this.addSingletonStack(Items.FLINT_AND_STEEL, BuildItemCollector::setUnbreakable);
        }

        if (block instanceof CampfireBlock && !state.getValue(CampfireBlock.LIT)) {
            this.addSingletonStack(Items.IRON_SHOVEL, BuildItemCollector::setUnbreakable);
        }

        // Fluids
        var fluid = state.getFluidState().getType();

        if (fluid == Fluids.WATER) {
            this.addCount(Items.WATER_BUCKET, 1);
        }

        if (block == Blocks.WATER_CAULDRON) {
            for (int i = 0; i < state.getValue(LayeredCauldronBlock.LEVEL); i++) {
                this.add(PotionContents.createItemStack(Items.POTION, Potions.WATER));
            }
        }

        if (fluid == Fluids.LAVA || block == Blocks.LAVA_CAULDRON) {
            this.addCount(Items.LAVA_BUCKET, 1);
        }

        // Multiblocks
        setCount(pickStack, state, BlockStateProperties.EGGS);
        setCount(pickStack, state, BlockStateProperties.CANDLES);
        setCount(pickStack, state, BlockStateProperties.FLOWER_AMOUNT);
        setCount(pickStack, state, BlockStateProperties.LAYERS);
        setCount(pickStack, state, BlockStateProperties.PICKLES);

        if (block instanceof MultifaceSpreadeableBlock) {
            pickStack.setCount(MultifaceSpreadeableBlock.availableFaces(state).size());
        }

        if (block instanceof SlabBlock && state.getValue(SlabBlock.TYPE) == SlabType.DOUBLE) {
            pickStack.setCount(pickStack.getCount() * 2);
        }

        if (block instanceof VineBlock) {
            int count = 0;

            if (state.getValue(VineBlock.UP)) count++;
            if (state.getValue(VineBlock.NORTH)) count++;
            if (state.getValue(VineBlock.EAST)) count++;
            if (state.getValue(VineBlock.SOUTH)) count++;
            if (state.getValue(VineBlock.WEST)) count++;

            pickStack.setCount(count);
        }

        // Blocks containing items
        if (block instanceof BrewingStandBlock) {
            for (var property : BrewingStandBlock.HAS_BOTTLE) {
                this.addCount(Items.GLASS_BOTTLE, state.getValue(property) ? 1 : 0);
            }
        }

        if (block instanceof CandleCakeBlockAccessor cake) {
            this.addCount(cake.buildrush$getCandle(), 1);
        }

        if (block instanceof ChiseledBookShelfBlock) {
            for (var property : ChiseledBookShelfBlock.SLOT_OCCUPIED_PROPERTIES) {
                this.addCount(Items.BOOK, state.getValue(property) ? 1 : 0);
            }
        }

        if (block instanceof EndPortalFrameBlock && state.getValue(EndPortalFrameBlock.HAS_EYE)) {
            this.addCount(Items.ENDER_EYE, 1);
        }

        if (block instanceof FlowerPotBlock && block != Blocks.FLOWER_POT) {
            this.addCount(Items.FLOWER_POT, 1);
        }

        if (block instanceof LecternBlock && state.getValue(LecternBlock.HAS_BOOK)) {
            this.addCount(Items.BOOK, 1);
        }

        if (block instanceof RespawnAnchorBlock) {
            this.addCount(Items.GLOWSTONE, state.getValue(RespawnAnchorBlock.CHARGE));
        }

        // Block entities
        if (blockEntity != null) {
            BuildItemCollector.addBlockEntityNbt(world, pickStack, blockEntity);
        }

        this.add(pickStack);
    }

    public List<ItemStack> getStacks() {
        var stacks = new ArrayList<ItemStack>();

        for (var entry : this.counts.object2IntEntrySet()) {
            var item = entry.getKey();
            int count = entry.getIntValue();

            while (count > 0) {
                ItemStack stack = new ItemStack(item, Math.min(count, item.getDefaultMaxStackSize()));
                count -= stack.getCount();

                stacks.add(stack);
            }
        }

        stacks.addAll(this.additionalStacks);
        stacks.addAll(this.singletonStacks.values());

        stacks.sort(COMPARATOR);
        return stacks;
    }

    public boolean isSingletonStack(ItemStack stack) {
        return this.singletonStacks.containsValue(stack);
    }

    private static boolean isIgnored(BlockState state) {
        if (state.isAir()) return true;

        // Multipart blocks
        if (state.is(Blocks.PISTON_HEAD)) return true;

        // Multipart block states
        if (isPropertyValue(state, BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER)) return true;
        if (isPropertyValue(state, BlockStateProperties.BED_PART, BedPart.FOOT)) return true;

        return false;
    }

    private static <T extends Comparable<T>> boolean isPropertyValue(BlockState state, Property<T> property, T value) {
        return state.hasProperty(property) && state.getValue(property) == value;
    }

    private static boolean needsFlintAndSteel(BlockState state) {
        var block = state.getBlock();

        if (block instanceof CandleBlock) return state.getValue(CandleBlock.LIT);
        if (block instanceof CandleCakeBlock) return state.getValue(CandleCakeBlock.LIT);

        return state.is(BlockTags.PORTALS) || state.is(BlockTags.FIRE);
    }

    private static void setCount(ItemStack stack, BlockState state, IntegerProperty property) {
        if (state.hasProperty(property)) {
            stack.setCount(state.getValue(property));
        }
    }

    private static void setUnbreakable(ItemStack stack) {
        stack.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);
    }

    public static void addBlockEntityNbt(ServerLevel level, ItemStack stack, BlockEntity blockEntity) {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(blockEntity.problemPath(), LOGGER)) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, level.registryAccess());
            blockEntity.saveCustomOnly(output);
            blockEntity.removeComponentsFromTag(output);
            BlockItem.setBlockEntityData(stack, blockEntity.getType(), output);
            stack.applyComponents(blockEntity.collectComponents());
        }
    }
}
