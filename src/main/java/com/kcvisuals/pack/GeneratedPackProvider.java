package com.kcvisuals.pack;

import com.kcvisuals.KCPaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.resource.DirectoryResourcePack;
import net.minecraft.resource.ResourcePackInfo;
import net.minecraft.resource.ResourcePackPosition;
import net.minecraft.resource.ResourcePackProfile;
import net.minecraft.resource.ResourcePackProvider;
import net.minecraft.resource.ResourcePackSource;
import net.minecraft.resource.ResourceType;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Offers the generated folder ({@link KCPaths#generatedPack()}) to the client resource pack manager. */
public final class GeneratedPackProvider implements ResourcePackProvider {
    public static final String PACK_ID = "kcvisuals:custom";
    private static final Logger LOGGER = LoggerFactory.getLogger("KC Visuals");
    private static final ResourcePackPosition POSITION =
            new ResourcePackPosition(false, ResourcePackProfile.InsertionPosition.TOP, false);

    private final Supplier<Path> dir;

    public GeneratedPackProvider(Supplier<Path> dir) {
        this.dir = dir;
    }

    @Override
    public void register(Consumer<ResourcePackProfile> adder) {
        Path path = dir.get();
        if (path == null) {
            return;
        }
        try {
            if (!Files.isRegularFile(path.resolve("pack.mcmeta"))) {
                PackBuilder.writeMcmeta(path);
            }
        } catch (IOException e) {
            LOGGER.error("Cannot prepare generated pack at {}", path, e);
            return;
        }
        ResourcePackInfo info = new ResourcePackInfo(
                PACK_ID, Text.literal(KCPaths.CUSTOM_PACK_NAME), ResourcePackSource.NONE, Optional.empty());
        ResourcePackProfile profile = ResourcePackProfile.create(
                info, new DirectoryResourcePack.DirectoryBackedFactory(path), ResourceType.CLIENT_RESOURCES, POSITION);
        if (profile == null) {
            LOGGER.error("Generated pack at {} has unreadable metadata", path);
            return;
        }
        adder.accept(profile);
    }
}
