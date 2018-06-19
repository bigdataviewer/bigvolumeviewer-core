uniform float intensity_offset;
uniform float intensity_scale;

vec4 convert( float v )
{
	return vec4( vec3( intensity_offset + intensity_scale * v ), 1 );
}
