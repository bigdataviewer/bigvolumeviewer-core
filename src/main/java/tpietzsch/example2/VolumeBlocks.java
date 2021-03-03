/*-
 * #%L
 * Volume rendering of bdv datasets
 * %%
 * Copyright (C) 2018 - 2021 Tobias Pietzsch
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package tpietzsch.example2;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.imglib2.Interval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.LinAlgHelpers;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import tpietzsch.blockmath.MipmapSizes;
import tpietzsch.blockmath.RequiredBlock;
import tpietzsch.blockmath.RequiredBlocks;
import tpietzsch.blocks.TileAccess;
import tpietzsch.cache.CacheSpec;
import tpietzsch.cache.DefaultFillTask;
import tpietzsch.cache.FillTask;
import tpietzsch.cache.ImageBlockKey;
import tpietzsch.cache.TextureCache;
import tpietzsch.cache.TextureCache.Tile;
import tpietzsch.cache.UploadBuffer;
import tpietzsch.multires.MultiResolutionStack3D;
import tpietzsch.multires.ResolutionLevel3D;
import tpietzsch.util.MatrixMath;

import static tpietzsch.blockmath.FindRequiredBlocks.getRequiredLevelBlocksFrustum;
import static tpietzsch.cache.TextureCache.ContentState.INCOMPLETE;

/**
 * For setting up the volume for one frame, call methods in this order:
 * <ol>
 *     <li>{@link #init(MultiResolutionStack3D, int, Matrix4fc)}</li>
 *     <li>{@link #getFillTasks()}</li>
 *     <li>{@link #makeLut(int)}</li>
 * </ol>
 * {@link #getLutBlockScales(int)} can be called at any point after {@code init()}.
 * <p>
 * For the next frame start over with {@code init()}.
 */
public class VolumeBlocks
{
	private final TextureCache textureCache;
	private final CacheSpec cacheSpec;
	private final LookupTextureARGB lut;
	private final TileAccess.Cache tileAccess;
	private final MipmapSizes sizes;

	public VolumeBlocks( final TextureCache textureCache )
	{
		this.textureCache = textureCache;
		this.cacheSpec = textureCache.spec();
		this.lut = new LookupTextureARGB();
		this.tileAccess = new TileAccess.Cache();
		this.sizes = new MipmapSizes();
	}

	private MultiResolutionStack3D< ? > multiResolutionStack;

	/** {@code projection * view * model} matrix */
	final Matrix4f pvm = new Matrix4f();

	/**
	 * Chosen base resolution level for rendering the volume.
	 * Every block in the volumes LUT is at this level or higher (coarser).
	 */
	private int baseLevel;

	/**
	 * Volume blocks at {@link #baseLevel} that should make it into the LUT (at {@code baseLevel} or higher).
	 */
	private RequiredBlocks requiredBlocks;

	/**
	 * @param multiResolutionStack single-channel, multi-resolution source
	 * @param viewportWidth width of the surface to be rendered
	 * @param pv {@code projection * view} matrix, transforms world coordinates to NDC coordinates
	 */
	public void init(
			final MultiResolutionStack3D< ? > multiResolutionStack,
			final int viewportWidth,
			final Matrix4fc pv)
	{
		this.multiResolutionStack = multiResolutionStack;

		final Matrix4f model = MatrixMath.affine( multiResolutionStack.getSourceTransform(), new Matrix4f() );
		pvm.set( pv ).mul( model );
		sizes.init( pvm, viewportWidth, multiResolutionStack.resolutions() );
		baseLevel = sizes.getBaseLevel();
	}

	/**
	 * Get the base resolution level for rendering the volume.
	 * Every block in the volumes LUT is at this level or higher (coarser).
	 * <p>
	 * This is chosen automatically, when calling {@link #init(MultiResolutionStack3D, int, Matrix4fc)}.
	 * It can be manually adjusted using {@link #setBaseLevel(int)}.
	 */
	public int getBaseLevel()
	{
		return baseLevel;
	}

	/**
	 * Set the base resolution level for rendering the volume.
	 * Every block in the volumes LUT is at this level or higher (coarser).
	 */
	public void setBaseLevel( final int baseLevel )
	{
		this.baseLevel = baseLevel;
	}

	/**
	 * Get the size of a voxel at base resolution in world coordinates.
	 * Take a source voxel (0,0,0)-(1,1,1) at the
	 * base mipmap level and transform it to world coordinates.
	 * Take the minimum of the transformed voxels edge lengths.
	 */
	public double getBaseLevelVoxelSizeInWorldCoordinates()
	{
		final AffineTransform3D sourceToWorld = multiResolutionStack.getSourceTransform();
		final int[] r = multiResolutionStack.resolutions().get( baseLevel ).getR();

		final double[] tzero = new double[ 3 ];
		sourceToWorld.apply( new double[ 3 ], tzero );

		final double[] one = new double[ 3 ];
		final double[] tone = new double[ 3 ];
		double voxelSize = Double.POSITIVE_INFINITY;
		for ( int i = 0; i < 3; ++i )
		{
			for ( int d = 0; d < 3; ++d )
				one[ d ] = d == i ? r[ d ] : 0;
			sourceToWorld.apply( one, tone );
			LinAlgHelpers.subtract( tone, tzero, tone );
			voxelSize = Math.min( voxelSize, LinAlgHelpers.length( tone ) );
		}
		return voxelSize;
	}

	/**
	 * Sets up {@code RequiredBlocks} (internally) and creates a list of {@code FillTask}s for the cache to process.
	 *
	 * @return list of {@code FillTask}s
	 */
	public List< FillTask > getFillTasks()
	{
		// block coordinates are grid coordinates of baseLevel resolution
		requiredBlocks = getRequiredBlocks( baseLevel );
		assignBestLevels( requiredBlocks, baseLevel, baseLevel );
		final List< FillTask > fillTasks = getFillTasks( requiredBlocks, baseLevel );
		return fillTasks;
	}

	/**
	 * @return whether every required block was completely available at the desired resolution level.
	 * I.e., if {@code false} is returned, the frame should be repainted until the remaining incomplete blocks are loaded.
	 */
	public boolean makeLut( final int timestamp )
	{
		final int[] rmin = requiredBlocks.getMin();
		final int[] rmax = requiredBlocks.getMax();
		lut.init( rmin, rmax, baseLevel );

		boolean complete = true;
		final int maxLevel = multiResolutionStack.resolutions().size() - 1;
		final int[] r = multiResolutionStack.resolutions().get( baseLevel ).getR();
		final int[] gj = new int[ 3 ];
		for ( RequiredBlock block : requiredBlocks.getBlocks() )
		{
			final int[] g0 = block.getGridPos();
			for ( int level = block.getBestLevel(); level <= maxLevel; ++level )
			{
				final ResolutionLevel3D< ? > resolution = multiResolutionStack.resolutions().get( level );
				final double[] sj = resolution.getS();
				for ( int d = 0; d < 3; ++d )
					gj[ d ] = ( int ) ( g0[ d ] * sj[ d ] * r[ d ] );
				final Tile tile = textureCache.get( new ImageBlockKey<>( resolution, gj ) );
				if ( tile != null )
				{
					tile.useAtTimestamp( timestamp );
					lut.putTile( g0, tile, level );
					if ( level != block.getBestLevel() || tile.state() == INCOMPLETE )
						complete = false;
					break;
				}
				else if ( level == maxLevel )
					complete = false;
			}
		}
		return complete;
	}

	/**
	 * Set up {@code lutBlockScales} array for shader.
	 * <ul>
	 * <li>{@code lutBlockScales[0] = (0,0,0)} is used for oob blocks.</li>
	 * <li>{@code lutBlockScales[i+1]} is the relative scale between {@code baseLevel} and level {@code baseLevel+i}</li>
	 * <li>{@code lutBlockScales[i+1] = (0,0,0)} for {@code i > maxLevel} is used to fill up the array to {@code NUM_BLOCK_SCALES}</li>
	 * </ul>
	 *
	 * @param NUM_BLOCK_SCALES
	 * @return
	 */
	public float[][] getLutBlockScales( final int NUM_BLOCK_SCALES )
	{
		final float[][] lutBlockScales = new float[ NUM_BLOCK_SCALES ][ 3 ];

		final int[] r = multiResolutionStack.resolutions().get( baseLevel ).getR();
		for ( int d = 0; d < 3; ++d )
			lutBlockScales[ 0 ][ d ] = 0;

		final int maxLevel = multiResolutionStack.resolutions().size() - 1;
		for ( int level = baseLevel; level <= maxLevel; ++level )
		{
			final ResolutionLevel3D< ? > resolution = multiResolutionStack.resolutions().get( level );
			final double[] sj = resolution.getS();
			final int i = 1 + level - baseLevel;
			for ( int d = 0; d < 3; ++d )
				lutBlockScales[ i ][ d ] = ( float ) ( sj[ d ] * r[ d ] );
		}

		return lutBlockScales;
	}

	// TODO: revise / remove
	public Matrix4f getIms()
	{
		return MatrixMath.affine( multiResolutionStack.getSourceTransform(), new Matrix4f() ).mul( getUpscale( baseLevel ) ).invert();
	}

	// TODO: revise / remove
	public Vector3f getSourceLevelMin()
	{
		final Interval lbb = multiResolutionStack.resolutions().get( baseLevel ).getImage();
		final Vector3f sourceLevelMin = new Vector3f( lbb.min( 0 ), lbb.min( 1 ), lbb.min( 2 ) );
		return sourceLevelMin;
	}

	// TODO: revise / remove
	public Vector3f getSourceLevelMax()
	{
		final Interval lbb = multiResolutionStack.resolutions().get( baseLevel ).getImage();
		final Vector3f sourceLevelMax = new Vector3f( lbb.max( 0 ), lbb.max( 1 ), lbb.max( 2 ) );
		return sourceLevelMax;
	}

	public LookupTextureARGB getLookupTexture()
	{
		return lut;
	}





	/**
	 * Get visible blocks in grid coordinates of {@code baseLevel} resolution.
	 */
	private RequiredBlocks getRequiredBlocks( final int baseLevel )
	{
		final Matrix4fc pvms = pvm.mul( getUpscale( baseLevel ), new Matrix4f() );
		final long[] gridMin = new long[ 3 ];
		final long[] gridMax = new long[ 3 ];
		getGridMinMax( baseLevel, gridMin, gridMax );
		return getRequiredLevelBlocksFrustum( pvms, cacheSpec.blockSize(), gridMin, gridMax );
	}

	/**
	 * Determine best resolution level for each block.
	 * Block coordinates are grid coordinates of {@code baseLevel} resolution
	 * Best resolution is capped at {@code minLevel}.
	 * ({@code minLevel <= bestLevel})
	 */
	private void assignBestLevels( final RequiredBlocks requiredBlocks, final int baseLevel, final int minLevel )
	{
		final int[] r = multiResolutionStack.resolutions().get( baseLevel ).getR();
		final int[] blockSize = cacheSpec.blockSize();
		final int[] scale = new int[] {
				blockSize[ 0 ] * r[ 0 ],
				blockSize[ 1 ] * r[ 1 ],
				blockSize[ 2 ] * r[ 2 ]
		};
		final Vector3f blockCenter = new Vector3f();
		final Vector3f tmp = new Vector3f();
		for ( final RequiredBlock block : requiredBlocks.getBlocks() )
		{
			final int[] g0 = block.getGridPos();
			blockCenter.set(
					( g0[ 0 ] + 0.5f ) * scale[ 0 ],
					( g0[ 1 ] + 0.5f ) * scale[ 1 ],
					( g0[ 2 ] + 0.5f ) * scale[ 2 ] );
			final int bestLevel = Math.max( minLevel, sizes.bestLevel( blockCenter, tmp ) );
			block.setBestLevel( bestLevel );
		}
	}

	private List< FillTask > getFillTasks( final RequiredBlocks requiredBlocks, final int baseLevel )
	{
		final int maxLevel = multiResolutionStack.resolutions().size() - 1;
		final int[] r = multiResolutionStack.resolutions().get( baseLevel ).getR();
		final HashSet< ImageBlockKey< ? > > existingKeys = new HashSet<>();
		final List< FillTask > fillTasks = new ArrayList<>();
		final int[] gj = new int[ 3 ];
		for ( RequiredBlock block : requiredBlocks.getBlocks() )
		{
			final int[] g0 = block.getGridPos();
			for ( int level = block.getBestLevel(); level <= maxLevel; ++level )
			{
				final ResolutionLevel3D< ? > resolution = multiResolutionStack.resolutions().get( level );
				final double[] sj = resolution.getS();
				for ( int d = 0; d < 3; ++d )
					gj[ d ] = ( int ) ( g0[ d ] * sj[ d ] * r[ d ] );

				final ImageBlockKey< ResolutionLevel3D< ? > > key = new ImageBlockKey<>( resolution, gj );
				if ( !existingKeys.contains( key ) )
				{
					existingKeys.add( key );
					final Tile tile = textureCache.get( key );
					if ( tile != null || canLoadCompletely( key ) || level == maxLevel )
					{
						fillTasks.add( new DefaultFillTask( key, buf -> loadTile( key, buf ), () -> containsData( key ) ) );
						break;
					}
				}
				else
					break; // TODO: is this always ok?
			}
		}

		return fillTasks;
	}

	private boolean canLoadCompletely( final ImageBlockKey< ResolutionLevel3D< ? > > key )
	{
		return tileAccess.get( key.image(), cacheSpec ).canLoadCompletely( key.pos(), false );
	}

	private boolean containsData( final ImageBlockKey< ResolutionLevel3D< ? > > key )
	{
		/*
		 * TODO.
		 *
		 * ContainsData is called to determine whether it makes sense to upload a partially completed block.
		 *
		 * Using TileAccess.canLoadCompletely will not upload any partial block, saving on texture upload bandwidth.
		 *
		 * Using TileAccess.canLoadPartially will upload any partial blocks, consuming (typically much) more texture upload bandwidth,
		 * but potentially presenting a more complete volume to the user.
		 *
		 * Decisions, decisions...
		 */
		return tileAccess.get( key.image(), cacheSpec ).canLoadCompletely( key.pos(), true );
//		return tileAccess.get( key.image(), cacheSpec ).canLoadPartially( key.pos() );
	}

	private boolean loadTile( final ImageBlockKey< ResolutionLevel3D< ? > > key, final UploadBuffer buffer )
	{
		return tileAccess.get( key.image(), cacheSpec ).loadTile( key.pos(), buffer );
	}

	/**
	 * Compute intersection of view frustum and source bounding box in grid coordinates of the specified resolutions {@code level}.
	 *
	 * @param level
	 * 		resolution level
	 */
	private void getGridMinMax( int level, long[] gridMin, long[] gridMax )
	{
		final Matrix4fc ipvms = pvm.mul( getUpscale( level ), new Matrix4f() ).invert();

		final Interval lbb = multiResolutionStack.resolutions().get( level ).getImage();
		final Vector3f fbbmin = new Vector3f();
		final Vector3f fbbmax = new Vector3f();
		ipvms.frustumAabb( fbbmin, fbbmax );
		for ( int d = 0; d < 3; ++d )
		{
			final float lbbmin = lbb.min( d ); // TODO -0.5 offset?
			final float lbbmax = lbb.max( d ); // TODO -0.5 offset?
			gridMin[ d ] = ( long ) ( Math.max( fbbmin.get( d ), lbbmin ) / cacheSpec.blockSize()[ d ] );
			gridMax[ d ] = ( long ) ( Math.min( fbbmax.get( d ), lbbmax ) / cacheSpec.blockSize()[ d ] );
		}
	}

	private Matrix4f getUpscale( int level )
	{
		return getUpscale( level, new Matrix4f() );
	}

	private Matrix4f getUpscale( int level, Matrix4f dest )
	{
		final int[] r = multiResolutionStack.resolutions().get( level ).getR();
		final int bsx = r[ 0 ];
		final int bsy = r[ 1 ];
		final int bsz = r[ 2 ];
		return dest.set(
				bsx, 0, 0, 0,
				0, bsy, 0, 0,
				0, 0, bsz, 0,
				0.5f * ( bsx - 1 ), 0.5f * ( bsy - 1 ), 0.5f * ( bsz - 1 ), 1 );
	}
}
