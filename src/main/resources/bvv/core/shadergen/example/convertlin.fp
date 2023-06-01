uniform float scale;
uniform float offset;

float convert( float v )
{
	return scale * v + offset;
}

