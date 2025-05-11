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

import bdv.tools.brightness.ConverterSetup;
import bvv.core.cache.CacheSpec;
import bvv.core.cache.TextureCache;
import bvv.core.multires.SourceStacks;
import bvv.core.render.VolumeShaderSignature.PixelType;
import bvv.core.shadergen.Uniform1f;
import bvv.core.shadergen.Uniform2f;
import bvv.core.shadergen.Uniform3f;
import bvv.core.shadergen.Uniform3fv;
import bvv.core.shadergen.Uniform4f;
import bvv.core.shadergen.UniformMatrix4f;
import bvv.core.shadergen.UniformSampler;
import bvv.core.shadergen.generate.Segment;
import bvv.core.shadergen.generate.SegmentTemplate;
import bvv.core.shadergen.generate.SegmentType;
import bvv.core.shadergen.generate.SegmentedShader;
import bvv.core.shadergen.generate.SegmentedShaderBuilder;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import net.imglib2.type.numeric.ARGBType;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector2f;
import org.joml.Vector4f;
import bvv.core.backend.GpuContext;
import bvv.core.backend.Texture;
import bvv.core.backend.Texture2D;
import bvv.core.dither.DitherBuffer;

public class MultiVolumeShaderMip
{
	private static final int NUM_BLOCK_SCALES = 10;

	private final VolumeShaderSignature signature;

	private final boolean useDepthTexture;

	// step size on near plane = pixel_width
	// step size on far plane = degrade * pixel_width
	private double degrade;

	private final SegmentedShader prog;
	private final VolumeSegment[] volumeSegments;
	private final ConverterSegment[] converterSegments;

	private final UniformMatrix4f uniformIpv;
	private final Uniform2f uniformViewportSize;

	private final Uniform1f uniformNw;
	private final Uniform1f uniformFwnw;
	private final Uniform1f uniformXf;

	private final UniformMatrix4f uniformTransform;
	private final Uniform2f uniformDsp;

	private int viewportWidth;
	private String sceneDepthTextureName;

	/**
	 * Functional interface to be able to use and integrate custom segments. Basically takes a three-parameter
	 * lambda to contain segments, their instances, and the volume id. The lambda is run for each volume id. This
	 * interface then enables custom segments and instances to be connected with each other, e.g. making new custom
	 * per-volume uniforms possible.
	 */
	@FunctionalInterface
	public interface SegmentConsumer
	{
		/**
		 * Runs the lambda that was handed over, offering access to available segments, their instances, and the current
		 * volume index. The lambda is executed for each individual volume id.
		 *
		 * @param segments
		 * @param segmentInstances
		 * @param volumeIndex
		 */
		void accept( Map< SegmentType, SegmentTemplate > segments,
				Map< SegmentType, Segment > segmentInstances, int volumeIndex );
	}

	/**
	 * @param runBeforeBinding
	 * 		<em>(added for use by scenery)</em>
	 * 		This is a lambda (functional interface) called for each volume to be able to establish additional segment
	 * 		binding, for example to connect convert-segment to sample-segment for alpha blending instead of
	 * 		max projection.
	 */
	public MultiVolumeShaderMip( VolumeShaderSignature signature, final boolean useDepthTexture, final double degrade,
			final Map< SegmentType, SegmentTemplate > segments,
			final SegmentConsumer runBeforeBinding,
			final String depthTextureName )
	{
		this.signature = signature;
		this.useDepthTexture = useDepthTexture;
		this.degrade = degrade;
		this.sceneDepthTextureName = depthTextureName;

		final int numVolumes = signature.getVolumeSignatures().size();

		// ensure we have all segments we need
		if ( !Arrays.stream( SegmentType.values() ).allMatch( segments::containsKey ) )
			throw new IllegalStateException( "Segments array does not contain all required SegmentTypes." );

		final SegmentedShaderBuilder builder = new SegmentedShaderBuilder();
		final Segment vp = segments.get( SegmentType.VertexShader ).instantiate();
		builder.vertex( vp );

		final SegmentTemplate templateVolBlocks = segments.get( SegmentType.SampleMultiresolutionVolume );
		final SegmentTemplate templateVolSimple = segments.get( SegmentType.SampleVolume );
		final SegmentTemplate templateVolSimpleRGBA = segments.get( SegmentType.SampleRGBAVolume );
		final SegmentTemplate templateConvert = segments.get( SegmentType.Convert );
		final SegmentTemplate templateConvertRGBA = segments.get( SegmentType.ConvertRGBA );
		final SegmentTemplate templateMaxDepth = segments.get( SegmentType.MaxDepth );
		builder.fragment( templateMaxDepth.instantiate() );

		final SegmentTemplate templateMainFp = segments.get( SegmentType.FragmentShader );
		final Segment fp = templateMainFp.instantiate();
		fp.repeat( "vis", numVolumes );

		final SegmentTemplate templateAccumulateMipBlocks = segments.get( SegmentType.AccumulatorMultiresolution );
		final SegmentTemplate templateAccumulateMipSimple = segments.get( SegmentType.Accumulator );

		final Segment[] sampleVolumeSegs = new Segment[ numVolumes ];
		final Segment[] convertSegs = new Segment[ numVolumes ];
		final Segment[] accumulateSegs = new Segment[ numVolumes ];
		for ( int i = 0; i < numVolumes; ++i )
		{
			final HashMap< SegmentType, Segment > instancedSegments = new HashMap<>();
			final VolumeShaderSignature.VolumeSignature volumeSignature = signature.getVolumeSignatures().get( i );
			instancedSegments.put(SegmentType.FragmentShader, fp);

			final Segment accumulate;
			final Segment sampleVolume;
			switch ( volumeSignature.getSourceStackType() )
			{
			case MULTIRESOLUTION:
				accumulate = templateAccumulateMipBlocks.instantiate();
				instancedSegments.put( SegmentType.AccumulatorMultiresolution, accumulate );
				sampleVolume = templateVolBlocks.instantiate();
				instancedSegments.put( SegmentType.SampleMultiresolutionVolume, sampleVolume );
				break;
			case SIMPLE:
				accumulate = templateAccumulateMipSimple.instantiate();
				instancedSegments.put( SegmentType.Accumulator, accumulate );
				sampleVolume = volumeSignature.getPixelType() == VolumeShaderSignature.PixelType.ARGB
						? templateVolSimpleRGBA.instantiate()
						: templateVolSimple.instantiate();
				instancedSegments.put( SegmentType.SampleVolume, sampleVolume );
				break;
			default:
				throw new IllegalArgumentException();
			}

			final Segment convert;
			switch ( volumeSignature.getPixelType() )
			{
			default:
			case USHORT:
			case UBYTE:
				convert = templateConvert.instantiate();
				instancedSegments.put( SegmentType.Convert, convert );
				break;
			case ARGB:
				convert = templateConvertRGBA.instantiate();
				instancedSegments.put( SegmentType.ConvertRGBA, convert );
				break;
			}

			if ( runBeforeBinding != null )
				runBeforeBinding.accept( segments, instancedSegments, i );

			fp.bind( "intersectBoundingBox", i, sampleVolume );
			fp.bind( "vis", i, accumulate );
			accumulate.bind( "sampleVolume", sampleVolume );
			accumulate.bind( "convert", convert );

			sampleVolumeSegs[ i ] = sampleVolume;
			convertSegs[ i ] = convert;
			accumulateSegs[ i ] = accumulate;
		}
		fp.insert( "SampleVolume", sampleVolumeSegs );
		fp.insert( "Convert", convertSegs );
		fp.insert( "Accumulate", accumulateSegs );

		builder.fragment( fp );
		prog = builder.build();

		uniformIpv = prog.getUniformMatrix4f( "ipv" );
		uniformViewportSize = prog.getUniform2f( "viewportSize" );
		uniformNw = prog.getUniform1f( "nw" );
		uniformFwnw = prog.getUniform1f( "fwnw" );
		uniformXf = prog.getUniform1f( "xf" );

		volumeSegments = new VolumeSegment[ numVolumes ];
		converterSegments = new ConverterSegment[ numVolumes ];
		for ( int i = 0; i < numVolumes; ++i )
		{
			final VolumeShaderSignature.VolumeSignature volumeSignature = signature.getVolumeSignatures().get( i );
			switch ( volumeSignature.getSourceStackType() )
			{
			case SIMPLE:
				volumeSegments[ i ] = new VolumeSimpleSegment( prog, sampleVolumeSegs[ i ] );
				break;
			case MULTIRESOLUTION:
			{
				volumeSegments[ i ] = new VolumeBlocksSegment( prog, sampleVolumeSegs[ i ] );
			}
				break;
			}
			converterSegments[ i ] = new ConverterSegment( prog, convertSegs[ i ], volumeSignature.getPixelType() );
		}

		uniformTransform = prog.getUniformMatrix4f( "transform" );
		uniformDsp = prog.getUniform2f( "dsp" );

		uniformTransform.set( new Matrix4f() );
		uniformDsp.set( new Vector2f() );

//		final StringBuilder vertexShaderCode = prog.getVertexShaderCode();
//		System.out.println( "vertexShaderCode = " + vertexShaderCode );
//		System.out.println( "\n\n--------------------------------\n\n" );
//
//		final StringBuilder fragmentShaderCode = prog.getFragmentShaderCode();
//		System.out.println( "fragmentShaderCode = " + fragmentShaderCode );
//		System.out.println( "\n\n--------------------------------\n\n" );
//		
//		String folderToSave = "/home/eugene/Desktop/projects/bvv/20250502_shaders/";
//		
//		File file = new File(folderToSave+"vertex.txt");
//		try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
//		    writer.append(vertexShaderCode);
//		}
//		catch ( IOException exc )
//		{
//			// TODO Auto-generated catch block
//			exc.printStackTrace();
//		}
//		
//		file = new File(folderToSave+"fragment.txt");
//		try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
//		    writer.append(fragmentShaderCode);
//		}
//		catch ( IOException exc )
//		{
//			// TODO Auto-generated catch block
//			exc.printStackTrace();
//		}
	}

	public static Map< SegmentType, SegmentTemplate > getDefaultSegments( boolean useDepthTexture )
	{
		final HashMap< SegmentType, SegmentTemplate > segments = new HashMap<>();

		segments.put( SegmentType.SampleMultiresolutionVolume, new SegmentTemplate(
				"sample_volume_blocks.frag",
				"volumeCache", "blockSize", "paddedBlockSize", "cachePadOffset","cacheSize",
				"im", "sourcemin", "sourcemax", "intersectBoundingBox",
				"lutSampler", "blockScales", "lutSize", "lutOffset", "sampleVolume" ) );
		segments.put( SegmentType.SampleVolume, new SegmentTemplate(
				"sample_volume_simple.frag",
				"im", "sourcemax", "intersectBoundingBox",
				"volume", "sampleVolume" ) );
		segments.put( SegmentType.SampleRGBAVolume, new SegmentTemplate(
				"sample_volume_simple_rgba.frag",
				"im", "sourcemax", "intersectBoundingBox",
				"volume", "sampleVolume" ) );
		segments.put( SegmentType.Convert, new SegmentTemplate(
				"convert.frag",
				"convert", "offset", "scale" ) );
		segments.put( SegmentType.ConvertRGBA, new SegmentTemplate(
				"convert_rgba.frag",
				"convert", "offset", "scale" ) );
		segments.put( SegmentType.MaxDepth, new SegmentTemplate(
				useDepthTexture ? "maxdepthtexture.frag" : "maxdepthone.frag" ) );
		segments.put( SegmentType.VertexShader, new SegmentTemplate( "multi_volume.vert" ) );
		segments.put( SegmentType.FragmentShader, new SegmentTemplate(
				"multi_volume.frag",
				"intersectBoundingBox", "vis", "SampleVolume", "Convert", "Accumulate" ) );
		segments.put( SegmentType.AccumulatorMultiresolution, new SegmentTemplate(
				"accumulate_mip_blocks.frag",
				"vis", "sampleVolume", "convert" ) );
		segments.put( SegmentType.Accumulator, new SegmentTemplate(
				"accumulate_mip_simple.frag",
				"vis", "sampleVolume", "convert" ) );

		return segments;
	}

	public MultiVolumeShaderMip( VolumeShaderSignature signature, final boolean useDepthTexture, final double degrade )
	{
		this( signature, useDepthTexture, degrade, getDefaultSegments( useDepthTexture ), null, "sceneDepth" );
	}

	public void setTextureCache( TextureCache textureCache )
	{
		CacheSpec spec = textureCache.spec();
		final int[] bs = spec.blockSize();
		final int[] pbs = spec.paddedBlockSize();
		final int[] bo = spec.padOffset();
		prog.getUniform3f( "blockSize" ).set( bs[ 0 ], bs[ 1 ], bs[ 2 ] );
		prog.getUniform3f( "paddedBlockSize" ).set( pbs[ 0 ], pbs[ 1 ], pbs[ 2 ] );
		prog.getUniform3f( "cachePadOffset" ).set( bo[ 0 ], bo[ 1 ], bo[ 2 ] );

		prog.getUniformSampler( "volumeCache" ).set( textureCache );
		prog.getUniform3f( "cacheSize" ).set( textureCache.texWidth(), textureCache.texHeight(), textureCache.texDepth() );
	}

	public void setDepthTexture( Texture2D depth )
	{
		if ( !useDepthTexture )
			throw new UnsupportedOperationException();

		prog.getUniformSampler( sceneDepthTextureName ).set( depth );
	}

	public void setDepthTextureName( String name )
	{
		sceneDepthTextureName = name;
	}

	public void setConverter( int index, ConverterSetup converter )
	{
		converterSegments[ index ].setData( converter );
	}

	/**
	 * Set uniform {@code name} to the given {@code value}, where the uniform
	 * type is inferred from the type of {@code value}.
	 * <p>
	 * <em>(added for use by scenery)</em>
	 *
	 * @param index
	 * 		index of the volume
	 * @param name
	 * 		uniform name
	 * @param value
	 * 		value to set for uniform {@code name}
	 */
	public void setUniform( final int index, final String name, final Object value)
	{
		prog.setUniformValueByType( volumeSegments[ index ].volume, name, value );
	}

	/**
	 * Set uniform array {@code name} to the given {@code value}, where the
	 * uniform type is inferred from the type of {@code value}. The {@code
	 * value} type currently must be either {@code float[]} or {@code int[]} and
	 * the value represents vectors of length {@code elementSize} packed into a
	 * single array.
	 * <p>
	 * <em>(added for use by scenery)</em>
	 *
	 * @param index
	 * 		index of the volume
	 * @param name
	 * 		uniform name
	 * @param value
	 * 		value to set for uniform {@code name}
	 */
	public void setUniform( final int index, final String name, final int elementSize, final Object value )
	{
		prog.setUniformValueByType( volumeSegments[ index ].volume, name, elementSize, value );
	}

	// TODO remove
	/**
	 * @deprecated replace by {@code setUniform( index, name, texture )}
	 */
	@Deprecated
	public void registerCustomSampler( int index, String name, Texture texture )
	{
		setUniform( index, name, texture );
	}

	// TODO remove
	/**
	 * @deprecated replace by {@code setUniform( index, name, value )}
	 */
	@Deprecated
	public void setCustomUniformForVolume( int index, String name, Object value )
	{
		setUniform( index, name, value );
	}

	// TODO remove
	// TODO is this still needed?
	@Deprecated
	public void removeCustomUniformForVolume( int index, String name )
	{
		throw new UnsupportedOperationException();
	}

	// TODO remove
	/**
	 * @deprecated replace by {@code setUniform( index, name, elementSize, value )}
	 */
	@Deprecated
	public void setCustomIntArrayUniformForVolume( int index, String name, final int elementSize, final int[] value )
	{
		setUniform( index, name, elementSize, value );
	}

	// TODO remove
	/**
	 * @deprecated replace by {@code setUniform( index, name, elementSize, value )}
	 */
	@Deprecated
	public void setCustomFloatArrayUniformForVolume( int index, String name, final int elementSize, final float[] value )
	{
		setUniform( index, name, elementSize, value );
	}

	public void setVolume( int index, VolumeBlocks volume, TextureCache textureCache )
	{
		final VolumeShaderSignature.VolumeSignature vs = signature.getVolumeSignatures().get( index );
		if ( vs.getSourceStackType() != SourceStacks.SourceStackType.MULTIRESOLUTION )
			throw new IllegalArgumentException();

		( ( VolumeBlocksSegment ) volumeSegments[ index ] ).setData( volume, textureCache );
	}

	public void setVolume( int index, SimpleVolume volume )
	{
		final VolumeShaderSignature.VolumeSignature vs = signature.getVolumeSignatures().get( index );
		if ( vs.getSourceStackType() != SourceStacks.SourceStackType.SIMPLE )
			throw new IllegalArgumentException();

		( ( VolumeSimpleSegment ) volumeSegments[ index ] ).setData( volume );
	}


	public void setDither( DitherBuffer dither, int step )
	{
		uniformViewportSize.set( dither.effectiveViewportWidth(), dither.effectiveViewportHeight() );
		uniformTransform.set( dither.ndcTransform( step ) );
		uniformDsp.set( dither.fragShift( step ) );
	}

	/**
	 * Note that this will only take effect after {@link #setProjectionViewMatrix(Matrix4fc, double)}
	 * <p>
	 * <em>(added for use by scenery)</em>
	 */
	public void setDegrade( Double farPlaneStepSizeDegradation )
	{
		degrade = farPlaneStepSizeDegradation;
	}

	/**
	 * @param minWorldVoxelSize pass {@code 0} if unknown.
	 */
	public void setProjectionViewMatrix( final Matrix4fc pv, final double minWorldVoxelSize )
	{
		final Matrix4f ipv = pv.invert( new Matrix4f() );
		final float dx = ( float ) ( 2.0 / viewportWidth );

		final Vector4f a = ipv.transform( new Vector4f( 0, 0, -1, 1 ) );
		final Vector4f c = ipv.transform( new Vector4f( 0, 0,  1, 1 ) );
		final Vector4f b = ipv.transform( new Vector4f( 0, 0,  0, 1 ) );
		final Vector4f adx = ipv.transform( new Vector4f( dx, 0, -1, 1 ) );
		final Vector4f cdx = ipv.transform( new Vector4f( dx, 0,  1, 1 ) );
		a.div( a.w() );
		b.div( b.w() );
		c.div( c.w() );
		adx.div( adx.w() );
		cdx.div( cdx.w() );

		final double sNear = Math.max( adx.sub( a ).length(), minWorldVoxelSize );
		final double sFar = Math.max( cdx.sub( c ).length(), minWorldVoxelSize );
		final double ac = c.sub( a ).length();
		final double scale = 1.0 / ac;
		final double nw = sNear * scale;
		final double fw = degrade * sFar * scale;
		final double ab = b.sub( a, new Vector4f() ).length();
		final double f = ab / ac;

		uniformIpv.set( ipv );
		uniformNw.set( ( float ) nw );
		uniformFwnw.set( ( float ) ( fw - nw ) );
		uniformXf.set( ( float ) f );
	}

	public void setViewportWidth( int width )
	{
		viewportWidth = width;
	}

	public void setEffectiveViewportSize( int width, int height )
	{
		uniformViewportSize.set( width, height );
	}

	public void use( GpuContext context )
	{
		prog.use( context );
	}

	public void bindSamplers( GpuContext context )
	{
		prog.bindSamplers( context );
	}

	public void setUniforms( GpuContext context )
	{
		prog.setUniforms( context );
	}

	static class ConverterSegment
	{
		private final Uniform4f uniformOffset;
		private final Uniform4f uniformScale;

		private final VolumeShaderSignature.PixelType pixelType;
		private final double rangeScale;

		public ConverterSegment( final SegmentedShader prog, final Segment segment, final VolumeShaderSignature.PixelType pixelType )
		{
			uniformOffset = prog.getUniform4f( segment,"offset" );
			uniformScale = prog.getUniform4f( segment,"scale" );

			this.pixelType = pixelType;

			switch ( pixelType )
			{
			default:
			case USHORT:
				rangeScale = 0xffff;
				break;
			case UBYTE:
			case ARGB:
				rangeScale = 0xff;
				break;
			}
		}

		public void setData( ConverterSetup converter )
		{
			final double fmin = converter.getDisplayRangeMin() / rangeScale;
			final double fmax = converter.getDisplayRangeMax() / rangeScale;
			final double s = 1.0 / ( fmax - fmin );
			final double o = -fmin * s;

			if ( pixelType == VolumeShaderSignature.PixelType.ARGB )
			{
				uniformOffset.set( ( float ) o, ( float ) o, ( float ) o, ( float ) o );
				uniformScale.set( ( float ) s, ( float ) s, ( float ) s, ( float ) s );
			}
			else
			{
				final int color = converter.getColor().get();
				final double r = ( double ) ARGBType.red( color ) / 255.0;
				final double g = ( double ) ARGBType.green( color ) / 255.0;
				final double b = ( double ) ARGBType.blue( color ) / 255.0;

//				final double l = 0.2126 * r + 0.7152 * g + 0.0722 * b;
//				final double l = 0.299 * r + 0.587 * g + 0.114 * b;

				uniformOffset.set(
						( float ) ( o * r ),
						( float ) ( o * g ),
						( float ) ( o * b ),
						( float ) ( o ) );
				uniformScale.set(
						( float ) ( s * r ),
						( float ) ( s * g ),
						( float ) ( s * b ),
						( float ) ( s ) );
			}
		}
	}

	static abstract class VolumeSegment
	{
		final Segment volume;

		public VolumeSegment( final Segment volume )
		{
			this.volume = volume;
		}
	}

	static class VolumeBlocksSegment extends VolumeSegment
	{

		private final UniformSampler uniformVolumeCache;
		private final Uniform3f uniformBlockSize;
		private final Uniform3f uniformPaddedBlockSize;
		private final Uniform3f uniformCachePadOffset;
		private final Uniform3f uniformCacheSize;
		private final Uniform3fv uniformBlockScales;
		private final UniformSampler uniformLutSampler;
		private final Uniform3f uniformLutSize;
		private final Uniform3f uniformLutOffset;
		private final UniformMatrix4f uniformIm;
		private final Uniform3f uniformSourcemin;
		private final Uniform3f uniformSourcemax;

		public VolumeBlocksSegment( final SegmentedShader prog, final Segment volume)
		{
			super( volume );
			
			uniformVolumeCache = prog.getUniformSampler(volume, "volumeCache" );
			uniformBlockSize = prog.getUniform3f(volume, "blockSize" );
			uniformPaddedBlockSize = prog.getUniform3f(volume, "paddedBlockSize" );
			uniformCachePadOffset = prog.getUniform3f(volume, "cachePadOffset" );
			uniformCacheSize = prog.getUniform3f(volume, "cacheSize" );
			uniformBlockScales = prog.getUniform3fv( volume, "blockScales" );
			uniformLutSampler = prog.getUniformSampler( volume, "lutSampler" );
			uniformLutSize = prog.getUniform3f( volume, "lutSize" );
			uniformLutOffset = prog.getUniform3f( volume, "lutOffset" );
			uniformIm = prog.getUniformMatrix4f( volume, "im" );
			uniformSourcemin = prog.getUniform3f( volume, "sourcemin" );
			uniformSourcemax = prog.getUniform3f( volume, "sourcemax" );

		}

		public void setData( VolumeBlocks blocks, TextureCache textureCache )
		{
			
			CacheSpec spec = textureCache.spec();
			final int[] bs = spec.blockSize();
			final int[] pbs = spec.paddedBlockSize();
			final int[] bo = spec.padOffset();
			uniformVolumeCache.set( textureCache );
			uniformBlockSize.set( bs[ 0 ], bs[ 1 ], bs[ 2 ] );
			uniformPaddedBlockSize.set( pbs[ 0 ], pbs[ 1 ], pbs[ 2 ] );
			uniformCachePadOffset.set( bo[ 0 ], bo[ 1 ], bo[ 2 ] );
			uniformCacheSize.set( textureCache.texWidth(), textureCache.texHeight(), textureCache.texDepth() );
			
			uniformBlockScales.set( blocks.getLutBlockScales( NUM_BLOCK_SCALES ) );
			final LookupTextureARGB lut = blocks.getLookupTexture();
			uniformLutSampler.set( lut );
			uniformLutSize.set( lut.getSize3f() );
			uniformLutOffset.set( lut.getOffset3f() );
			uniformIm.set( blocks.getIms() );
			uniformSourcemin.set( blocks.getSourceLevelMin() );
			uniformSourcemax.set( blocks.getSourceLevelMax() );
		}
	}

	static class VolumeSimpleSegment extends VolumeSegment
	{
		private final UniformSampler uniformVolumeSampler;
		private final UniformMatrix4f uniformIm;
		private final Uniform3f uniformSourcemax;

		public VolumeSimpleSegment( final SegmentedShader prog, final Segment volume )
		{
			super( volume );
			uniformVolumeSampler = prog.getUniformSampler( volume, "volume" );
			uniformIm = prog.getUniformMatrix4f( volume, "im" );
			uniformSourcemax = prog.getUniform3f( volume, "sourcemax" );
		}

		public void setData( SimpleVolume volume )
		{
			uniformVolumeSampler.set( volume.getVolumeTexture() );
			uniformIm.set( volume.getIms() );
			uniformSourcemax.set( volume.getSourceMax() );
		}
	}
}
