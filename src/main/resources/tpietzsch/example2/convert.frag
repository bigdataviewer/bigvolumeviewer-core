uniform vec4 offset;
uniform vec4 scale;

vec4 convert( float v )
{
	return offset + scale * v;
}
