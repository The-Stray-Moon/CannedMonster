package cc.sighs.cannedmonster.bootstrap;

import cpw.mods.jarhandling.JarContents;
import net.neoforged.fml.loading.targets.CommonLaunchHandler;
import net.neoforged.neoforgespi.ILaunchContext;
import net.neoforged.neoforgespi.locating.IDiscoveryPipeline;
import net.neoforged.neoforgespi.locating.IModFileCandidateLocator;
import net.neoforged.neoforgespi.locating.IncompatibleFileReporting;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;

import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Map;

public final class FusionModLocator implements IModFileCandidateLocator {
    static final String GAME_JAR = "/META-INF/cannedmonster/game.jar";

    @Override
    public void findCandidates(ILaunchContext context, IDiscoveryPipeline pipeline) {
        var resource = FusionModLocator.class.getResource(GAME_JAR);
        if (resource == null) {
            if (CommonLaunchHandler.getGroupedModFolders().containsKey("cannedmonster")) {
                return;
            }
            throw new IllegalStateException("Missing embedded CannedMonster game jar");
        }
        try {
            Path path = Path.of(resource.toURI());
            URI uri = URI.create("jij:" + path.toAbsolutePath().toUri().getRawSchemeSpecificPart()).normalize();
            var filesystem = FileSystems.newFileSystem(uri, Map.of("packagePath", path));
            pipeline.addJarContent(JarContents.of(filesystem.getPath("/")),
                    ModFileDiscoveryAttributes.DEFAULT, IncompatibleFileReporting.ERROR);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot discover embedded CannedMonster game jar", exception);
        }
    }

    @Override
    public String toString() {
        return "cannedmonster embedded game";
    }
}
