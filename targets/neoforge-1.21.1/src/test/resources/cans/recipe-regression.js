var CraftInput=Java.loadClass('net.minecraft.world.item.crafting.CraftingInput');
var CraftType=Java.loadClass('net.minecraft.world.item.crafting.RecipeType');
var ArrayList=Java.loadClass('java.util.ArrayList');
var EmptyStack=Java.loadClass('net.minecraft.world.item.ItemStack').EMPTY;
var recipeRows=[
 ['iron_can',['minecraft:iron_nugget'],true],
 ['large_iron_can',['minecraft:iron_ingot'],true],
 ['monster_can',['cannedmonster:iron_can','minecraft:redstone'],false],
 ['throwable_monster_can',['cannedmonster:iron_can','minecraft:redstone','minecraft:gunpowder'],false],
 ['large_monster_can',['cannedmonster:large_iron_can','minecraft:redstone'],false],
 ['throwable_large_monster_can',['cannedmonster:large_iron_can','minecraft:redstone','minecraft:gunpowder'],false],
 ['random_monster_can',['cannedmonster:large_iron_can','minecraft:ender_pearl','minecraft:gunpowder'],false]
];
var crafted=[];
for(var row of recipeRows){var slots=new ArrayList();for(var i=0;i<9;i++)slots.add(EmptyStack);if(row[2]){for(var slot of [0,2,3,5,7])slots.set(slot,AutoJS.stack(row[1][0],1));}else{for(var i=0;i<row[1].length;i++)slots.set(i,AutoJS.stack(row[1][i],1));}var input=CraftInput.of(3,3,slots);var match=world.getRecipeManager().getRecipeFor(CraftType.CRAFTING,input,world);if(!match.isPresent())throw new Error('Recipe did not match '+row[0]);var result=match.get().value().assemble(input,world.registryAccess());var id=String(AutoJS.id(result));if(id!=='cannedmonster:'+row[0])throw new Error('Wrong crafting result '+id);crafted.push(id);}
JSON.stringify(crafted);
