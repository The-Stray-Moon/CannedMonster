package cc.sighs.cannedmonster.bootstrap;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.ITransformerVotingContext;
import cpw.mods.modlauncher.api.TargetType;
import cpw.mods.modlauncher.api.TransformerVoteResult;
import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;

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
    public List<? extends ITransformer<?>> transformers() {
        var mods = LoadingModList.get();
        if (mods == null) {
            throw new IllegalStateException("Fusion ASM requires completed mod discovery");
        }
        var roots = mods.getModFiles().stream()
                .map(file -> file.getFile().getSecureJar().getRootPath()).toList();
        return List.of(new Adapter(new BodyTransformer(BytecodeIndex.scan(roots))));
    }

    static final class Adapter implements ITransformer<ClassNode> {
        private final BodyTransformer transformer;
        private final Set<Target<ClassNode>> targets;

        Adapter(BodyTransformer transformer) {
            this.transformer = transformer;
            this.targets = transformer.targets().stream().map(Target::targetClass).collect(Collectors.toUnmodifiableSet());
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
        public TargetType<ClassNode> getTargetType() {
            return TargetType.CLASS;
        }

        @Override
        public Set<Target<ClassNode>> targets() {
            return targets;
        }
    }
}
