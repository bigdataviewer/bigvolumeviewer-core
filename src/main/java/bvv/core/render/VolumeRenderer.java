/*-
 * #%L
 * Volume rendering of bdv datasets
 * %%
 * Copyright (C) 2018 - 2024 Tobias Pietzsch
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

import static bvv.core.blocks.TileAccess.getPrimitiveType;
import static com.jogamp.opengl.GL.GL_ALWAYS;
import static com.jogamp.opengl.GL.GL_BLEND;
import static com.jogamp.opengl.GL.GL_DEPTH_TEST;
import static com.jogamp.opengl.GL.GL_ONE_MINUS_SRC_ALPHA;
import static com.jogamp.opengl.GL.GL_SRC_ALPHA;
import static com.jogamp.opengl.GL.GL_UNPACK_ALIGNMENT;
import static bvv.core.backend.Texture.InternalFormat.R8;
import static bvv.core.backend.Texture.InternalFormat.R16;
import static bvv.core.render.VolumeRenderer.RepaintType.DITHER;
import static bvv.core.render.VolumeRenderer.RepaintType.FULL;
import static bvv.core.render.VolumeRenderer.RepaintType.LOAD;
import static bvv.core.render.VolumeRenderer.RepaintType.NONE;
import static bvv.core.render.VolumeShaderSignature.PixelType.ARGB;
import static bvv.core.render.VolumeShaderSignature.PixelType.UBYTE;
import static bvv.core.render.VolumeShaderSignature.PixelType.USHORT;
import static bvv.core.multires.SourceStacks.SourceStackType.MULTIRESOLUTION;
import static bvv.core.multires.SourceStacks.SourceStackType.SIMPLE;
import static net.imglib2.type.PrimitiveType.BYTE;
import static net.imglib2.type.PrimitiveType.SHORT;

import bvv.core.backend.Texture.InternalFormat;
import bvv.core.cache.CacheSpec;
import bvv.core.cache.FillTask;
import bvv.core.cache.ProcessFillTasks;
import bvv.core.cache.TextureCache;
import bvv.core.util.DefaultQuad;

import com.jogamp.opengl.GL3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

import net.imglib2.type.PrimitiveType;
import net.imglib2.type.numeric.ARGBType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.volatiles.VolatileUnsignedByteType;
import net.imglib2.type.volatiles.VolatileUnsignedShortType;

import org.joml.Matrix4f;

import bdv.tools.brightness.ConverterSetup;
import bvv.core.backend.jogl.JoglGpuContext;
import bvv.core.blocks.TileAccess;
import bvv.core.cache.PboChain;
import bvv.core.dither.DitherBuffer;
import bvv.core.render.VolumeShaderSignature.VolumeSignature;
import bvv.core.multires.MultiResolutionStack3D;
import bvv.core.multires.SimpleStack3D;
import bvv.core.multires.Stack3D;
import bvv.core.offscreen.OffScreenFrameBufferWithDepth;

public class VolumeRenderer
{
	private final int renderWidth;

	private final int renderHeight;

	// ... RenderState ...
//	final List< MultiResolutionStack3D< VolatileUnsignedShortType > > renderStacks;
//	final List< ConverterSetup > renderConverters;
//	final Matrix4f pv,

	// ... repainting ...

	public enum RepaintType
	{
		NONE,
		SCENE,
		DITHER,
		LOAD,
		FULL;
	}

	private static class Repaint
	{
		RepaintType type;

		Repaint()
		{
			this.type = NONE;
		}

		void request( final RepaintType type )
		{
			if ( this.type.ordinal() < type.ordinal() )
				this.type = type;
		}
	}

	private final Repaint nextRequestedRepaint = new Repaint();

	private int ditherStep = 0;

	private int targetDitherSteps = 0;

	/**
	 * Currently used volume shader program.
	 * This is used when redrawing without changing {@code RenderState}.
	 */
	private MultiVolumeShaderMip progvol;

	// ... dithering ...

	private final DitherBuffer dither;

	private final int numDitherSteps;

	// ... gpu cache ...
	private final TextureCacheAndPboChain cacheR8;

	private final TextureCacheAndPboChain cacheR16;

	private final ForkJoinPool forkJoinPool;

	/**
	 * Shader programs for rendering multiple cached and/or simple volumes.
	 */
	private final HashMap< VolumeShaderSignature, MultiVolumeShaderMip > progvols;

	/**
	 * VolumeBlocks for one volume each.
	 * These have associated lookup textures, so we keep them around and reuse them so that we do not create new textures all the time.
	 */
	private final ArrayList< VolumeBlocks > volumes;

	/**
	 * provides SimpleVolumes for SimpleStacks.
	 */
	private final SimpleStackManager simpleStackManager = new DefaultSimpleStackManager();

	private final DefaultQuad quad;

	private static class TextureCacheAndPboChain
	{
		private final TextureCache textureCache;

		private final PboChain pboChain;

		TextureCacheAndPboChain( final InternalFormat format, final int[] blockSize, final int maxCacheSizeInMB )
		{
			final CacheSpec cacheSpec = new CacheSpec( format, blockSize );
			final int[] cacheGridDimensions = TextureCache.findSuitableGridSize( cacheSpec, maxCacheSizeInMB );
			textureCache = new TextureCache( cacheGridDimensions, cacheSpec );
			pboChain = new PboChain( 5, 100, textureCache );
		}

		public TextureCache textureCache()
		{
			return textureCache;
		}

		public PboChain pboChain()
		{
			return pboChain;
		}
	}

	public VolumeRenderer(
			final int renderWidth,
			final int renderHeight,
			final int ditherWidth,
			final int ditherStep,
			final int numDitherSamples,
			final int[] cacheBlockSize,
			final int maxCacheSizeInMB )
	{
		this.renderWidth = renderWidth;
		this.renderHeight = renderHeight;

		// Set up one cache texture for each supported data type. Note that no
		// GPU memory is allocated until the respective data type is actually
		// used for the first time.
		cacheR8 = new TextureCacheAndPboChain( R8, cacheBlockSize, maxCacheSizeInMB );
		cacheR16 = new TextureCacheAndPboChain( R16, cacheBlockSize, maxCacheSizeInMB );

		final int parallelism = Math.max( 1, Runtime.getRuntime().availableProcessors() / 2 );
		forkJoinPool = new ForkJoinPool( parallelism );


		// set up dither buffer (or null)
		if ( ditherWidth <= 1 )
		{
			dither = null;
			numDitherSteps = 1;
		}
		else
		{
			dither = new DitherBuffer( renderWidth, renderHeight, ditherWidth, ditherStep, numDitherSamples );
			numDitherSteps = dither.numSteps();
		}


		volumes = new ArrayList<>();
		progvols = new HashMap<>();
		progvols.put( new VolumeShaderSignature( Collections.emptyList() ), null );
		quad = new DefaultQuad();
	}

	/**
	 * Make sure that we can deal with at least {@code n} blocked volumes.
	 * I.e., add VolumeBlock luts if necessary.
	 *
	 * @param n
	 * 		number of blocked volumes that shall be rendered
	 */
	private void needAtLeastNumBlockVolumes( final int n )
	{
		while ( volumes.size() < n )
			volumes.add( new VolumeBlocks() );
	}

	private MultiVolumeShaderMip createMultiVolumeShader( final VolumeShaderSignature signature )
	{
		return new MultiVolumeShaderMip( signature, true, 1.0 );
	}

	public void init( final GL3 gl )
	{
		gl.glPixelStorei( GL_UNPACK_ALIGNMENT, 1 );
	}

	/**
	 * @param maxAllowedStepInVoxels
	 * 		Set to {@code 0} to base step size purely on pixel width of render target
	 */
	// TODO rename paint() like in MultiResolutionRenderer?
	public RepaintType draw(
			final GL3 gl,
			final RepaintType type,
			final OffScreenFrameBufferWithDepth sceneBuf,
			final List< Stack3D< ? > > renderStacks,
			final List< ConverterSetup > renderConverters,
			final Matrix4f pv,
			final int maxRenderMillis,
			final double maxAllowedStepInVoxels )
	{
		final long maxRenderNanoTime = System.nanoTime() + 1_000_000L * maxRenderMillis;
		final JoglGpuContext context = JoglGpuContext.get( gl );
		nextRequestedRepaint.type = NONE;
		if ( renderStacks.isEmpty() )
			return nextRequestedRepaint.type;

		gl.glEnable( GL_DEPTH_TEST );
		gl.glDepthFunc( GL_ALWAYS );

		if ( type == FULL )
		{
			ditherStep = 0;
			targetDitherSteps = numDitherSteps;
		}
		else if ( type == LOAD )
		{
			targetDitherSteps = ditherStep + numDitherSteps;
		}

		if ( type == FULL || type == LOAD )
		{
			final List< VolumeSignature > volumeSignatures = new ArrayList<>();
			final List< MultiResolutionStack3D< ? > > multiResStacks = new ArrayList<>();
			for ( int i = 0; i < renderStacks.size(); i++ )
			{
				final Stack3D< ? > stack = renderStacks.get( i );
				if ( stack instanceof MultiResolutionStack3D )
				{
					if ( !TileAccess.isSupportedType( stack.getType() ) )
						throw new IllegalArgumentException();
					multiResStacks.add( ( MultiResolutionStack3D< ? > ) stack );
					final Object pixelType = stack.getType();
					if ( ( pixelType instanceof UnsignedShortType ) || ( pixelType instanceof VolatileUnsignedShortType ) )
						volumeSignatures.add( new VolumeSignature( MULTIRESOLUTION, USHORT ) );
					else if ( ( pixelType instanceof UnsignedByteType ) || ( pixelType instanceof VolatileUnsignedByteType ) )
						volumeSignatures.add( new VolumeSignature( MULTIRESOLUTION, UBYTE ) );
					else
						throw new IllegalArgumentException( "Multiresolution stack with pixel type " + pixelType.getClass().getName() + " unsupported in BigVolumeViewer." );
				}
				else if ( stack instanceof SimpleStack3D )
				{
					final Object pixelType = stack.getType();
					if ( pixelType instanceof UnsignedShortType )
						volumeSignatures.add( new VolumeSignature( SIMPLE, USHORT ) );
					else if ( pixelType instanceof UnsignedByteType )
						volumeSignatures.add( new VolumeSignature( SIMPLE, UBYTE ) );
					else if ( pixelType instanceof ARGBType )
						volumeSignatures.add( new VolumeSignature( SIMPLE, ARGB ) );
					else
						throw new IllegalArgumentException();
				}
				else
					throw new IllegalArgumentException();
			}
			needAtLeastNumBlockVolumes( multiResStacks.size() );
			updateBlocks( context, multiResStacks, pv );

			double minWorldVoxelSize = Double.POSITIVE_INFINITY;
			progvol = progvols.computeIfAbsent( new VolumeShaderSignature( volumeSignatures ), this::createMultiVolumeShader );
			if ( progvol != null )
			{
				int mri = 0;
				for ( int i = 0; i < renderStacks.size(); i++ )
				{
					progvol.setConverter( i, renderConverters.get( i ) );
					if ( volumeSignatures.get( i ).getSourceStackType() == MULTIRESOLUTION )
					{
						final VolumeBlocks volume = volumes.get( mri++ );
						progvol.setVolume( i, volume );
						minWorldVoxelSize = Math.min( minWorldVoxelSize, volume.getBaseLevelVoxelSizeInWorldCoordinates() );
					}
					else
					{
						final SimpleStack3D< ? > simpleStack3D = ( SimpleStack3D< ? > ) renderStacks.get( i );
						final SimpleVolume volume = simpleStackManager.getSimpleVolume( context, simpleStack3D );
						progvol.setVolume( i, volume );
						minWorldVoxelSize = Math.min( minWorldVoxelSize, volume.getVoxelSizeInWorldCoordinates() );
					}
				}
				progvol.setDepthTexture( sceneBuf.getDepthTexture() );
				progvol.setViewportWidth( renderWidth );
				progvol.setProjectionViewMatrix( pv, maxAllowedStepInVoxels * minWorldVoxelSize );
			}

			simpleStackManager.freeUnusedSimpleVolumes( context );
		}

		if ( progvol != null )
		{
			if ( dither != null )
			{
				if ( ditherStep != targetDitherSteps )
				{
					dither.bind( gl );
					progvol.use( context );
					progvol.bindSamplers( context );
					gl.glDisable( GL_BLEND );
					while ( ditherStep < targetDitherSteps )
					{
						progvol.setDither( dither, ditherStep % numDitherSteps );
						progvol.setUniforms( context );
						quad.draw( gl );
						gl.glFinish();
						++ditherStep;
						if ( System.nanoTime() > maxRenderNanoTime )
							break;
					}
					dither.unbind( gl );
				}

				gl.glEnable( GL_BLEND );
				gl.glBlendFunc( GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA );
				final int stepsCompleted = Math.min( ditherStep, numDitherSteps );
				dither.dither( gl, stepsCompleted, renderWidth, renderHeight );
//				dither.getStitchBuffer().drawQuad( gl );
//				dither.getDitherBuffer().drawQuad( gl );

				if ( ditherStep != targetDitherSteps )
					nextRequestedRepaint.request( DITHER );
			}
			else // no dithering
			{
				gl.glEnable( GL_BLEND );
				gl.glBlendFunc( GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA );
				progvol.use( context );
				progvol.bindSamplers( context );
				progvol.setEffectiveViewportSize( renderWidth, renderHeight );
				progvol.setUniforms( context );
				quad.draw( gl );
			}
		}

		return nextRequestedRepaint.type;
	}

	static class VolumeAndTasks
	{
		private final List< FillTask > tasks;
		private final VolumeBlocks volume;
		private final int maxLevel;

		int numTasks()
		{
			return tasks.size();
		}

		VolumeAndTasks( final List< FillTask > tasks, final VolumeBlocks volume, final int maxLevel )
		{
			this.tasks = new ArrayList<>( tasks );
			this.volume = volume;
			this.maxLevel = maxLevel;
		}
	}


	/**
	 * @param context
	 * @param multiResStacks
	 * @param volumes
	 * 		VolumeBlocks to use (not initialized yet, indices corresponds to multiResStacks indices
	 * @param cache
	 * 		the texture cache to use
	 * @param viewportWidth
	 * 		width of the surface to be rendered
	 * @param pv
	 *        {@code projection * view} matrix, transforms world coordinates to NDC coordinates
	 *
	 * @return {@code true} if all required blocks for all volumes are loaded.
	 * (That is, if {@code false} is returned, the frame should be repainted
	 * until the remaining incomplete blocks are loaded.)
	 */
	private static boolean updateBlocks(
			final JoglGpuContext context,
			final List< ? extends MultiResolutionStack3D< ? > > multiResStacks,
			final List< VolumeBlocks > volumes,
			final TextureCacheAndPboChain cache,
			final ForkJoinPool forkJoinPool,
			final int viewportWidth,
			final Matrix4f pv )
	{
		final TextureCache textureCache = cache.textureCache();
		final PboChain pboChain = cache.pboChain();

		final List< VolumeAndTasks > tasksPerVolume = new ArrayList<>();
		int numTasks = 0;
		for ( int i = 0; i < multiResStacks.size(); i++ )
		{
			final MultiResolutionStack3D< ? > stack = multiResStacks.get( i );
			final VolumeBlocks volume = volumes.get( i );
			volume.init( stack, textureCache, viewportWidth, pv );
			final List< FillTask > tasks = volume.getFillTasks();
			numTasks += tasks.size();
			tasksPerVolume.add( new VolumeAndTasks( tasks, volume, stack.resolutions().size() - 1 ) );
		}

		A:
		while ( numTasks > textureCache.getMaxNumTiles() )
		{
			tasksPerVolume.sort( Comparator.comparingInt( VolumeAndTasks::numTasks ).reversed() );
			for ( final VolumeAndTasks vat : tasksPerVolume )
			{
				final int baseLevel = vat.volume.getBaseLevel();
				if ( baseLevel < vat.maxLevel )
				{
					vat.volume.setBaseLevel( baseLevel + 1 );
					numTasks -= vat.numTasks();
					vat.tasks.clear();
					vat.tasks.addAll( vat.volume.getFillTasks() );
					numTasks += vat.numTasks();
					continue A;
				}
			}
			break;
		}

		final ArrayList< FillTask > fillTasks = new ArrayList<>();
		for ( final VolumeAndTasks vat : tasksPerVolume )
			fillTasks.addAll( vat.tasks );
		if ( fillTasks.size() > textureCache.getMaxNumTiles() )
			fillTasks.subList( textureCache.getMaxNumTiles(), fillTasks.size() ).clear();

		try
		{
			ProcessFillTasks.parallel( textureCache, pboChain, context, forkJoinPool, fillTasks );
		}
		catch ( final InterruptedException e )
		{
			e.printStackTrace();
		}

		boolean complete = true;
		final int timestamp = textureCache.nextTimestamp();
		for ( int i = 0; i < multiResStacks.size(); i++ )
		{
			final VolumeBlocks volume = volumes.get( i );
			complete &= volume.makeLut( timestamp );
			volume.getLookupTexture().upload( context );
		}

		return complete;
	}

	private void updateBlocks(
			final JoglGpuContext context,
			final List< ? extends MultiResolutionStack3D< ? > > multiResStacks,
			final Matrix4f pv )
	{

		final List< MultiResolutionStack3D< ? > > multiResStacksR8 = new ArrayList<>();
		final List< VolumeBlocks > volumesR8 = new ArrayList<>();

		final List< MultiResolutionStack3D< ? > > multiResStacksR16 = new ArrayList<>();
		final List< VolumeBlocks > volumesR16 = new ArrayList<>();

		for ( int i = 0; i < multiResStacks.size(); i++ )
		{
			final MultiResolutionStack3D< ? > stack = multiResStacks.get( i );
			final VolumeBlocks volume = volumes.get( i );
			final PrimitiveType primitiveType = getPrimitiveType( stack.getType() );
			if ( primitiveType == BYTE )
			{
				multiResStacksR8.add( stack );
				volumesR8.add( volume );
			}
			else if ( primitiveType == SHORT )
			{
				multiResStacksR16.add( stack );
				volumesR16.add( volume );
			}
			else
			{
				throw new IllegalArgumentException();
			}
		}

		boolean complete = true;
		complete &= updateBlocks( context, multiResStacksR8, volumesR8, cacheR8, forkJoinPool, renderWidth, pv );
		complete &= updateBlocks( context, multiResStacksR16, volumesR16, cacheR16, forkJoinPool, renderWidth, pv );
		if ( !complete )
			nextRequestedRepaint.request( LOAD );
	}
}
