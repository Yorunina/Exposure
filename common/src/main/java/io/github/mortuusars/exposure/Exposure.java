package io.github.mortuusars.exposure;

import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import io.github.mortuusars.exposure.advancements.predicate.TamedPredicate;
import io.github.mortuusars.exposure.advancements.trigger.FrameExposedTrigger;
import io.github.mortuusars.exposure.advancements.trigger.FramePrintedTrigger;
import io.github.mortuusars.exposure.commands.argument.*;
import io.github.mortuusars.exposure.data.ColorPalette;
import io.github.mortuusars.exposure.data.Filter;
import io.github.mortuusars.exposure.data.Lens;
import io.github.mortuusars.exposure.util.supporter.Supporters;
import io.github.mortuusars.exposure.world.block.FlashBlock;
import io.github.mortuusars.exposure.world.block.LightroomBlock;
import io.github.mortuusars.exposure.world.block.entity.LightroomBlockEntity;
import io.github.mortuusars.exposure.world.camera.CameraId;
import io.github.mortuusars.exposure.world.camera.ExposureType;
import io.github.mortuusars.exposure.world.camera.capture.DitherMode;
import io.github.mortuusars.exposure.world.camera.component.CompositionGuide;
import io.github.mortuusars.exposure.world.camera.component.FlashMode;
import io.github.mortuusars.exposure.world.camera.component.SelfTimer;
import io.github.mortuusars.exposure.world.camera.component.ShutterSpeed;
import io.github.mortuusars.exposure.world.camera.film.properties.FilmStyle;
import io.github.mortuusars.exposure.world.camera.frame.Frame;
import io.github.mortuusars.exposure.world.entity.CameraStandEntity;
import io.github.mortuusars.exposure.world.entity.GlassPhotographFrameEntity;
import io.github.mortuusars.exposure.world.entity.PhotographFrameEntity;
import io.github.mortuusars.exposure.world.inventory.*;
import io.github.mortuusars.exposure.world.item.*;
import io.github.mortuusars.exposure.world.item.camera.CameraItem;
import io.github.mortuusars.exposure.world.item.camera.ShutterState;
import io.github.mortuusars.exposure.world.item.component.StoredItemStack;
import io.github.mortuusars.exposure.world.item.component.album.AlbumContent;
import io.github.mortuusars.exposure.world.item.component.album.SignedAlbumContent;
import io.github.mortuusars.exposure.world.item.crafting.recipe.serializer.ComponentTransferringRecipeSerializer;
import io.github.mortuusars.exposure.world.item.interfaces.DefaultFilmStyle;
import io.github.mortuusars.exposure.world.item.util.ItemAndStack;
import io.github.mortuusars.exposure.world.level.storage.ExposureIdentifier;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.critereon.EntitySubPredicate;
import net.minecraft.advancements.critereon.PlayerTrigger;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.stats.StatFormatter;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;


public class Exposure {
    public static final String ID = "exposure";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final List<String> MODS_REQUIRING_DIRECT_CAPTURE = List.of("veil", "pmweather");
    public static final int MAX_ENTITIES_IN_FRAME = 10;

    public static void init() {
        Blocks.init();
        BlockEntityTypes.init();
        EntityTypes.init();
        Items.init();
        CreativeTabs.init();
        DataComponents.init();
        ExposureCriteriaTriggers.init();
        ItemSubPredicates.init();
        EntitySubPredicates.init();
        MenuTypes.init();
        RecipeSerializers.init();
        SoundEvents.init();
        ArgumentTypes.init();

        // Query supporters early, so it will be available right away when needed
        Supporters.query();
    }

    public static void initServer(MinecraftServer server) {
        ExposureServer.init(server);
    }

    /**
     * Creates resource location in the mod namespace with the given filePath.
     */
    public static ResourceLocation resource(String path) {
        return new ResourceLocation(ID, path);
    }

    public static class Blocks {
        public static final Supplier<LightroomBlock> LIGHTROOM = Register.block("lightroom",
              () -> new LightroomBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BROWN)
                    .strength(2.5f)
                    .sound(SoundType.WOOD)));

        public static final Supplier<FlashBlock> FLASH = Register.block("flash",
              () -> new FlashBlock(BlockBehaviour.Properties.copy(net.minecraft.world.level.block.Blocks.AIR)
                    .strength(-1.0F, 3600000.8F)
                    .noLootTable()
                    .mapColor(MapColor.NONE)
                    .noOcclusion()
                    .noCollission()
                    .lightLevel(state -> 15)));

        static void init() {
        }
    }

    public static class BlockEntityTypes {
        public static final Supplier<BlockEntityType<LightroomBlockEntity>> LIGHTROOM =
              Register.blockEntityType("lightroom", () -> Register.newBlockEntityType(LightroomBlockEntity::new, Blocks.LIGHTROOM.get()));

        static void init() {
        }
    }

    public static class Items {
        public static final Supplier<CameraItem> CAMERA = Register.item("camera",
              () -> new CameraItem(new Item.Properties()
                    .stacksTo(1)
                    //       .component(DataComponents.CAMERA_ACTIVE, false)
              ));

        public static final Supplier<FilmRollItem> BLACK_AND_WHITE_FILM = Register.item("black_and_white_film",
              () -> new FilmRollItem(ExposureType.BLACK_AND_WHITE, FilmRollItem.BAR_BLACK_AND_WHITE,
                    new Item.Properties()
                          .stacksTo(16), FilmStyle.EMPTY));

        public static final Supplier<FilmRollItem> COLOR_FILM = Register.item("color_film",
              () -> new FilmRollItem(ExposureType.COLOR, FilmRollItem.BAR_COLOR,
                    new Item.Properties()
                          .stacksTo(16), FilmStyle.EMPTY));

        public static final Supplier<FilmRollItem> HIGH_SENSITIVITY_BLACK_AND_WHITE_FILM = Register.item("high_sensitivity_black_and_white_film",
              () -> new FilmRollItem(ExposureType.BLACK_AND_WHITE, FilmRollItem.BAR_BLACK_AND_WHITE,
                    new Item.Properties()
                          .stacksTo(16), FilmStyle.create()
                    .withSensitivity(2f)
                    .withNoise(0.065f)));

        public static final Supplier<FilmRollItem> HIGH_SENSITIVITY_COLOR_FILM = Register.item("high_sensitivity_color_film",
              () -> new FilmRollItem(ExposureType.COLOR, FilmRollItem.BAR_COLOR,
                    new Item.Properties()
                          .stacksTo(16), FilmStyle.create()
                    .withSensitivity(2f)
                    .withNoise(0.065f)));

        public static final Supplier<DevelopedFilmItem> DEVELOPED_BLACK_AND_WHITE_FILM = Register.item("developed_black_and_white_film",
              () -> new DevelopedFilmItem(ExposureType.BLACK_AND_WHITE, new Item.Properties()
                    .stacksTo(1)));

        public static final Supplier<DevelopedFilmItem> DEVELOPED_COLOR_FILM = Register.item("developed_color_film",
              () -> new DevelopedFilmItem(ExposureType.COLOR, new Item.Properties()
                    .stacksTo(1)));

        public static final Supplier<PhotographItem> PHOTOGRAPH = Register.item("photograph",
              () -> new PhotographItem(new Item.Properties()
                    .stacksTo(1)));

        public static final Supplier<ChromaticSheetItem> CHROMATIC_SHEET = Register.item("chromatic_sheet",
              () -> new ChromaticSheetItem(new Item.Properties()
                    .stacksTo(1)));

        public static final Supplier<PhotographItem> AGED_PHOTOGRAPH = Register.item("aged_photograph",
              () -> new AgedPhotographItem(new Item.Properties()
                    .stacksTo(1)));

        public static final Supplier<InterplanarProjectorItem> INTERPLANAR_PROJECTOR = Register.item("interplanar_projector",
              () -> new InterplanarProjectorItem(new Item.Properties()));
        public static final Supplier<BrokenInterplanarProjectorItem> BROKEN_INTERPLANAR_PROJECTOR = Register.item("broken_interplanar_projector",
              () -> new BrokenInterplanarProjectorItem(new Item.Properties()));

        public static final Supplier<StackedPhotographsItem> STACKED_PHOTOGRAPHS = Register.item("stacked_photographs",
              () -> new StackedPhotographsItem(new Item.Properties()
                    .stacksTo(1)));

        public static final Supplier<AlbumItem> ALBUM = Register.item("album",
              () -> new AlbumItem(new Item.Properties()
                    .stacksTo(1)));
        public static final Supplier<SignedAlbumItem> SIGNED_ALBUM = Register.item("signed_album",
              () -> new SignedAlbumItem(new Item.Properties()
                    .stacksTo(1)));

        public static final Supplier<PhotographFrameItem> PHOTOGRAPH_FRAME = Register.item("photograph_frame",
              () -> new PhotographFrameItem(new Item.Properties()));
        public static final Supplier<GlassPhotographFrameItem> GLASS_PHOTOGRAPH_FRAME = Register.item("glass_photograph_frame",
              () -> new GlassPhotographFrameItem(new Item.Properties()));

        public static final Supplier<CameraStandItem> CAMERA_STAND = Register.item("camera_stand",
              () -> new CameraStandItem(new Item.Properties()));

        public static final Supplier<BlockItem> LIGHTROOM = Register.item("lightroom",
              () -> new BlockItem(Blocks.LIGHTROOM.get(), new Item.Properties()));

        static void init() {
        }
    }

    public static class CreativeTabs {
        public static final Supplier<CreativeModeTab> EXPOSURE = Register.creativeTab("exposure", () ->
              CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
                    .title(Component.translatable("itemGroup.exposure.exposure"))
                    .icon(() -> new ItemStack(Items.CAMERA.get()))
                    .displayItems((params, output) -> {
                        output.accept(Items.CAMERA.get());
                        output.accept(Items.CAMERA_STAND.get());
                        output.accept(Items.BLACK_AND_WHITE_FILM.get());
                        output.accept(Items.COLOR_FILM.get());
                        output.accept(Items.HIGH_SENSITIVITY_BLACK_AND_WHITE_FILM.get());
                        output.accept(Items.HIGH_SENSITIVITY_COLOR_FILM.get());
                        output.accept(Items.DEVELOPED_BLACK_AND_WHITE_FILM.get());
                        output.accept(Items.DEVELOPED_COLOR_FILM.get());
                        output.accept(Items.PHOTOGRAPH.get());
                        output.accept(Items.AGED_PHOTOGRAPH.get());
                        output.accept(Items.STACKED_PHOTOGRAPHS.get());
                        output.accept(Items.ALBUM.get());
                        output.accept(Items.PHOTOGRAPH_FRAME.get());
                        output.accept(Items.GLASS_PHOTOGRAPH_FRAME.get());
                        output.accept(Items.INTERPLANAR_PROJECTOR.get());
                        output.accept(Items.LIGHTROOM.get());
                    })
                    .build());

        static void init() {
        }
    }

    public static class DataComponents {
        // Camera State

        public static Boolean getBoolean(ItemStack stack, String key) {//booleans are bytes internally
            return stack.hasTag() && stack.getTag().contains(key, Tag.TAG_BYTE) ? stack.getTag().getBoolean(key) : null;
        }

        public static void setBoolean(ItemStack stack, String key, Boolean value) {
            if (value == null) {
                stack.removeTagKey(key);
            } else {
                stack.getOrCreateTag().putBoolean(key, value);
            }
        }

        static Integer getInt(ItemStack stack, String key) {
            return stack.hasTag() && stack.getTag().contains(key, Tag.TAG_INT) ? stack.getTag().getInt(key) : null;
        }

        static void setInt(ItemStack stack, String key, Integer value) {
            if (value == null) {
                stack.removeTagKey(key);
            } else {
                stack.getOrCreateTag().putInt(key, value);
            }
        }

        static Long getLong(ItemStack stack, String key) {
            return stack.hasTag() && stack.getTag().contains(key, Tag.TAG_LONG) ? stack.getTag().getLong(key) : null;
        }

        static void setLong(ItemStack stack, String key, Long value) {
            if (value == null) {
                stack.removeTagKey(key);
            } else {
                stack.getOrCreateTag().putLong(key, value);
            }
        }

        public static Float getFloat(ItemStack stack, String key) {
            return stack.hasTag() && stack.getTag().contains(key, Tag.TAG_FLOAT) ? stack.getTag().getFloat(key) : null;
        }

        public static void setFloat(ItemStack stack, String key, Float value) {
            if (value == null) {
                stack.removeTagKey(key);
            } else {
                stack.getOrCreateTag().putFloat(key, value);
            }
        }

        public static Double getDouble(ItemStack stack, String key) {
            return stack.hasTag() && stack.getTag().contains(key, Tag.TAG_DOUBLE) ? stack.getTag().getDouble(key) : null;
        }

        public static void setDouble(ItemStack stack, String key, Double value) {
            if (value == null) {
                stack.removeTagKey(key);
            } else {
                stack.getOrCreateTag().putDouble(key, value);
            }
        }

        static String getString(ItemStack stack, String key) {
            return stack.hasTag() && stack.getTag().contains(key, Tag.TAG_STRING) ? stack.getTag().getString(key) : null;
        }

        static void setString(ItemStack stack, String key, String value) {
            if (value == null) {
                stack.removeTagKey(key);
            } else {
                stack.getOrCreateTag().putString(key, value);
            }
        }


        @Nullable
        static <T> T getValue(ItemStack stack, String key, Codec<T> codec) {
            if (!stack.hasTag()) return null;
            Tag tag = stack.getTag().get(key);
            if (tag == null) return null;
            return codec.parse(new Dynamic<>(NbtOps.INSTANCE, tag)).resultOrPartial(LOGGER::error).orElse(null);
        }

        static <T> void setValue(ItemStack stack, String key, T value, Codec<T> codec) {
            if (value == null) {
                stack.removeTagKey(key);
            } else {
                codec.encodeStart(NbtOps.INSTANCE, value).resultOrPartial(LOGGER::error).ifPresent(tag -> stack.getOrCreateTag().put(key, tag));
            }
        }

        static <E extends Enum<E>> E getEnum(ItemStack stack, String key, Class<E> clazz) {
            E[] constants = clazz.getEnumConstants();
            return stack.hasTag() && stack.getTag().contains(key, Tag.TAG_INT) ? constants[stack.getTag().getInt(key)] : null;
        }

        static <E extends Enum<E>> void setEnum(ItemStack stack, E value, String key) {
            if (value == null) {
                stack.removeTagKey(key);
            } else {
                stack.getOrCreateTag().putInt(key, value.ordinal());
            }
        }

        @Nullable
        public static CameraId getCameraId(ItemStack stack) {
            return getValue(stack, "camera_id", CameraId.CODEC);
        }

        public static void setCameraId(ItemStack stack, CameraId id) {
            setValue(stack, "camera_id", id, CameraId.CODEC);
        }

        public static Boolean getCameraGold(ItemStack stack) {
            return getBoolean(stack, "camera_gold");
        }

        public static boolean getCameraGold(ItemStack stack, boolean fallback) {
            Boolean aBoolean = getBoolean(stack, "camera_gold");
            return aBoolean == null ? fallback : aBoolean;
        }

        public static void setCameraGold(ItemStack stack, Boolean cameraGold) {
            setBoolean(stack, "camera_gold", cameraGold);
        }


        public static Boolean getCameraActive(ItemStack stack) {
            return getBoolean(stack, "camera_active");
        }

        public static boolean getCameraActive(ItemStack stack, boolean fallback) {
            Boolean aBoolean = getBoolean(stack, "camera_active");
            return aBoolean == null ? fallback : aBoolean;
        }

        public static void setCameraActive(ItemStack stack, Boolean cameraActive) {
            setBoolean(stack, "camera_active", cameraActive);
        }

        public static Boolean getCameraDisassembled(ItemStack stack) {
            return getBoolean(stack, "camera_disassembled");
        }

        public static boolean getCameraDisassembled(ItemStack stack, boolean fallback) {
            Boolean aBoolean = getBoolean(stack, "camera_disassembled");
            return aBoolean == null ? fallback : aBoolean;
        }

        public static void setCameraDisassembled(ItemStack stack, Boolean cameraDisassembled) {
            setBoolean(stack, "camera_disassembled", cameraDisassembled);
        }

        public static Long getLastCameraActionTime(ItemStack stack) {
            return getLong(stack, "camera_last_action_time");
        }

        public static long getLastCameraActionTime(ItemStack stack, long fallback) {
            Long cameraLastActionTime = getLong(stack, "camera_last_action_time");
            return cameraLastActionTime == null ? fallback : cameraLastActionTime;
        }

        public static void setCameraLastActionTime(ItemStack stack, Long lastCameraActionTime) {
            setLong(stack, "camera_last_action_time", lastCameraActionTime);
        }


        public static Boolean getSelfieMode(ItemStack stack) {
            return getBoolean(stack, "camera_selfie_mode");
        }

        public static boolean getSelfieMode(ItemStack stack, boolean fallback) {
            Boolean aBoolean = getBoolean(stack, "camera_selfie_mode");
            return aBoolean == null ? fallback : aBoolean;
        }

        public static void setSelfieMode(ItemStack stack, Boolean selfieMode) {
            setBoolean(stack, "camera_selfie_mode", selfieMode);
        }

        public static void setCameraShutterState(ItemStack stack, ShutterState state) {
            setValue(stack, "camera_shutter_state", state, ShutterState.CODEC);
        }

        public static ShutterState getCameraShutterState(ItemStack stack) {
            return getValue(stack, "camera_shutter_state", ShutterState.CODEC);
        }

        public static ShutterState getCameraShutterState(ItemStack stack, ShutterState shutterState) {
            ShutterState value = getCameraShutterState(stack);
            return value == null ? shutterState : value;
        }

        public static Long getTimerStartTick(ItemStack stack) {
            return getLong(stack, "camera_timer_start_tick");
        }

        public static long getTimerStartTick(ItemStack stack, long fallback) {
            Long cameraLastActionTime = getLong(stack, "camera_timer_start_tick");
            return cameraLastActionTime == null ? fallback : cameraLastActionTime;
        }

        public static void setTimerStartTick(ItemStack stack, Long timerStartTick) {
            setLong(stack, "camera_timer_start_tick", timerStartTick);
        }


        public static Long getTimerEndTick(ItemStack stack) {
            return getLong(stack, "camera_timer_end_tick");
        }

        public static long getTimerEndTick(ItemStack stack, long fallback) {
            Long cameraLastActionTime = getLong(stack, "camera_timer_end_tick");
            return cameraLastActionTime == null ? fallback : cameraLastActionTime;
        }

        public static void setTimerEndTick(ItemStack stack, Long timerEndTick) {
            setLong(stack, "camera_timer_end_tick", timerEndTick);
        }

        public static Long getTimerLastReleaseTick(ItemStack stack) {
            return getLong(stack, "camera_timer_last_release_tick");
        }

        public static long getTimerLastReleaseTick(ItemStack stack, long fallback) {
            Long cameraLastActionTime = getLong(stack, "camera_timer_last_release_tick");
            return cameraLastActionTime == null ? fallback : cameraLastActionTime;
        }

        public static void setTimerLastReleaseTick(ItemStack stack, Long timerLastReleaseTick) {
            setLong(stack, "camera_timer_last_release_tick", timerLastReleaseTick);
        }

        // Settings

        public static ShutterSpeed getShutterSpeed(ItemStack stack, String key) {
            return getValue(stack, key, ShutterSpeed.CODEC);
        }

        public static void setShutterSpeed(ItemStack stack, String key, ShutterSpeed value) {
            setValue(stack, key, value, ShutterSpeed.CODEC);
        }

        public static CompositionGuide getCompositionGuide(ItemStack stack, String key) {
            return getValue(stack, key, CompositionGuide.CODEC);
        }

        public static void setCompositionGuide(ItemStack stack, String key, CompositionGuide value) {
            setValue(stack, key, value, CompositionGuide.CODEC);
        }

        public static void setSelfTimer(ItemStack stack, String key, SelfTimer selfTimer) {
            setEnum(stack, selfTimer, key);
        }

        public static SelfTimer getSelfTimer(ItemStack stack, String key) {
            return getEnum(stack, key, SelfTimer.class);
        }

        public static void setFlashMode(ItemStack stack, String key, FlashMode flashMode) {
            setEnum(stack, flashMode, key);
        }

        public static FlashMode getFlashMode(ItemStack stack, String key) {
            return getEnum(stack, key, FlashMode.class);
        }

        // Attachments

        public static StoredItemStack getStoredItemStack(ItemStack stack, String key) {
            return getValue(stack, key, StoredItemStack.CODEC);
        }

        public static StoredItemStack getStoredItemStack(ItemStack stack, String key, StoredItemStack fallback) {
            StoredItemStack storedItemStack = getStoredItemStack(stack, key);
            return storedItemStack == null ? fallback : storedItemStack;
        }

        public static void setStoredItemStack(ItemStack stack, String key, StoredItemStack value) {
            setValue(stack, key, value, StoredItemStack.CODEC);
        }

        // Film

        public static Integer getFilmFrameCount(ItemStack stack) {
            return getInt(stack, "film_frame_count");
        }

        public static int getFilmFrameCount(ItemStack stack, int fallback) {
            Integer filmFrameCount = getInt(stack, "film_frame_count");
            return filmFrameCount == null ? fallback : filmFrameCount;
        }


        public static Integer getFilmFrameSize(ItemStack stack) {
            return getInt(stack, "film_frame_size");
        }

        public static void setFilmFrameSize(ItemStack stack, int filmFrameSize) {
            setInt(stack, "film_frame_size", filmFrameSize);
        }

        public static int getFilmFrameSize(ItemStack stack, int fallback) {
            Integer filmFrameSize = getInt(stack, "film_frame_size");
            return filmFrameSize == null ? fallback : filmFrameSize;
        }

        public static void setFilmStyle(ItemStack stack, FilmStyle style) {
            setValue(stack, "film_style", style, FilmStyle.CODEC);
        }

        public static FilmStyle getFilmStyle(ItemStack stack) {
            FilmStyle value = getValue(stack, "film_style", FilmStyle.CODEC);
            if (value == null && stack.getItem() instanceof DefaultFilmStyle defaultFilmStyle) {
                return defaultFilmStyle.getDefaultFilmStyle();
            }
            return value;
        }

        public static FilmStyle getFilmStyle(ItemStack stack, FilmStyle fallback) {
            FilmStyle filmStyle = getFilmStyle(stack);
            return filmStyle == null ? fallback : filmStyle;
        }

        public static ResourceLocation getFilmColorPalette(ItemStack stack) {
            return getValue(stack, "film_color_palette", ResourceLocation.CODEC);
        }

        public static DitherMode getFilmDitherMode(ItemStack stack) {
            return getEnum(stack, "film_dither_mode", DitherMode.class);
        }

        public static DitherMode getFilmDitherMode(ItemStack stack, DitherMode fallback) {
            DitherMode filmDitherMode = getFilmDitherMode(stack);
            return filmDitherMode == null ? fallback : filmDitherMode;
        }

        public static void setFilmDitherMode(ItemStack stack, DitherMode mode) {
            setEnum(stack, mode, "film_dither_mode");
        }

        public static void setFilmFrames(ItemStack stack, List<Frame> state) {
            setValue(stack, "film_frames", state, Frame.CODEC.listOf());
        }

        public static List<Frame> getFilmFrames(ItemStack stack) {
            dataFixFilmFrames(stack);
            return getValue(stack, "film_frames", Frame.CODEC.listOf());
        }

        public static List<Frame> getFilmFrames(ItemStack stack, List<Frame> fallback) {
            List<Frame> value = getFilmFrames(stack);
            return value == null ? fallback : value;
        }

        private static void dataFixFilmFrames(ItemStack stack) {
            if (!Config.Common.DATAFIX_OLD_IDS.get()) {
                return;
            }

            @Nullable CompoundTag tag = stack.getTag();
            if (tag != null && !tag.isEmpty() && !tag.contains("film_frames")) {
                if (tag.contains("Frames", Tag.TAG_LIST)) {
                    ListTag oldFrames = tag.getList("Frames", Tag.TAG_COMPOUND);
                    ListTag newFrames = new ListTag();
                    for (int i = 0; i < oldFrames.size(); i++) {
                        CompoundTag frame = oldFrames.getCompound(i);
                        if (frame.contains("Id", Tag.TAG_STRING)) {
                            CompoundTag newFrame = new CompoundTag();
                            newFrame.putString("identifier", frame.getString("Id"));
                            newFrames.add(newFrame);
                        } else if (frame.contains("Texture", Tag.TAG_STRING)) {
                            CompoundTag newFrame = new CompoundTag();
                            CompoundTag identifier = new CompoundTag();
                            identifier.putString("texture", frame.getString("Texture"));
                            newFrame.put("identifier", identifier);
                            newFrames.add(newFrame);
                        }
                    }

                    tag.put("film_frames", newFrames);
                }
            }
        }

        // Photograph

        private static void dataFixPhotographId(ItemStack stack) {
            if (!Config.Common.DATAFIX_OLD_IDS.get()) {
                return;
            }

            @Nullable CompoundTag tag = stack.getTag();
            if (tag != null && !tag.isEmpty() && !tag.contains("photograph_frame")) {
                if (tag.contains("Id", Tag.TAG_STRING)) {
                    CompoundTag frame = new CompoundTag();
                    frame.putString("identifier", tag.getString("Id"));
                    tag.put("photograph_frame", frame);
                } else if (tag.contains("Texture", Tag.TAG_STRING)) {
                    CompoundTag frame = new CompoundTag();
                    CompoundTag identifier = new CompoundTag();
                    identifier.putString("texture", tag.getString("Texture"));
                    frame.put("identifier", identifier);
                    tag.put("photograph_frame", frame);
                }
            }
        }

        public static @NotNull ExposureIdentifier getExposureIdentifier(ItemStack stack) {
            dataFixPhotographId(stack);

            @Nullable CompoundTag tag = stack.getTag();
            if (tag == null || tag.isEmpty()) {
                return ExposureIdentifier.EMPTY;
            }

            @Nullable Tag identifierTag = tag.getCompound("photograph_frame").get("identifier");
            if (identifierTag == null) {
                return ExposureIdentifier.EMPTY;
            }

            return ExposureIdentifier.CODEC.parse(NbtOps.INSTANCE, identifierTag)
                  .resultOrPartial(e -> LOGGER.error("Cannot decode ExposureIdentifier from tag '{}': {}", identifierTag, e))
                  .orElse(ExposureIdentifier.EMPTY);
        }

        public static void setPhotographFrame(ItemStack stack, Frame state) {
            setValue(stack, "photograph_frame", state, Frame.CODEC);
        }

        public static Frame getPhotographFrame(ItemStack stack) {
            dataFixPhotographId(stack);
            return getValue(stack, "photograph_frame", Frame.CODEC);
        }

        public static Frame getPhotographFrame(ItemStack stack, Frame fallback) {
            Frame value = getPhotographFrame(stack);
            return value == null ? fallback : value;
        }

        public static void setPhotographType(ItemStack stack, ExposureType state) {
            setValue(stack, "photograph_type", state, ExposureType.CODEC);
        }

        public static ExposureType getPhotographType(ItemStack stack) {
            return getValue(stack, "photograph_type", ExposureType.CODEC);
        }

        public static ExposureType getPhotographType(ItemStack stack, ExposureType fallback) {
            ExposureType value = getPhotographType(stack);
            return value == null ? fallback : value;
        }


        public static Integer getPhotographGeneration(ItemStack stack) {
            return getInt(stack, "photograph_generation");
        }

        public static int getPhotographGeneration(ItemStack stack, int fallback) {
            Integer photographGeneration = getInt(stack, "photograph_generation");
            return photographGeneration == null ? fallback : photographGeneration;
        }

        public static void setPhotographGeneration(ItemStack stack, Integer integer) {
            setInt(stack, "photograph_generation", integer);
        }

        public static List<ItemAndStack<PhotographItem>> getStackedPhotographs(ItemStack stack) {
            return getValue(stack, "stacked_photographs", StackedPhotographsItem.PHOTOGRAPH_ITEM_AND_STACK_CODEC.listOf());
        }

        public static List<ItemAndStack<PhotographItem>> getStackedPhotographs(ItemStack stack, List<ItemAndStack<PhotographItem>> fallback) {
            List<ItemAndStack<PhotographItem>> list = getStackedPhotographs(stack);
            return list == null ? fallback : list;
        }

        public static void setStackedPhotographs(ItemStack stack, List<ItemAndStack<PhotographItem>> value) {
            setValue(stack, "stacked_photographs", value, StackedPhotographsItem.PHOTOGRAPH_ITEM_AND_STACK_CODEC.listOf());
        }

        // Album

        public static AlbumContent getAlbumContent(ItemStack stack) {
            return getValue(stack, "album_content", AlbumContent.CODEC);
        }

        public static AlbumContent getAlbumContent(ItemStack stack, AlbumContent fallback) {
            AlbumContent albumContent = getAlbumContent(stack);
            return albumContent == null ? fallback : albumContent;
        }

        public static void setAlbumContent(ItemStack stack, AlbumContent content) {
            setValue(stack, "album_content", content, AlbumContent.CODEC);
        }

        public static SignedAlbumContent getSignedAlbumContent(ItemStack stack) {
            return getValue(stack, "signed_album_content", SignedAlbumContent.CODEC);
        }

        public static SignedAlbumContent getSignedAlbumContent(ItemStack stack, SignedAlbumContent fallback) {
            SignedAlbumContent signedAlbumContent = getSignedAlbumContent(stack);
            return signedAlbumContent == null ? fallback : signedAlbumContent;
        }

        public static void setSignedAlbumContent(ItemStack stack, SignedAlbumContent content) {
            setValue(stack, "signed_album_content", content, SignedAlbumContent.CODEC);
        }


        // --

        public static DitherMode getInterplanarProjectorMode(ItemStack stack) {
            return getEnum(stack, "interplanar_projector_mode", DitherMode.class);
        }

        public static DitherMode getInterplanarProjectorMode(ItemStack stack, DitherMode fallback) {
            DitherMode interplanarProjectorMode = getInterplanarProjectorMode(stack);
            return interplanarProjectorMode == null ? fallback : interplanarProjectorMode;
        }

        public static void setInterplanarProjectorMode(ItemStack stack, DitherMode mode) {
            setEnum(stack, mode, "interplanar_projector_mode");
        }

        public static String getInterplanarProjectorErrorCode(ItemStack stack, String fallback) {
            String interplanarProjectorErrorCode = getString(stack, "interplanar_projector_error_code");
            return interplanarProjectorErrorCode == null ? fallback : interplanarProjectorErrorCode;
        }

        public static void setInterplanarProjectorErrorCode(ItemStack stack, String code) {
            setString(stack, "interplanar_projector_error_code", code);
        }


        public static void setChromaticLayers(ItemStack stack, List<Frame> state) {
            setValue(stack, "chromatic_layers", state, Frame.CODEC.listOf());
        }

        public static List<Frame> getChromaticLayers(ItemStack stack) {
            return getValue(stack, "chromatic_layers", Frame.CODEC.listOf());
        }

        public static List<Frame> getChromaticLayers(ItemStack stack, List<Frame> fallback) {
            List<Frame> value = getValue(stack, "chromatic_layers", Frame.CODEC.listOf());
            return value == null ? fallback : value;
        }

        static void init() {
        }
    }

    public static class EntityTypes {
        public static final Supplier<EntityType<PhotographFrameEntity>> PHOTOGRAPH_FRAME = Register.entityType("photograph_frame",
              PhotographFrameEntity::new, MobCategory.MISC, false, builder -> builder
                    .sized(0.5f, 0.5f)
                    .updateInterval(Integer.MAX_VALUE));

        public static final Supplier<EntityType<GlassPhotographFrameEntity>> CLEAR_PHOTOGRAPH_FRAME = Register.entityType("glass_photograph_frame",
              GlassPhotographFrameEntity::new, MobCategory.MISC, false, builder -> builder
                    .sized(0.5f, 0.5f)
                    .updateInterval(Integer.MAX_VALUE));

        public static final Supplier<EntityType<CameraStandEntity>> CAMERA_STAND = Register.entityType("camera_stand",
              CameraStandEntity::new, MobCategory.MISC, false, builder -> builder
                    .sized(0.7f, 1.6f)
                    .updateInterval(3)
              //                .eyeHeight(1.40625f)
        );

        static void init() {
        }
    }

    public static class MenuTypes {
        public static final Supplier<MenuType<CameraInHandAttachmentsMenu>> CAMERA_IN_HAND = Register.menuType("camera_in_hand", CameraInHandAttachmentsMenu::fromBuffer);
        public static final Supplier<MenuType<CameraOnStandAttachmentsMenu>> CAMERA_ON_STAND = Register.menuType("camera_on_stand", CameraOnStandAttachmentsMenu::fromBuffer);
        public static final Supplier<MenuType<AlbumMenu>> ALBUM = Register.menuType("album", AlbumMenu::fromBuffer);
        public static final Supplier<MenuType<SignedAlbumMenu>> SIGNED_ALBUM = Register.menuType("signed_album", SignedAlbumMenu::fromBuffer);
        public static final Supplier<MenuType<LecternAlbumMenu>> LECTERN_ALBUM = Register.menuType("lectern_album", LecternAlbumMenu::new);
        public static final Supplier<MenuType<LightroomMenu>> LIGHTROOM = Register.menuType("lightroom", LightroomMenu::fromBuffer);
        public static final Supplier<MenuType<ItemRenameMenu>> ITEM_RENAME = Register.menuType("item_rename", ItemRenameMenu::fromBuffer);

        static void init() {
        }
    }

    public static class RecipeSerializers {
        public static final Supplier<ComponentTransferringRecipeSerializer<?>> COMPONENT_TRANSFERRING =
              Register.recipeSerializer("component_transferring", () ->
                    new ComponentTransferringRecipeSerializer<>("source", ComponentTransferringRecipeSerializer.COMPONENT_TRANSFERRING));

        public static final Supplier<ComponentTransferringRecipeSerializer<?>> FILM_DEVELOPING = Register.recipeSerializer("film_developing",
              () -> new ComponentTransferringRecipeSerializer<>("film", ComponentTransferringRecipeSerializer.FILM_DEVELOPING));
        public static final Supplier<ComponentTransferringRecipeSerializer<?>> PHOTOGRAPH_COPYING = Register.recipeSerializer("photograph_copying",
              () -> new ComponentTransferringRecipeSerializer<>("photograph", ComponentTransferringRecipeSerializer.PHOTOGRAPH_COPYING));
        public static final Supplier<ComponentTransferringRecipeSerializer<?>> PHOTOGRAPH_AGING = Register.recipeSerializer("photograph_aging",
              () -> new ComponentTransferringRecipeSerializer<>("photograph", ComponentTransferringRecipeSerializer.PHOTOGRAPH_AGING));

        static void init() {
        }
    }

    public static class SoundEvents {
        public static final Supplier<SoundEvent> VIEWFINDER_OPEN = register("item", "camera.viewfinder_open");
        public static final Supplier<SoundEvent> VIEWFINDER_CLOSE = register("item", "camera.viewfinder_close");
        public static final Supplier<SoundEvent> SHUTTER_OPEN = register("item", "camera.shutter_open");
        public static final Supplier<SoundEvent> SHUTTER_CLOSE = register("item", "camera.shutter_close");
        public static final Supplier<SoundEvent> SHUTTER_TICKING = register("item", "camera.shutter_ticking");
        public static final Supplier<SoundEvent> FILM_ADVANCE = register("item", "camera.film_advance");
        public static final Supplier<SoundEvent> FILM_ADVANCE_LAST = register("item", "camera.film_advance_last");
        public static final Supplier<SoundEvent> FILM_REMOVED = register("item", "camera.film_removed");
        public static final Supplier<SoundEvent> CAMERA_GENERIC_CLICK = register("item", "camera.generic_click");
        public static final Supplier<SoundEvent> CAMERA_BUTTON_CLICK = register("item", "camera.button_click");
        public static final Supplier<SoundEvent> CAMERA_RELEASE_BUTTON_CLICK = register("item", "camera.release_button_click");
        public static final Supplier<SoundEvent> CAMERA_DIAL_CLICK = register("item", "camera.dial_click");
        public static final Supplier<SoundEvent> CAMERA_LENS_RING_CLICK = register("item", "camera.lens_ring_click");
        public static final Supplier<SoundEvent> CAMERA_TIMER_TICK = register("item", "camera.timer_tick");
        public static final Supplier<SoundEvent> LENS_INSERT = register("item", "camera.lens_insert");
        public static final Supplier<SoundEvent> LENS_REMOVE = register("item", "camera.lens_remove");
        public static final Supplier<SoundEvent> FILTER_INSERT = register("item", "camera.filter_insert");
        public static final Supplier<SoundEvent> FILTER_REMOVE = register("item", "camera.filter_remove");
        public static final Supplier<SoundEvent> FLASH = register("item", "camera.flash");
        public static final Supplier<SoundEvent> INTERPLANAR_PROJECT = register("item", "camera.interplanar_projector.project");

        public static final Supplier<SoundEvent> PHOTOGRAPH_PLACE = register("item", "photograph.place");
        public static final Supplier<SoundEvent> PHOTOGRAPH_BREAK = register("item", "photograph.break");
        public static final Supplier<SoundEvent> PHOTOGRAPH_RUSTLE = register("item", "photograph.rustle");

        public static final Supplier<SoundEvent> PHOTOGRAPH_FRAME_PLACE = register("item", "photograph_frame.place");
        public static final Supplier<SoundEvent> PHOTOGRAPH_FRAME_BREAK = register("item", "photograph_frame.break");
        public static final Supplier<SoundEvent> PHOTOGRAPH_FRAME_ADD_ITEM = register("item", "photograph_frame.add_item");
        public static final Supplier<SoundEvent> PHOTOGRAPH_FRAME_REMOVE_ITEM = register("item", "photograph_frame.remove_item");
        public static final Supplier<SoundEvent> PHOTOGRAPH_FRAME_ROTATE_ITEM = register("item", "photograph_frame.rotate_item");

        public static final Supplier<SoundEvent> CAMERA_STAND_PLACE = register("entity", "camera_stand.place");
        public static final Supplier<SoundEvent> CAMERA_STAND_HIT = register("entity", "camera_stand.hit");
        public static final Supplier<SoundEvent> CAMERA_STAND_BREAK = register("entity", "camera_stand.break");
        public static final Supplier<SoundEvent> CAMERA_STAND_SET_CAMERA = register("entity", "camera_stand.set_camera");
        public static final Supplier<SoundEvent> CAMERA_STAND_REMOVE_CAMERA = register("entity", "camera_stand.remove_camera");

        public static final Supplier<SoundEvent> LIGHTROOM_PRINT = register("block", "lightroom.print");

        public static final Supplier<SoundEvent> WRITE = register("misc", "write");
        public static final Supplier<SoundEvent> BSOD = register("misc", "bsod");

        private static Supplier<SoundEvent> register(String category, String key) {
            Preconditions.checkState(category != null && !category.isEmpty(), "'category' should not be empty.");
            Preconditions.checkState(key != null && !key.isEmpty(), "'key' should not be empty.");
            String path = category + "." + key;
            return Register.soundEvent(path, () -> SoundEvent.createVariableRangeEvent(Exposure.resource(path)));
        }

        static void init() {
        }
    }

    public static class Stats {
        public static final Map<ResourceLocation, StatFormatter> STATS = new HashMap<>();

        public static final ResourceLocation INTERACT_WITH_LIGHTROOM =
              register(Exposure.resource("interact_with_lightroom"), StatFormatter.DEFAULT);
        public static final ResourceLocation FILM_FRAMES_EXPOSED =
              register(Exposure.resource("film_frames_exposed"), StatFormatter.DEFAULT);
        public static final ResourceLocation FLASHES_TRIGGERED =
              register(Exposure.resource("flashes_triggered"), StatFormatter.DEFAULT);

        @SuppressWarnings("SameParameterValue")
        private static ResourceLocation register(ResourceLocation location, StatFormatter formatter) {
            STATS.put(location, formatter);
            return location;
        }

        public static void register() {
            STATS.forEach((location, formatter) -> {
                net.minecraft.core.Registry.register(BuiltInRegistries.CUSTOM_STAT, location, location);
                net.minecraft.stats.Stats.CUSTOM.get(location, formatter);
            });
        }
    }

    public static class ExposureCriteriaTriggers {
        public static FrameExposedTrigger FRAME_EXPOSED = CriteriaTriggers.register(new FrameExposedTrigger());
        public static FramePrintedTrigger FRAME_PRINTED = CriteriaTriggers.register(new FramePrintedTrigger());
        public static PlayerTrigger PHOTOGRAPH_ENDERMAN_EYES = CriteriaTriggers.register(new PlayerTrigger(resource("photograph_enderman_eyes")));
        public static PlayerTrigger SUCCESSFULLY_PROJECT_IMAGE = CriteriaTriggers.register(new PlayerTrigger(resource("successfully_project_image")));

        public static void init() {
        }
    }

    public static class ItemSubPredicates {
        /*     public static Supplier<ItemSubPredicate.NbtType<FramePredicate>> FRAME = Register.itemSubPredicate("frame",
                   () -> new ItemSubPredicate.NbtType<>(FramePredicate.CODEC));
   */
        public static void init() {
        }
    }

    public static class EntitySubPredicates {
        public static final EntitySubPredicate.Type TAMED = register("tamed", TamedPredicate.CODEC);

        public static void init() {
        }

        public static EntitySubPredicate.Type register(String name, Codec<? extends EntitySubPredicate> codec) {
            BiMap<String, EntitySubPredicate.Type> types = HashBiMap.create(EntitySubPredicate.Types.TYPES);
            EntitySubPredicate.Type type = jsonObject -> codec.decode(JsonOps.INSTANCE, jsonObject).get().orThrow().getFirst();
            types.put(name, type);
            return type;
        }
    }

    public static class LootTables {
        public static final ResourceLocation SIMPLE_DUNGEON_INJECT = Exposure.resource("chests/simple_dungeon");
        public static final ResourceLocation ABANDONED_MINESHAFT_INJECT = Exposure.resource("chests/abandoned_mineshaft");
        public static final ResourceLocation STRONGHOLD_CROSSING_INJECT = Exposure.resource("chests/stronghold_crossing");
        public static final ResourceLocation VILLAGE_PLAINS_HOUSE_INJECT = Exposure.resource("chests/village_plains_house");
        public static final ResourceLocation SHIPWRECK_MAP_INJECT = Exposure.resource("chests/shipwreck_map");
    }

    public static class Tags {
        public static class Items {
            public static final TagKey<Item> FILM_ROLLS = TagKey.create(net.minecraft.core.registries.Registries.ITEM, Exposure.resource("film_rolls"));
            public static final TagKey<Item> BLACK_AND_WHITE_FILM_ROLLS = TagKey.create(net.minecraft.core.registries.Registries.ITEM, Exposure.resource("black_and_white_film_rolls"));
            public static final TagKey<Item> COLOR_FILM_ROLLS = TagKey.create(net.minecraft.core.registries.Registries.ITEM, Exposure.resource("color_film_rolls"));
            public static final TagKey<Item> DEVELOPED_FILM_ROLLS = TagKey.create(net.minecraft.core.registries.Registries.ITEM, Exposure.resource("developed_film_rolls"));
            public static final TagKey<Item> CYAN_PRINTING_DYES = TagKey.create(net.minecraft.core.registries.Registries.ITEM, Exposure.resource("cyan_printing_dyes"));
            public static final TagKey<Item> MAGENTA_PRINTING_DYES = TagKey.create(net.minecraft.core.registries.Registries.ITEM, Exposure.resource("magenta_printing_dyes"));
            public static final TagKey<Item> YELLOW_PRINTING_DYES = TagKey.create(net.minecraft.core.registries.Registries.ITEM, Exposure.resource("yellow_printing_dyes"));
            public static final TagKey<Item> BLACK_PRINTING_DYES = TagKey.create(net.minecraft.core.registries.Registries.ITEM, Exposure.resource("black_printing_dyes"));
            public static final TagKey<Item> PHOTO_PAPERS = TagKey.create(net.minecraft.core.registries.Registries.ITEM, Exposure.resource("photo_papers"));
            public static final TagKey<Item> PHOTO_AGERS = TagKey.create(net.minecraft.core.registries.Registries.ITEM, Exposure.resource("photo_agers"));
            public static final TagKey<Item> FLASHES = TagKey.create(net.minecraft.core.registries.Registries.ITEM, Exposure.resource("flashes"));
            public static final TagKey<Item> LENSES = TagKey.create(net.minecraft.core.registries.Registries.ITEM, Exposure.resource("lenses"));
            public static final TagKey<Item> FILTERS = TagKey.create(net.minecraft.core.registries.Registries.ITEM, Exposure.resource("filters"));

            public static final TagKey<Item> RED_FILTERS = TagKey.create(net.minecraft.core.registries.Registries.ITEM, Exposure.resource("red_filters"));
            public static final TagKey<Item> GREEN_FILTERS = TagKey.create(net.minecraft.core.registries.Registries.ITEM, Exposure.resource("green_filters"));
            public static final TagKey<Item> BLUE_FILTERS = TagKey.create(net.minecraft.core.registries.Registries.ITEM, Exposure.resource("blue_filters"));
        }

        public static class Blocks {
            public static final TagKey<Block> CHROMATIC_REFRACTORS = TagKey.create(net.minecraft.core.registries.Registries.BLOCK, Exposure.resource("chromatic_refractors"));
        }

        public static class Entities {
            public static final TagKey<EntityType<?>> IGNORES_CAMERA = TagKey.create(net.minecraft.core.registries.Registries.ENTITY_TYPE, Exposure.resource("ignores_camera"));
        }
    }

    public static class ArgumentTypes {
        public static final Supplier<ArgumentTypeInfo<SizeMultiplierArgument, SingletonArgumentInfo<SizeMultiplierArgument>.Template>> EXPOSURE_SIZE =
              Register.commandArgumentType("exposure_size", SizeMultiplierArgument.class, SingletonArgumentInfo.contextFree(SizeMultiplierArgument::new));
        public static final Supplier<ArgumentTypeInfo<ExposureLookArgument, SingletonArgumentInfo<ExposureLookArgument>.Template>> EXPOSURE_LOOK =
              Register.commandArgumentType("exposure_look", ExposureLookArgument.class, SingletonArgumentInfo.contextFree(ExposureLookArgument::new));
        public static final Supplier<ArgumentTypeInfo<ShaderLocationArgument, SingletonArgumentInfo<ShaderLocationArgument>.Template>> SHADER_LOCATION =
              Register.commandArgumentType("shader_location", ShaderLocationArgument.class, SingletonArgumentInfo.contextFree(ShaderLocationArgument::new));
        public static final Supplier<ArgumentTypeInfo<TextureLocationArgument, SingletonArgumentInfo<TextureLocationArgument>.Template>> TEXTURE_LOCATION =
              Register.commandArgumentType("texture_location", TextureLocationArgument.class, SingletonArgumentInfo.contextFree(TextureLocationArgument::new));
        public static final Supplier<ArgumentTypeInfo<ColorPaletteArgument, SingletonArgumentInfo<ColorPaletteArgument>.Template>> COLOR_PALETTE_LOCATION =
              Register.commandArgumentType("color_palette_location", ColorPaletteArgument.class, SingletonArgumentInfo.contextFree(ColorPaletteArgument::new));

        public static void init() {
        }
    }

    public static class Registries {
        public static final ResourceKey<Registry<ColorPalette>> COLOR_PALETTE = ResourceKey.createRegistryKey(Exposure.resource("color_palette"));
        public static final ResourceKey<Registry<Lens>> LENS = ResourceKey.createRegistryKey(Exposure.resource("lens"));
        public static final ResourceKey<Registry<Filter>> FILTER = ResourceKey.createRegistryKey(Exposure.resource("filter"));
    }
}
