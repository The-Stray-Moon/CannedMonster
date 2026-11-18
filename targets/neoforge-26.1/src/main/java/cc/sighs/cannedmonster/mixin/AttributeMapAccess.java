package cc.sighs.cannedmonster.mixin;

import cc.sighs.cannedmonster.fusion.NativeAttributeAccess;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

@Mixin(AttributeMap.class)
public interface AttributeMapAccess extends NativeAttributeAccess {
    @Override
    @Accessor("attributes")
    Map<Holder<Attribute>, AttributeInstance> cannedmonster$instances();

    @Override
    @Invoker("onAttributeModified")
    void cannedmonster$modified(AttributeInstance attribute);
}
