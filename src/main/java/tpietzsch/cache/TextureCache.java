package tpietzsch.cache;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import net.imglib2.iterator.ZeroMinIntervalIterator;
import net.imglib2.util.IntervalIndexer;
import net.imglib2.util.Intervals;

import static tpietzsch.cache.TextureCache.ContentState.INCOMPLETE;


/**
 * Not thread-safe. UploadSets are supposed to be submitted and processed sequentially.
 */
public class TextureCache
{
	public enum ContentState
	{
		INCOMPLETE,
		COMPLETE
	}

	static class Tile
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
			lru = -1;
		}
	}

	// width, height, depth in tiles
	private int[] dimensions;

	// tiles arranged in flattened texture order
	private final Tile[] tiles;

	// tiles arranged by (lru, z, y, x)
	private final ArrayList< Tile > lruOrdered = new ArrayList<>();

	// tiles.length - 1. Tile 0 is reserved for out-of-bounds.
	private final int numUnblockedTiles;

	// maps key of currently present blocks to tile containing them
	// tilemap.get(key).content == key
	private final Map< ImageBlockKey< ? >, Tile > tilemap = new ConcurrentHashMap<>();

	private static final AtomicInteger timestampGen = new AtomicInteger();

	public TextureCache( int[] dimensions )
	{
		assert dimensions.length == 3;

		final int len = ( int ) Intervals.numElements( dimensions );
		tiles = new Tile[ len ];

		int i = 0;
		for ( int z = 0; z < dimensions[ 2 ]; ++z )
			for ( int y = 0; y < dimensions[ 1 ]; ++y )
				for ( int x = 0; x < dimensions[ 0 ]; ++x )
					tiles[ i++ ] = new Tile( x, y, z );

		// i = 0 is reserved for out-of-bounds block
		for ( i = 1; i < len; ++i )
			lruOrdered.add( tiles[ i ] );
		numUnblockedTiles = len - 1;
	}

	void stage( UploadSet uploadSet )
	{
		final int timestamp = timestampGen.incrementAndGet();

		final Set< ? extends FillTask > tasks = uploadSet.entries;
		for ( FillTask task : tasks )
		{
			final Tile tile = tilemap.get( task.getKey() );
			if ( tile != null )
			{
				tile.lru = timestamp;
				if ( tile.state == INCOMPLETE )
					enqueueRefill( task );
			}
			else
			{
				enqueueFill( task );
			}
		}
	}

	ArrayDeque< FillTask > fill = new ArrayDeque<>();

	private void enqueueFill( final FillTask task )
	{
		// TODO
		fill.add( task );
	}

	void assignFillTiles( int currentTimestamp )
	{
		final int size = fill.size();
		lruOrdered.sort( lruComparator );
		if ( size > numUnblockedTiles || lruOrdered.get( size - 1 ).lru == currentTimestamp )
			throw new IllegalArgumentException( "Requested blocks don't fit into TextureCache." );

		final List< Tile > fillTiles = new ArrayList<>( lruOrdered.subList( 0, size ) );
	}

	private void enqueueRefill( final FillTask task )
	{
		// TODO
	}

	/**
	 * Called for each tile when its content is updated.
	 *
	 * @param tile
	 * @param key
	 * @param state
	 */
	void assign( Tile tile, ImageBlockKey< ? > key, ContentState state )
	{
		if ( tile.content != key )
		{
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

			final int dz = t1.z - t2.z;
			if ( dz != 0 )
				return dz;

			final int dy = t1.y - t2.y;
			if ( dy != 0 )
				return dy;

			final int dx = t1.x - t2.x;
			return dx;
		}
	};

	// TODO: use BlockingFetchQueues?
}
