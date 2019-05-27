package tpietzsch.example2;

import bdv.tools.brightness.ConverterSetup;
import net.imglib2.type.numeric.ARGBType;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector2f;
import org.joml.Vector4f;
import tpietzsch.backend.GpuContext;
import tpietzsch.backend.Texture2D;
import tpietzsch.cache.CacheSpec;
import tpietzsch.cache.TextureCache;
import tpietzsch.dither.DitherBuffer;
import tpietzsch.example2.VolumeShaderSignature.PixelType;
import tpietzsch.example2.VolumeShaderSignature.VolumeSignature;
import tpietzsch.shadergen.Uniform1f;
import tpietzsch.shadergen.Uniform2f;
import tpietzsch.shadergen.Uniform3f;
import tpietzsch.shadergen.Uniform3fv;
import tpietzsch.shadergen.Uniform4f;
import tpietzsch.shadergen.UniformMatrix4f;
import tpietzsch.shadergen.UniformSampler;
import tpietzsch.shadergen.generate.Segment;
import tpietzsch.shadergen.generate.SegmentTemplate;
import tpietzsch.shadergen.generate.SegmentedShader;
import tpietzsch.shadergen.generate.SegmentedShaderBuilder;

import static tpietzsch.example2.VolumeShaderSignature.PixelType.ARGB;
import static tpietzsch.multires.SourceStacks.SourceStackType.MULTIRESOLUTION;
import static tpietzsch.multires.SourceStacks.SourceStackType.SIMPLE;

public class MultiVolumeShaderMip
{
	private static final int NUM_BLOCK_SCALES = 10;

	private final VolumeShaderSignature signature;

	private final boolean useDepthTexture;

	// step size on near plane = pixel_width
	// step size on far plane = degrade * pixel_width
	private final double degrade;

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

	public MultiVolumeShaderMip( VolumeShaderSignature signature, final boolean useDepthTexture, final double degrade )
	{
		this.signature = signature;
		this.useDepthTexture = useDepthTexture;
		this.degrade = degrade;

		final int numVolumes = signature.getVolumeSignatures().size();

		final SegmentedShaderBuilder builder = new SegmentedShaderBuilder();
		final Segment vp = new SegmentTemplate("multi_volume.vert" ).instantiate();
		builder.vertex( vp );

		final SegmentTemplate templateVolBlocks = new SegmentTemplate(
				"sample_volume_blocks.frag",
				"im", "sourcemin", "sourcemax", "intersectBoundingBox",
				"lutSampler", "blockScales", "lutSize", "lutOffset", "sampleVolume" );
		final SegmentTemplate templateVolSimple = new SegmentTemplate(
				"sample_volume_simple.frag",
				"im", "sourcemax", "intersectBoundingBox",
				"volume", "sampleVolume" );
		final SegmentTemplate templateVolSimpleRGBA = new SegmentTemplate(
				"sample_volume_simple_rgba.frag",
				"im", "sourcemax", "intersectBoundingBox",
				"volume", "sampleVolume" );
		final SegmentTemplate templateConvert = new SegmentTemplate(
				"convert.frag",
				"convert", "offset", "scale" );
		final SegmentTemplate templateConvertRGBA = new SegmentTemplate(
				"convert_rgba.frag",
				"convert", "offset", "scale" );
		final SegmentTemplate templateMaxDepth = new SegmentTemplate(
				useDepthTexture ? "maxdepthtexture.frag" : "maxdepthone.frag" );
		builder.fragment( templateMaxDepth.instantiate() );
		final SegmentTemplate templateMainFp = new SegmentTemplate(
				"multi_volume.frag",
				"intersectBoundingBox", "vis", "SampleVolume", "Convert", "Accumulate" );
		final Segment fp = templateMainFp.instantiate();
		fp.repeat( "vis", numVolumes );

		final SegmentTemplate templateAccumulateMipBlocks = new SegmentTemplate(
				"accumulate_mip_blocks.frag",
				"vis", "sampleVolume", "convert" );
		final SegmentTemplate templateAccumulateMipSimple = new SegmentTemplate(
				"accumulate_mip_simple.frag",
				"vis", "sampleVolume", "convert" );

		final Segment[] sampleVolumeSegs = new Segment[ numVolumes ];
		final Segment[] convertSegs = new Segment[ numVolumes ];
		final Segment[] accumulateSegs = new Segment[ numVolumes ];
		for ( int i = 0; i < numVolumes; ++i )
		{
			final VolumeSignature volumeSignature = signature.getVolumeSignatures().get( i );

			final Segment accumulate;
			final Segment sampleVolume;
			switch ( volumeSignature.getSourceStackType() )
			{
			case MULTIRESOLUTION:
				accumulate = templateAccumulateMipBlocks.instantiate();
				sampleVolume = templateVolBlocks.instantiate();
				break;
			case SIMPLE:
				accumulate = templateAccumulateMipSimple.instantiate();
				sampleVolume = volumeSignature.getPixelType() == ARGB
						? templateVolSimpleRGBA.instantiate()
						: templateVolSimple.instantiate();
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
				break;
			case ARGB:
				convert = templateConvertRGBA.instantiate();
				break;
			}

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
			final VolumeSignature volumeSignature = signature.getVolumeSignatures().get( i );
			switch ( volumeSignature.getSourceStackType() )
			{
			case SIMPLE:
				volumeSegments[ i ] = new VolumeSimpleSegment( prog, sampleVolumeSegs[ i ] );
				break;
			case MULTIRESOLUTION:
				volumeSegments[ i ] = new VolumeBlocksSegment( prog, sampleVolumeSegs[ i ] );
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
//		final StringBuilder fragmentShaderCode = prog.getFragmentShaderCode();
//		System.out.println( "fragmentShaderCode = " + fragmentShaderCode );
//		System.out.println( "\n\n--------------------------------\n\n" );
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

		prog.getUniformSampler( "sceneDepth" ).set( depth );
	}

	public void setConverter( int index, ConverterSetup converter )
	{
		converterSegments[ index ].setData( converter );
	}

	public void setVolume( int index, VolumeBlocks volume )
	{
		final VolumeSignature vs = signature.getVolumeSignatures().get( index );
		if ( vs.getSourceStackType() != MULTIRESOLUTION )
			throw new IllegalArgumentException();

		( ( VolumeBlocksSegment ) volumeSegments[ index ] ).setData( volume );
	}

	public void setVolume( int index, SimpleVolume volume )
	{
		final VolumeSignature vs = signature.getVolumeSignatures().get( index );
		if ( vs.getSourceStackType() != SIMPLE )
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

		private final PixelType pixelType;
		private final double rangeScale;

		public ConverterSegment( final SegmentedShader prog, final Segment segment, final PixelType pixelType )
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

			if ( pixelType == ARGB )
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

	interface VolumeSegment
	{}

	static class VolumeBlocksSegment implements VolumeSegment
	{
		private final Uniform3fv uniformBlockScales;
		private final UniformSampler uniformLutSampler;
		private final Uniform3f uniformLutSize;
		private final Uniform3f uniformLutOffset;
		private final UniformMatrix4f uniformIm;
		private final Uniform3f uniformSourcemin;
		private final Uniform3f uniformSourcemax;

		public VolumeBlocksSegment( final SegmentedShader prog, final Segment volume )
		{
			uniformBlockScales = prog.getUniform3fv( volume, "blockScales" );
			uniformLutSampler = prog.getUniformSampler( volume,"lutSampler" );
			uniformLutSize = prog.getUniform3f( volume, "lutSize" );
			uniformLutOffset = prog.getUniform3f( volume, "lutOffset" );
			uniformIm = prog.getUniformMatrix4f( volume, "im" );
			uniformSourcemin = prog.getUniform3f( volume,"sourcemin" );
			uniformSourcemax = prog.getUniform3f( volume,"sourcemax" );
		}

		public void setData( VolumeBlocks blocks )
		{
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

	static class VolumeSimpleSegment implements VolumeSegment
	{
		private final UniformSampler uniformVolumeSampler;
		private final UniformMatrix4f uniformIm;
		private final Uniform3f uniformSourcemax;

		public VolumeSimpleSegment( final SegmentedShader prog, final Segment volume )
		{
			uniformVolumeSampler = prog.getUniformSampler( volume,"volume" );
			uniformIm = prog.getUniformMatrix4f( volume, "im" );
			uniformSourcemax = prog.getUniform3f( volume,"sourcemax" );
		}

		public void setData( SimpleVolume volume )
		{
			uniformVolumeSampler.set( volume.getVolumeTexture() );
			uniformIm.set( volume.getIms() );
			uniformSourcemax.set( volume.getSourceMax() );
		}
	}
}
