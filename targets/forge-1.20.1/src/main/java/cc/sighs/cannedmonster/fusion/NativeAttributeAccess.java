package cc.sighs.cannedmonster.fusion;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

import java.util.Map;

public interface NativeAttributeAccess {
    Map<Attribute, AttributeInstance> cannedmonster$instances();

    void cannedmonster$modified(AttributeInstance attribute);
}
