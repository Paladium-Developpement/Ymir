package fr.paladium.ymir.internal.world.source;

import java.util.List;

import fr.paladium.ymir.lib.YmirBlockRotationRegistry;
import fr.paladium.ymir.lib.YmirWorldRotation;
import fr.paladium.ymir.lib.YmirWorldSource;
import lombok.RequiredArgsConstructor;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

@RequiredArgsConstructor
public class YmirSourceChunkProvider implements IChunkProvider {

	private final WorldServer world;
	private final YmirWorldSource source;
	private final IChunkProvider fallback;

	@Override
	public boolean canSave() {
		return true;
	}

	@Override
	public String makeString() {
		return "YmirSource";
	}

	@Override
	public void saveExtraData() {}

	@Override
	public int getLoadedChunkCount() {
		return 0;
	}

	@Override
	public boolean unloadQueuedChunks() {
		return false;
	}

	@Override
	public Chunk loadChunk(final int x, final int z) {
		return this.provideChunk(x, z);
	}

	@Override
	public Chunk provideChunk(final int x, final int z) {
		if (!this.source.contains(x, z)) {
			return this.empty(x, z);
		}

		return YmirSourceRegionCache.inst().read(this.source.getDirectory(), this.source.getSourceChunkX(x, z), this.source.getSourceChunkZ(x, z)).map(root -> this.convert(root.getCompoundTag("Level"), x, z)).orElseGet(() -> this.empty(x, z));
	}

	@Override
	public boolean chunkExists(final int x, final int z) {
		return true;
	}

	@Override
	public void recreateStructures(final int x, final int z) {}

	@Override
	public boolean saveChunks(final boolean all, final IProgressUpdate progress) {
		return true;
	}

	@Override
	public void populate(final IChunkProvider provider, final int x, final int z) {}

	@Override
	public List<?> getPossibleCreatures(final EnumCreatureType type, final int x, final int y, final int z) {
		return this.fallback.getPossibleCreatures(type, x, y, z);
	}

	@Override
	public ChunkPosition func_147416_a(final World world, final String structure, final int x, final int y, final int z) {
		return null;
	}

	private Chunk empty(final int x, final int z) {
		final Chunk chunk = new Chunk(this.world, x, z);
		chunk.generateSkylightMap();
		chunk.isTerrainPopulated = true;
		return chunk;
	}

	private Chunk convert(final NBTTagCompound level, final int x, final int z) {
		final Chunk chunk = new Chunk(this.world, x, z);
		final boolean sky = !this.world.provider.hasNoSky;
		final ExtendedBlockStorage[] storages = new ExtendedBlockStorage[16];
		final NBTTagList sections = level.getTagList("Sections", 10);
		for (int index = 0; index < sections.tagCount(); index++) {
			final NBTTagCompound section = sections.getCompoundTagAt(index);
			final int y = section.getByte("Y");
			storages[y] = this.convert(section, y, sky);
		}

		chunk.setStorageArrays(storages);
		if (level.hasKey("Biomes", 7)) {
			chunk.setBiomeArray(this.rotate(level.getByteArray("Biomes")));
		}

		chunk.generateHeightMap();
		chunk.isTerrainPopulated = true;
		chunk.isLightPopulated = level.getBoolean("LightPopulated");
		this.entities(chunk, level.getTagList("Entities", 10));
		this.tileEntities(chunk, level.getTagList("TileEntities", 10));
		return chunk;
	}

	private ExtendedBlockStorage convert(final NBTTagCompound section, final int y, final boolean sky) {
		final ExtendedBlockStorage storage = new ExtendedBlockStorage(y << 4, sky);
		final YmirWorldRotation rotation = this.source.getRotation();
		final byte[] blocks = section.getByteArray("Blocks");
		final NibbleArray add = section.hasKey("Add", 7) ? new NibbleArray(section.getByteArray("Add"), 4) : null;
		final NibbleArray data = new NibbleArray(section.getByteArray("Data"), 4);
		final NibbleArray blockLight = new NibbleArray(section.getByteArray("BlockLight"), 4);
		final NibbleArray skyLight = sky && section.hasKey("SkyLight", 7) ? new NibbleArray(section.getByteArray("SkyLight"), 4) : null;
		for (int index = 0; index < 4096; index++) {
			final int localX = index & 15;
			final int localZ = index >> 4 & 15;
			final int localY = index >> 8;
			final int targetX = rotation.rotateX(localX, localZ) & 15;
			final int targetZ = rotation.rotateZ(localX, localZ) & 15;
			final Block block = Block.getBlockById(blocks[index] & 255 | (add == null ? 0 : add.get(localX, localY, localZ) << 8));
			storage.func_150818_a(targetX, localY, targetZ, block);
			storage.setExtBlockMetadata(targetX, localY, targetZ, YmirBlockRotationRegistry.inst().rotate(block, data.get(localX, localY, localZ), rotation));
			storage.setExtBlocklightValue(targetX, localY, targetZ, blockLight.get(localX, localY, localZ));
			if (skyLight != null) {
				storage.setExtSkylightValue(targetX, localY, targetZ, skyLight.get(localX, localY, localZ));
			}
		}

		storage.removeInvalidBlocks();
		return storage;
	}

	private byte[] rotate(final byte[] biomes) {
		final YmirWorldRotation rotation = this.source.getRotation();
		final byte[] rotated = new byte[biomes.length];
		for (int index = 0; index < biomes.length; index++) {
			final int localX = index & 15;
			final int localZ = index >> 4;
			rotated[(rotation.rotateZ(localX, localZ) & 15) << 4 | rotation.rotateX(localX, localZ) & 15] = biomes[index];
		}
		return rotated;
	}

	private void entities(final Chunk chunk, final NBTTagList entities) {
		for (int index = 0; index < entities.tagCount(); index++) {
			NBTTagCompound tag = this.transformEntity((NBTTagCompound) entities.getCompoundTagAt(index).copy());
			Entity entity = EntityList.createEntityFromNBT(tag, this.world);
			chunk.hasEntities = true;
			if (entity != null) {
				chunk.addEntity(entity);
			}

			while (entity != null && tag.hasKey("Riding", 10)) {
				tag = this.transformEntity(tag.getCompoundTag("Riding"));
				final Entity mount = EntityList.createEntityFromNBT(tag, this.world);
				if (mount != null) {
					chunk.addEntity(mount);
					entity.mountEntity(mount);
				}
				entity = mount;
			}
		}
	}

	private void tileEntities(final Chunk chunk, final NBTTagList tileEntities) {
		for (int index = 0; index < tileEntities.tagCount(); index++) {
			final NBTTagCompound tag = (NBTTagCompound) tileEntities.getCompoundTagAt(index).copy();
			final int x = tag.getInteger("x");
			final int z = tag.getInteger("z");
			tag.setInteger("x", this.source.getWorldX(x, z));
			tag.setInteger("z", this.source.getWorldZ(x, z));
			if (tag.hasKey("Rot", 1)) {
				tag.setByte("Rot", (byte) (tag.getByte("Rot") + this.source.getRotation().getQuarters() * 4 & 15));
			}

			final TileEntity tileEntity = TileEntity.createAndLoadEntity(tag);
			if (tileEntity != null) {
				chunk.addTileEntity(tileEntity);
			}
		}
	}

	private NBTTagCompound transformEntity(final NBTTagCompound tag) {
		final YmirWorldRotation rotation = this.source.getRotation();
		final NBTTagList position = tag.getTagList("Pos", 6);
		if (position.tagCount() == 3) {
			final double x = position.func_150309_d(0);
			final double z = position.func_150309_d(2);
			final NBTTagList rotated = new NBTTagList();
			rotated.appendTag(new NBTTagDouble(this.source.getWorldX(x, z)));
			rotated.appendTag(new NBTTagDouble(position.func_150309_d(1)));
			rotated.appendTag(new NBTTagDouble(this.source.getWorldZ(x, z)));
			tag.setTag("Pos", rotated);
		}

		final NBTTagList look = tag.getTagList("Rotation", 5);
		if (look.tagCount() == 2) {
			final NBTTagList rotated = new NBTTagList();
			rotated.appendTag(new NBTTagFloat(rotation.rotateYaw(look.func_150308_e(0))));
			rotated.appendTag(new NBTTagFloat(look.func_150308_e(1)));
			tag.setTag("Rotation", rotated);
		}

		if (tag.hasKey("TileX", 3) && tag.hasKey("TileZ", 3)) {
			final int x = tag.getInteger("TileX");
			final int z = tag.getInteger("TileZ");
			tag.setInteger("TileX", this.source.getWorldX(x, z));
			tag.setInteger("TileZ", this.source.getWorldZ(x, z));
		}

		if (tag.hasKey("Direction", 1)) {
			tag.setByte("Direction", (byte) (tag.getByte("Direction") + rotation.getQuarters() & 3));
			tag.removeTag("Dir");
		}
		return tag;
	}

}