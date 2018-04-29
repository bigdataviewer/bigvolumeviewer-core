out vec4 FragColor;

uniform mat4 ip;
uniform mat4 ivm;

uniform vec3 sourcemin;
uniform vec3 sourcemax;

uniform sampler3D scaleLut;
uniform sampler3D offsetLut;
uniform sampler3D volumeCache;

uniform vec3 blockSize;
uniform vec3 lutSize;
uniform vec3 padSize;

uniform float intensity_offset;
uniform float intensity_scale;

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
    const vec2 viewportSize = vec2( 640 * 2, 480 * 2 );

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
        vec3 q = ( pos + padSize ) / ( blockSize * lutSize );
        vec3 qs = texture( scaleLut, q ).xyz;
        vec3 qd = texture( offsetLut, q ).xyz;
        float v = texture( volumeCache, q * qs + qd ).r;
        FragColor = vec4( vec3( intensity_offset + intensity_scale * v ), 1 );
    }
    else
        discard;
}
