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

	MinFilter texMinFilter();

	MagFilter texMagFilter();

	Wrap texWrap();
}
