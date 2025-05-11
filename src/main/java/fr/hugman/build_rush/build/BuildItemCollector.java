package fr.hugman.build_rush.build;

import fr.hugman.build_rush.BuildRush;
import fr.hugman.build_rush.mixin.CandleCakeBlockAccessor;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.*;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.potion.Potions;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.util.Unit;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

public class BuildItemCollector {
    private static final Comparator<ItemStack> COMPARATOR = Comparator.comparingInt(ItemStack::getCount).reversed();

    private final List<ItemStack> additionalStacks = new ArrayList<>();
    private final Object2IntOpenHashMap<Item> counts = new Object2IntOpenHashMap<>();
    private final Map<Item, ItemStack> singletonStacks = new HashMap<>();

    private void add(ItemStack stack) {
        if (!stack.isEmpty()) {
            this.additionalStacks.add(stack);
        }
    }

    private void addCount(ItemConvertible item, int count) {
        this.counts.addTo(item.asItem(), count);
    }

    private void addSingletonStack(Item item, Consumer<ItemStack> creator) {
        this.singletonStacks.computeIfAbsent(item, i -> {
            var stack = new ItemStack(i);
            creator.accept(stack);
            return stack;
        });
    }

    public void accept(ServerWorld world, BlockPos pos) {
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

    public void accept(ServerWorld world, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
        if (isIgnored(state)) return;

        var block = state.getBlock();
        var pickStack = state.getPickStack(world, pos, true);

        // Blocks requiring tools
        if (needsFlintAndSteel(state)) {
            this.addSingletonStack(Items.FLINT_AND_STEEL, BuildItemCollector::setUnbreakable);
        }

        if (block instanceof CampfireBlock && !state.get(CampfireBlock.LIT)) {
            this.addSingletonStack(Items.IRON_SHOVEL, BuildItemCollector::setUnbreakable);
        }

        // Fluids
        var fluid = state.getFluidState().getFluid();

        if (fluid == Fluids.WATER) {
            this.addCount(Items.WATER_BUCKET, 1);
        }

        if (block == Blocks.WATER_CAULDRON) {
            for (int i = 0; i < state.get(LeveledCauldronBlock.LEVEL); i++) {
                this.add(PotionContentsComponent.createStack(Items.POTION, Potions.WATER));
            }
        }

        if (fluid == Fluids.LAVA || block == Blocks.LAVA_CAULDRON) {
            this.addCount(Items.LAVA_BUCKET, 1);
        }

        // Multiblocks
        setCount(pickStack, state, Properties.EGGS);
        setCount(pickStack, state, Properties.CANDLES);
        setCount(pickStack, state, Properties.FLOWER_AMOUNT);
        setCount(pickStack, state, Properties.LAYERS);
        setCount(pickStack, state, Properties.PICKLES);

        if (block instanceof MultifaceGrowthBlock) {
            pickStack.setCount(MultifaceGrowthBlock.collectDirections(state).size());
        }

        if (block instanceof SlabBlock && state.get(SlabBlock.TYPE) == SlabType.DOUBLE) {
            pickStack.setCount(pickStack.getCount() * 2);
        }

        if (block instanceof VineBlock) {
            int count = 0;

            if (state.get(VineBlock.UP)) count++;
            if (state.get(VineBlock.NORTH)) count++;
            if (state.get(VineBlock.EAST)) count++;
            if (state.get(VineBlock.SOUTH)) count++;
            if (state.get(VineBlock.WEST)) count++;

            pickStack.setCount(count);
        }

        // Blocks containing items
        if (block instanceof BrewingStandBlock) {
            for (var property : BrewingStandBlock.BOTTLE_PROPERTIES) {
                this.addCount(Items.GLASS_BOTTLE, state.get(property) ? 1 : 0);
            }
        }

        if (block instanceof CandleCakeBlockAccessor cake) {
            this.addCount(cake.buildrush$getCandle(), 1);
        }

        if (block instanceof ChiseledBookshelfBlock) {
            for (var property : ChiseledBookshelfBlock.SLOT_OCCUPIED_PROPERTIES) {
                this.addCount(Items.BOOK, state.get(property) ? 1 : 0);
            }
        }

        if (block instanceof EndPortalFrameBlock && state.get(EndPortalFrameBlock.EYE)) {
            this.addCount(Items.ENDER_EYE, 1);
        }

        if (block instanceof FlowerPotBlock && block != Blocks.FLOWER_POT) {
            this.addCount(Items.FLOWER_POT, 1);
        }

        if (block instanceof LecternBlock && state.get(LecternBlock.HAS_BOOK)) {
            this.addCount(Items.BOOK, 1);
        }

        if (block instanceof RespawnAnchorBlock) {
            this.addCount(Items.GLOWSTONE, state.get(RespawnAnchorBlock.CHARGES));
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
                ItemStack stack = new ItemStack(item, Math.min(count, item.getMaxCount()));
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
        if (state.isOf(Blocks.PISTON_HEAD)) return true;

        // Multipart block states
        if (isPropertyValue(state, Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER)) return true;
        if (isPropertyValue(state, Properties.BED_PART, BedPart.FOOT)) return true;

        return false;
    }

    private static <T extends Comparable<T>> boolean isPropertyValue(BlockState state, Property<T> property, T value) {
        return state.contains(property) && state.get(property) == value;
    }

    private static boolean needsFlintAndSteel(BlockState state) {
        var block = state.getBlock();

        if (block instanceof CandleBlock) return state.get(CandleBlock.LIT);
        if (block instanceof CandleCakeBlock) return state.get(CandleCakeBlock.LIT);

        return state.isIn(BlockTags.PORTALS) || state.isIn(BlockTags.FIRE);
    }

    private static void setCount(ItemStack stack, BlockState state, IntProperty property) {
        if (state.contains(property)) {
            stack.setCount(state.get(property));
        }
    }

    private static void setUnbreakable(ItemStack stack) {
        stack.set(DataComponentTypes.UNBREAKABLE, Unit.INSTANCE);
    }

    public static void addBlockEntityNbt(ServerWorld world, ItemStack stack, BlockEntity blockEntity) {
        NbtCompound nbt = blockEntity.createNbtWithIdentifyingData(world.getRegistryManager());
        BlockItem.setBlockEntityData(stack, blockEntity.getType(), nbt);
        stack.applyComponentsFrom(blockEntity.createComponentMap());
    }
}
