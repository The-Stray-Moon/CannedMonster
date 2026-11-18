package cc.sighs.cannedmonster.bootstrap;

import net.neoforged.neoforgespi.transformation.BytecodeProvider;
import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ClassProcessorIds;
import net.neoforged.neoforgespi.transformation.ProcessorName;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ASM processor that binds fused members to their body's fields and continues the body's native
 * lifecycle through {@code OrderedCalls}. Runs before mixins so that our own injected bridge
 * methods are not redirected.
 */
public final class FusionClassProcessor implements ClassProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger("cannedmonster");
    private static final ProcessorName NAME = new ProcessorName("cannedmonster", "fusion");
    private volatile BodyTransformer transformer;

    @Override
    public ProcessorName name() {
        return NAME;
    }

    @Override
    public Set<ProcessorName> runsBefore() {
        return Set.of(ClassProcessorIds.MIXIN);
    }

    @Override
    public void link(LinkContext context) {
        Source source = new Source(context.bytecodeProvider());
        transformer = new BodyTransformer(source);
        source.stripAll();
    }

    @Override
    public boolean handlesClass(SelectionContext context) {
        if (context.empty() || context.type().getSort() != org.objectweb.asm.Type.OBJECT) return false;
        String name = context.type().getInternalName();
        if (name.startsWith("cc/sighs/cannedmonster/")) return false;
        return !(name.startsWith("java/") || name.startsWith("javax/") || name.startsWith("jdk/")
                || name.startsWith("sun/") || name.startsWith("com/") || name.startsWith("org/")
                || name.startsWith("io/") || name.startsWith("it/") || name.startsWith("cpw/")
                || name.startsWith("net/neoforged/") || name.startsWith("net/minecraftforge/"));
    }

    @Override
    public ComputeFlags processClass(TransformationContext context) {
        BodyTransformer current = transformer;
        if (current == null) return ComputeFlags.NO_REWRITE;
        try {
            return current.transform(context.node()) ? ComputeFlags.COMPUTE_FRAMES : ComputeFlags.NO_REWRITE;
        } catch (RuntimeException failure) {
            LOGGER.error("Cannot transform {}", context.type().getClassName(), failure);
            return ComputeFlags.NO_REWRITE;
        }
    }

    private static final class Source implements BodyTransformer.ClassSource {
        private final BytecodeProvider provider;
        private final Map<String, ClassNode> cache = new ConcurrentHashMap<>();
        private volatile boolean stripped;

        private Source(BytecodeProvider provider) {
            this.provider = provider;
        }

        @Override
        public ClassNode get(String name) {
            if (name == null) return null;
            ClassNode node = cache.get(name);
            if (node != null) return node;
            node = load(name);
            if (node == null) return null;
            if (stripped) strip(node);
            ClassNode existing = cache.putIfAbsent(name, node);
            if (existing != null) return existing;
            return node;
        }

        private ClassNode load(String name) {
            String binary = name.replace('/', '.');
            try {
                byte[] bytes = provider.getByteCode(binary);
                if (bytes == null) return null;
                ClassNode node = new ClassNode();
                new ClassReader(bytes).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                return node;
            } catch (ClassNotFoundException | RuntimeException missing) {
                return null;
            }
        }

        void stripAll() {
            stripped = true;
            cache.values().forEach(Source::strip);
        }

        private static void strip(ClassNode node) {
            for (var method : node.methods) {
                method.instructions = new InsnList();
                method.tryCatchBlocks.clear();
                method.localVariables = null;
            }
        }
    }
}
