package tpietzsch.cache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.imglib2.util.Intervals;
import tpietzsch.backend.Texture3D;
import tpietzsch.blocks.ByteUtils;

import static tpietzsch.cache.TextureCache.ContentState.INCOMPLETE;


/**
 * Not thread-safe. UploadSets are supposed to be submitted and processed
 * sequentially.
 */
public class TextureCache implements Texture3D
{
	public enum ContentState
	{
		INCOMPLETE,
		COMPLETE
	}

	public static class Tile
	{
		final int x;

		final int y;

		final int z;

		ImageBlockKey< ? > content;

		ContentState state;

		int lru;

		Tile( final int x, final int y, final int z )
		{
			this.x = x;
			this.y = y;
			this.z = z;
			state = INCOMPLETE;
			lru = -1;
		}

		/** Tile X coordinate in texture grid (padded blocks) */
		public int x()
		{
			return x;
		}

		/** Tile Y coordinate in texture grid (padded blocks) */
		public int y()
		{
			return y;
		}

		/** Tile Z coordinate in texture grid (padded blocks) */
		public int z()
		{
			return z;
		}

		public ContentState state()
		{
			return state;
		}
	}

	static class TileFillTask implements FillTask
	{
		private final FillTask task; // wrapped task
		private final Tile tile;

		public TileFillTask( final FillTask task, final Tile tile )
		{
			this.task = task;
			this.tile = tile;
		}

		@Override
		public ImageBlockKey< ? > getKey()
		{
			return task.getKey();
		}

		@Override
		public void fill( final UploadBuffer buffer )
		{
			task.fill( buffer );
		}

		Tile getTile()
		{
			return tile;
		}
	}

	private final int texWidth;
	private final int texHeight;
	private final int texDepth;

	// width, height, depth in tiles
	private final int[] dimensions; // TODO: unused? remove?

	// tile size and texture format
	private final CacheSpec spec;

	// tiles arranged in (z,y,x)-flattened texture order
	private final Tile[] tiles; // TODO: unused? remove?

	// tiles arranged by (lru, x, y, z)
	private final ArrayList< Tile > lruOrdered = new ArrayList<>();

	// tiles.length - 1. Tile 0 is reserved for out-of-bounds.
	private final int numUnblockedTiles;

	// maps key of currently present blocks to tile containing them
	// tilemap.get(key).content == key
	private final Map< ImageBlockKey< ? >, Tile > tilemap = new ConcurrentHashMap<>();

	private static final AtomicInteger timestampGen = new AtomicInteger();

	public TextureCache(
			final int[] dimensions,
			final CacheSpec spec )
	{
		assert dimensions.length == 3;

		this.dimensions = dimensions;
		this.spec = spec;

		final int[] tileDimensions = spec.paddedBlockSize();
		texWidth = dimensions[ 0 ] * tileDimensions[ 0 ];
		texHeight = dimensions[ 1 ] * tileDimensions[ 1 ];
		texDepth = dimensions[ 2 ] * tileDimensions[ 2 ];

		final int len = ( int ) Intervals.numElements( dimensions );
		tiles = new Tile[ len ];

		int i = 0;
		for ( int x = 0; x < dimensions[ 0 ]; ++x )
			for ( int y = 0; y < dimensions[ 1 ]; ++y )
				for ( int z = 0; z < dimensions[ 2 ]; ++z )
					tiles[ i++ ] = new Tile( x, y, z );

		// i = 0 is reserved for out-of-bounds block
		for ( i = 1; i < len; ++i )
			lruOrdered.add( tiles[ i ] );
		numUnblockedTiles = len - 1;
	}

	public CacheSpec spec()
	{
		return spec;
	}

	/**
	 * @return number of tiles that can fit into this cache
	 */
	public int getMaxNumTiles()
	{
		return numUnblockedTiles;
	}

	public Tile get( final ImageBlockKey< ? > key )
	{
		return tilemap.get( key );
	}

	ArrayList< TileFillTask > stage( final Collection< ? extends FillTask > tasks )
	{
		final int timestamp = timestampGen.incrementAndGet();

		final ArrayList< TileFillTask > update = new ArrayList<>();
		final ArrayList< FillTask > create = new ArrayList<>();

		for ( final FillTask task : tasks )
		{
			final Tile tile = tilemap.get( task.getKey() );
			if ( tile == null )
				create.add( task );
			else
			{
				tile.lru = timestamp;
				if ( tile.state == INCOMPLETE )
					update.add( new TileFillTask( task, tile ) );
			}
		}

		/*
		 *
		 * TODO: sort 'create' list by desired loading order
		 * TODO: similar to BlockingFetchQueues priority levels?
		 *
		 */

		final ArrayList< TileFillTask > tileFillTasks = new ArrayList<>( tasks.size() );
		final int newsize = create.size();
		final List< Tile > fillTiles = assignFillTiles( newsize, timestamp );
		for ( int i = 0; i < newsize; ++i )
		{
			final Tile tile = fillTiles.get( i );
			tile.lru = timestamp;
			tileFillTasks.add( new TileFillTask( create.get( i ), tile ) );
		}
		tileFillTasks.addAll( update );

		initializeBlockedTiles( tileFillTasks );

		return tileFillTasks;
	}

	private boolean blockedTileInitialized = false;

	/**
	 * Initialize out-of-bounds blocks etc.
	 */
	private void initializeBlockedTiles( ArrayList< TileFillTask > tileFillTasks )
	{
		if ( blockedTileInitialized )
			return;

		blockedTileInitialized = true;

		final Tile oobTile = tiles[ 0 ];
		final Object dummyImage = new Object();
		final ImageBlockKey< Object > oobDummyKey = new ImageBlockKey<>( dummyImage, new int[] { 0, 0, 0 } );
		final int elementsPerTile = ( int ) Intervals.numElements( spec.paddedBlockSize() );
		tileFillTasks.add( new TileFillTask( new DefaultFillTask( oobDummyKey, buf -> {
			ByteUtils.setShorts( ( short ) 0, buf.getAddress(), elementsPerTile );
			return true;
		} ), oobTile ) );
	}

	private List< Tile > assignFillTiles( final int size, final int currentTimestamp )
	{
		if ( size == 0 )
			return Collections.emptyList();

		lruOrdered.sort( lruComparator );
		if ( size > numUnblockedTiles || lruOrdered.get( size - 1 ).lru == currentTimestamp )
			throw new IllegalArgumentException( "Requested blocks don't fit into TextureCache." );
		return lruOrdered.subList( 0, size );
	}

	/**
	 * Called for each tile when its content is updated.
	 *
	 * @param tile
	 * @param key
	 * @param state
	 */
	void assign( final Tile tile, final ImageBlockKey< ? > key, final ContentState state )
	{
		if ( ! key.equals( tile.content ) )
		{
			if ( tile.content != null )
				tilemap.remove( tile.content );
			tilemap.put( key, tile );
		}
		tile.content = key;
		tile.state = state;
	}

	private static final Comparator< Tile > lruComparator = new Comparator< Tile >()
	{
		@Override
		public int compare( final Tile t1, final Tile t2 )
		{
			final int dlru = t1.lru - t2.lru;
			if ( dlru != 0 )
				return dlru;

			final int dx = t1.x - t2.x;
			if ( dx != 0 )
				return dx;

			final int dy = t1.y - t2.y;
			if ( dy != 0 )
				return dy;

			final int dz = t1.z - t2.z;
			return dz;
		}
	};



	/*
	 * static helpers
	 */

	/**
	 * Find suitable size of a 3D texture (in multiples of {@code cacheSpec.paddedBlockSize()})
	 * such that it requires less than {@code maxMemoryInMB}, the texture is
	 * roughly square, and fits as many blocks as possible.
	 *
	 * @param cacheSpec
	 *            provides voxel type and size of an individual block.
	 * @param maxMemoryInMB
	 * @return size of 3D texture in multiples of {@code CacheSpec.paddedBlockSize()}.
	 */
	public static int[] findSuitableGridSize( final CacheSpec cacheSpec, final int maxMemoryInMB )
	{
		final long numVoxels = ( long ) maxMemoryInMB * 1024 * 1024 / cacheSpec.format().getBytesPerElement();
		final double sideLength = Math.pow( numVoxels, 1.0 / 3.0 );
		final int[] gridSize = new int[ 3 ];
		for ( int d = 0; d < 3; ++d )
			gridSize[ d ] = ( int ) ( sideLength / cacheSpec.paddedBlockSize()[ d ] );
		return gridSize;
	}



	/*
	 * ... implements Texture3D
	 */

	@Override
	public InternalFormat texInternalFormat()
	{
		return spec.format();
	}

	@Override
	public int texWidth()
	{
		return texWidth;
	}

	@Override
	public int texHeight()
	{
		return texHeight;
	}

	@Override
	public int texDepth()
	{
		return texDepth;
	}

	@Override
	public MinFilter texMinFilter()
	{
		return MinFilter.LINEAR;
	}

	@Override
	public MagFilter texMagFilter()
	{
		return MagFilter.LINEAR;
	}

	@Override
	public Wrap texWrap()
	{
		return Wrap.CLAMP_TO_EDGE;
	}
}
