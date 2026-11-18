package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.NativeAttributeAccess;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

@Mixin(AttributeMap.class)
public abstract class AttributeMapAccess implements NativeAttributeAccess {
    @Override
    @Accessor("attributes")
    public abstract Map<Attribute, AttributeInstance> cannedmonster$instances();

    @Override
    @Invoker("onAttributeModified")
    public abstract void cannedmonster$modified(AttributeInstance attribute);
}
