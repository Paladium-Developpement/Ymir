package fr.paladium.ymir.internal.world.source;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import fr.paladium.ymir.internal.world.backend.YmirWorldBackend;
import fr.paladium.ymir.lib.Ymir;
import fr.paladium.ymir.lib.YmirWorldSource;
import lombok.RequiredArgsConstructor;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.passive.EntityAnimal;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.util.MathHelper;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.SpawnerAnimals;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;

@RequiredArgsConstructor
public class YmirSourceChunkProvider implements IChunkProvider {

	private final WorldServer world;
	private final YmirWorldSource source;
	private final Set<Long> visited = new HashSet<>();
	private final Set<Long> generated = new HashSet<>();
	private final Map<Long, CompletableFuture<Optional<YmirSourceChunk>>> prefetched = new ConcurrentHashMap<>();

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
		final CompletableFuture<Optional<YmirSourceChunk>> prefetch = this.prefetched.remove(ChunkCoordIntPair.chunkXZ2Int(x, z));
		final boolean ready = prefetch != null && prefetch.isDone() && !prefetch.isCompletedExceptionally();
		if (prefetch != null && !ready) {
			prefetch.cancel(false);
		}

		final Optional<YmirSourceChunk> chunk = ready ? prefetch.join() : YmirSourceReader.inst().read(this.source, x, z, !this.world.provider.hasNoSky);
		return chunk.map(found -> this.convert(found, x, z)).orElseGet(() -> this.empty(x, z));
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
		return this.world.getBiomeGenForCoords(x, z).getSpawnableList(type);
	}

	@Override
	public ChunkPosition func_147416_a(final World world, final String structure, final int x, final int y, final int z) {
		return null;
	}

	public void prefetch(final int x, final int z) {
		if (this.source.contains(x, z) && !this.world.theChunkProviderServer.chunkExists(x, z)) {
			this.prefetched.computeIfAbsent(ChunkCoordIntPair.chunkXZ2Int(x, z), key -> this.source.take(x, z, !this.world.provider.hasNoSky).orElseGet(() -> YmirSourceReader.inst().readAsync(this.source, x, z, !this.world.provider.hasNoSky)));
		}
	}

	public void prefetch(final int centerX, final int centerZ, final int radius) {
		final Iterator<Map.Entry<Long, CompletableFuture<Optional<YmirSourceChunk>>>> iterator = this.prefetched.entrySet().iterator();
		while (iterator.hasNext()) {
			final Map.Entry<Long, CompletableFuture<Optional<YmirSourceChunk>>> entry = iterator.next();
			final long key = entry.getKey();
			if (Math.max(Math.abs((int) key - centerX), Math.abs((int) (key >> 32) - centerZ)) > radius) {
				entry.getValue().cancel(false);
				iterator.remove();
			}
		}

		for (int x = centerX - radius; x <= centerX + radius; x++) {
			for (int z = centerZ - radius; z <= centerZ + radius; z++) {
				this.prefetch(x, z);
			}
		}
	}

	public void spawnCreatures(final Chunk chunk) {
		final int view = MinecraftServer.getServer().getConfigurationManager().getViewDistance();
		if (this.isWatched(chunk.xPosition, chunk.zPosition, view)) {
			this.populate(chunk.xPosition, chunk.zPosition, view);
		}
	}

	public void spawnCreatures(final int centerX, final int centerZ) {
		final int view = MinecraftServer.getServer().getConfigurationManager().getViewDistance();
		for (int x = centerX - view; x <= centerX + view; x++) {
			for (int z = centerZ - view; z <= centerZ + view; z++) {
				if (this.generated.contains(ChunkCoordIntPair.chunkXZ2Int(x, z)) && this.world.theChunkProviderServer.chunkExists(x, z)) {
					this.populate(x, z, view);
				}
			}
		}
	}

	public void release() {
		this.source.release();
	}

	public boolean isPrefetched(final int x, final int z) {
		final CompletableFuture<Optional<YmirSourceChunk>> prefetch = this.prefetched.get(ChunkCoordIntPair.chunkXZ2Int(x, z));
		return prefetch == null || prefetch.isDone();
	}

	private boolean isWatched(final int chunkX, final int chunkZ, final int radius) {
		if (this.world.playerEntities.isEmpty()) {
			final ChunkCoordinates spawn = this.world.getSpawnPoint();
			return Math.abs((spawn.posX >> 4) - chunkX) <= radius && Math.abs((spawn.posZ >> 4) - chunkZ) <= radius;
		}

		for (final Object entity : this.world.playerEntities) {
			final EntityPlayer player = (EntityPlayer) entity;
			if (Math.abs((MathHelper.floor_double(player.posX) >> 4) - chunkX) <= radius && Math.abs((MathHelper.floor_double(player.posZ) >> 4) - chunkZ) <= radius) {
				return true;
			}
		}
		return false;
	}

	private void populate(final int chunkX, final int chunkZ, final int radius) {
		if (!this.generated.remove(ChunkCoordIntPair.chunkXZ2Int(chunkX, chunkZ)) || !this.world.getGameRules().getGameRuleBooleanValue("doMobSpawning")) {
			return;
		}

		final int limit = Ymir.get(this.world).map(YmirWorldBackend.get()::getAnimalSpawnLimit).orElse(0) * (radius * 2 + 1) * (radius * 2 + 1) / 256;
		if (this.countAnimals(chunkX, chunkZ, radius) >= limit) {
			return;
		}

		final int x = chunkX << 4;
		final int z = chunkZ << 4;
		SpawnerAnimals.performWorldGenSpawning(this.world, this.world.getBiomeGenForCoords(x + 8, z + 8), x, z, 16, 16, this.world.rand);
	}

	private int countAnimals(final int chunkX, final int chunkZ, final int radius) {
		int count = 0;
		for (final Object entity : this.world.loadedEntityList) {
			if (!(entity instanceof EntityAnimal)) {
				continue;
			}

			final EntityAnimal animal = (EntityAnimal) entity;
			if (Math.abs((MathHelper.floor_double(animal.posX) >> 4) - chunkX) <= radius && Math.abs((MathHelper.floor_double(animal.posZ) >> 4) - chunkZ) <= radius) {
				count++;
			}
		}
		return count;
	}

	private Chunk empty(final int x, final int z) {
		final Chunk chunk = new Chunk(this.world, x, z);
		chunk.generateSkylightMap();
		chunk.isTerrainPopulated = true;
		chunk.isLightPopulated = true;
		return chunk;
	}

	private Chunk convert(final YmirSourceChunk source, final int x, final int z) {
		final Chunk chunk = new Chunk(this.world, x, z);
		chunk.setStorageArrays(source.getStorages());
		if (source.getBiomes() != null) {
			chunk.setBiomeArray(source.getBiomes());
		}

		this.heightMap(chunk);
		chunk.isTerrainPopulated = true;
		chunk.isLightPopulated = true;
		this.entities(chunk, source.getEntities());
		this.tileEntities(chunk, source.getTileEntities());
		if (this.visited.add(ChunkCoordIntPair.chunkXZ2Int(x, z))) {
			this.generated.add(ChunkCoordIntPair.chunkXZ2Int(x, z));
		}
		return chunk;
	}

	private void heightMap(final Chunk chunk) {
		final int top = chunk.getTopFilledSegment() + 15;
		int lowest = Integer.MAX_VALUE;
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				int y = top;
				while (y > 0 && chunk.getBlock(x, y - 1, z).getLightOpacity() == 0) {
					y--;
				}

				chunk.heightMap[z << 4 | x] = y;
				lowest = Math.min(lowest, y);
			}
		}
		chunk.heightMapMinimum = lowest;
	}

	private void entities(final Chunk chunk, final NBTTagList entities) {
		for (int index = 0; index < entities.tagCount(); index++) {
			NBTTagCompound tag = entities.getCompoundTagAt(index);
			Entity entity = EntityList.createEntityFromNBT(tag, this.world);
			chunk.hasEntities = true;
			if (entity != null) {
				chunk.addEntity(entity);
			}

			while (entity != null && tag.hasKey("Riding", 10)) {
				tag = tag.getCompoundTag("Riding");
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
			final TileEntity tileEntity = TileEntity.createAndLoadEntity(tileEntities.getCompoundTagAt(index));
			if (tileEntity != null) {
				chunk.addTileEntity(tileEntity);
			}
		}
	}

}