#define NUM_BLOCK_SCALES 10

out vec4 FragColor;

uniform vec2 viewportSize;

uniform mat4 ip;
uniform mat4 ivm;

uniform vec3 sourcemin;
uniform vec3 sourcemax;

uniform usampler3D lut;
uniform sampler3D volumeCache;

uniform vec3 blockSize;
uniform vec3 paddedBlockSize;
uniform vec3 cachePadOffset;
uniform vec3 cacheSize; // TODO: get from texture!?
uniform vec3 blockScales[ NUM_BLOCK_SCALES ];
//uniform vec3 lutSize; // TODO: get from texture!?
//uniform vec3 padSize;

uniform vec3 lutScale;
uniform vec3 lutOffset;


uniform float intensity_offset;
uniform float intensity_scale;
vec4 convert( float v )
{
	return vec4( vec3( intensity_offset + intensity_scale * v ), 1 );
}

vec4 intersectScreenPlane( vec3 r_o, vec3 r_d )
{
    float t = -r_o.z / r_d.z;
    return vec4( r_o + t * r_d, 1 );
}

bool contained( vec3 pos, vec3 min, vec3 max )
{
    return
        all ( greaterThanEqual( pos, sourcemin ) ) &&
        all ( lessThanEqual( pos, sourcemax ) );
}

void main()
{
    // frag coord in NDC
    vec2 uv = 2 * gl_FragCoord.xy / viewportSize - 1;

    // NDC of frag on near and far plane
    vec4 front = vec4( uv, -1, 1 );
    vec4 back = vec4( uv, 1, 1 );

    // calculate eye ray in source space
    vec4 mfront = ip * front;
    mfront *= 1 / mfront.w;
    vec4 mback = ip * back;
    mback *= 1 / mback.w;
    vec3 step = normalize( (mback - mfront).xyz );

    // find intersection with screen plane
    vec4 intersection = intersectScreenPlane( mfront.xyz, step );
    vec3 pos = ( ivm * intersection ).xyz;

    if ( contained( pos, sourcemin, sourcemax ) )
    {
//		vec3 q = ( pos + blockSize * padSize ) / ( blockSize * lutSize );
		vec3 q = pos * lutScale + lutOffset;

		uvec4 lutv = texture( lut, q );
		vec3 B0 = lutv.xyz * paddedBlockSize + cachePadOffset;
		vec3 sj = blockScales[ lutv.w ];

		vec3 c0 = B0 + mod( pos * sj, blockSize ) + 0.5 * sj;
		                                       // + 0.5 ( sj - 1 )   + 0.5 for tex coord offset
        float v = texture( volumeCache, c0 / cacheSize ).r;
		FragColor = convert( v );
    }
    else
        discard;
}
