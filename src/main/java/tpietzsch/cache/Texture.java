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

//		gl.glTexStorage3D( GL_TEXTURE_3D, 1, GL_R16, w, h, d );
//		gl.glTexParameteri( GL_TEXTURE_3D, GL_TEXTURE_MIN_FILTER, GL_LINEAR );
//		gl.glTexParameteri( GL_TEXTURE_3D, GL_TEXTURE_MAG_FILTER, GL_LINEAR );
//		gl.glTexParameteri( GL_TEXTURE_3D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE );
//		gl.glTexParameteri( GL_TEXTURE_3D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE );
//		gl.glTexParameteri( GL_TEXTURE_3D, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE );
}
