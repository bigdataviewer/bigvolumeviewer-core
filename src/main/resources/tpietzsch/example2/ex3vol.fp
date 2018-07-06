out vec4 FragColor;
uniform vec2 viewportSize;
uniform mat4 ipv;

uniform sampler3D volumeCache;

// -- comes from CacheSpec -----
uniform vec3 blockSize;
uniform vec3 paddedBlockSize;
uniform vec3 cachePadOffset;

// -- comes from TextureCache --
uniform vec3 cacheSize; // TODO: get from texture!?



uniform float intensity_offset;
uniform float intensity_scale;
vec4 convert( float v )
{
	return vec4( vec3( intensity_offset + intensity_scale * v ), 1 );
}


void main()
{
	// frag coord in NDC
	vec2 uv = 2 * gl_FragCoord.xy / viewportSize - 1;

	// NDC of frag on near and far plane
	vec4 front = vec4( uv, -1, 1 );
	vec4 back = vec4( uv, 1, 1 );

	// calculate eye ray in world space
	vec4 wfront = ipv * front;
	wfront *= 1 / wfront.w;
	vec4 wback = ipv * back;
	wback *= 1 / wback.w;


	// -- bounding box intersection for volume X ---------------------------------------
	float tnear, tfar;
	intersectBoundingBox( wfront, wback, tnear, tfar );
	tnear = max( 0, tnear );
	tfar = min( 1, tfar );
	// -------------------------------------------------------



	if ( tnear < tfar )
	{
		vec4 fb = wback - wfront;
		vec4 wpos = mix( wfront, wback, tnear );
		vec4 wstep = normalize( fb );
		int numSteps = int( trunc( ( tfar - tnear ) * length( fb ) ) + 1 );

		float v = 0;
		for ( int i = 0; i < numSteps; ++i, wpos += wstep )
		{
			float x = blockTexture( wpos, volumeCache, cacheSize, blockSize, paddedBlockSize, cachePadOffset );
			v = max( v, x );
		}
		FragColor = convert( v );
	}
	else
		discard;
}
