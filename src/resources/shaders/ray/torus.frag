#version 430 compatibility

const uint AREA = 0;
const uint MONO = 1;

struct fragment {
    uint color;
    float depth;
    float ao;
    uint prev;
};

const float doubmax = 1000000000.0; // max (inf)
const uint AND = 1;
const uint OR = 2;
const uint DEBUG = 3;
const vec4 YELLOW = vec4(1.0, 1.0, 0.0, 0.5);

uniform vec4 viewport;
uniform uvec2 window;

// cavity coloring
uniform uint surfaceLabel;
uniform uint cavityColoring;
uniform vec3 cavityColor1;
uniform vec3 cavityColor2;

// tunnel coloring
uniform vec3 tunnelColor;

// cavity selection
uniform bool selectCavity;
uniform uint cavityLabel;

// ambient occlusion & lighting
uniform bool phong;
uniform bool ao;
uniform float lambda;
uniform float volumeSize;
uniform float aoExponent;
uniform float aoThreshold;
uniform bool silhouettes;
uniform bool bfmod;

in vec4 objPos;
in vec4 camPos;
in vec4 lightPos;
in vec4 radii;
in vec4 visibilitySphere;
in vec4 plane1;
in vec4 plane2;
in flat uint operation;
//in flat uint index;
in vec4 color;

in vec3 rotMatT0;
in vec3 rotMatT1;
in vec3 rotMatT2;

in flat uint label;
in flat float area;

uniform sampler3D aoVolumeTex;

layout(std430) buffer ABuffer {
    fragment fragments[];
};

layout(std430) buffer ABufferIndex {
    uint fragCount;
    uint fragIndices[];
};

float acos3(float x);
float curoot(float x);
int quadratic(float b, float c, out vec4 rts, float dis);
float cubic(float p, float q, float r);
int ferrari(float a, float b, float c, float d, out vec4 rts);

void storeIntersection(vec3 intersection, vec3 ray, vec4 color, bool outer);

void storeFragment(vec4 color, float depth, float ao) {
    uvec2 ucoord = uvec2(floor(gl_FragCoord.xy));
    uint index = atomicAdd(fragCount, 1);
    fragments[index].color = packUnorm4x8(color);
    fragments[index].depth = depth;
    fragments[index].ao = ao;
    fragments[index].prev = atomicExchange(fragIndices[ucoord.y * window.x + ucoord.x], index);
}

#define r2 radii.y
#define R radii.z
#define R2 radii.w

void main() {
    // change color if selected
    vec4 col = (selectCavity && (cavityLabel == label)) ? YELLOW : color;

    // transform fragment coordinates from window coordinates to view coordinates.
    vec4 coord = gl_FragCoord
        * vec4(viewport.z, viewport.w, 2.0, 0.0) 
        + vec4(-1.0, -1.0, -1.0, 1.0);

    // transform fragment coordinates from view coordinates to object coordinates.
    coord = gl_ModelViewProjectionMatrixInverse * coord;
    coord /= coord.w;
    coord -= objPos; // ... and to glyph space

    // calc the viewing ray
    vec3 ray = rotMatT0 * coord.x + rotMatT1 * coord.y + rotMatT2 * coord.z;
    ray = normalize(ray - camPos.xyz);

    float r = radii.x;

    // calculate the base point of the ray
    vec3 a = camPos.xyz + (length(camPos.xyz) - (R + r)) * ray;
    
    // compute coefficients of the quartic equation for the ray-torus-intersection
    float K = dot(a, a) - (R2 + r2);
    float A = 4.0 * dot(a, ray);
    float B = 2.0 * (2.0 * dot(a, ray) * dot(a, ray) + K + 2.0 * R2 * ray.z*ray.z);
    float C = 4.0 * (K * dot(a, ray) + 2.0 * R2 * a.z * ray.z);
    float D = K*K + 4.0 * R2 * (a.z*a.z - r2);
    
    vec4 lambdas = vec4(0.0, 0.0, 0.0, 0.0);
    int numRoots = ferrari(A, B, C, D, lambdas);
    if (numRoots < 2) {
        discard;
    }

    /*float second = lambdas.x;
    vec3 intersection = a + ray * second;
    // handle singularity
    if (r > R) {
        float cutRad2 = r2 - R2;
        if (dot(intersection, intersection) < cutRad2) {
            second = lambdas.x;
            if (lambdas.y > second && numRoots > 1) { second = lambdas.y; }
            if (lambdas.z > second && numRoots > 2) { second = lambdas.z; }
            if (lambdas.w > second && numRoots > 3) { second = lambdas.w; }
            intersection = a + ray * second;
        }
    }*/
    // discard fragment if the intersection point lies outside the sphere
    /*if (length(intersection - visibilitySphere.xyz) > visibilitySphere.w) {
        discard;
    }*/
    
    // compute inward-facing normal
    /*vec3 normal;
    //normal = ( intersection - vec3( normalize( intersection.xy), 0.0));
    float factor01 = dot(intersection, intersection) - r2 - R2;
    normal.x = 4.0*intersection.x*factor01;
    normal.y = 4.0*intersection.y*factor01;
    normal.z = 4.0*intersection.z*factor01 + 8.0*R2*intersection.z;
    normal = -normalize(normal);

    // phong lighting with directional light
    vec4 fragColor = 0.2 * color;
    fragColor += 0.8 * -dot(normal, normalize(ray)) * color;
    fragColor.a = min(1.0, color.a / abs(dot(normal, normalize(ray))));
    
    // calculate depth
    vec3 tmp = intersection;
    intersection.x = dot(rotMatT0, tmp.xyz);
    intersection.y = dot(rotMatT1, tmp.xyz);
    intersection.z = dot(rotMatT2, tmp.xyz);

    intersection += objPos.xyz;

    // TODO move after clpping
    vec4 Ding = vec4(intersection, 1.0);
    float depth = dot(gl_ModelViewProjectionMatrixTranspose[2], Ding);
    float depthW = dot(gl_ModelViewProjectionMatrixTranspose[3], Ding);
    float fragDepth = ((depth / depthW) + 1.0) * 0.5;

    // discard fragment if the intersection point lies outside clipping planes
    if (operation == AND) {
        if (dot(plane1.xyz, intersection) < -plane1.w
            || dot(plane2.xyz, intersection) < -plane2.w) {
            //storeFragment(vec4(1.0, 1.0, 0.0, 0.3), fragDepth); // DEBUG
            discard;
        }
    } else if (operation == OR) {
        if (dot(plane1.xyz, intersection) < -plane1.w
            && dot(plane2.xyz, intersection) < -plane2.w) {
            //storeFragment(vec4(1.0, 0.0, 1.0, 0.3), fragDepth); // DEBUG
            discard;
        }
    } else if (operation == DEBUG) {
        storeFragment(vec4(1.0, 0.0, 1.0, 0.7), fragDepth); // DEBUG
        discard;
    } /* else operation == NOP */

    // store fragment to A-buffer
    /*if (0.0 <= fragDepth && fragDepth <= 1.0) {
        storeFragment(fragColor, fragDepth);
    }*/

    /*float tmp;
    // bubble sort - 1 round
    if (numRoots > 1) if (lambdas.x > lambdas.y) { tmp = lambdas.x; lambdas.x = lambdas.y; lambdas.y = tmp; }
    if (numRoots > 2) if (lambdas.y > lambdas.z) { tmp = lambdas.y; lambdas.y = lambdas.z; lambdas.z = tmp; }
    if (numRoots > 3) if (lambdas.z > lambdas.w) { tmp = lambdas.z; lambdas.z = lambdas.w; lambdas.w = tmp; }
    // 2 round
    if (numRoots > 1) if (lambdas.x > lambdas.y) { tmp = lambdas.x; lambdas.x = lambdas.y; lambdas.y = tmp; }
    if (numRoots > 2) if (lambdas.y > lambdas.z) { tmp = lambdas.y; lambdas.y = lambdas.z; lambdas.z = tmp; }
    // 3 round
    if (numRoots > 1) if (lambdas.x > lambdas.y) { tmp = lambdas.x; lambdas.x = lambdas.y; lambdas.y = tmp; }*/

    if (label == surfaceLabel) {
        /*if (numRoots >= 2)*/ storeIntersection(a + ray * lambdas.x, ray, col, false); // second intersection
        /*if (numRoots >= 2)*/ storeIntersection(a + ray * lambdas.y, ray, vec4(1.0, 1.0, 1.0, 0.5), true); // first intersection
        if (numRoots == 4) {
            storeIntersection(a + ray * lambdas.w, ray, vec4(1.0, 1.0, 1.0, 0.5), true); // third intersection
            storeIntersection(a + ray * lambdas.z, ray, col, false); // fourth intersection
        }
    } else {
        /*if (numRoots >= 2)*/ storeIntersection(a + ray * lambdas.x, ray, vec4(1.0, 1.0, 1.0, 0.5), false); // second intersection
        /*if (numRoots >= 2)*/ storeIntersection(a + ray * lambdas.y, ray, col, true); // first intersection
        if (numRoots == 4) {
            storeIntersection(a + ray * lambdas.w, ray, col, true); // third intersection
            storeIntersection(a + ray * lambdas.z, ray, vec4(1.0, 1.0, 1.0, 0.5), false); // fourth intersection
        }
        //if (numRoots == 2) storeIntersection(a + ray * lambdas.x, ray, vec4(0.0, 1.0, 0.0, color.a), true);
    }

    discard;
}

void storeIntersection(vec3 intersection, vec3 ray, vec4 color, bool outer) {
    // handle singularity
    if (radii.x > R) {
        float cutRad2 = r2 - R2;
        if (dot(intersection, intersection) < cutRad2) {
            return;
        }
    }
    
    // discard fragment if the intersection point lies outside the sphere
    if (length(intersection - visibilitySphere.xyz) > visibilitySphere.w) {
        return;
    }

    // compute inward-facing normal
    vec3 normal;
    //normal = ( intersection - vec3( normalize( intersection.xy), 0.0));
    float factor01 = dot(intersection, intersection) - r2 - R2;
    normal.x = 4.0 * intersection.x * factor01;
    normal.y = 4.0 * intersection.y * factor01;
    normal.z = 4.0 * intersection.z * factor01 + 8.0 * R2 * intersection.z;
    normal = -normalize(normal);
    if (outer) {
        normal = -normal;
    }

    if (label != surfaceLabel) {
        outer = !outer;
        // color cavity
        if (cavityColoring == MONO) {
            color.rgb = cavityColor1;
        } else {
            color.rgb = mix(cavityColor1, cavityColor2, area);
        }
    }

    // calculate world intersection
    vec3 tmp = intersection;
    intersection.x = dot(rotMatT0, tmp.xyz);
    intersection.y = dot(rotMatT1, tmp.xyz);
    intersection.z = dot(rotMatT2, tmp.xyz);

    intersection += objPos.xyz;

    vec3 worldNormal;
    worldNormal.x = dot(rotMatT0, normal.xyz);
    worldNormal.y = dot(rotMatT1, normal.xyz);
    worldNormal.z = dot(rotMatT2, normal.xyz);
    float aoFactor = texture3D(aoVolumeTex, (intersection + lambda * worldNormal) / volumeSize).r;
    if (label == surfaceLabel && aoFactor > 0.9) {
        color.rgb = tunnelColor;
    }

    // phong lighting with directional light
    vec4 fragColor = color;
    if (!outer && phong) {
        fragColor.rgb = 0.2 * color.rgb;
        fragColor.rgb += 0.8 * -dot(normal, ray) * color.rgb;
    } else if (outer && phong) {
        fragColor.rgb = 0.2 * color.rgb;
        fragColor.rgb += 0.4 * -dot(normal, ray) * color.rgb;
    } else if (outer && !phong && label == surfaceLabel) {
        fragColor = vec4(0.6, 0.6, 0.6, 0.5);
    }

    float silhouetteFactor = dot(normal, -ray) + 1.0;
    float bfmodFactor = 1.0 - dot(normal, -ray /* + C */);

    if (ao) {
        /*if (!outer) {
            fragColor.rgb *= 0.5 * (1.0 + max(1.0 - aoFactor, 0.0));
        }*/
        //fragColor.a = min(color.a + aoFactor, 1.0);
        fragColor.a = min(pow(aoFactor / aoThreshold, aoExponent), 1.0);
    }

    if (silhouettes) {
        //fragColor.a = min(1.0, color.a / abs(dot(normal, ray)));
        fragColor.a = pow(fragColor.a, silhouetteFactor);
    }

    if (outer && bfmod) {
        fragColor.a = fragColor.a * bfmodFactor;
    }

    // TODO move after clpping
    vec4 Ding = vec4(intersection, 1.0);
    float depth = dot(gl_ModelViewProjectionMatrixTranspose[2], Ding);
    float depthW = dot(gl_ModelViewProjectionMatrixTranspose[3], Ding);
    float fragDepth = ((depth / depthW) + 1.0) * 0.5;

    // discard fragment if the intersection point lies outside clipping planes
    if (operation == AND) {
        if (dot(plane1.xyz, intersection) < -plane1.w
            || dot(plane2.xyz, intersection) < -plane2.w) {
            //storeFragment(vec4(1.0, 1.0, 0.0, 0.3), fragDepth); // DEBUG
            return;
        }
    } else if (operation == OR) {
        if (dot(plane1.xyz, intersection) < -plane1.w
            && dot(plane2.xyz, intersection) < -plane2.w) {
            //storeFragment(vec4(1.0, 0.0, 1.0, 0.3), fragDepth); // DEBUG
            return;
        }
    } else if (operation == DEBUG) {
        //storeFragment(vec4(1.0, 0.0, 1.0, 0.7), fragDepth); // DEBUG
        return;
    } /* else operation == NOP */

    // store fragment to A-buffer
    if (0.0 <= fragDepth && fragDepth <= 1.0) {
        storeFragment(fragColor, depthW /*fragDepth*/, aoFactor);
    }
}

int ferrari(float a, float b, float c, float d, out vec4 rts) {
    rts = vec4(0.0, 0.0, 0.0, 0.0);
    
    int nquar, n1, n2;
    float asq, ainv2;
    vec4 v1, v2;
    float p, q, r;
    float y;
    float e, f, esq, fsq, ef;
    float g, gg, h, hh;

    asq = a*a;

    p = b;
    q = a * c - 4.0 * d;
    r = (asq - 4.0 * b) * d + c*c;
    y = cubic(p, q, r);

    esq = 0.25 * asq - b - y;
    if (esq < 0.0) {
        return 0;
    }
    else {
        fsq = 0.25*y*y - d;
        if (fsq < 0.0) {
            return 0;
        }
        else
        {
            ef = -(0.25*a*y + 0.5*c);
            if (((a > 0.0) && (y > 0.0) && (c > 0.0))
                || ((a > 0.0) && (y < 0.0) && (c < 0.0))
                || ((a < 0.0) && (y > 0.0) && (c < 0.0))
                || ((a < 0.0) && (y < 0.0) && (c > 0.0))
                ||  (a == 0.0) || (y == 0.0) || (c == 0.0))
            // use ef
            {
                if ( (b < 0.0) && (y < 0.0) && (esq > 0.0) )
                {
                    e = sqrt( esq);
                    f = ef/e;
                }
                else if( (d < 0.0) && (fsq > 0.0) )
                {
                    f = sqrt( fsq);
                    e = ef/f;
                }
                else
                {
                    e = sqrt( esq);
                    f = sqrt( fsq);
                    if( ef < 0.0 ) f = -f;
                }
            }
            else
            {
                e = sqrt( esq);
                f = sqrt( fsq);
                if( ef < 0.0 ) f = -f;
            }
            // note that e >= 0.0
            ainv2 = a*0.5;
            g = ainv2 - e;
            gg = ainv2 + e;
            if( ((b > 0.0) && (y > 0.0))
                || ((b < 0.0) && (y < 0.0)) )
            {
                if( ( a > 0.0) && (e != 0.0) )
                    g = (b + y)/gg;
                else if( e != 0.0 )
                    gg = (b + y)/g;
            }
            if( (y == 0.0) && (f == 0.0) )
            {
                h = 0.0;
                hh = 0.0;
            }
            else if( ((f > 0.0) && (y < 0.0))
                || ((f < 0.0) && (y > 0.0)) )
            {
                hh = -0.5*y + f;
                h = d/hh;
            }
            else
            {
                h = -0.5*y - f;
                hh = d/h;
            }
            n1 = quadratic( gg, hh, v1, gg*gg - 4.0 * hh);
            n2 = quadratic( g, h, v2, g*g - 4.0 * h);
            nquar = n1 + n2;
            rts.x = v1.x;
            rts.y = v1.y;
            if( n1 == 0 )
            {
                rts.x = v2.x;
                rts.y = v2.y;
            }
            else
            {
                rts.z = v2.x;
                rts.w = v2.y;
            }
            return nquar;
        }
    }
}

float cubic(float p, float q, float r) {    
    int nrts;
    float po3, po3sq, qo3;
    float uo3, u2o3, uo3sq4, uo3cu4;
    float v, vsq, wsq;
    float m, mcube, n;
    float muo3, s, scube, t, cosk, sinsqk;
    float root;

    m = 0.0;
    nrts = 0;
    if( (p > doubmax) || (p <  -doubmax) )
        root = -p;
    else
    {
        if( (q > doubmax) || (q <  -doubmax) )
        {
            if (q > 0.0)
                root = -r/q;
            else
                root = -sqrt( -q);
        }
        else
        {
            if( (r > doubmax) || (r <  -doubmax) )
                root = -curoot( r);
            else
            {
                po3 = p * (1.0/3.0);
                po3sq = po3*po3 ;
                if( po3sq > doubmax )
                    root = -p;
                else
                {
                    v = r + po3*(po3sq + po3sq - q) ;
                    if( (v > doubmax) || (v < -doubmax) )
                        root = -p;
                    else
                    {
                        vsq = v*v ;
                        qo3 = q * (1.0/3.0);
                        uo3 = qo3 - po3sq ;
                        u2o3 = uo3 + uo3 ;
                        if( (u2o3 > doubmax) || (u2o3 < -doubmax) )
                        {
                            if (p == 0.0)
                            {
                                if (q > 0.0)
                                    root = -r/q;
                                else
                                    root = -sqrt( -q);
                            }
                            else
                                root = -q/p;
                        }
                        uo3sq4 = u2o3 * u2o3 ;
                        if( uo3sq4 > doubmax)
                        {
                            if (p == 0.0)
                            {
                                if( q > 0.0 )
                                    root = -r/q;
                                else
                                    root = -sqrt( abs( q));
                            }
                            else
                                root = -q/p;
                        }
                        uo3cu4 = uo3sq4 * uo3;
                        wsq = uo3cu4 + vsq;
                        if( wsq >= 0.0 )
                        {
                            // cubic has one real root
                            nrts = 1;
                            if( v <= 0.0 )
                                mcube = ( -v + sqrt( wsq))*0.5;
                            if( v  > 0.0 )
                                mcube = ( -v - sqrt( wsq))*0.5;
                            m = curoot( mcube);
                            if( m != 0.0 )
                                n = -uo3/m;
                            else
                                n = 0.0;
                            root = m + n - po3;
                        }
                        else
                        {
                            nrts = 3;
                            // cubic has three real roots
                            if( uo3 < 0.0 )
                            {
                                muo3 = -uo3;
                                s = sqrt( muo3);
                                scube = s*muo3;
                                t =  -v/(scube+scube);
                                cosk = acos3( t);
                                if( po3 < 0.0 )
                                    root = (s+s)*cosk - po3;
                                else
                                {
                                    sinsqk = 1.0 - cosk*cosk;
                                    if( sinsqk < 0.0 )
                                        sinsqk = 0.0;
                                    root = s*( -cosk - sqrt( 3.0)*sqrt( sinsqk)) - po3;
                                }
                            }
                            else
                                // cubic has multiple root
                                root = curoot( v) - po3;
                        }
                    }
                }
            }
        }
    }
    return root;
}

int quadratic(float b, float c, out vec4 rts, float dis) {
    int nquad;
    float rtdis;

    if (dis >= 0.0) {
        nquad = 2;
        rtdis = sqrt(dis);
        if (b > 0.0) {
            rts.x = (-b - rtdis) * 0.5;
        } else {
            rts.x = (-b + rtdis) * 0.5;
        }
        if (rts.x == 0.0) {
            rts.y = -b;
        } else {
            rts.y = c / rts.x;
        }
    } else {
        nquad = 0;
        rts.x = 0.0;
        rts.y = 0.0;
    }
    return nquad;
}

float curoot(float x) {
    int neg = 0;
    float absx = x;
    if (x < 0.0) {
        absx = -x;
        neg = 1;
    }
    
    float value = exp(log(absx) * (1.0 / 3.0));
    if (neg == 1) {
        value = -value;
    }
    
    return value;
}

float acos3(float x) {
    return cos(acos(x) * (1.0 / 3.0));
}