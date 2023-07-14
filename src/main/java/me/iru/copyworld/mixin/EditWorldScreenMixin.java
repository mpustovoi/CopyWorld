package me.iru.copyworld.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.world.EditWorldScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.world.level.storage.LevelStorage;
import net.minecraft.world.level.storage.LevelSummary;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

@Mixin(EditWorldScreen.class)
public abstract class EditWorldScreenMixin extends Screen {

    @Shadow @Final private LevelStorage.Session storageSession;
    @Shadow protected abstract void commit();

    public EditWorldScreenMixin() {
        super(Text.translatable("selectWorld.edit.title"));
    }

    @Inject(method = "init()V", at = @At("HEAD"))
    protected void init(CallbackInfo info) {
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("selectWorld.edit.copy"), button -> {
            Path worldPath = this.getWorldPath();
            if(worldPath == null) {
                return;
            }
            Path copyWorldPath = generateName(worldPath);

            try {
                FileUtils.copyDirectory(worldPath.toFile(), copyWorldPath.toFile());
            } catch(IOException e) {
                if (!e.getMessage().contains("session.lock")) {
                    System.err.println(e.getMessage());
                    this.showToast(Text.translatable("selectWorld.edit.copyFailed"), Text.literal(e.getMessage()));
                    return;
                }
            }

            Path levelDatPath = Paths.get(worldPath + File.separator + "level.dat");
            Path copiedLevelDatPath = Paths.get(copyWorldPath + File.separator + "level.dat");
            try {
                NbtCompound nbt = NbtIo.readCompressed(levelDatPath.toFile());
                String originalName = nbt.getCompound("Data").getString("LevelName");
                NbtCompound copiedNbt = NbtIo.readCompressed(copiedLevelDatPath.toFile());
                copiedNbt.getCompound("Data").putString("LevelName", originalName + "§e Copy");
                NbtIo.writeCompressed(copiedNbt, copiedLevelDatPath.toFile());
                this.showToast(
                        Text.translatable("selectWorld.edit.copySuccess"),
                        Text.translatable("selectWorld.edit.copySuccessDetails", originalName)
                );
            } catch(IOException e) {
                System.err.println(e.getMessage());
                this.showToast(Text.translatable("selectWorld.edit.copyFailed"), Text.literal(e.getMessage()));
                return;
            }
            this.commit();
        }).dimensions(this.width / 2 - 100, this.height / 4 + 168 + 5, 200, 20).build());
    }

    @Unique
    @Nullable
    private Path getWorldPath() {
        LevelSummary summary = this.storageSession.getLevelSummary();
        if(summary == null) {
            this.showToast(Text.translatable("selectWorld.edit.copyFailed"), Text.translatable("selectWorld.edit.copySummaryFailed"));
            return null;
        }
        // please someone tell me an easier way to get the world path lol
        String iconPathString = summary.getIconPath().toString();
        return Paths.get(iconPathString.substring(0, iconPathString.length() - 9));
    }

    @Unique
    private void showToast(MutableText title, MutableText description) {
        MinecraftClient.getInstance().getToastManager().add(new SystemToast(SystemToast.Type.WORLD_BACKUP, title, description));
    }

    @Unique
    private Path generateName(Path original) {
        Path name = Paths.get(original + "-copy");
        while(name.toFile().exists()) {
            name = Paths.get(name + "-copy");
        }
        return name;
    }
}
