package cc.sighs.cannedmonster.bootstrap;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformerVotingContext;
import cpw.mods.modlauncher.api.TransformerVoteResult;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class FusionTransformationService implements ITransformationService {
    @Override
    public String name() {
        return "cannedmonster_asm";
    }

    @Override
    public void initialize(IEnvironment environment) {}

    @Override
    public void onLoad(IEnvironment environment, Set<String> otherServices) {}

    @Override
    public List<ITransformer> transformers() {
        List<Path> roots = new ArrayList<>();
        LoadingModList mods = LoadingModList.get();
        if (mods != null) {
            for (var info : mods.getModFiles()) {
                try {
                    roots.add(info.getFile().getSecureJar().getRootPath());
                } catch (RuntimeException ignored) {
                }
            }
        }
        try {
            roots.addAll(FMLLoader.getLaunchHandler().getMinecraftPaths().minecraftPaths());
        } catch (RuntimeException ignored) {
        }
        for (String entry : System.getProperty("java.class.path", "").split(File.pathSeparator)) {
            if (entry.isEmpty()) continue;
            Path path = Path.of(entry);
            if (Files.isDirectory(path) && Files.isDirectory(path.resolve("cc/sighs/cannedmonster"))) roots.add(path);
        }
        if (roots.isEmpty()) {
            throw new IllegalStateException("Fusion ASM could not locate Minecraft or mod bytecode");
        }
        return List.of(new Adapter(new BodyTransformer(BytecodeIndex.scan(roots))));
    }

    static final class Adapter implements ITransformer<ClassNode> {
        private final BodyTransformer transformer;
        private final Set<ITransformer.Target> targets;

        Adapter(BodyTransformer transformer) {
            this.transformer = transformer;
            this.targets = transformer.targets().stream().map(ITransformer.Target::targetClass).collect(Collectors.toUnmodifiableSet());
        }

        @Override
        public ClassNode transform(ClassNode input, ITransformerVotingContext context) {
            return transformer.transform(input);
        }

        @Override
        public TransformerVoteResult castVote(ITransformerVotingContext context) {
            return TransformerVoteResult.YES;
        }

        @Override
        public Set<ITransformer.Target> targets() {
            return targets;
        }
    }
}
