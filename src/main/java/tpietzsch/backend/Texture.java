package tpietzsch.backend;

public interface Texture
{
	enum InternalFormat
	{
		R16( 2 );

		InternalFormat( final int bytesPerElement )
		{
			this.bytesPerElement = bytesPerElement;
		}

		public int getBytesPerElement()
		{
			return bytesPerElement;
		}

		private int bytesPerElement;
	}

	enum MagFilter
	{
		NEAREST,
		LINEAR
	}

	enum MinFilter
	{
		NEAREST,
		LINEAR
	}

	enum Wrap
	{
		CLAMP_TO_EDGE,
		REPEAT
	}

	InternalFormat texInternalFormat();

	int texWidth();

	int texHeight();

	int texDepth();

	// whether its a 1D, 2D, or 3D texture
	int texDims();

	MinFilter texMinFilter();

	MagFilter texMagFilter();

	Wrap texWrap();
}
