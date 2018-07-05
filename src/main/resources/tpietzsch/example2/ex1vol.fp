#define NUM_BLOCK_SCALES 10

out vec4 FragColor;

uniform vec2 viewportSize;

uniform mat4 ipvm;

uniform vec3 sourcemin;
uniform vec3 sourcemax;

uniform usampler3D lut;
uniform sampler3D volumeCache;


// -- comes from CacheSpec -----
uniform vec3 blockSize;
uniform vec3 paddedBlockSize;
uniform vec3 cachePadOffset;
// -----------------------------



// -- comes from TextureCache --
uniform vec3 cacheSize; // TODO: get from texture!?
// -----------------------------




// -- comes from LUT -----------
uniform vec3 blockScales[ NUM_BLOCK_SCALES ];
uniform vec3 lutScale;
uniform vec3 lutOffset;
// -----------------------------




uniform float intensity_offset;
uniform float intensity_scale;
vec4 convert( float v )
{
	return vec4( vec3( intensity_offset + intensity_scale * v ), 1 );
}

// intersect ray with a box
// http://www.siggraph.org/education/materials/HyperGraph/raytrace/rtinter3.htm
void intersectBox( vec3 r_o, vec3 r_d, vec3 boxmin, vec3 boxmax, out float tnear, out float tfar )
{
	// compute intersection of ray with all six bbox planes
	vec3 invR = 1 / r_d;
	vec3 tbot = invR * ( boxmin - r_o );
	vec3 ttop = invR * ( boxmax - r_o );

	// re-order intersections to find smallest and largest on each axis
	vec3 tmin = min(ttop, tbot);
	vec3 tmax = max(ttop, tbot);

	// find the largest tmin and the smallest tmax
	tnear = max( max( tmin.x, tmin.y ), max( tmin.x, tmin.z ) );
	tfar = min( min( tmax.x, tmax.y ), min( tmax.x, tmax.z ) );
}

void main()
{
	// frag coord in NDC
	vec2 uv = 2 * gl_FragCoord.xy / viewportSize - 1;

	// NDC of frag on near and far plane
	vec4 front = vec4( uv, -1, 1 );
	vec4 back = vec4( uv, 1, 1 );

	// calculate eye ray in source space
	vec4 mfront = ipvm * front;
	mfront *= 1 / mfront.w;
	vec4 mback = ipvm * back;
	mback *= 1 / mback.w;
	vec3 frontToBack = (mback - mfront).xyz;
	vec3 step = normalize( frontToBack );

	// find intersection with box
	float tnear, tfar;
	intersectBox( mfront.xyz, step, sourcemin, sourcemax, tnear, tfar );
	tnear = max( 0, tnear );
	tfar = min( length( frontToBack ), tfar );
	const float sub = 1;
	if ( tnear < tfar )
	{
		vec3 pos = mfront.xyz + tnear * step + 0.5;
		int numSteps = int( sub * trunc( tfar - tnear ) + 1 );
		vec3 posStep = ( 1 / sub ) * step;
		float v = 0;
		for ( int i = 0; i < numSteps; ++i, pos += posStep )
		{
//			vec3 q = ( pos + blockSize * padSize ) / ( blockSize * lutSize );
			vec3 q = pos * lutScale - lutOffset;

			uvec4 lutv = texture( lut, q );
			vec3 B0 = lutv.xyz * paddedBlockSize + cachePadOffset;
			vec3 sj = blockScales[ lutv.w ];

			vec3 c0 = B0 + mod( pos * sj, blockSize ) + 0.5 * sj;
			                                       // + 0.5 ( sj - 1 )   + 0.5 for tex coord offset
			v = max( v, texture( volumeCache, c0 / cacheSize ).r );
		}
		FragColor = convert( v );
	}
	else
		discard;
}
