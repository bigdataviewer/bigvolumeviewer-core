package tpietzsch.blocks;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.cache.UncheckedCache;
import net.imglib2.cache.ref.WeakRefLoaderCache;
import net.imglib2.img.cell.AbstractCellImg;
import net.imglib2.type.NativeType;
import net.imglib2.type.PrimitiveType;
import net.imglib2.util.Fraction;
import tpietzsch.cache.CacheSpec;
import tpietzsch.cache.UploadBuffer;
import tpietzsch.multires.ResolutionLevel3D;

import static net.imglib2.type.PrimitiveType.SHORT;
import static tpietzsch.backend.Texture.InternalFormat.R16;

/**
 * Copy blocks from a {@link ResolutionLevel3D} source to an {@link UploadBuffer}.
 * <p>
 * This class is not thread-safe. Use {@link Cache} to keep cached instances per thread.
 *
 * @param <S>
 *            primitive array type of image data, e.g. {@code short[]}.
 */
public class TileAccess< S >
{
	private final CacheSpec cacheSpec;

	// src?
	private final GridDataAccess< S > dataAccess;

	// ?
	private final CopySubArray< S, ByteUtils.Address > copySubArray;

	// ?
	private final CopyGridBlock gcopy = new CopyGridBlock();

	/** temporary to store block min (computed from gridPos) */
	private final int[] min = new int[ 3 ];

	public TileAccess(
			final GridDataAccess< S > dataAccess,
			final CopySubArray< S, ByteUtils.Address > copySubArray,
			final CacheSpec cacheSpec )
	{
		this.dataAccess = dataAccess;
		this.copySubArray = copySubArray;
		this.cacheSpec = cacheSpec;
	}

	public boolean canLoadCompletely( final int[] gridPos, final boolean failfast )
	{
		for ( int d = 0; d < 3; ++d )
			min[ d ] = gridPos[ d ] * cacheSpec.blockSize()[ d ] - cacheSpec.padOffset()[ d ];
		return gcopy.canLoadCompletely( min, cacheSpec.paddedBlockSize(), dataAccess, failfast );
	}

	/**
	 * Load data for the tile at {@code gridPos} into {@code buffer}.
	 * The tile is a padded block according to the {@code CacheSpec}.
	 */
	public boolean loadTile( final int[] gridPos, final UploadBuffer buffer )
	{
		for ( int d = 0; d < 3; ++d )
			min[ d ] = gridPos[ d ] * cacheSpec.blockSize()[ d ] - cacheSpec.padOffset()[ d ];
		return gcopy.copy( min, cacheSpec.paddedBlockSize(), buffer, dataAccess, copySubArray );
	}

	@SuppressWarnings( { "rawtypes", "unchecked" } )
	static TileAccess< ? > create( final ResolutionLevel3D< ? > resolutionLevel3D, final CacheSpec cacheSpec )
	{
		final Object type = resolutionLevel3D.getType();
		if ( isSupportedType( type ) )
		{
			final RandomAccessibleInterval< ? > img = resolutionLevel3D.getImage();
			final boolean cellimg = img instanceof AbstractCellImg;

			if ( cacheSpec.format() == R16 && cellimg )
			{
				final boolean volatil = type instanceof Volatile;
				return new TileAccess<>(
						volatil
								? new GridDataAccessImp.VolatileCells<>( ( AbstractCellImg ) img )
								: new GridDataAccessImp.Cells<>( ( AbstractCellImg ) img ),
						new CopySubArrayImp.ShortToAddress(),
						cacheSpec
				);
			}
		}

		throw new UnsupportedOperationException( "pixel and/or image type not supported (yet)." );
	}

	public static boolean isSupportedType( final Object type )
	{
		// Currently only [Volatile]UnsignedShortType CellImgs are handled correctly
		if ( type instanceof NativeType )
		{
			final PrimitiveType primitive = ( ( NativeType ) type ).getNativeTypeFactory().getPrimitiveType();
			final Fraction epp = ( ( NativeType ) type ).getEntitiesPerPixel();
			if ( primitive == SHORT && epp.getNumerator() == epp.getDenominator() )
				return true;
		}

		return false;
	}

	/**
	 * Thread-local weak cache for {@code TileAccess} to avoid creating too many of them.
	 * (There should be one {@code TileAccess} per thread, source stack, and {@code CacheSpec}.)
	 */
	public static class Cache
	{
		private static class Key
		{
			final ResolutionLevel3D< ? > resolutionLevel3D;
			final CacheSpec cacheSpec;
			private final int hashcode;

			Key( final ResolutionLevel3D< ? > resolutionLevel3D, final CacheSpec cacheSpec )
			{
				this.resolutionLevel3D = resolutionLevel3D;
				this.cacheSpec = cacheSpec;
				int value = resolutionLevel3D.hashCode();
				value = 31 * value + cacheSpec.hashCode();
				this.hashcode = value;
			}

			@Override
			public boolean equals( final Object o )
			{
				if ( !( o instanceof Cache.Key ) )
					return false;

				final Cache.Key key = ( Cache.Key ) o;

				if ( !resolutionLevel3D.equals( key.resolutionLevel3D ) )
					return false;
				return cacheSpec.equals( key.cacheSpec );
			}

			@Override
			public int hashCode()
			{
				return hashcode;
			}
		}

		private final ThreadLocal< UncheckedCache< Cache.Key, TileAccess< ? > > > accesses = ThreadLocal.withInitial( () ->
				new WeakRefLoaderCache< Cache.Key, TileAccess< ? > >()
						.withLoader( key -> TileAccess.create( key.resolutionLevel3D, key.cacheSpec ) )
						.unchecked() );

		public TileAccess< ? > get( final ResolutionLevel3D< ? > resolutionLevel3D, final CacheSpec cacheSpec )
		{
			return accesses.get().get( new Cache.Key( resolutionLevel3D, cacheSpec ) );
		}
	}
}
