/*-
 * #%L
 * Volume rendering of bdv datasets
 * %%
 * Copyright (C) 2018 - 2026 Tobias Pietzsch
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
package bvv.core.render;

import bvv.core.blockmath.FindRequiredBlocks;
import bvv.core.blockmath.MipmapSizes;
import bvv.core.blockmath.RequiredBlock;
import bvv.core.blockmath.RequiredBlocks;
import bvv.core.cache.CacheSpec;
import bvv.core.cache.DefaultFillTask;
import bvv.core.cache.FillTask;
import bvv.core.cache.ImageBlockKey;
import bvv.core.cache.TextureCache;
import java.nio.ByteBuffer;
import bvv.core.multires.ResolutionLevel3D;
import bvv.core.util.MatrixMath;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.imglib2.Interval;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.util.LinAlgHelpers;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import bvv.core.blocks.TileAccess;
import bvv.core.multires.MultiResolutionStack3D;

/**
 * For setting up the volume for one frame, call methods in this order:
 * <ol>
 *     <li>{@link #init(MultiResolutionStack3D, TextureCache, int, Matrix4fc)}</li>
 *     <li>{@link #getFillTasks()}</li>
 *     <li>{@link #makeLut(int)}</li>
 * </ol>
 * {@link #getLutBlockScales(int)} can be called at any point after {@code init()}.
 * <p>
 * For the next frame start over with {@code init()}.
 */
public class VolumeBlocks
{
	private final LookupTextureARGB lut;
	private final MipmapSizes sizes;

	public VolumeBlocks()
	{
		this.lut = new LookupTextureARGB();
		this.sizes = new MipmapSizes();
	}

	private MultiResolutionStack3D< ? > multiResolutionStack;
	private TextureCache textureCache;
	private CacheSpec cacheSpec;

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
			final TextureCache textureCache,
			final int viewportWidth,
			final Matrix4fc pv)
	{
		this.multiResolutionStack = multiResolutionStack;
		this.textureCache = textureCache;
		this.cacheSpec = textureCache.spec();

		final Matrix4f model = MatrixMath.affine( multiResolutionStack.getSourceTransform(), new Matrix4f() );
		pvm.set( pv ).mul( model );
		sizes.init( pvm, viewportWidth, multiResolutionStack.resolutions() );
		baseLevel = sizes.getBaseLevel();
	}

	/**
	 * Get the base resolution level for rendering the volume.
	 * Every block in the volumes LUT is at this level or higher (coarser).
	 * <p>
	 * This is chosen automatically, when calling {@link #init(MultiResolutionStack3D, TextureCache, int, Matrix4fc)}.
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
				final TextureCache.Tile tile = textureCache.get( new ImageBlockKey<>( resolution, gj ) );
				if ( tile != null )
				{
					tile.useAtTimestamp( timestamp );
					lut.putTile( g0, tile, level );
					if ( level != block.getBestLevel() || tile.state() == TextureCache.ContentState.INCOMPLETE )
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

	/**
	 * Get the transformation from world coordinates to voxel coordinates of
	 * the {@link #getBaseLevel() base level}, in OpenGL convention (integer
	 * coordinates at voxel min corners, see the NB in {@code getUpscale()}).
	 * <p>
	 * This is the shader uniform {@code im}: used to intersect rays with the
	 * source bounding box and to locate samples in the LUT and texture cache.
	 *
	 * @return world to base-level voxel coordinates (OpenGL convention)
	 */
	public Matrix4f getIms()
	{
		return MatrixMath.affine( multiResolutionStack.getSourceTransform(), new Matrix4f() ).mul( getUpscale( baseLevel ) ).invert();
	}

	/**
	 * Get the min corner of the source bounding box, in voxel coordinates of
	 * the {@link #getBaseLevel() base level} in OpenGL convention (the
	 * coordinates {@link #getIms()} maps to). This is shader uniform
	 * {@code sourcemin}; together with {@link #getSourceLevelMax()} it clips
	 * rays to the source.
	 * <p>
	 * The box is the extent of the <em>full-resolution</em> image (level 0),
	 * not that of the base level image. With OpenGL convention, voxel
	 * coordinates of different levels differ by a pure scale, {@code p_b = p_0
	 * / r_b} (see the NB in {@code getUpscale()}), so the level-0 box
	 * {@code [min_0, max_0 + 1]} becomes {@code [min_0 / r_b, (max_0 + 1) /
	 * r_b]}.
	 * <p>
	 * Why not the base level's own extent: Downsampled sizes are rounded
	 * (up in current BDV export, down in some older pyramids), so unless the
	 * full-resolution size is divisible by {@code r_b}, the base level image
	 * sticks out past the source or falls short of it, by up to {@code r_b - 1}
	 * full-resolution voxels at the max faces. Since the base level changes
	 * with zoom, the rendered extent would then jump while zooming, and would
	 * differ from rendering the same image as a {@code SIMPLE} stack. Using the
	 * full-resolution extent gives the source one fixed spatial extent. Where
	 * the base level falls short, rays sample the zero padding of the cache
	 * tiles over the missing fraction of a voxel.
	 *
	 * @return min corner of the full-resolution source extent, in base-level
	 *         voxel coordinates (OpenGL convention)
	 */
	public Vector3f getSourceLevelMin()
	{
		final Interval full = multiResolutionStack.resolutions().get( 0 ).getImage();
		final int[] r = multiResolutionStack.resolutions().get( baseLevel ).getR();
		final float min_x = full.min( 0 ) / ( float ) r[ 0 ];
		final float min_y = full.min( 1 ) / ( float ) r[ 1 ];
		final float min_z = full.min( 2 ) / ( float ) r[ 2 ];
		return new Vector3f( min_x, min_y, min_z );
	}

	/**
	 * Get the max corner (exclusive) of the source bounding box, in voxel
	 * coordinates of the {@link #getBaseLevel() base level} in OpenGL convention (the
	 * coordinates {@link #getIms()} maps to). This is shader uniform
	 * {@code sourcemax}.
	 * <p>
	 * See {@link #getSourceLevelMin()}.
	 *
	 * @return max corner of the full-resolution source extent, in base-level
	 *         voxel coordinates (OpenGL convention)
	 */
	public Vector3f getSourceLevelMax()
	{
		final Interval full = multiResolutionStack.resolutions().get( 0 ).getImage();
		final int[] r = multiResolutionStack.resolutions().get( baseLevel ).getR();
		final float max_x = ( full.max( 0 ) + 1 ) / ( float ) r[ 0 ];
		final float max_y = ( full.max( 1 ) + 1 ) / ( float ) r[ 1 ];
		final float max_z = ( full.max( 2 ) + 1 ) / ( float ) r[ 2 ];
		return new Vector3f( max_x, max_y, max_z );
	}

	public LookupTextureARGB getLookupTexture()
	{
		return lut;
	}

	public TextureCache getTextureCache()
	{
		return textureCache;
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
		return FindRequiredBlocks.getRequiredLevelBlocksFrustum( pvms, cacheSpec.blockSize(), gridMin, gridMax );
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
					( g0[ 0 ] + 0.5f ) * scale[ 0 ] - 0.5f,
					( g0[ 1 ] + 0.5f ) * scale[ 1 ] - 0.5f,
					( g0[ 2 ] + 0.5f ) * scale[ 2 ] - 0.5f);
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
					final TextureCache.Tile tile = textureCache.get( key );
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
		return TileAccess.of( key.image(), cacheSpec ).canLoadCompletely( key.pos(), false );
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
		return TileAccess.of( key.image(), cacheSpec ).canLoadCompletely( key.pos(), true );
//		return TileAccess.of( key.image(), cacheSpec ).canLoadPartially( key.pos() );
	}

	private boolean loadTile( final ImageBlockKey< ResolutionLevel3D< ? > > key, final ByteBuffer buffer )
	{
		return TileAccess.of( key.image(), cacheSpec ).loadTile( key.pos(), buffer );
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

		// NB: The grid range is clamped to the *level* image lbb, not to the
		// full-resolution extent used as the ray clipping box (see
		// getSourceLevelMin()). The two differ when the full-resolution size
		// is not divisible by r. If the level image is rounded up, it is
		// larger by less than one voxel. The box then ends inside the last
		// level voxel, so both select the same blocks. If it is rounded down,
		// it is smaller, and the box reaches up to (r - 1) / r voxels past the
		// last level voxel.
		// If that sliver falls into a block beyond gridMax (when max + 1 is a
		// multiple of blockSize), the block is never required, its LUT entry
		// stays 0 (out of bounds), and rays sample 0 there. Otherwise it lies
		// in the last required block, whose tile has zeros there from
		// extendZero padding. Either way the result is 0, so this is
		// accepted rather than clamping to the box.
		final Interval lbb = multiResolutionStack.resolutions().get( level ).getImage();
		final Vector3f fbbmin = new Vector3f();
		final Vector3f fbbmax = new Vector3f();
		ipvms.frustumAabb( fbbmin, fbbmax );
		for ( int d = 0; d < 3; ++d )
		{
			final float lbbmin = lbb.min( d );
			final float lbbmax = lbb.max( d );
			gridMin[ d ] = ( long ) Math.floor( Math.max( fbbmin.get( d ), lbbmin ) / cacheSpec.blockSize()[ d ] );
			gridMax[ d ] = ( long ) Math.floor( Math.min( fbbmax.get( d ), lbbmax ) / cacheSpec.blockSize()[ d ] );
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

		// NB: Level coordinates here are in OpenGL convention (integer
		// coordinates at voxel min corner), full-resolution coordinates in
		// ImgLib2 convention (integer coordinates at voxel centre).
		//
		// In ImgLib2 convention, the default mipmap convention maps level
		// coordinate x to full-resolution x_0 = r * x + (r - 1) / 2.
		// (See DownSamplingScheme.DEFAULT_BLOCK_AVERAGE and
		// bdv.util.MipmapTransforms.getMipmapTransformDefault().)
		//
		// With OpenGL-convention level coordinate p = x + 0.5 this becomes
		// x_0 = r * p - 0.5: a pure scale between levels, plus a fixed
		// half-voxel shift into ImgLib2 convention at full resolution.
		// (E.g., r = 2: p = 0 -> x_0 = -0.5, the min corner of full-res voxel 0.)
		//
		// The result is in full-resolution source coordinates; the source
		// transform to world is applied separately (see getIms()).

		return dest.set(
				bsx, 0, 0, 0,
				0, bsy, 0, 0,
				0, 0, bsz, 0,
				-0.5f, -0.5f, -0.5f, 1 );
	}
}
