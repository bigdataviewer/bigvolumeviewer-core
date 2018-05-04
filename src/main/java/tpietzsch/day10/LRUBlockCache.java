package tpietzsch.day10;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import net.imglib2.util.IntervalIndexer;
import net.imglib2.util.Intervals;

/**
 * A LRU cache associating keys to blocks. The idea is that a key identifies a
 * chunk of image data and a block identifies coordinates in a texture where the
 * chunk will be stored on the GPU.
 * <p>
 * When a key is added to the {@link LRUBlockCache}, a block with unused texture
 * coordinates is created . If the cache is already full, least recently used
 * key is removed and the associated block is re-used for the newly added key.
 *
 * @param <K>
 *            key type
 *
 * @author Tobias Pietzsch
 */
public class LRUBlockCache< K >
{
	public static class TextureBlock
	{
		/**
		 * Get the grid coordinates of the block.
		 *
		 * @return grid coordinates of the block.
		 */
		public int[] getGridPos()
		{
			return gridPos;
		}

		/**
		 * Get the min texture coordinates of the block.
		 *
		 * @return min texture coordinates of the block.
		 */
		public int[] getPos()
		{
			return pos;
		}

		private final int[] gridPos; // TODO: REMOVE?

		private final int[] pos;

		public TextureBlock( final int[] gridPos, final int[] pos )
		{
			this.gridPos = gridPos;
			this.pos = pos;
		}
	}

	private final int[] gridSize;

	private final int[] blockSize;

	private final int numReservedBlocks;

	private final int capacity;

	private final LinkedHashMap< K, TextureBlock > map;

	public LRUBlockCache( final int[] blockSize, final int[] gridSize )
	{
		this( blockSize, gridSize, 0 );
	}

	public LRUBlockCache( final int[] blockSize, final int[] gridSize, final int numReservedBlocks )
	{
		this.blockSize = blockSize.clone();
		this.gridSize = gridSize.clone();
		this.numReservedBlocks = numReservedBlocks;
		this.capacity = ( int ) Intervals.numElements( gridSize ) - numReservedBlocks;
		map = new LinkedHashMap<>( ( int ) ( capacity * 1.75f ), 0.75f, true );
	}

	public boolean contains( final K key )
	{
		return map.containsKey( key );
	}

	public TextureBlock get( final K key )
	{
		return map.get( key );
	}

	/**
	 * Put a new key into the map. The key must not be currently
	 * {@link #contains(Object) contained} in the map!
	 * <p>
	 * Returns the block ({@code B}) that is now associated with {@code key}.
	 * The block contains grid coordinates indicating where the data associated
	 * with {@code key} should be stored in the texture.
	 * <p>
	 * If the block grid is full, this will cause the least-recently used block
	 * do be removed from the map (and reinserted and re-used for the inserted
	 * key).
	 */
	public TextureBlock add( final K key )
	{
		// TODO remove after debugging...
		if ( map.containsKey( key ) )
			throw new IllegalArgumentException();

		final TextureBlock block;
		final int size = map.size();
		if ( size >= capacity )
		{
			final Iterator< Entry< K, TextureBlock > > it = map.entrySet().iterator();
			block = it.next().getValue();
			it.remove();
		}
		else
		{
			final int[] gridPos = new int[ 3 ];
			final int[] pos = new int[ 3 ];
			IntervalIndexer.indexToPosition( size + numReservedBlocks, gridSize, gridPos );
			for ( int d = 0; d < 3; ++d )
				pos[ d ] = blockSize[ d ] * gridPos[ d ];
			block = new TextureBlock( gridPos, pos );
		}
		map.put( key, block );
		return block;
	}

	/**
	 * Get the size of the grid of blocks.
	 *
	 * @return the size of the grid.
	 */
	public int[] getGridSize()
	{
		return gridSize;
	}

	/**
	 * Get the size of a block.
	 *
	 * @return the size of a block.
	 */
	public int[] getBlockSize()
	{
		return blockSize;
	}

	/**
	 * Get the number of blocks this cache can hold.
	 * This is the number of elements in the {@link #getGridSize() grid}.
	 *
	 * @return the number of blocks this cache can hold.
	 */
	public int getCapacity()
	{
		return capacity;
	}

	@Override
	public String toString()
	{
		final StringBuilder sb = new StringBuilder();
		sb.append( String.format( "gridSize = %d x %d x %d",
				gridSize[ 0 ], gridSize[ 1 ], gridSize[ 2 ] ) );
		sb.append( String.format( "blockSize = %d x %d x %d",
				blockSize[ 0 ], blockSize[ 1 ], blockSize[ 2 ] ) );
		final int[] size = new int[] {
				gridSize[ 0 ] * blockSize[ 0 ],
				gridSize[ 1 ] * blockSize[ 1 ],
				gridSize[ 2 ] * blockSize[ 2 ] };
		sb.append( String.format( "textureSize = %d x %d x %d  (%d elements)",
				size[ 0 ], size[ 1 ], size[ 2 ], Intervals.numElements( size ) ) );
		return null;
	}

	/**
	 * Find suitable size of a 3D texture (in multiples of {@code blockSize})
	 * such that it requires less than {@code maxMemoryInMB}, the texture is
	 * roughly square, and fits as many blocks as possible.
	 *
	 * @param blockSize
	 *            size of an individual block.
	 * @param bytesPerVoxel
	 * @param maxMemoryInMB
	 * @return size of 3D texture in multiples of {@code blockSize}.
	 */
	public static int[] findSuitableGridSize( final int[] blockSize, final int bytesPerVoxel, final int maxMemoryInMB )
	{
		final double numVoxels = maxMemoryInMB * 1024 * 1024 / bytesPerVoxel;
		final double sideLength = Math.pow( numVoxels, 1.0 / 3.0 );
		final int[] gridSize = new int[ 3 ];
		for ( int d = 0; d < 3; ++d )
			gridSize[ d ] = ( int ) ( sideLength / blockSize[ d ] );
		return gridSize;
	}
}
