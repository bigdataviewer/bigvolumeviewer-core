package tpietzsch.example2;

import net.imglib2.display.ColorConverter;
import net.imglib2.type.numeric.ARGBType;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import tpietzsch.backend.GpuContext;
import tpietzsch.backend.Texture2D;
import tpietzsch.cache.CacheSpec;
import tpietzsch.cache.TextureCache;
import tpietzsch.dither.DitherBuffer;
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

public class MultiVolumeShaderMip8
{
	private static final int NUM_BLOCK_SCALES = 10;

	private final int numVolumes;

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

	public MultiVolumeShaderMip8( final int numVolumes, final boolean useDepthTexture, final double degrade )
	{
		this.numVolumes = numVolumes;
		this.useDepthTexture = useDepthTexture;
		this.degrade = degrade;

		final SegmentedShaderBuilder builder = new SegmentedShaderBuilder();
		final Segment vp = new SegmentTemplate("ex8vol.vp" ).instantiate();
		builder.vertex( vp );

		final SegmentTemplate templateIntersectBox = new SegmentTemplate(
				"intersectbox.fp" );
		builder.fragment( templateIntersectBox.instantiate() );
		final SegmentTemplate templateBlkVol = new SegmentTemplate(
				"blkvol.fp",
				"im", "sourcemin", "sourcemax", "intersectBoundingBox",
				"lutSampler", "blockScales", "lutScale", "lutOffset", "blockTexture" );
		final SegmentTemplate templateColConv = new SegmentTemplate(
				"colconv.fp",
				"convert", "offset", "scale" );
		final SegmentTemplate templateMaxDepth = new SegmentTemplate(
				useDepthTexture ? "maxdepthtexture.fp" : "maxdepthone.fp" );
		builder.fragment( templateMaxDepth.instantiate() );
		final SegmentTemplate templateFp = new SegmentTemplate(
				"ex8vol.fp",
				"intersectBoundingBox", "blockTexture", "convert", "vis" );
		final Segment fp = templateFp.instantiate();
		fp.repeat( "vis", numVolumes );
		final Segment blkVols[] = new Segment[ numVolumes ];
		final Segment colConvs[] = new Segment[ numVolumes ];
		for ( int i = 0; i < numVolumes; ++i )
		{
			final Segment blkVol = templateBlkVol.instantiate();
			builder.fragment( blkVol );
			fp.bind( "intersectBoundingBox", i, blkVol, "intersectBoundingBox" );
			fp.bind( "blockTexture", i, blkVol, "blockTexture" );
			blkVols[ i ] = blkVol;

			final Segment colConv = templateColConv.instantiate();
			builder.fragment( colConv );
			fp.bind( "convert", i, colConv, "convert" );
			colConvs[ i ] = colConv;
		}
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
			volumeSegments[ i ] = new VolumeSegment( prog, blkVols[ i ] );
			converterSegments[ i ] = new ConverterSegment( prog, colConvs[ i ] );
		}

		uniformTransform = prog.getUniformMatrix4f( "transform" );
		uniformDsp = prog.getUniform2f( "dsp" );

		uniformTransform.set( new Matrix4f() );

//		final StringBuilder vertexShaderCode = prog.getVertexShaderCode();
//		System.out.println( "vertexShaderCode = " + vertexShaderCode );
//		System.out.println( "\n\n--------------------------------\n\n" );
//		final StringBuilder fragementShaderCode = prog.getFragementShaderCode();
//		System.out.println( "fragementShaderCode = " + fragementShaderCode );
//		System.out.println( "\n\n--------------------------------\n\n" );
	}

	public int getNumVolumes()
	{
		return numVolumes;
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

	public void setConverter( int index, ColorConverter converter )
	{
		converterSegments[ index ].setData( converter );
	}

	public void setVolume( int index, VolumeBlocks volume )
	{
		volumeSegments[ index ].setData( volume );
	}

	public void setDither( DitherBuffer dither, int step )
	{
		uniformViewportSize.set( dither.effectiveViewportWidth(), dither.effectiveViewportHeight() );
		uniformTransform.set( dither.ndcTransform( step ) );
		uniformDsp.set( dither.fragShift( step ) );
	}

	public void setProjectionViewMatrix( final Matrix4fc pv )
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

		final double sNear = adx.sub( a ).length();
		final double sFar = cdx.sub( c ).length();
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
		prog.bindSamplers( context );
		prog.setUniforms( context );
	}

	static class ConverterSegment
	{
		private final Uniform4f uniformOffset;
		private final Uniform4f uniformScale;

		public ConverterSegment( final SegmentedShader prog, final Segment segment )
		{
			uniformOffset = prog.getUniform4f( segment,"offset" );
			uniformScale = prog.getUniform4f( segment,"scale" );
		}

		public void setData( ColorConverter converter )
		{
			final double fmin = converter.getMin() / 0xffff;
			final double fmax = converter.getMax() / 0xffff;
			final double s = 1.0 / ( fmax - fmin );
			final double o = -fmin * s;

			final int color = converter.getColor().get();
			final double r = ( double ) ARGBType.red( color ) / 255.0;
			final double g = ( double ) ARGBType.green( color ) / 255.0;
			final double b = ( double ) ARGBType.blue( color ) / 255.0;

//			final double l = 0.2126 * r + 0.7152 * g + 0.0722 * b;
			final double l = 0.299 * r + 0.587 * g + 0.114 * b;

			uniformOffset.set(
					( float ) ( o * r ),
					( float ) ( o * g ),
					( float ) ( o * b ),
					( float ) ( o * l ) );
			uniformScale.set(
					( float ) ( s * r ),
					( float ) ( s * g ),
					( float ) ( s * b ),
					( float ) ( s * l ) );
		}
	}

	static class VolumeSegment
	{
		private final Uniform3fv uniformBlockScales;
		private final UniformSampler uniformLutSampler;
		private final Uniform3f uniformLutScale;
		private final Uniform3f uniformLutOffset;
		private final UniformMatrix4f uniformIm;
		private final Uniform3f uniformSourcemin;
		private final Uniform3f uniformSourcemax;

		public VolumeSegment( final SegmentedShader prog, final Segment volume )
		{
			uniformBlockScales = prog.getUniform3fv( volume, "blockScales" );
			uniformLutSampler = prog.getUniformSampler( volume,"lutSampler" );
			uniformLutScale = prog.getUniform3f( volume, "lutScale" );
			uniformLutOffset = prog.getUniform3f( volume, "lutOffset" );
			uniformIm = prog.getUniformMatrix4f( volume, "im" );
			uniformSourcemin = prog.getUniform3f( volume,"sourcemin" );
			uniformSourcemax = prog.getUniform3f( volume,"sourcemax" );
		}

		public void setData( VolumeBlocks blocks )
		{
			uniformBlockScales.set( blocks.getLutBlockScales( NUM_BLOCK_SCALES ) );
			uniformLutSampler.set( blocks.getLookupTexture() );
			uniformLutScale.set( blocks.getLutScale() );
			uniformLutOffset.set( blocks.getLutOffset() );
			uniformIm.set( blocks.getIms() );
			uniformSourcemin.set( blocks.getSourceLevelMin() );
			uniformSourcemax.set( blocks.getSourceLevelMax() );
		}
	}
}
