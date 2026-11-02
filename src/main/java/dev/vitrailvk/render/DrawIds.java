package dev.vitrailvk.render;

import dev.vitrail.render.BlockStateIds;
import dev.vitrail.render.PackNameIds;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.ByteBuffer;

/** The three identifiers in the entity vertex input, in the translator's lane order. */
public record DrawIds(int entity, int blockEntity, int item) {
	public static final DrawIds NONE = new DrawIds(0, -1, -1);

	static DrawIds entity(Entity entity) {
		int id;
		if (entity instanceof ZombieVillager zombie && zombie.isConverting() && PackNameIds.namesConvertingVillager()) {
			id = PackNameIds.convertingVillager();
		} else if (entity instanceof Player && Minecraft.getInstance().getCameraEntity() == entity
				&& PackNameIds.namesCurrentPlayer()) {
			id = PackNameIds.currentPlayer();
		} else {
			id = PackNameIds.entity(entity.getType());
		}
		return new DrawIds(id, -1, -1);
	}

	static DrawIds blockEntity(BlockState state) {
		return new DrawIds(0, BlockStateIds.id(state), -1);
	}

	DrawIds withItem(ItemStack stack) {
		return new DrawIds(this.entity, this.blockEntity,
				stack.isEmpty() ? -1 : PackNameIds.item(BuiltInRegistries.ITEM.getKey(stack.getItem())));
	}

	/** The shader sees Iris's unsigned-short lanes, widened to the pipeline's unsigned integers. */
	void put(ByteBuffer buffer, int offset) {
		buffer.putInt(offset, this.entity & 0xffff);
		buffer.putInt(offset + 4, this.blockEntity & 0xffff);
		buffer.putInt(offset + 8, this.item & 0xffff);
		buffer.putInt(offset + 12, 0);
	}
}
