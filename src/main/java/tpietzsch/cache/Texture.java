package tpietzsch.cache;

public interface Texture
{
	enum InternalFormat
	{
		R16
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
