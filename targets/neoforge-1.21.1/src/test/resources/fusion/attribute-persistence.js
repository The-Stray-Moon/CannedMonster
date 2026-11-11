var F = Java.loadClass('cc.sighs.cannedmonster.fusion.FusionRuntime');
var ET = Java.loadClass('net.minecraft.world.entity.EntityType');
var Attr = Java.loadClass('net.minecraft.world.entity.ai.attributes.Attributes');
var count = 0;
var result = [];
for (var body of AutoJS.level().getAllEntities()) {
    if (!body.getTags().contains('attribute-persistence')) continue;
    if (F.facets(body).size() !== 5) throw new Error('Member order/count not restored');
    if (Math.abs(body.getAttributeBaseValue(Attr.MAX_HEALTH) - 340/6) > 0.00001) throw new Error('Average changed after restart');
    var extra = ET.PIG.create(AutoJS.level());
    extra.getAttribute(Attr.MAX_HEALTH).setBaseValue(80);
    F.prepend(body, extra);
    if (Math.abs(body.getAttributeBaseValue(Attr.MAX_HEALTH) - 60) > 0.00001) throw new Error('Original weights not restored');
    body.removeTag('attribute-persistence');
    body.addTag('attribute-persistence-verified');
    result.push({members:F.facets(body).size()+1, average:body.getAttributeBaseValue(Attr.MAX_HEALTH)});
    count++;
}
if (count !== 1) throw new Error('Expected one persistence test body, found '+count);
JSON.stringify({passed:3, bodies:result});
