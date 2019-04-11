package tpietzsch.example2;

import bdv.tools.brightness.ConverterSetup;
import com.jogamp.opengl.GL3;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import net.imglib2.type.volatiles.VolatileUnsignedShortType;
import org.joml.Matrix4f;
import tpietzsch.backend.jogl.JoglGpuContext;
import tpietzsch.cache.CacheSpec;
import tpietzsch.cache.FillTask;
import tpietzsch.cache.PboChain;
import tpietzsch.cache.ProcessFillTasks;
import tpietzsch.cache.TextureCache;
import tpietzsch.dither.DitherBuffer;
import tpietzsch.multires.MultiResolutionStack3D;
import tpietzsch.offscreen.OffScreenFrameBufferWithDepth;
import tpietzsch.util.DefaultQuad;

import static com.jogamp.opengl.GL.GL_ALWAYS;
import static com.jogamp.opengl.GL.GL_BLEND;
import static com.jogamp.opengl.GL.GL_DEPTH_TEST;
import static com.jogamp.opengl.GL.GL_ONE_MINUS_SRC_ALPHA;
import static com.jogamp.opengl.GL.GL_SRC_ALPHA;
import static com.jogamp.opengl.GL.GL_UNPACK_ALIGNMENT;
import static tpietzsch.backend.Texture.InternalFormat.R16;
import static tpietzsch.example2.VolumeRenderer.RepaintType.DITHER;
import static tpietzsch.example2.VolumeRenderer.RepaintType.FULL;
import static tpietzsch.example2.VolumeRenderer.RepaintType.LOAD;
import static tpietzsch.example2.VolumeRenderer.RepaintType.NONE;

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

		void request( RepaintType type )
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
	private MultiVolumeShaderMip9 progvol;




	// ... dithering ...

	private final DitherBuffer dither;
	private final int numDitherSteps;




	// ... gpu cache ...
	// TODO This could be packaged into one class and potentially shared between renderers?
	private final CacheSpec cacheSpec; // TODO remove
	private final TextureCache textureCache;
	private final PboChain pboChain;
	private final ForkJoinPool forkJoinPool;



	/**
	 * Shader programs for rendering {@code 1 .. N} volumes.
	 * At index {@code i} in the list is the shader for {@code i} volumes.
	 * At index {@code 0} there is {@code null}.
	 */
	private final ArrayList< MultiVolumeShaderMip9 > progvols;

	/**
	 * VolumeBlocks for one volume each.
	 * These have associated lookup textures, so we keep them around and reuse them so that we do not create new textures all the time.
	 * And deleting textures is not yet in the backend... (TODO)
	 */
	private final ArrayList< VolumeBlocks > volumes;

	private final DefaultQuad quad;




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

		// set up gpu cache
		// TODO This could be packaged into one class and potentially shared between renderers?
		cacheSpec = new CacheSpec( R16, cacheBlockSize );
		final int[] cacheGridDimensions = TextureCache.findSuitableGridSize( cacheSpec, maxCacheSizeInMB );
		textureCache = new TextureCache( cacheGridDimensions, cacheSpec );
		pboChain = new PboChain( 5, 100, textureCache );
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
		progvols = new ArrayList<>( Collections.singletonList( null ) );
		quad = new DefaultQuad();
	}

	/**
	 * Make sure that we can deal with at least {@code n} volumes.
	 * I.e., add shader programs and VolumeBlock luts if necessary.
	 *
	 * @param n
	 * 		number of volumes that shall be rendered
	 */
	private void needAtLeastNumVolumes( int n )
	{
		while ( volumes.size() < n )
		{
			volumes.add( new VolumeBlocks( textureCache ) );

			final MultiVolumeShaderMip9 progvol = new MultiVolumeShaderMip9( volumes.size(), true, 1.0 );
			progvol.setTextureCache( textureCache );
			progvols.add( progvol );
		}
	}

	public void init( final GL3 gl )
	{
		gl.glPixelStorei( GL_UNPACK_ALIGNMENT, 2 );
	}

	// TODO rename paint() like in MultiResolutionRenderer?
	public RepaintType draw(
			final GL3 gl,
			final RepaintType type,
			final OffScreenFrameBufferWithDepth sceneBuf,
			final List< MultiResolutionStack3D< VolatileUnsignedShortType > > renderStacks,
			final List< ConverterSetup > renderConverters,
			final Matrix4f pv,
			final int maxRenderMillis )
	{
		final long maxRenderNanoTime = System.nanoTime() + 1_000_000L * maxRenderMillis;
		final JoglGpuContext context = JoglGpuContext.get( gl );
		nextRequestedRepaint.type = NONE;

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
			needAtLeastNumVolumes( renderStacks.size() );
			updateBlocks( context, renderStacks, pv );

			double minWorldVoxelSize = Double.POSITIVE_INFINITY;
			progvol = progvols.get( renderStacks.size() );
			if ( progvol != null )
			{
				for ( int i = 0; i < renderStacks.size(); i++ )
				{
					progvol.setConverter( i, renderConverters.get( i ) );
					progvol.setVolume( i, volumes.get( i ) );
					minWorldVoxelSize = Math.min( minWorldVoxelSize, volumes.get( i ).getBaseLevelVoxelSizeInWorldCoordinates() );
				}
				progvol.setDepthTexture( sceneBuf.getDepthTexture() );
				progvol.setViewportWidth( renderWidth );
				progvol.setProjectionViewMatrix( pv, minWorldVoxelSize );
			}
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

	private void updateBlocks(
			final JoglGpuContext context,
			final List< MultiResolutionStack3D< VolatileUnsignedShortType > > renderStacks,
			final Matrix4f pv )
	{
		final List< VolumeAndTasks > tasksPerVolume = new ArrayList<>();
		int numTasks = 0;
		for ( int i = 0; i < renderStacks.size(); i++ )
		{
			final MultiResolutionStack3D< VolatileUnsignedShortType > stack = renderStacks.get( i );
			final VolumeBlocks volume = volumes.get( i );
			volume.init( stack, renderWidth, pv );
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

		boolean needsRepaint = false;
		for ( int i = 0; i < renderStacks.size(); i++ )
		{
			final VolumeBlocks volume = volumes.get( i );
			final boolean complete = volume.makeLut();
			if ( !complete )
				needsRepaint = true;
			volume.getLookupTexture().upload( context );
		}

		if ( needsRepaint )
			nextRequestedRepaint.request( LOAD );
	}

}
