uniform vec4 offset;
uniform vec4 scale;

vec4 convert( vec4 v )
{
	return offset + scale * v;
}
